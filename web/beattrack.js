// beattrack.js
// Standalone beat tracking for the radio stream (outside a live broadcast). Onsets go in (onset-worklet.js
// computes one set per 10 ms at audio rate); a tempo, a beat phase and a confidence come out. Tempo works as
// the desktop's BeatTrackerEngine does: autocorrelation of three onset bands, a comb over each lag's multiples,
// a preference for usual tempos, then a check of where the accents fall. Phase: a comb filter at that tempo
// over the last few seconds of a separate envelope. BeatClock turns the results into the smooth, forward-only
// beat count the modulators read (a BeatFlywheel fed anchors), so a preset's "on the beat" pulses land on the
// music's beats. Pure, so it is unit-tested in node. Measure changes with checks/tempo_report.mjs.

import { BeatFlywheel } from './beatclock.js';

export const HOP = 0.01;            // seconds per envelope sample
const WINDOW = 800;                 // 8 s of envelope kept
const MIN_SAMPLES = 300;            // 3 s before the first estimate
const MIN_BPM = 70;
const MAX_BPM = 180;
const PRIOR_BPM = 120;
const PRIOR_OCTAVES = 1;            // width of the tempo prior (log-gaussian, in octaves)
const BAND_MEAN_RATE = 0.0017;      // each band in units of its usual rise over about 6 s, so a loud kick does not decide alone
const AC_WINDOW = 400;              // 4 s, weighted towards the newest
const COMB_MULTIPLES = 4;           // a beat repeats at 2, 3 and 4 beats; a syncopated gap does not
const METRE_CANDIDATES = 6;         // strongest comb peaks checked for where the accents fall
const METRE_SWITCH_MARGIN = 1.25;   // a relative must hit this much harder to replace the comb's choice
const METRE_STAY_BONUS = 1.3;       // the tempo already held, or an octave of it, gets this much more
const UNWRAP_STRIKE = 0.55;         // the half period replaces the lag while its accents reach this share
const UNWRAP_MAX_BPM = 180;         // so drum and bass reads 174, not 87
const TEMPO_TOLERANCE = 0.03;       // estimates within 3% refine the tempo; farther ones must repeat
const TEMPO_REPEATS = 3;            // consecutive agreeing estimates before the tempo jumps
const COMB_DECAY = 0.85;            // weight of each older beat in the phase comb
const CONTRAST_FLOOR = 0.35;        // the best phase's lead over the average one that reads as confidence 0
const CONTRAST_RANGE = 0.3;         // and the further lead that reads as confidence 1
export const LOCK_CONFIDENCE = 0.35; // below this an estimate does not move the phase
const TEMPO_SETTLE = 20;            // readings (about 5 s) a tempo must hold before the clock follows it; music holds, talk does not
const JUMP_CONFIRM = 8;             // readings (about 2 s) that must agree before the clock jumps to a new phase
const JUMP_BEATS = 0.25;            // a correction this large is a jump (BeatFlywheel snaps rather than blends it)

const wrapHalf = (x) => x - Math.round(x); // into [-0.5, 0.5]

/** Each value's lead over its 150 ms moving average: what sticks out. */
function aboveAverage(raw) {
  const n = raw.length, half = 7;
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    let s = 0, c = 0;
    for (let j = Math.max(0, i - half); j <= Math.min(n - 1, i + half); j++) { s += raw[j]; c++; }
    out[i] = Math.max(0, raw[i] - s / c);
  }
  return out;
}

export class BeatTracker {
  constructor() { this.reset(); }

  reset() {
    this.env = new Float32Array(WINDOW);
    this.bands = [new Float32Array(WINDOW), new Float32Array(WINDOW), new Float32Array(WINDOW)];
    this.bandMean = [0.01, 0.01, 0.01];
    this.hasBands = false;
    this.lastBin = -1;     // absolute bin index of the newest written sample
    this.count = 0;
    this.curBin = null;    // bin being accumulated
    this.curVal = 0;
    this.curBands = null;
    this.bpm = PRIOR_BPM;
    this.tempoKnown = false;
    this.candidate = 0;
    this.candidateHits = 0;
  }

  /**
   * One onset value at time `t` (seconds), with the worklet's three band rises when it has them; without
   * them `value` serves for tempo too. Values in the same 10 ms bin merge (max); skipped bins are 0.
   */
  push(value, t, bands = null) {
    const scaled = bands ? bands.map((rise, k) => {
      this.bandMean[k] += (rise - this.bandMean[k]) * BAND_MEAN_RATE;
      return rise / (this.bandMean[k] + 1e-4);
    }) : null;
    if (scaled) this.hasBands = true;
    const bin = Math.floor(t / HOP);
    if (this.curBin === null) { this.curBin = bin; this.curVal = value; this.curBands = scaled; return; }
    if (bin === this.curBin) {
      this.curVal = Math.max(this.curVal, value);
      if (scaled) this.curBands = this.curBands ? this.curBands.map((b, k) => Math.max(b, scaled[k])) : scaled;
      return;
    }
    if (bin < this.curBin) return;
    this.#write(this.curBin, this.curVal, this.curBands);
    for (let b = Math.max(this.curBin + 1, bin - WINDOW); b < bin; b++) this.#write(b, 0, null);
    this.curBin = bin;
    this.curVal = value;
    this.curBands = scaled;
  }

  #write(bin, v, bands) {
    const at = ((bin % WINDOW) + WINDOW) % WINDOW;
    this.env[at] = v;
    for (let k = 0; k < 3; k++) this.bands[k][at] = bands ? bands[k] : 0;
    this.lastBin = bin;
    this.count = Math.min(WINDOW, this.count + 1);
  }

  /** The last `n` values of a ring, oldest first. */
  #recent(ring, n) {
    const out = new Float32Array(n);
    for (let i = 0; i < n; i++) out[i] = ring[(((this.lastBin - n + 1 + i) % WINDOW) + WINDOW) % WINDOW];
    return out;
  }

  /**
   * Tempo and phase from the envelope so far: `{bpm, beatTime, confidence}`, where `beatTime` (seconds,
   * same clock as push) is the most recent beat. `null` until there are 3 s of envelope.
   */
  analyse() {
    const n = this.count;
    if (n < MIN_SAMPLES) return null;
    const x = aboveAverage(this.#recent(this.env, n));
    let sum = 0, energy = 0;
    for (let i = 0; i < n; i++) { sum += x[i]; energy += x[i] * x[i]; }
    if (energy < 1e-9 || sum < 1e-6) return { bpm: this.bpm, beatTime: null, confidence: 0 };

    this.#updateTempo(this.#estimateTempo(x, n));

    // Phase: comb at the tracked tempo; the offset with the most onset energy is the time since the last beat.
    const sample = (pos) => {
      if (pos < 0 || pos > n - 1) return 0;
      const i = Math.floor(pos), f = pos - i;
      return x[i] * (1 - f) + (i + 1 < n ? x[i + 1] : 0) * f;
    };
    // Comb over the envelope positions in [lo, hi) at `period` samples: for each offset p (samples before
    // the newest), the decayed sum of onsets one period apart. Returns the best offset, its sum, and how
    // far it stands above the mean offset.
    const comb = (period, lo, hi, decay) => {
      const steps = Math.ceil(period);
      let best = 0, bestS = -1, total = 0;
      for (let p = 0; p < steps; p++) {
        let s = 0, w = 1;
        for (let pos = n - 1 - p; pos >= lo; pos -= period, w *= decay) {
          if (pos < hi) s += w * Math.max(sample(pos), sample(pos - 1), sample(pos + 1));
        }
        total += s;
        if (s > bestS) { bestS = s; best = p; }
      }
      return { best, score: bestS, contrast: (bestS - total / steps) / (bestS + 1e-9) };
    };
    // The autocorrelation tempo is only good to a lag step (~1-2%), which over 8 s smears the comb and
    // drags the phase; so fine-tune the period (+-2% in 0.1% steps) to the one whose comb peaks highest.
    let period = 60 / this.bpm / HOP;
    let bestFit = comb(period, 0, n, 1).score;
    const base = period;
    for (let k = -20; k <= 20; k++) {
      const per = base * (1 + k * 0.001);
      const fit = comb(per, 0, n, 1).score;
      if (fit > bestFit) { bestFit = fit; period = per; }
    }
    this.bpm = 60 / (period * HOP);
    const all = comb(period, 0, n, COMB_DECAY);
    // A real beat puts the older and the newer half of the window on the same grid; onsets that only
    // happen to line up across the whole window do not.
    const older = comb(period, 0, n / 2, 1), newer = comb(period, n / 2, n, 1);
    const halvesAgree = Math.abs(wrapHalf((older.best - newer.best) / period)) < 0.1;
    // How far the best phase stands above the average one. The floor is low because real music rarely
    // stands out as far as a steady synthetic beat; the halves check and BeatClock's agreement rules
    // catch what gets past it.
    const confidence = halvesAgree ? Math.max(0, Math.min(1, (all.contrast - CONTRAST_FLOOR) / CONTRAST_RANGE)) : 0;
    const beatTime = this.lastBin * HOP - all.best * HOP;
    return { bpm: this.bpm, beatTime, confidence };
  }

  /** This window's tempo in BPM. `x` is the phase envelope, which stands in when there are no bands. */
  #estimateTempo(x, n) {
    const channels = this.hasBands ? this.bands.map((ring) => aboveAverage(this.#recent(ring, n))) : [x];
    const minLag = Math.floor(60 / MAX_BPM / HOP);
    const maxLag = Math.ceil(60 / MIN_BPM / HOP);

    // Autocorrelation out to the slowest tempo's multiples, so the comb below can see them.
    const ac = new Float32Array(COMB_MULTIPLES * maxLag + 2);
    for (let lag = minLag; lag < ac.length && lag < n; lag++) {
      const win = Math.min(AC_WINDOW, n - lag);
      let s = 0;
      for (const c of channels) {
        for (let j = 0; j < win; j++) s += (1 - j / win) * c[n - 1 - j] * c[n - 1 - j - lag];
      }
      ac[lag] = s / win;
    }

    // A lag's score: the comb over its multiples, times a preference for usual tempos so a whole bar (which
    // always correlates best) does not win.
    const prior = new Float32Array(maxLag + 2);
    const score = new Float32Array(maxLag + 2);
    for (let lag = minLag; lag <= maxLag + 1; lag++) {
      const oct = Math.log2(60 / (lag * HOP) / PRIOR_BPM) / PRIOR_OCTAVES;
      prior[lag] = Math.exp(-0.5 * oct * oct);
      let comb = 0, norm = 0;
      for (let k = 1; k <= COMB_MULTIPLES && lag * k < ac.length; k++) { comb += ac[lag * k] / k; norm += 1 / k; }
      score[lag] = (comb / norm) * prior[lag];
    }
    let best = minLag;
    for (let lag = minLag + 1; lag <= maxLag; lag++) if (score[lag] > score[best]) best = lag;

    const onsets = this.#sumChannels(channels, n);
    best = this.#settleMetre(best, score, prior, minLag, maxLag, onsets, n);

    // Is the half period the real beat? A backbeat puts an accent on every beat of it; hats between the beats fall short.
    while (best >> 1 >= minLag) {
      const half = best >> 1;
      if (60 / (half * HOP) <= UNWRAP_MAX_BPM && foldedStrike(onsets, half, n) >= UNWRAP_STRIKE * foldedStrike(onsets, best, n)) best = half;
      else break;
    }

    let lagF = best;
    if (best > minLag && best < maxLag) {
      const a = score[best - 1], b = score[best], c = score[best + 1];
      const d = a - 2 * b + c;
      if (d < 0) lagF = best + 0.5 * (a - c) / d;
    }
    return 60 / (lagF * HOP);
  }

  #sumChannels(channels, n) {
    if (channels.length === 1) return channels[0];
    const out = new Float32Array(n);
    for (const c of channels) for (let i = 0; i < n; i++) out[i] += c[i];
    return out;
  }

  /**
   * Picks among the comb's strongest lags by where the accents fall, which autocorrelation alone cannot
   * tell from a 3:2 or 4:3 relative. The tempo already held, or an octave of it, gets a bonus: a groove that
   * shifts for a bar should not move the tempo.
   */
  #settleMetre(best, score, prior, minLag, maxLag, onsets, n) {
    const value = (lag) => {
      let v = foldedStrike(onsets, lag, n) * prior[lag];
      if (this.tempoKnown) {
        const r = 60 / (lag * HOP) / this.bpm;
        if (Math.abs(r - 1) < 0.04 || Math.abs(r - 2) < 0.08 || Math.abs(r - 0.5) < 0.02) v *= METRE_STAY_BONUS;
      }
      return v;
    };
    const peaks = [];
    for (let lag = minLag + 1; lag < maxLag; lag++) {
      if (score[lag] > score[lag - 1] && score[lag] >= score[lag + 1]) peaks.push(lag);
    }
    peaks.sort((a, b) => score[b] - score[a]);
    let pick = best, pickValue = value(best) * METRE_SWITCH_MARGIN;
    for (const lag of peaks.slice(0, METRE_CANDIDATES)) {
      if (lag === best) continue;
      const v = value(lag);
      if (v > pickValue) { pickValue = v; pick = lag; }
    }
    return pick;
  }

  #updateTempo(est) {
    if (!this.tempoKnown) { this.bpm = est; this.tempoKnown = true; return; }
    if (Math.abs(est / this.bpm - 1) < TEMPO_TOLERANCE) {
      this.bpm += (est - this.bpm) * 0.3;
      this.candidateHits = 0;
      return;
    }
    if (this.candidateHits > 0 && Math.abs(est / this.candidate - 1) < TEMPO_TOLERANCE) this.candidateHits++;
    else { this.candidate = est; this.candidateHits = 1; }
    if (this.candidateHits >= TEMPO_REPEATS) { this.bpm = est; this.candidateHits = 0; }
  }
}

/**
 * How hard the strongest repeating position hits with this period: the onsets folded at it, the best
 * position's mean per cycle. On the true beat every cycle lands on an accent; a syncopated gap of 1.5 beats
 * alternates between an accent and whatever falls on the off-beat. The period is only good to a lag step, and
 * half a step smears the accents over 8 s, so it tries either side.
 */
function foldedStrike(onsets, period, n) {
  let best = 0;
  for (let offset = -0.75; offset <= 0.75; offset += 0.25) {
    const per = Math.max(1, period + offset);
    const bins = Math.ceil(per);
    const fold = new Float32Array(bins);
    let pos = 0;
    for (let i = n - 1; i >= 0; i--) {
      fold[Math.min(pos | 0, bins - 1)] += onsets[i];
      pos += 1;
      if (pos >= per) pos -= per;
    }
    let top = 0;
    for (let p = 0; p < bins; p++) {
      const v = fold[p] + 0.5 * (fold[(p + 1) % bins] + fold[(p + bins - 1) % bins]);
      if (v > top) top = v;
    }
    best = Math.max(best, (top * per) / n);
  }
  return best;
}

/**
 * The standalone beat clock: runs at the tracker's tempo (120 BPM until there is one) once that tempo has held
 * for TEMPO_SETTLE readings, even while the phase is unsure, and is pulled onto the tracker's beat grid by
 * confident estimates at a settled tempo. Two estimates in a row must agree on the phase before it moves, and a
 * jump must hold for JUMP_CONFIRM readings, so an off-beat passage does not shift the clock.
 */
export class BeatClock {
  constructor() {
    this.fly = new BeatFlywheel();
    this.locked = false;
    this.lastLockMs = -Infinity;
    this.pending = null; // previous confident estimate: {frac, atMs}
    this.jumpReadings = 0;
    this.heldBpm = 0;    // the tempo being timed, and how many readings it has held
    this.heldReadings = 0;
  }

  get bpm() { return this.fly.valid ? this.fly.bpm : PRIOR_BPM; }

  /** Beats at `nowMs`; call once per frame. */
  read(nowMs) {
    if (!this.fly.valid) this.fly.anchor(0, PRIOR_BPM, nowMs);
    this.locked = nowMs - this.lastLockMs < 4000;
    return this.fly.read(nowMs);
  }

  /** Applies one BeatTracker.analyse() result at `nowMs` (beatTime in seconds, same clock as nowMs / 1000). */
  update(result, nowMs) {
    if (!this.fly.valid) this.fly.anchor(0, PRIOR_BPM, nowMs);
    if (result) {
      if (this.heldBpm > 0 && Math.abs(result.bpm / this.heldBpm - 1) < TEMPO_TOLERANCE) this.heldReadings++;
      else { this.heldBpm = result.bpm; this.heldReadings = 1; }
    }
    const settled = this.heldReadings >= TEMPO_SETTLE;
    if (result && settled) this.fly.retune(result.bpm, nowMs);
    if (!settled || !result || result.beatTime === null || result.confidence < LOCK_CONFIDENCE) { this.pending = null; return; }
    const frac = (((nowMs / 1000 - result.beatTime) * result.bpm / 60) % 1 + 1) % 1;
    const prev = this.pending;
    this.pending = { frac, atMs: nowMs, bpm: result.bpm };
    if (!prev) return;
    const expected = prev.frac + ((nowMs - prev.atMs) / 1000) * result.bpm / 60;
    if (Math.abs(wrapHalf(frac - expected)) > 0.1) { this.jumpReadings = 0; return; } // the two readings disagree: wait
    // A small backward error is absorbed by the flywheel coasting; a large one jumps forward to the next
    // beat instead, so the beat count (and every LFO phase) never runs backward.
    const predicted = this.fly.predict(nowMs);
    let err = wrapHalf(frac - predicted);
    if (err < -0.25) err += 1;
    if (err > JUMP_BEATS && ++this.jumpReadings < JUMP_CONFIRM) { this.lastLockMs = nowMs; return; }
    this.jumpReadings = 0;
    this.fly.anchor(predicted + err, result.bpm, nowMs);
    this.lastLockMs = nowMs;
  }
}
