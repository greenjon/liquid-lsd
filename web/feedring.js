// A mono PCM ring buffer with a linear resampler: audio pushed in at the stream's rate is pulled out at
// the audio context's rate. Used by feed-worklet.js (embed mode); plain JS so node can test it.

const PREFILL_S = 0.04;   // wait for this much audio before starting, and again after an underrun
const MAX_LAG_S = 0.30;   // more than this queued (the host sent a burst): skip ahead...
const KEEP_LAG_S = 0.08;  // ...to this much, so the picture stays close to the sound

export class FeedRing {
  constructor(seconds = 2, rate = 44100) {
    this.rate = rate;
    this.buf = new Float32Array(Math.ceil(seconds * 192000));
    this.written = 0;   // samples ever pushed
    this.pos = 0;       // read position in samples (fractional)
    this.primed = false;
    this.underruns = 0;
  }

  setRate(rate) {
    if (rate > 0 && rate !== this.rate) this.rate = rate;
  }

  /** Appends samples: Float32Array (-1..1) or Int16Array. */
  push(samples) {
    const n = this.buf.length;
    const scale = samples instanceof Int16Array ? 1 / 32768 : 1;
    for (let i = 0; i < samples.length; i++) this.buf[(this.written + i) % n] = samples[i] * scale;
    this.written += samples.length;
    const queued = this.written - this.pos;
    if (queued > this.rate * MAX_LAG_S) this.pos = this.written - this.rate * KEEP_LAG_S;
    if (queued > n - 2) this.pos = this.written - n + 2;
  }

  get queued() { return this.written - this.pos; }

  /** Fills `out` (Float32Array) at `outRate`; silence while the ring is priming or has run dry. */
  pull(out, outRate) {
    const n = this.buf.length;
    const step = this.rate / outRate;
    if (!this.primed) {
      if (this.queued < this.rate * PREFILL_S) { out.fill(0); return; }
      this.primed = true;
    }
    for (let i = 0; i < out.length; i++) {
      if (this.written - this.pos < 2) {
        out.fill(0, i);
        this.primed = false;
        this.underruns++;
        return;
      }
      const i0 = Math.floor(this.pos);
      const frac = this.pos - i0;
      const a = this.buf[i0 % n], b = this.buf[(i0 + 1) % n];
      out[i] = a + (b - a) * frac;
      this.pos += step;
    }
  }
}
