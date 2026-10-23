// beattrack.js
// Standalone beat tracking for the radio stream (outside a live broadcast). An onset envelope goes in
// (onset-worklet.js computes one value per 10 ms at audio rate); a tempo, a beat phase and a confidence
// come out. Tempo: autocorrelation of the envelope, weighted towards ~120 BPM so it does not lock to
// double or half time. Phase: a comb filter at that tempo over the last few seconds. BeatClock turns
// the results into the smooth, forward-only beat count the modulators read (a BeatFlywheel fed anchors),
// so a preset's "on the beat" pulses land on the music's beats. Pure, so it is unit-tested in node.

import { BeatFlywheel } from './beatclock.js';

export const HOP = 0.01;            // seconds per envelope sample
const WINDOW = 800;                 // 8 s of envelope kept
const MIN_SAMPLES = 300;            // 3 s before the first estimate
const MIN_BPM = 70;
const MAX_BPM = 180;
const PRIOR_BPM = 120;
const PRIOR_OCTAVES = 0.7;          // width of the tempo prior (log-gaussian, in octaves)
const TEMPO_TOLERANCE = 0.03;       // estimates within 3% refine the tempo; farther ones must repeat
const TEMPO_REPEATS = 3;            // consecutive agreeing estimates before the tempo jumps
const COMB_DECAY = 0.85;            // weight of each older beat in the phase comb
export const LOCK_CONFIDENCE = 0.35; // below this an estimate is ignored and the clock coasts

const wrapHalf = (x) => x - Math.round(x); // into [-0.5, 0.5]

export class BeatTracker {
  constructor() { this.reset(); }

  reset() {
    this.env = new Float32Array(WINDOW);
    this.lastBin = -1;     // absolute bin index of the newest written sample
    this.count = 0;
    this.curBin = null;    // bin being accumulated
    this.curVal = 0;
    this.bpm = PRIOR_BPM;
    this.tempoKnown = false;
    this.candidate = 0;
    this.candidateHits = 0;
  }

  /** One onset-envelope value at time `t` (seconds). Values in the same 10 ms bin merge (max); skipped bins are 0. */
  push(value, t) {
    const bin = Math.floor(t / HOP);
    if (this.curBin === null) { this.curBin = bin; this.curVal = value; return; }
    if (bin === this.curBin) { this.curVal = Math.max(this.curVal, value); return; }
    if (bin < this.curBin) return;
    this.#write(this.curBin, this.curVal);
    for (let b = Math.max(this.curBin + 1, bin - WINDOW); b < bin; b++) this.#write(b, 0);
    this.curBin = bin;
    this.curVal = value;
  }

  #write(bin, v) {
    this.env[((bin % WINDOW) + WINDOW) % WINDOW] = v;
    this.lastBin = bin;
    this.count = Math.min(WINDOW, this.count + 1);
  }

  /**
   * Tempo and phase from the envelope so far: `{bpm, beatTime, confidence}`, where `beatTime` (seconds,
   * same clock as push) is the most recent beat. `null` until there are 3 s of envelope.
   */
  analyse() {
    const n = this.count;
    if (n < MIN_SAMPLES) return null;
    // Oldest first; subtract a 150 ms moving average and keep what sticks out (the onsets).
    const raw = new Float32Array(n);
    for (let i = 0; i < n; i++) raw[i] = this.env[(((this.lastBin - n + 1 + i) % WINDOW) + WINDOW) % WINDOW];
    const x = new Float32Array(n);
    const half = 7;
    let sum = 0, energy = 0;
    for (let i = 0; i < n; i++) {
      let s = 0, c = 0;
      for (let j = Math.max(0, i - half); j <= Math.min(n - 1, i + half); j++) { s += raw[j]; c++; }
      x[i] = Math.max(0, raw[i] - s / c);
      sum += x[i];
      energy += x[i] * x[i];
    }
    if (energy < 1e-9 || sum < 1e-6) return { bpm: this.bpm, beatTime: null, confidence: 0 };

    // Tempo: autocorrelation plus its second harmonic, under the prior.
    const minLag = Math.floor(60 / MAX_BPM / HOP);
    const maxLag = Math.ceil(60 / MIN_BPM / HOP);
    const ac = new Float32Array(2 * maxLag + 2);
    for (let lag = minLag; lag < ac.length && lag < n; lag++) {
      let s = 0;
      for (let i = lag; i < n; i++) s += x[i] * x[i - lag];
      ac[lag] = s / (n - lag);
    }
    const score = (lag) => {
      const bpm = 60 / (lag * HOP);
      const oct = Math.log2(bpm / PRIOR_BPM) / PRIOR_OCTAVES;
      return (ac[lag] + 0.5 * (ac[2 * lag] ?? 0)) * Math.exp(-0.5 * oct * oct);
    };
    let best = minLag;
    for (let lag = minLag + 1; lag <= maxLag; lag++) if (score(lag) > score(best)) best = lag;
    let lagF = best;
    if (best > minLag && best < maxLag) {
      const a = score(best - 1), b = score(best), c = score(best + 1);
      const d = a - 2 * b + c;
      if (d < 0) lagF = best + 0.5 * (a - c) / d;
    }
    this.#updateTempo(60 / (lagF * HOP));

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
    // How far the best phase stands above the average one: random onsets lining up reach ~0.6-0.7, a
    // steady beat (even with a third of its kicks missing) ~0.9.
    const confidence = halvesAgree ? Math.max(0, Math.min(1, (all.contrast - 0.6) / 0.3)) : 0;
    const beatTime = this.lastBin * HOP - all.best * HOP;
    return { bpm: this.bpm, beatTime, confidence };
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
 * The standalone beat clock: coasts at the last tempo (120 BPM until there is one) and is pulled onto
 * the tracker's beat grid by confident estimates. Two estimates in a row must agree on the phase
 * before it moves, so a single off-beat reading never shifts the clock.
 */
export class BeatClock {
  constructor() {
    this.fly = new BeatFlywheel();
    this.locked = false;
    this.lastLockMs = -Infinity;
    this.pending = null; // previous confident estimate: {frac, atMs}
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
    if (!result || result.beatTime === null || result.confidence < LOCK_CONFIDENCE) { this.pending = null; return; }
    const frac = (((nowMs / 1000 - result.beatTime) * result.bpm / 60) % 1 + 1) % 1;
    const prev = this.pending;
    this.pending = { frac, atMs: nowMs, bpm: result.bpm };
    if (!prev) return;
    const expected = prev.frac + ((nowMs - prev.atMs) / 1000) * result.bpm / 60;
    if (Math.abs(wrapHalf(frac - expected)) > 0.1) return; // the two readings disagree: wait
    // A small backward error is absorbed by the flywheel coasting; a large one jumps forward to the next
    // beat instead, so the beat count (and every LFO phase) never runs backward.
    const predicted = this.fly.predict(nowMs);
    let err = wrapHalf(frac - predicted);
    if (err < -0.25) err += 1;
    this.fly.anchor(predicted + err, result.bpm, nowMs);
    this.lastLockMs = nowMs;
  }
}
