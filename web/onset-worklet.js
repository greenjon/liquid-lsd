// onset-worklet.js
// AudioWorklet: the onsets beattrack.js tracks, computed at audio rate so their timing does not depend on
// the frame rate. Every 10 ms hop it measures two things, each best at a different job:
//  - The phase envelope: log energy of the bass (two one-pole low-passes at 150 Hz) and of the full signal;
//    its value is their rise since the previous hop (bass weighted double). Finds where the beat is.
//  - The tempo bands: RMS level through the same three filters as the desktop's tracker (`AudioEngine`: a low
//    pass at 150 Hz, a band pass at 1 kHz and a high pass at 5 kHz); each band's rise since the previous
//    hop. Finds how fast the beat is.
// Posted in batches of 10 with the context time of the first one.

const HOP_SEC = 0.01;
const FILTER_Q = 0.707;

/** Biquad coefficients, as BiquadFilter.kt computes them. */
function biquad(type, freq, rate) {
  const w = (2 * Math.PI * freq) / rate;
  const cs = Math.cos(w);
  const alpha = Math.sin(w) / (2 * FILTER_Q);
  const norm = 1 / (1 + alpha);
  let b0, b1, b2;
  if (type === 'low') { b0 = (1 - cs) * 0.5; b1 = 1 - cs; b2 = b0; }
  else if (type === 'high') { b0 = (1 + cs) * 0.5; b1 = -(1 + cs); b2 = b0; }
  else { b0 = alpha; b1 = 0; b2 = -alpha; }
  return { b0: b0 * norm, b1: b1 * norm, b2: b2 * norm, a1: -2 * cs * norm, a2: (1 - alpha) * norm, z1: 0, z2: 0, energy: 0, prev: 0 };
}

class OnsetProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.hop = Math.round(sampleRate * HOP_SEC);
    this.a = 1 - Math.exp((-2 * Math.PI * 150) / sampleRate);
    this.lp1 = 0;
    this.lp2 = 0;
    this.low = 0;
    this.full = 0;
    this.n = 0;
    this.prevLow = 0;
    this.prevFull = 0;
    this.filters = [biquad('low', 150, sampleRate), biquad('band', 1000, sampleRate), biquad('high', 5000, sampleRate)];
    this.values = [];
    this.bands = [];
    this.t0 = 0;
  }

  process(inputs, outputs) {
    const input = inputs[0];
    if (input && input.length) {
      const l = input[0], r = input[1] ?? input[0];
      for (let i = 0; i < l.length; i++) {
        const x = 0.5 * (l[i] + r[i]);
        this.lp1 += this.a * (x - this.lp1);
        this.lp2 += this.a * (this.lp1 - this.lp2);
        this.low += this.lp2 * this.lp2;
        this.full += x * x;
        for (const f of this.filters) {
          const y = f.b0 * x + f.z1;
          f.z1 = f.b1 * x - f.a1 * y + f.z2;
          f.z2 = f.b2 * x - f.a2 * y;
          f.energy += y * y;
        }
        if (++this.n < this.hop) continue;
        const low = Math.log1p((1000 * this.low) / this.hop);
        const full = Math.log1p((1000 * this.full) / this.hop);
        const v = 2 * Math.max(0, low - this.prevLow) + Math.max(0, full - this.prevFull);
        this.prevLow = low;
        this.prevFull = full;
        const rises = this.filters.map((f) => {
          const level = Math.sqrt(f.energy / this.hop);
          const rise = Math.max(0, level - f.prev);
          f.prev = level;
          f.energy = 0;
          return rise;
        });
        if (this.values.length === 0) this.t0 = currentTime + (i + 1) / sampleRate;
        this.values.push(v);
        this.bands.push(rises);
        this.low = this.full = 0;
        this.n = 0;
        if (this.values.length >= 10) {
          this.port.postMessage({ t0: this.t0, hop: this.hop / sampleRate, values: this.values, bands: this.bands });
          this.values = [];
          this.bands = [];
        }
      }
    }
    // Silent output: the node is connected (through a muted gain) only so the graph keeps pulling it.
    for (const ch of outputs[0] ?? []) ch.fill(0);
    return true;
  }
}

registerProcessor('lsd-onset', OnsetProcessor);
