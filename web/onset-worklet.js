// onset-worklet.js
// AudioWorklet: the onset envelope beattrack.js tracks, computed at audio rate so its timing does not
// depend on the frame rate. Every 10 ms hop: log energy of the bass (two one-pole low-passes at
// 150 Hz) and of the full signal; the envelope is their rise since the previous hop (bass weighted
// double). Values are posted in batches of 10 with the context time of the first one.

class OnsetProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.hop = Math.round(sampleRate * 0.01);
    this.a = 1 - Math.exp((-2 * Math.PI * 150) / sampleRate);
    this.lp1 = 0;
    this.lp2 = 0;
    this.low = 0;
    this.full = 0;
    this.n = 0;
    this.prevLow = 0;
    this.prevFull = 0;
    this.values = [];
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
        if (++this.n < this.hop) continue;
        const low = Math.log1p((1000 * this.low) / this.hop);
        const full = Math.log1p((1000 * this.full) / this.hop);
        const v = 2 * Math.max(0, low - this.prevLow) + Math.max(0, full - this.prevFull);
        this.prevLow = low;
        this.prevFull = full;
        if (this.values.length === 0) this.t0 = currentTime + (i + 1) / sampleRate;
        this.values.push(v);
        this.low = this.full = 0;
        this.n = 0;
        if (this.values.length >= 10) {
          this.port.postMessage({ t0: this.t0, hop: this.hop / sampleRate, values: this.values });
          this.values = [];
        }
      }
    }
    // Silent output: the node is connected (through a muted gain) only so the graph keeps pulling it.
    for (const ch of outputs[0] ?? []) ch.fill(0);
    return true;
  }
}

registerProcessor('lsd-onset', OnsetProcessor);
