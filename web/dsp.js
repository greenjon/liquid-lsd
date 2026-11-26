// Liquid LSD Web Audio DSP & spaz.org Integration (Phase 2)
// Real-time Web Audio analysis, beat detection, and CV output

import { BeatTracker, BeatClock } from './beattrack.js';
import { EnergyWatch } from './energy.js';
import { embed } from './embedmode.js';
import { SpectrumAnalyzer, audioFrame, BINS } from './audiotex.js';
import { startSideFeed, backoffMs } from './sidefeed.js';

export const cvState = {
  // The audio_* signals are normalised exactly as the desktop's AudioEngine publishes them (auto gain,
  // see AudioAgc below), so a preset's depths mean the same thing in both places.
  audio_amp:      0.0,   // broadband RMS / its auto-gain divisor, range 0..1
  audio_bass:     0.0,   // low-frequency RMS (<= 150 Hz), same
  audio_mid:      0.0,   // mid-frequency RMS (~1 kHz), same
  audio_himid:    0.0,   // hi-mid RMS (about 2-5 kHz), same
  audio_high:     0.0,   // high-frequency RMS (>= 5 kHz), same
  audio_flux_amp:  0.0,  // the bands' hits, weighted (FrameTransient below), stretched like amp
  audio_flux_bass: 0.0,  // hits in the bass (FrameTransient below), stretched like bass
  audio_flux_mid:  0.0,
  audio_flux_himid: 0.0,
  audio_flux_high: 0.0,
  // The same signals at the fixed scales, for the detectors (breakdowns, the fallback beat envelope, no
  // signal): like the desktop's, they must hear the real level, which auto gain would hide.
  raw: { audio_amp: 0, audio_bass: 0, audio_flux_amp: 0 },
  totalBeats:     0.0,   // standalone beat clock (beattrack.js): locked to the music's beats when it can be
  bpm:            120.0, // tracked tempo (120 until there is one)
  beatLocked:     false, // true while the clock follows a confidently tracked beat
  section:        'normal', // 'normal' | 'breakdown' (energy.js), for the autopilot
  drops:          0,     // drops detected so far (a breakdown ending with the low end back)
  dropLag:        0,     // s between the latest drop and its detection (audio_drop dates the hit back by it)
  audio_energy:   0.0,   // log2(now / usual level), -1..1, 0 in warm-up and silence (energy.js); no auto gain
  audio_energy_bass: 0.0,  // the same per band (AudioAgc.energy)
  audio_energy_mid: 0.0,
  audio_energy_himid: 0.0,
  audio_energy_high: 0.0,
  noSignal:       false, // the TV is on but the stream is down or silent (renderer shows snow)
  sideFeed:       '',    // the live radio's second copy for the picture (sidefeed.js): '' when not used, else its status
  glitchUntil:    0,     // performance.now() until which the picture rolls: a tape going in or out (renderer)
  isLive:         false, // true once AudioContext is running and stream is connected
};

// Analysis nodes and pre-allocated buffers
const RESUME_WAIT_MS = 1500; // startAudio: how long a suspended context gets to resume before play() decides
let audioCtx = null;
let gainNode = null;
let eqNodes = null; // [bass, mid, treble, trim] between the volume and the speakers
let broadbandAnalyser = null;
let bassAnalyser = null;
let midAnalyser = null;
let highAnalyser = null;
let himidAnalyser = null;

let broadBuf = null;
let bassBuf = null;
let midBuf = null;
let highBuf = null;
let himidBuf = null;

let analysisReady = false;
// Shader audio textures (audiotex.js): the newest 1024 samples each frame -> spectrum + waveform.
let texAnalyser = null;
let texBuf = null;
const spectrum = new SpectrumAnalyzer();
let feedNode = null;     // embed mode: the worklet the host's PCM goes into

// ~11.6 ms of samples at 44.1 kHz: the same block length the desktop takes its RMS over
const ANALYSER_FFT = 512;

// RMS Helper
function calcRms(analyser, buf) {
  analyser.getFloatTimeDomainData(buf);
  let sum = 0;
  for (let i = 0; i < buf.length; i++) {
    sum += buf[i] * buf[i];
  }
  return Math.sqrt(sum / buf.length);
}

const AMP_SCALE = 0.25;
const FLUX_BAND_SCALE = 0.05;
const FLUX_AMP_SCALE = 0.1;
const clamp01 = (v) => Math.max(0, Math.min(1, v));
const FIXED = { amp: AMP_SCALE, bass: AMP_SCALE, mid: AMP_SCALE, high: AMP_SCALE, himid: AMP_SCALE };

const BANDS = ['amp', 'bass', 'mid', 'high', 'himid'];

/**
 * Per-band auto gain, a port of the desktop's AudioAgc (keep the two identical). Each band is divided by
 * its own long-term level (a ~20 s mean, a running mean while it fills) times SCALE, so steady music
 * reads as it always has, whatever the stream's level. The long-term levels freeze while `watch` (the
 * section detector, energy.js) holds (a breakdown, or one being confirmed), so a breakdown reads quiet and a bass dropout is not
 * boosted into kicks, and they jump to the current level when the watch adopts a new normal (a quieter
 * or louder song). After two seconds under SILENT_RMS everything waits and starts afresh, so a new feed
 * levels in at once.
 * Pure, so it is unit-tested in node.
 */
export class AudioAgc {
  static LONG_TAU = 20;    // s, the same reference time as the section watch
  static SHORT_TAU = 1.5;  // s, the "now" level a new normal jumps to
  static FLOOR = 0.02;     // smallest divisor: silence and room noise get at most 12.5x
  static SILENT_RMS = 0.003; // about -50 dBFS broadband...
  static SILENCE_RESET = 2;  // ...for this long (s) resets
  static ENERGY_FLOOR = 0.002; // a band's long-term RMS below this has nothing in it to compare with
  /** Long-term mean to divisor, per band: calibrated so steady music lands where the old peak-following gain put it. */
  static SCALE = { amp: 1.5, bass: 2.0, mid: 2.0, high: 2.7, himid: 2.4 };

  constructor() {
    this.watch = new EnergyWatch();
    this.ref = { amp: 0, bass: 0, mid: 0, high: 0, himid: 0 };
    this.short = { amp: 0, bass: 0, mid: 0, high: 0, himid: 0 };
    this.time = 0;
    this.quiet = 0;
  }

  /** Feeds this frame's band RMS values (`dt` seconds since the last) and returns the divisors to use. */
  update(rms, dt) {
    this.quiet = rms.amp < AudioAgc.SILENT_RMS ? this.quiet + dt : 0;
    if (this.quiet > AudioAgc.SILENCE_RESET) { // silence: forget the old level and wait, so the next feed levels in at once
      if (this.time > 0) this.#restart();
      return this.#divisors();
    }
    this.watch.update(clamp01(rms.bass / AMP_SCALE), clamp01(rms.amp / AMP_SCALE), dt);
    this.time += dt;
    const frozen = this.watch.holding;
    for (const band of BANDS) {
      this.short[band] += (rms[band] - this.short[band]) * Math.min(1, dt / AudioAgc.SHORT_TAU);
      if (this.watch.rebased) this.ref[band] = this.short[band];
      else if (!frozen) this.ref[band] += (rms[band] - this.ref[band]) * Math.min(1, dt / Math.min(AudioAgc.LONG_TAU, this.time));
    }
    return this.#divisors();
  }

  /** How `band` sits against its own long-term level, log2(short / long) clamped to -1..1; 0 in warm-up and when it is empty. */
  energy(band) {
    if (this.time < AudioAgc.LONG_TAU || this.ref[band] < AudioAgc.ENERGY_FLOOR) return 0;
    return Math.max(-1, Math.min(1, Math.log2(Math.max(this.short[band], 1e-4) / this.ref[band])));
  }

  #divisors() {
    const out = {};
    for (const band of BANDS) out[band] = Math.max(this.ref[band] * AudioAgc.SCALE[band], AudioAgc.FLOOR);
    return out;
  }

  reset() {
    this.#restart();
    this.quiet = 0;
  }

  #restart() {
    this.watch.reset();
    for (const band of BANDS) this.ref[band] = this.short[band] = 0;
    this.time = 0;
  }
}

/**
 * Turns this frame's band RMS values (and last frame's) into the levels the desktop publishes (level /
 * divisor), plus the plain rise in level that the detectors hear, as the desktop's beat detector does.
 * The published audio_flux_* come from FrameTransient below. `div` defaults to the fixed scales. Pure, so
 * it is unit-tested in node.
 */
export function bandSignals(rms, prev, div = FIXED) {
  const bassFlux = Math.max(0, rms.bass - prev.bass);
  const midFlux  = Math.max(0, rms.mid  - prev.mid);
  const highFlux = Math.max(0, rms.high - prev.high);
  const onsetStrength = bassFlux * 2.0 + midFlux * 0.8 + highFlux * 0.3;
  const stretch = (band) => div[band] / AMP_SCALE;
  return {
    audio_amp:  clamp01(rms.amp  / div.amp),
    audio_bass: clamp01(rms.bass / div.bass),
    audio_mid:  clamp01(rms.mid  / div.mid),
    audio_himid: clamp01((rms.himid ?? 0) / (div.himid ?? AMP_SCALE)),
    audio_high: clamp01(rms.high / div.high),
    audio_flux_amp:  clamp01(onsetStrength / (FLUX_AMP_SCALE * stretch('amp'))),
    audio_flux_bass: clamp01(bassFlux / (FLUX_BAND_SCALE * stretch('bass'))),
    audio_flux_mid:  clamp01(midFlux  / (FLUX_BAND_SCALE * stretch('mid'))),
    audio_flux_high: clamp01(highFlux / (FLUX_BAND_SCALE * stretch('high'))),
  };
}

/**
 * Scores a band's rise in level against its recent rises, so what stands out passes and what is always there
 * does not. A port of the desktop's TransientGate (audio/TransientDetector.kt): keep the two identical.
 */
export class TransientGate {
  static LOCAL_TAU = 1;   // s, the recent rises a hit is compared with
  static C_LOW = 3;       // no hit at this many times the recent rise...
  static C_HIGH = 8;      // ...a whole one at this many
  static EPS = 1e-4;      // about -80 dBFS, so near silence has no contrast to speak of

  constructor() { this.meanRise = 0; }

  step(rise, dt) {
    const contrast = rise / (this.meanRise + TransientGate.EPS);
    const t = Math.max(0, Math.min(1, (contrast - TransientGate.C_LOW) / (TransientGate.C_HIGH - TransientGate.C_LOW)));
    const gate = t * t * (3 - 2 * t);
    this.meanRise += (rise - this.meanRise) * Math.min(1, dt / TransientGate.LOCAL_TAU);
    return gate * rise;
  }

  reset() { this.meanRise = 0; }
}

/**
 * A band's hits from one level per frame (the desktop measures on a fixed grid inside each audio block). The rise
 * is against the level about one analysis window ago, however many frames that is, so the frame rate matters little.
 * Held like the desktop's, so HitScale's reference climbs the same amount per hit at any frame rate.
 */
export class FrameTransient {
  static LAG = 0.0116; // s
  static HOLD = 0.034;  // s, TransientDetector.HOLD_SEC

  constructor() { this.gate = new TransientGate(); this.past = []; this.t = 0; this.held = 0; this.age = 0; }

  update(level, dt) {
    this.t += dt;
    let before = null;
    for (let i = this.past.length - 1; i >= 0; i--) {
      if (this.t - this.past[i].t >= FrameTransient.LAG) { before = this.past[i].level; break; }
    }
    this.past.push({ t: this.t, level });
    while (this.past.length > 2 && this.t - this.past[1].t >= FrameTransient.LAG) this.past.shift();
    const hit = this.gate.step(before === null ? 0 : Math.max(0, level - before), dt);
    this.age += dt;
    if (hit >= this.held || this.age > FrameTransient.HOLD) { this.held = hit; this.age = 0; }
    return this.held;
  }

  reset() { this.gate.reset(); this.past.length = 0; this.t = 0; this.held = 0; this.age = 0; }
}

/**
 * Sizes a band's hits against its own recent strong hits, so every kind of music uses the range. A port of the
 * desktop's HitScale (audio/TransientDetector.kt): keep the two identical. The reference stops falling while
 * `frozen` (the section watch holds), so a breakdown stays quiet.
 */
export class HitScale {
  static ATTACK = 0.05;  // s
  static DECAY = 8;      // s
  static MAX_BOOST = 3;  // the most a band is lifted above its auto-gain scale

  constructor() { this.ref = 0; }

  signal(hit, agcScale, dt, frozen) {
    if (hit > this.ref) this.ref += (hit - this.ref) * (1 - Math.exp(-dt / HitScale.ATTACK));
    else if (!frozen) this.ref *= Math.exp(-dt / HitScale.DECAY);
    return hit / Math.max(this.ref, agcScale / HitScale.MAX_BOOST);
  }

  reset() { this.ref = 0; }
}

const HIT_SCALE = 0.167;     // TransientDetector.SCALE: a rise of this reads 1.0 on auto gain alone
const HIT_AMP_SCALE = 0.333; // TransientDetector.AMP_SCALE
const HIT_WEIGHTS = { bass: 2, mid: 0.8, himid: 0.5, high: 0.3 };

/** The published audio_flux_* from the bands' hits and the auto-gain divisors, as AudioEngine does it. `scales`: a HitScale per band and amp. */
export function hitSignals(hits, div, scales, dt, frozen) {
  const scaled = (hit, scale, band) => clamp01(scales[band].signal(hit, scale * div[band] / AMP_SCALE, dt, frozen));
  let amp = 0;
  for (const band of Object.keys(HIT_WEIGHTS)) amp += hits[band] * HIT_WEIGHTS[band];
  return {
    audio_flux_amp: scaled(amp, HIT_AMP_SCALE, 'amp'),
    audio_flux_bass: scaled(hits.bass, HIT_SCALE, 'bass'),
    audio_flux_mid: scaled(hits.mid, HIT_SCALE, 'mid'),
    audio_flux_himid: scaled(hits.himid, HIT_SCALE, 'himid'),
    audio_flux_high: scaled(hits.high, HIT_SCALE, 'high'),
  };
}

const agc = new AudioAgc();
const prevRms = { bass: 0, mid: 0, high: 0 };
const transients = { bass: new FrameTransient(), mid: new FrameTransient(), himid: new FrameTransient(), high: new FrameTransient() };
const hitScales = { amp: new HitScale(), bass: new HitScale(), mid: new HitScale(), himid: new HitScale(), high: new HitScale() };

function updateAmplitudes(dt) {
  const rms = {
    amp:  calcRms(broadbandAnalyser, broadBuf),
    bass: calcRms(bassAnalyser,      bassBuf),
    mid:  calcRms(midAnalyser,       midBuf),
    high: calcRms(highAnalyser,      highBuf),
    himid: calcRms(himidAnalyser,    himidBuf),
  };
  const raw = bandSignals(rms, prevRms);
  cvState.raw.audio_amp = raw.audio_amp;
  cvState.raw.audio_bass = raw.audio_bass;
  cvState.raw.audio_flux_amp = raw.audio_flux_amp;
  const div = agc.update(rms, dt);
  Object.assign(cvState, bandSignals(rms, prevRms, div));
  Object.assign(cvState, hitSignals({
    bass: transients.bass.update(rms.bass, dt),
    mid: transients.mid.update(rms.mid, dt),
    himid: transients.himid.update(rms.himid, dt),
    high: transients.high.update(rms.high, dt),
  }, div, hitScales, dt, agc.watch.holding));
  prevRms.bass = rms.bass; prevRms.mid = rms.mid; prevRms.high = rms.high;
}

// Standalone beat clock (beattrack.js). The onset worklet feeds the tracker; every 250 ms its estimate sets
// the clock's tempo and pulls it onto the music's beats. With no audio (or no clear beat) the clock coasts,
// so beat-synced modulators keep running.
const tracker = new BeatTracker();
const beatClock = new BeatClock();
let onsetFromWorklet = false;
let lastAnalyseMs = -Infinity;

/** performance.now() time at which audio at context time `t` is heard (so the beat lines up with the sound). */
function ctxToPerfMs(t) {
  const ts = audioCtx.getOutputTimestamp ? audioCtx.getOutputTimestamp() : null;
  if (ts && ts.performanceTime > 0) return ts.performanceTime + (t - ts.contextTime) * 1000;
  const latency = audioCtx.outputLatency || audioCtx.baseLatency || 0;
  return performance.now() + (t - audioCtx.currentTime + latency) * 1000;
}

function updateBeat() {
  const now = performance.now();
  if (analysisReady && now - lastAnalyseMs >= 250) {
    lastAnalyseMs = now;
    beatClock.update(tracker.analyse(), now);
  }
  cvState.totalBeats = beatClock.read(now);
  cvState.beatsAtMs = now;
  cvState.bpm = beatClock.bpm;
  cvState.beatLocked = beatClock.locked;
}

// Per-frame tick called from renderer.js rAF loop
// No signal: the TV is on but the stream failed, stalled or has been silent for 4 s. The renderer
// fades to analog snow. The stream is reconnected (every 10 s at most) only when its playhead has stopped:
// a playing stream that the analysis hears as silence is not fixed by reloading it, and each reload is a
// hiccup in the sound. `?nostatic` (headless checks, which have no audio) turns this off.
const STREAM_URL = 'https://radio.spaz.org:8060/radio.ogg';
const SILENT_AMP = 0.004;
const STUCK_S = 4;         // the playhead has not moved for this long: the stream is down
const DEAF_S = 2;          // the playhead moves but the analysis hears exact zeros for this long: see below
let wantAudio = false;
let quietFor = 0;
let lastRetryMs = 0;
// Reconnects: every 10 s for the first minute, so a short drop comes back as quickly as before; after that
// they back off (20 s, 40 s, then every minute or so, with jitter) until the stream plays again.
const RETRY_BASE_MS = 10000;
const RETRY_CAP_MS = 60000;
const RETRY_FLAT = 6;      // attempts at the plain 10 s before backing off
let retryAttempt = 0;
let retryWaitMs = RETRY_BASE_MS;
function noteRetry(now) {
  lastRetryMs = now;
  retryWaitMs = retryAttempt < RETRY_FLAT ? RETRY_BASE_MS : backoffMs(retryAttempt - RETRY_FLAT + 1, RETRY_BASE_MS, RETRY_CAP_MS);
  retryAttempt++;
}
function resetRetry() { retryAttempt = 0; retryWaitMs = RETRY_BASE_MS; }
const params = typeof location !== 'undefined' ? new URLSearchParams(location.search) : new URLSearchParams();
const noStatic = params.has('nostatic');

// Safari plays the live stream out loud but hands createMediaElementSource only zeros (a tape's file comes
// through). Once that is seen, the live radio's sound reaches the analysis from a second copy of the stream
// decoded in sidefeed.js instead, for the rest of the visit. `?sidefeed` uses it from the start (to test it).
let elementDeaf = params.has('sidefeed');
// Safari also plays that stream straight to the speakers, past the volume and the EQ below, so the volume
// dial sets the element's own volume instead (macOS; on an iPhone or iPad only the device's buttons can) and
// the EQ is unavailable on the live radio. Only WebKit does this: elsewhere exact zeros could be real dead air.
const webkit = typeof navigator !== 'undefined' && /AppleWebKit/.test(navigator.userAgent)
  && !/Chrome\/|Chromium\/|Edg\/|Android/.test(navigator.userAgent);
let elementBypass = false;
// Until the graph is heard carrying sound, a WebKit browser may be bypassing it: the dial is applied to the
// element from the start (a little too quiet for a moment if the graph works) rather than playing at full volume.
let graphHeard = false;
let deafFor = 0;
let sideFeed = null;
let lastPlayhead = -1;
let playheadStill = 0;     // s since the <audio> element's playhead last moved

function stopSideFeed() {
  sideFeed?.stop();
  sideFeed = null;
  cvState.sideFeed = elementDeaf ? 'stopped' : '';
}

function updateSignal(dt) {
  if (embed) { cvState.noSignal = false; return; } // the host plays the radio; silence is just a quiet picture
  if (tape && !micOn) { updateTapeSignal(dt); return; }
  const audioEl = document.getElementById('lsdAudio');
  const t = audioEl?.currentTime ?? -1;
  playheadStill = wantAudio && !micOn && (!audioEl || audioEl.paused || t === lastPlayhead) ? playheadStill + dt : 0;
  lastPlayhead = t;
  const playing = wantAudio && !micOn && playheadStill < 1; // currentTime may update only a few times a second

  if (webkit && !graphHeard && playing && analysisReady && cvState.raw.audio_amp > 0 && !elementDeaf) {
    graphHeard = true;
    applyElementVolume();
  }
  // Only WebKit hands the graph zeros while playing: elsewhere exact zeros are dead air, not a reason to
  // download a second copy (`?sidefeed` still forces it, for testing).
  if (!elementDeaf && webkit) {
    deafFor = playing && analysisReady && cvState.raw.audio_amp === 0 ? deafFor + dt : 0;
    if (deafFor > DEAF_S) {
      elementDeaf = true;
      console.warn('[dsp] the stream plays but the analysis hears silence: decoding a second copy for the picture');
      elementBypass = true;
      soundPathChanged();
    }
  }
  if (elementDeaf && playing && !sideFeed && audioCtx) {
    sideFeed = startSideFeed(audioCtx, analysisIn, STREAM_URL);
    quietFor = 0;
  }
  if (sideFeed) cvState.sideFeed = sideFeed.status;

  quietFor = analysisReady && cvState.raw.audio_amp > SILENT_AMP ? 0 : quietFor + dt;
  // The second copy cannot be decoded here: the sound plays, so a still picture, not snow.
  const blind = sideFeed?.status === 'unsupported' && playing;
  cvState.noSignal = wantAudio && !noStatic && quietFor > 4 && !blind;
  const now = performance.now();
  if (playing && !cvState.noSignal) resetRetry();
  if (cvState.noSignal && !micOn && playheadStill > STUCK_S && now - lastRetryMs > retryWaitMs) {
    noteRetry(now);
    reconnect();
  }
}

/** Reloads the stream (a fresh request, so a dead Icecast connection is not reused). */
function reconnect() {
  const audioEl = document.getElementById('lsdAudio');
  if (!audioEl || !audioCtx) return;
  if (tape) { loadTape().catch(() => { /* next try in 10 s */ }); return; }
  audioEl.src = `${STREAM_URL}?t=${Date.now()}`;
  audioEl.play().then(() => { analysisReady = true; cvState.isLive = true; }).catch(() => { /* next try in 10 s */ });
}

// A tape (tapedeck.js): an archive show playing through the same <audio> element and analysis graph as the
// radio, so everything that follows the radio follows it. Quiet is not a fault on a tape (shows have quiet
// passages), so its watchdog looks at whether the playhead moves. It stays through power-off, and comes back
// where it was.
const TAPE_STUCK_S = 6;
let tape = null; // { url, pos, paused, ready, stuck, lastT }
let tapeMetaListener = null; // loadTape's pending 'loadedmetadata' handler

/** Points the element at the tape, from `tape.pos`. The promise is play()'s: it rejects if the browser refuses. */
function loadTape() {
  const audioEl = document.getElementById('lsdAudio');
  if (!audioEl || !audioCtx || !tape) return Promise.resolve();
  const t = tape;
  t.ready = false;
  t.stuck = 0;
  // A stuck tape is reloaded every few seconds: the previous load's listener goes, so they do not pile up.
  if (tapeMetaListener) audioEl.removeEventListener('loadedmetadata', tapeMetaListener);
  tapeMetaListener = () => {
    tapeMetaListener = null;
    if (tape !== t) return;
    if (t.pos > 0) audioEl.currentTime = t.pos;
    t.ready = true;
    t.lastT = t.pos;
  };
  audioEl.addEventListener('loadedmetadata', tapeMetaListener, { once: true });
  audioEl.src = t.url;
  if (t.paused || micOn) return Promise.resolve();
  return audioEl.play().then(() => { analysisReady = true; cvState.isLive = true; });
}

function updateTapeSignal(dt) {
  const audioEl = document.getElementById('lsdAudio');
  const now = performance.now();
  const moved = audioEl.currentTime !== tape.lastT;
  if (tape.ready && moved && !audioEl.seeking) tape.pos = audioEl.currentTime;
  tape.lastT = audioEl.currentTime;
  tape.stuck = !wantAudio || tape.paused || audioEl.ended || moved ? 0 : tape.stuck + dt;
  cvState.noSignal = !noStatic && tape.stuck > TAPE_STUCK_S;
  if (moved && !cvState.noSignal) resetRetry();
  if (cvState.noSignal && now - lastRetryMs > retryWaitMs) {
    noteRetry(now);
    reconnect();
  }
}

function resetTransients() {
  for (const t of Object.values(transients)) t.reset();
  for (const s of Object.values(hitScales)) s.reset();
}

/** A seek or a new source is a new stretch of music: the beat and the levels start afresh. */
function resetAnalysis() {
  tracker.reset();
  agc.reset();
  resetTransients();
  quietFor = 0;
  lastRetryMs = performance.now();
}

/** Plays a tape from `startAt` seconds, instead of the radio or the microphone. */
export function insertTape(url, startAt = 0) {
  tape = { url, pos: startAt, paused: false, ready: false, stuck: 0, lastT: 0 };
  releaseMic();
  stopSideFeed();
  resetAnalysis();
  soundPathChanged(); // a tape's file goes through the graph, even in Safari
  if (wantAudio) loadTape().catch((err) => console.warn('[dsp] tape did not start:', err.message)); // TV off: power-on starts it
}

/** Takes the tape out and goes back to the radio (or stays on the microphone, if it took over). */
export function ejectTape() {
  if (!tape) return;
  tape = null;
  resetAnalysis();
  cvState.noSignal = false;
  soundPathChanged();
  if (wantAudio && !micOn) reconnect();
}

export function setTapePaused(paused) {
  if (!tape) return;
  tape.paused = paused;
  if (micOn) return; // the microphone has the input: the tape waits either way
  const audioEl = document.getElementById('lsdAudio');
  if (paused) audioEl?.pause();
  else if (wantAudio) audioEl?.play().catch((err) => console.warn('[dsp] tape did not resume:', err.message));
}

export function seekTape(seconds) {
  if (!tape) return;
  tape.pos = Math.max(0, seconds);
  resetAnalysis();
  const audioEl = document.getElementById('lsdAudio');
  if (tape.ready && audioEl) audioEl.currentTime = tape.pos; // not loaded yet: loadedmetadata goes to tape.pos
}

/** { pos, paused, duration } of the tape in, or null. `duration` is 0 until the browser knows it. */
export function tapeStatus() {
  if (!tape) return null;
  const d = document.getElementById('lsdAudio')?.duration;
  return { pos: tape.pos, paused: tape.paused, duration: Number.isFinite(d) ? d : 0 };
}

/** Refreshes audioFrame (the shaders' spectrum and waveform) from the newest samples. */
function updateAudioFrame(dt) {
  if (audioFrame.hold) return; // render_check pins a test signal
  if (analysisReady && texAnalyser) {
    texAnalyser.getFloatTimeDomainData(texBuf);
    const rate = audioCtx.sampleRate;
    spectrum.process(texBuf, texBuf.length, rate, Math.max(0, dt) * rate);
    audioFrame.wave.set(texBuf.subarray(texBuf.length - BINS));
  } else {
    // No input: the spectrum falls away at the release rate and the trace goes flat.
    const decay = Math.exp(-Math.max(0, dt) / 0.08);
    for (let k = 0; k < BINS; k++) spectrum.spectrum[k] *= decay;
    audioFrame.wave.fill(0);
  }
  audioFrame.spectrum.set(spectrum.spectrum);
}

/** One step of the analysis. `spectrum = false` skips the shaders' spectrum and waveform (lite mode draws none). */
export function tick(dt, spectrum = true) {
  updateSignal(dt);
  if (spectrum) updateAudioFrame(dt);
  if (analysisReady) {
    updateAmplitudes(dt);
    // Sections come from auto gain's own watch (updated in updateAmplitudes), fed the fixed-scale levels.
    cvState.section = agc.watch.section;
    cvState.drops = agc.watch.drops;
    cvState.dropLag = agc.watch.dropLag;
    cvState.audio_energy = agc.watch.energy;
    cvState.audio_energy_bass = agc.energy('bass');
    cvState.audio_energy_mid = agc.energy('mid');
    cvState.audio_energy_himid = agc.energy('himid');
    cvState.audio_energy_high = agc.energy('high');
    cvState.energy = agc.watch; // short / long levels, for tuning (window.LSD.cvState.energy)
    // Without the worklet, the per-frame onset strength is the envelope (coarser timing, same tracker).
    if (!onsetFromWorklet) tracker.push(cvState.raw.audio_flux_amp, performance.now() / 1000);
  }
  updateBeat();
}

let currentVolumeRatio = 0.8;

// Microphone input: the picture follows the room instead of the radio. Needs a secure page (https) and the
// viewer's permission; power-off puts the radio back.
let analysisIn = null;
let micOn = false;
let micStream = null;
let micSource = null;
let micPendingReq = 0;    // the request token of the permission prompt that is up, 0 for none
let micReq = 0;           // bumped to cancel a pending permission request
export const micAvailable = () => !embed && !!navigator.mediaDevices?.getUserMedia;
export const isMicOn = () => micOn;

function releaseMic() {
  micReq++;
  if (!micOn) return;
  micOn = false;
  micSource?.disconnect();
  micStream?.getTracks().forEach((t) => t.stop());
  micSource = micStream = null;
  window.dispatchEvent(new CustomEvent('lsd-mic', { detail: false }));
}

/** Switches the analysed input to the microphone (`on`) or back to the radio; rejects if the mic is refused. */
export async function setMic(on) {
  if (embed || !audioCtx) return micOn;
  if (!on && !micOn) micReq++; // cancels a request that is waiting on the prompt
  if (on === micOn) return micOn;
  if (on) {
    if (micPendingReq && micPendingReq === micReq) return micOn; // a live request is already waiting on the browser's prompt
    const req = ++micReq;
    micPendingReq = req;
    // Raw sound: the browser's speech clean-up would flatten the beat.
    let stream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false },
      });
    } finally {
      if (micPendingReq === req) micPendingReq = 0;
    }
    // Power-off, a tape going in or the viewer switching back while the prompt was up: no longer wanted.
    if (req !== micReq || !wantAudio || micOn || !audioCtx) {
      stream.getTracks().forEach((t) => t.stop());
      return micOn;
    }
    micStream = stream;
    micSource = audioCtx.createMediaStreamSource(stream);
    micSource.connect(analysisIn);
    micOn = true;
    analysisReady = true;   // the radio may never have played (offline): the microphone is input enough
    cvState.isLive = true;
    // A key or click turned it on: lets the analysis run even if the radio never could (offline at power-on).
    if (audioCtx.state === 'suspended') await audioCtx.resume().catch(() => {});
    stream.getAudioTracks()[0]?.addEventListener('ended', () => { if (micStream === stream) setMic(false).catch(() => {}); });
    document.getElementById('lsdAudio')?.pause();
    stopSideFeed();
    tracker.reset();
    quietFor = 0;
    window.dispatchEvent(new CustomEvent('lsd-mic', { detail: true }));
  } else {
    releaseMic();
    tracker.reset();
    quietFor = 0;
    lastRetryMs = performance.now();
    if (wantAudio) reconnect();
  }
  return micOn;
}

// User-gesture startup sequence
export async function startAudio() {
  const audioEl = document.getElementById('lsdAudio');

  if (!audioEl && !embed) {
    console.error('lsdAudio element not found');
    return;
  }

  wantAudio = true;
  quietFor = 0;
  lastRetryMs = performance.now(); // the first reconnect waits for this attempt to fail
  resetRetry();
  // Set stream src on user gesture (embed mode: the host app plays the radio and feeds us instead). A fresh
  // address each time, as in reconnect(): with the same one, Firefox replays the bytes it kept from the last
  // visit (a reload), so the sound starts at once, then goes silent while the live stream catches up.
  if (!embed && !tape) audioEl.src = `${STREAM_URL}?t=${Date.now()}`;
  applyElementVolume();

  if (!audioCtx) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    audioCtx = new AudioContextClass();
    audioEl?.addEventListener('ended', () => { if (tape) window.dispatchEvent(new CustomEvent('lsd-tape-ended')); });

    const radio = embed ? null : audioCtx.createMediaElementSource(audioEl);
    // Everything that is analysed comes through `source`: the radio, or the microphone (which is never
    // sent to the speakers, so it cannot feed back).
    const source = audioCtx.createGain();
    analysisIn = source;

    // Broadband Analyser
    broadbandAnalyser = audioCtx.createAnalyser();
    broadbandAnalyser.fftSize = ANALYSER_FFT;
    broadbandAnalyser.smoothingTimeConstant = 0.8;

    // Bass Filter & Analyser
    const bassFilter = audioCtx.createBiquadFilter();
    bassFilter.type = 'lowpass';
    bassFilter.frequency.value = 150;
    bassFilter.Q.value = 0.7;

    bassAnalyser = audioCtx.createAnalyser();
    bassAnalyser.fftSize = ANALYSER_FFT;
    bassAnalyser.smoothingTimeConstant = 0.85;

    // Mid Filter & Analyser
    const midFilter = audioCtx.createBiquadFilter();
    midFilter.type = 'bandpass';
    midFilter.frequency.value = 1000;
    midFilter.Q.value = 1.0;

    midAnalyser = audioCtx.createAnalyser();
    midAnalyser.fftSize = ANALYSER_FFT;
    midAnalyser.smoothingTimeConstant = 0.85;

    // High Filter & Analyser
    const highFilter = audioCtx.createBiquadFilter();
    highFilter.type = 'highpass';
    highFilter.frequency.value = 5000;
    highFilter.Q.value = 0.7;

    highAnalyser = audioCtx.createAnalyser();
    highAnalyser.fftSize = ANALYSER_FFT;
    highAnalyser.smoothingTimeConstant = 0.85;

    // Hi-Mid Filter & Analyser: about 1.9-5.2 kHz, between mid and high
    const himidFilter = audioCtx.createBiquadFilter();
    himidFilter.type = 'bandpass';
    himidFilter.frequency.value = 3200;
    himidFilter.Q.value = 1.05;

    himidAnalyser = audioCtx.createAnalyser();
    himidAnalyser.fftSize = ANALYSER_FFT;
    himidAnalyser.smoothingTimeConstant = 0.85;

    // Shader audio textures: the desktop's analyser takes the newest 1024 samples (audiotex.js).
    texAnalyser = audioCtx.createAnalyser();
    texAnalyser.fftSize = 1024;
    source.connect(texAnalyser);
    texBuf = new Float32Array(texAnalyser.fftSize);

    gainNode = audioCtx.createGain();
    gainNode.gain.value = currentVolumeRatio * currentVolumeRatio;

    // Wire graph
    if (radio) {
      radio.connect(source);
      radio.connect(gainNode);
      // The EQ shapes only what is heard: the analysis above taps the radio before it.
      const band = (type, hz) => { const f = audioCtx.createBiquadFilter(); f.type = type; f.frequency.value = hz; return f; };
      const bass = band('lowshelf', EQ_HZ.bass);
      const mid = band('peaking', EQ_HZ.mid);
      mid.Q.value = EQ_MID_Q;
      const treble = band('highshelf', EQ_HZ.treble);
      const trim = audioCtx.createGain();
      gainNode.connect(bass); bass.connect(mid); mid.connect(treble); treble.connect(trim);
      trim.connect(audioCtx.destination);
      eqNodes = [bass, mid, treble, trim];
      applyEq(true);
    }
    source.connect(broadbandAnalyser);
    source.connect(bassFilter);
    bassFilter.connect(bassAnalyser);
    source.connect(midFilter);
    midFilter.connect(midAnalyser);
    source.connect(highFilter);
    highFilter.connect(highAnalyser);
    source.connect(himidFilter);
    himidFilter.connect(himidAnalyser);

    if (embed) {
      // The host's PCM is the input. Nothing is audible from here; the analysers hang off a muted gain so
      // the graph keeps pulling them.
      await audioCtx.audioWorklet.addModule('feed-worklet.js');
      feedNode = new AudioWorkletNode(audioCtx, 'lsd-feed', { numberOfInputs: 0, outputChannelCount: [1] });
      feedNode.connect(source);
      const sink = audioCtx.createGain();
      sink.gain.value = 0;
      for (const a of [broadbandAnalyser, bassAnalyser, midAnalyser, highAnalyser, himidAnalyser, texAnalyser]) a.connect(sink);
      sink.connect(audioCtx.destination);
      window.dispatchEvent(new CustomEvent('lsd-embed-ready'));
    }

    // Onsets at audio rate for the beat tracker. The worklet's output only goes to a muted gain,
    // which keeps the graph pulling it.
    try {
      await audioCtx.audioWorklet.addModule('onset-worklet.js');
      const onset = new AudioWorkletNode(audioCtx, 'lsd-onset', { numberOfOutputs: 1 });
      const mute = audioCtx.createGain();
      mute.gain.value = 0;
      source.connect(onset);
      onset.connect(mute);
      mute.connect(audioCtx.destination);
      onset.port.onmessage = ({ data }) => {
        for (let i = 0; i < data.values.length; i++) tracker.push(data.values[i], ctxToPerfMs(data.t0 + i * data.hop) / 1000, data.bands?.[i]);
      };
      onsetFromWorklet = true;
    } catch (err) {
      console.warn('[dsp] Onset worklet unavailable; tracking beats per frame:', err.message);
    }

    // Pre-allocate analysis buffers
    broadBuf = new Float32Array(broadbandAnalyser.fftSize);
    bassBuf  = new Float32Array(bassAnalyser.fftSize);
    midBuf   = new Float32Array(midAnalyser.fftSize);
    highBuf  = new Float32Array(highAnalyser.fftSize);
    himidBuf = new Float32Array(himidAnalyser.fftSize);
  }

  // Without a click (AUTOPLAY, ?kiosk), a browser that blocks sound leaves resume() pending instead of
  // failing, so it gets a moment; the radio's play() below then says whether sound is allowed.
  if (audioCtx.state === 'suspended') {
    await Promise.race([audioCtx.resume(), new Promise((r) => setTimeout(r, RESUME_WAIT_MS))]);
  }

  if (embed) { /* the host app plays the radio */ }
  else if (micOn) audioEl.pause();  // the microphone is the input; the radio waits
  else if (tape) { tape.paused = false; await loadTape(); }
  else await audioEl.play();
  if (audioCtx.state === 'suspended') {
    // The element plays but the context it is routed through does not: nothing would be heard.
    await Promise.race([audioCtx.resume(), new Promise((r) => setTimeout(r, RESUME_WAIT_MS))]);
    if (audioCtx.state === 'suspended') {
      if (!embed) audioEl.pause();
      throw new DOMException('The browser wants a click before sound', 'NotAllowedError');
    }
  }

  cvState.isLive = true;
  analysisReady = true;
}

// Stop audio stream and pause analysis on power off
export async function stopAudio() {
  wantAudio = false;
  releaseMic();
  stopSideFeed();
  if (tape) tape.ready = false; // it stays in, at tape.pos
  cvState.noSignal = false;
  const audioEl = document.getElementById('lsdAudio');
  if (audioEl) {
    audioEl.pause();
    audioEl.removeAttribute('src');
    audioEl.load();
  }

  if (audioCtx && audioCtx.state === 'running') {
    await audioCtx.suspend();
  }

  cvState.isLive = false;
  analysisReady = false;
  tracker.reset(); // the next stream starts a fresh envelope; the clock coasts meanwhile
  cvState.audio_amp = 0.0;
  cvState.audio_bass = 0.0;
  cvState.audio_mid = 0.0;
  cvState.audio_high = 0.0;
  cvState.audio_himid = 0.0;
  cvState.audio_flux_himid = 0.0;
  cvState.audio_energy_bass = cvState.audio_energy_mid = cvState.audio_energy_himid = cvState.audio_energy_high = 0.0;
  cvState.audio_flux_amp = 0.0;
  cvState.audio_flux_bass = 0.0;
  cvState.audio_flux_mid = 0.0;
  cvState.audio_flux_high = 0.0;
  cvState.audio_energy = 0.0;
  cvState.raw.audio_amp = cvState.raw.audio_bass = cvState.raw.audio_flux_amp = 0.0;
  prevRms.bass = prevRms.mid = prevRms.high = 0;
  agc.reset(); // the next stream levels itself afresh
  resetTransients();
}

// volume: 0.0 (muted) to 1.0 (full) — uses squared curve for perceptual linearity
export function setVolume(volume) {
  currentVolumeRatio = volume;
  if (gainNode && audioCtx) {
    gainNode.gain.setTargetAtTime(volume * volume, audioCtx.currentTime, 0.05);
  }
  applyElementVolume();
}

/** The live radio is heard straight from the <audio> element (Safari), past the volume gain and the EQ. */
/** True while this viewer's side feed (the second copy of the stream for the analysis) is playing. */
export const isSideFeedRunning = () => !!sideFeed && sideFeed.status === 'playing';
export const radioBypassesGraph = () => elementBypass && !tape && !micOn && !embed;

let volumeFixed = null;
/** The page cannot set an element's volume (iPhone, iPad: the device's buttons do). */
export function elementVolumeFixed() {
  if (volumeFixed === null) {
    try { const a = new Audio(); a.volume = 0.5; volumeFixed = a.volume !== 0.5; } catch { volumeFixed = false; }
  }
  return volumeFixed;
}

/** The dial's volume on the element while the radio bypasses the graph, else 1 (the gain node has it). */
function applyElementVolume() {
  const audioEl = typeof document !== 'undefined' ? document.getElementById('lsdAudio') : null;
  if (!audioEl) return;
  const provisional = webkit && !graphHeard && !tape && !micOn && !embed && !elementVolumeFixed();
  const v = radioBypassesGraph() || provisional ? currentVolumeRatio * currentVolumeRatio : 1;
  if (audioEl.volume !== v) audioEl.volume = v;
}

/** The radio started or stopped bypassing the graph: the element's volume follows, and the UI (lsd-sound-path). */
function soundPathChanged() {
  applyElementVolume();
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('lsd-sound-path', { detail: radioBypassesGraph() }));
}

// -------------------------------------------------------
// Equalizer: three bands on what is heard (radio or tape), the same as the Spaz Radio app's.
// -------------------------------------------------------
export const EQ_MIN_DB = -12;
export const EQ_MAX_DB = 12;
const EQ_HZ = { bass: 120, mid: 1000, treble: 6000 };
const EQ_MID_Q = 0.7;
let eq = { on: false, bass: 0, mid: 0, treble: 0 };

/** Boosts would push loud masters past full scale, so the whole signal drops by the largest boost. */
export function eqHeadroomDb({ bass, mid, treble }) {
  return -Math.max(0, bass, mid, treble);
}

function applyEq(now = false) {
  if (!eqNodes || !audioCtx) return;
  const [bass, mid, treble, trim] = eqNodes;
  const g = eq.on ? eq : { bass: 0, mid: 0, treble: 0 };
  // Glide while playing; a suspended context would leave a glide pending, so set the value outright.
  const glide = !now && audioCtx.state === 'running';
  const set = (param, v) => {
    if (glide) { param.setTargetAtTime(v, audioCtx.currentTime, 0.03); return; }
    param.cancelScheduledValues(0);
    param.value = v;
  };
  set(bass.gain, g.bass);
  set(mid.gain, g.mid);
  set(treble.gain, g.treble);
  set(trim.gain, 10 ** (eqHeadroomDb(g) / 20));
}

/** The EQ's live gains [bass dB, mid dB, treble dB, trim ratio], or null before the sound has started (for the headless check). */
export const eqGains = () => eqNodes?.map((n) => n.gain.value) ?? null;

/** settings: { on, bass, mid, treble } in whole dB. Takes effect at once, or when the sound starts. */
export function setEq(settings) {
  const clamp = (v) => Math.max(EQ_MIN_DB, Math.min(EQ_MAX_DB, Math.round(Number(v) || 0)));
  eq = { on: !!settings.on, bass: clamp(settings.bass), mid: clamp(settings.mid), treble: clamp(settings.treble) };
  applyEq();
}

// -------------------------------------------------------
// Embed mode: audio from the host app
// -------------------------------------------------------
export const feedReady = () => !!feedNode;

/** Pauses / resumes the analysis (embed: the host hides the picture). */
export async function suspendAudio() {
  if (audioCtx?.state === 'running') await audioCtx.suspend();
}
export async function resumeAudio() {
  if (audioCtx && wantAudio && audioCtx.state === 'suspended') await audioCtx.resume().catch(() => {});
}

/**
 * Hands the analysis a block of the host's decoded audio (mono): an Int16Array / Float32Array, an
 * ArrayBuffer of little-endian 16-bit samples, or the same as a base64 string. `rate` is its sample
 * rate. Returns false (and drops the block) until the audio graph exists.
 */
export function feedPCM(data, rate) {
  if (!feedNode) return false;
  let pcm = data;
  let own = false;   // a buffer made here can be handed over without copying
  if (typeof data === 'string') {
    const bin = atob(data);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    pcm = new Int16Array(bytes.buffer, 0, bytes.length >> 1);
    own = true;
  } else if (data instanceof ArrayBuffer) {
    pcm = new Int16Array(data, 0, data.byteLength >> 1);
  }
  feedNode.port.postMessage({ pcm, rate }, own ? [pcm.buffer] : []);
  return true;
}
