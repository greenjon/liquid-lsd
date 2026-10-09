// evaluator.js
// Port of the desktop modulation evaluator (ModulatableParameter.evaluate, cv/Evaluators.kt,
// parameters/WaveformMath.kt). Desktop stays the source of truth: when those change, regenerate
// web/tools/evaluator_vectors.json (see WebEvaluatorVectorsTest) and run `node --test web/tools/*.test.mjs`.
//
// Parameters are ParameterDto-shaped objects ({baseValue, baseMin, baseMax, randomizeBase, modulators[]}),
// modulators are ModulatorDto-shaped. A bare number is accepted as a parameter with no modulators.
// Anything the desktop would not accept (unknown operator/waveform/genUnit) is skipped, never thrown.

export const OPERATORS = ['ADD', 'MUL', 'SCALE'];
export const WAVEFORMS = ['SINE', 'TRIANGLE', 'SQUARE', 'RANDOM'];
export const GEN_UNITS = ['TIME', 'BEAT', 'FRAME'];
export const GEN_MOD_MODES = ['NONE', 'AM', 'PM', 'ADD'];

// CV sources the desktop CVRegistry registers (beatPhase / sampleAndHold are legacy and skipped there too).
// midi_cc_* are not available on the web and are skipped.
const GENERATORS = new Set(['lfo', 'seq']);
const BIPOLAR_SOURCES = new Set(['lfo', 'beatSine']);
const AUDIO_SOURCES = new Set([
  'audio_amp', 'audio_bass', 'audio_mid', 'audio_high',
  'audio_flux_amp', 'audio_flux_bass', 'audio_flux_mid', 'audio_flux_high',
]);

/**
 * Clamp range and step count for an ISF input, as ISFVisualSource.createParameters / ISFInput.discreteSteps
 * derive them. Returns null for inputs that are not numeric parameters.
 */
export function paramSpec(input) {
  const type = String(input.TYPE).toLowerCase();
  if (type !== 'float' && type !== 'long' && type !== 'int') return null;
  const values = Array.isArray(input.VALUES) ? input.VALUES.filter((v) => typeof v === 'number') : null;
  const min = typeof input.MIN === 'number' ? input.MIN : (values?.length ? Math.min(...values) : 0);
  const max = typeof input.MAX === 'number' ? input.MAX : (values?.length ? Math.max(...values) : 1);
  let steps = null;
  if (type === 'float') {
    const step = Number(input.STEP);
    if (input.STEP !== undefined && Number.isFinite(step) && step > 0) {
      const n = (max - min) / step;
      if (n >= 1 && Math.abs(n - Math.round(n)) < 1e-3) steps = Math.round(n) + 1;
    }
  } else {
    const byValues = Array.isArray(input.VALUES) && input.VALUES.length >= 2 ? input.VALUES.length : null;
    const span = max - min;
    const bySpan = span >= 1 && span === Math.round(span) ? Math.round(span) + 1 : null;
    steps = byValues ?? bySpan;
  }
  return { min, max, steps };
}

/** Per-frame inputs to every evaluation. `followers` carries envelope-follower state between frames. */
export function makeEvalContext() {
  return { time: 0, beats: 0, frame: 0, dt: 1 / 60, bpm: 120, cv: {}, followers: new WeakMap() };
}

// ---------------------------------------------------------------- JVM-compatible hashing and RNG
// RANDOM waveforms seed from hashCodes of the modulator's fields, so those must match the JVM exactly.

const _buf = new DataView(new ArrayBuffer(8));

function hashString(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(h, 31) + s.charCodeAt(i)) | 0;
  return h;
}
function hashFloat(x) { // Float.hashCode == floatToIntBits
  if (Number.isNaN(x)) return 0x7fc00000;
  _buf.setFloat32(0, x);
  return _buf.getInt32(0);
}
function hashDouble(x) { // Double.hashCode == bits ^ (bits >>> 32)
  _buf.setFloat64(0, x);
  return _buf.getInt32(0) ^ _buf.getInt32(4);
}

const M1 = BigInt.asUintN(64, -4906477898972856333n); // the Long literals from Evaluators.kt
const M2 = BigInt.asUintN(64, -4265267296055433173n);
const MASK64 = (1n << 64n) - 1n;

/** randomFloatFromSeed(seed: Long): Float in [-1, 1), the splitmix-style hash from Evaluators.kt. */
function randomFloatFromSeed(seedInt) {
  let x = BigInt.asUintN(64, BigInt(seedInt)); // Int.toLong() sign-extends; asUintN keeps the bit pattern
  x ^= x >> 33n;
  x = (x * M1) & MASK64;
  x ^= x >> 33n;
  x = (x * M2) & MASK64;
  x ^= x >> 33n;
  const bits = Number((x >> 40n) & 0xffffffn);
  return (bits / 16777216) * 2 - 1;
}

const toInt = (v) => Math.max(-2147483648, Math.min(2147483647, Math.floor(v))); // Double.toInt() saturates
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
const F = Math.fround;

// ---------------------------------------------------------------- waveforms (WaveformMath.kt, Evaluators.kt)

function smoothShape(shaped, morph) {
  const k = 1.5 + (15 - 1.5) * morph;
  const maxVal = Math.log(Math.cosh(k)) / k;
  if (shaped >= 0) {
    const u = 1 - shaped;
    return 1 - (Math.log(Math.cosh(k * u)) / k) / maxVal;
  }
  const u = 1 + shaped;
  return -1 + (Math.log(Math.cosh(k * u)) / k) / maxVal;
}

export function calculateAdvancedLFO(phase, morph, hold, slope, waveform = 'SINE') {
  const safeHold = clamp(hold, 0, 0.999);
  const divisor = Math.max(1 - safeHold, 0.0001);
  let shaped;
  if (waveform === 'SQUARE') {
    const duty = clamp(slope, 0.001, 0.999);
    const vTh = 1 - 2 * duty;
    const shifted = (phase + (1 - duty) * 0.5) % 1.0;
    const pos = shifted < 0 ? shifted + 1 : shifted;
    const triRaw = pos < 0.5 ? pos / 0.5 : (1 - pos) / 0.5;
    shaped = clamp(((triRaw * 2 - 1) - vTh) / divisor, -1, 1);
  } else {
    let triRaw;
    if (slope <= 0.001) triRaw = clamp(1 - phase, 0, 1);
    else if (slope >= 0.999) triRaw = clamp(phase, 0, 1);
    else triRaw = phase < slope ? phase / slope : (1 - phase) / (1 - slope);
    shaped = clamp((triRaw * 2 - 1) / divisor, -1, 1);
  }
  if (morph >= 0.999) return shaped;
  return smoothShape(shaped, morph);
}

function calculateRandomWaveform(positivePhase, morph, hold, previousValue, currentValue) {
  const slideDuration = 1 - clamp(hold, 0, 0.999);
  const tSlide = positivePhase < slideDuration ? clamp(positivePhase / slideDuration, 0, 1) : 1;
  const result = smoothShape(tSlide * 2 - 1, morph);
  const t = (result + 1) / 2;
  return previousValue + (currentValue - previousValue) * t;
}

/** One generator voice: cycle position from the clock, then waveform (RANDOM interpolates seeded values per cycle). */
function generatorValue(ctx, mod, p, pmShift) {
  let cyclePosition;
  let period;
  if (p.unit === 'TIME') {
    period = Math.max(p.subdivision, 0.001);
    cyclePosition = ctx.time / period + p.phaseOffset + pmShift;
  } else if (p.unit === 'BEAT') {
    period = Math.max(p.subdivision, 0.01);
    cyclePosition = ctx.beats / period + p.phaseOffset + pmShift;
  } else {
    period = Math.max(p.subdivision, 1.0);
    cyclePosition = ctx.frame / period + p.phaseOffset + pmShift;
  }
  const phase = cyclePosition % 1.0;
  const positive = phase < 0 ? phase + 1 : phase;
  if (p.waveform === 'RANDOM') {
    const seed = hashDouble(period) ^ hashFloat(p.phaseOffset) ^ hashString(mod.sourceId) ^ p.seedSalt ^ hashString(mod.id ?? '');
    const cur = toInt(cyclePosition);
    const currentValue = randomFloatFromSeed((cur + seed) | 0);
    const previousValue = randomFloatFromSeed((cur - 1 + seed) | 0);
    return calculateRandomWaveform(positive, p.morph, p.hold, previousValue, currentValue);
  }
  return calculateAdvancedLFO(positive, p.morph, p.hold, p.slope, p.waveform);
}

// ---------------------------------------------------------------- modulators

export function validateModulator(mod) {
  const problems = [];
  if (!mod || typeof mod !== 'object') return ['modulator is not an object'];
  if (typeof mod.sourceId !== 'string') problems.push('sourceId missing');
  if (!OPERATORS.includes(mod.operator ?? 'ADD')) problems.push(`operator ${mod.operator}`);
  if (!WAVEFORMS.includes(mod.waveform ?? 'SINE')) problems.push(`waveform ${mod.waveform}`);
  if (!GEN_UNITS.includes(mod.genUnit ?? 'TIME')) problems.push(`genUnit ${mod.genUnit}`);
  if (!GEN_UNITS.includes(mod.modGenUnit ?? 'TIME')) problems.push(`modGenUnit ${mod.modGenUnit}`);
  if (!WAVEFORMS.includes(mod.modWaveform ?? 'SINE')) problems.push(`modWaveform ${mod.modWaveform}`);
  if (!GEN_MOD_MODES.includes(mod.generatorModMode ?? 'NONE')) problems.push(`generatorModMode ${mod.generatorModMode}`);
  return problems;
}

function isKnownSource(ctx, id) {
  return GENERATORS.has(id) || id === 'beatSine' || id === 'bpm' || AUDIO_SOURCES.has(id) || id in ctx.cv;
}

function evalLfo(ctx, mod) {
  const mode = mod.generatorModMode ?? 'NONE';
  const genDepth = F(mod.generatorModDepth ?? 1.0);
  let modVal = 0;
  if (mode !== 'NONE') {
    modVal = generatorValue(ctx, mod, {
      unit: mod.modGenUnit ?? 'TIME',
      subdivision: F(mod.modSubdivision ?? 1.0),
      phaseOffset: F(mod.modPhaseOffset ?? 0.0),
      waveform: mod.modWaveform ?? 'SINE',
      morph: F(mod.modMorph ?? 0.0),
      hold: F(mod.modHold ?? 0.0),
      slope: F(mod.modSlope ?? 0.5),
      seedSalt: 999,
    }, 0);
  }
  const pmShift = mode === 'PM' ? modVal * genDepth : 0;
  const carrier = generatorValue(ctx, mod, {
    unit: mod.genUnit ?? 'TIME',
    subdivision: F(mod.subdivision ?? 1.0),
    phaseOffset: F(mod.phaseOffset ?? 0.0),
    waveform: mod.waveform ?? 'SINE',
    morph: F(mod.morph ?? 0.0),
    hold: F(mod.hold ?? 0.0),
    slope: F(mod.slope ?? 0.5),
    seedSalt: 0,
  }, pmShift);
  if (mode === 'AM') return carrier * (1 + modVal * genDepth);
  if (mode === 'ADD') return carrier + modVal * genDepth;
  return carrier;
}

function evalSeq(ctx, mod) {
  const stepCount = clamp(Math.trunc(mod.seqStepCount ?? 16), 1, 32);
  const steps = mod.seqSteps?.length ? mod.seqSteps : new Array(32).fill(0);
  const unit = mod.genUnit ?? 'TIME';
  const sub = F(mod.subdivision ?? 1.0);
  const phaseOffset = F(mod.phaseOffset ?? 0.0);
  let cyclePosition;
  if (unit === 'TIME') cyclePosition = ctx.time / Math.max(sub, 0.001) + phaseOffset;
  else if (unit === 'BEAT') cyclePosition = ctx.beats / Math.max(sub, 0.001) + phaseOffset;
  else cyclePosition = ctx.frame / Math.max(sub, 1.0) + phaseOffset;

  const raw = Math.floor(cyclePosition);
  const cur = ((raw % stepCount) + stepCount) % stepCount;
  const next = (cur + 1) % stepCount;
  const curVal = cur < steps.length ? F(steps[cur]) : 0;
  const nextVal = next < steps.length ? F(steps[next]) : 0;
  const stepFrac = clamp(cyclePosition - raw, 0, 1);
  const hold = clamp(F(mod.seqHold ?? 1.0), 0, 1);
  if (hold >= 0.999 || stepFrac < hold) return curVal;
  const t = clamp((stepFrac - hold) / (1 - hold), 0, 1);
  const u = mod.seqCurveSmooth ? 0.5 - 0.5 * Math.cos(t * Math.PI) : t;
  return curVal + (nextVal - curVal) * u;
}

function follow(ctx, mod, input) {
  const attackMs = F(mod.attackMs ?? 0), decayMs = F(mod.decayMs ?? 0);
  if ((mod.followerMode ?? 'RAW') === 'RAW' || (attackMs <= 0 && decayMs <= 0)) return input;
  let state = ctx.followers.get(mod);
  if (!state) { state = { value: input }; ctx.followers.set(mod, state); }
  const dt = clamp(ctx.dt, 0.0001, 0.2);
  const attTau = Math.max(attackMs / 1000, 0.001);
  const decTau = Math.max(decayMs / 1000, 0.001);
  const cur = state.value;
  let next;
  if (input > cur) next = attackMs <= 0.5 ? input : cur + (1 - Math.exp(-dt / attTau)) * (input - cur);
  else next = decayMs <= 0.5 ? input : cur + (1 - Math.exp(-dt / decTau)) * (input - cur);
  state.value = next;
  return next;
}

/** Raw CV value of a modulator's source, before depth / offset (Evaluators.kt evaluateModulatorAtOffset). */
export function evaluateModulator(mod, ctx) {
  switch (mod.sourceId) {
    case 'lfo': return evalLfo(ctx, mod);
    case 'seq': return evalSeq(ctx, mod);
    case 'beatSine': return ctx.cv.beatSine ?? Math.sin(ctx.beats * 2 * Math.PI);
    case 'bpm': return ctx.cv.bpm ?? ctx.bpm;
    default: {
      const raw = ctx.cv[mod.sourceId] ?? 0;
      return AUDIO_SOURCES.has(mod.sourceId) ? follow(ctx, mod, raw) : raw;
    }
  }
}

// ---------------------------------------------------------------- parameters

/** Rounds to the nearest step of a stepped parameter (ModulatableParameter.snap). */
export function snap(v, min, max, steps) {
  if (!steps || steps < 2) return v;
  const stepSize = (max - min) / (steps - 1);
  if (stepSize <= 0) return v;
  const index = clamp(Math.round((v - min) / stepSize), 0, steps - 1);
  return min + index * stepSize;
}

/**
 * Final value of a parameter: base plus active modulators, clamped to [min, max] and snapped.
 * `spec` is {min, max, steps?}; without one the parameter is unclamped and unstepped.
 */
export function evaluateParameter(param, ctx, spec = {}, fallback = 0) {
  // Without a range the value is not clamped and ADD scales as if the range were 0..1.
  const hasRange = spec.min !== undefined && spec.max !== undefined;
  const min = hasRange ? spec.min : -Infinity, max = hasRange ? spec.max : Infinity, steps = spec.steps ?? null;
  let base;
  let mods;
  if (typeof param === 'number') {
    base = param;
  } else if (param && typeof param === 'object') {
    base = param.baseValue ?? fallback;
    mods = param.modulators;
  } else {
    base = fallback;
  }
  base = F(base);
  const finish = (v) => snap(clamp(v, min, max), min, max, steps);

  if (!Array.isArray(mods) || mods.length === 0) return finish(base);

  const active = [];
  for (const mod of mods) {
    if (!mod || mod.bypassed || !isKnownSource(ctx, mod.sourceId) || validateModulator(mod).length) continue;
    active.push(mod);
  }
  if (active.length === 0) return finish(base);

  const isBipolar = min < 0;
  const range = hasRange ? max - min : 1;
  const addScalar = isBipolar ? range / 2 : range;
  let result = base;
  for (const mod of active) {
    const cv = evaluateModulator(mod, ctx);
    const depth = F(mod.depth ?? (mod.sourceId === 'seq' ? 1.0 : 0.0));
    const dc = F(mod.dcOffset ?? 0.0);
    const bipolarSource = BIPOLAR_SOURCES.has(mod.sourceId);
    const rawAmount = (mod.sourceId !== 'seq' && bipolarSource && !isBipolar)
      ? ((cv + 1) / 2) * depth + dc
      : cv * depth + dc;
    const op = mod.operator ?? 'ADD';
    const amount = rawAmount * (op === 'ADD' ? addScalar : 1);
    if (op === 'ADD') result += amount;
    else if (op === 'MUL') result *= 1 + amount;
    else result *= 1 - depth + amount; // SCALE
  }
  return finish(result);
}

/**
 * The base value a freshly loaded parameter starts from: random within [baseMin, baseMax] when
 * randomizeBase is set (ModulatableParameter.randomizeBaseValue), else its stored baseValue.
 */
export function resolveBase(param, rng = Math.random) {
  if (typeof param === 'number') return param;
  if (!param?.randomizeBase || param.baseMin === param.baseMax) return param?.baseValue ?? 0;
  return rng() * (param.baseMax - param.baseMin) + param.baseMin;
}
