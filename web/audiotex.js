// Shader audio textures for the web TV: a port of the desktop's SpectrumAnalyzer.kt and AudioTexture.kt.
// Keep the two in step (golden vectors: web/tools/spectrum_vectors.json from WebSpectrumVectorsTest).
//
// Three textures, as on the desktop:
// - stacked 512x2 (Shadertoy convention) behind the fixed names `audioFFT` and `iChannel0`: row 0 the spectrum,
//   row 1 the waveform (0..1, silence 0.5);
// - 512x1 spectrum and 512x1 waveform for ISF `audioFFT` / `audio` inputs, bound under the shader's own names.

const SIZE = 1024;
export const BINS = 512;
const MIN_DB = -80;
const RELEASE_SEC = 0.08;
const LOG10_INV = 0.43429448;

/**
 * Display spectrum (port of SpectrumAnalyzer.kt): the newest 1024 samples, Hann-windowed, radix-2 FFT, 512
 * linear bins; magnitude normalised so a full-scale sine reads 1, then -80..0 dB mapped onto 0..1. Instant
 * attack, one-pole release (0.08 s). Pure, so it is unit-tested in node.
 */
export class SpectrumAnalyzer {
  constructor() {
    this.ring = new Float32Array(SIZE);
    this.writePos = 0;
    this.window = new Float32Array(SIZE);
    let sum = 0;
    for (let i = 0; i < SIZE; i++) { this.window[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / SIZE)); sum += this.window[i]; }
    this.normGain = 2 / sum;
    this.re = new Float32Array(SIZE);
    this.im = new Float32Array(SIZE);
    this.cosT = new Float32Array(SIZE / 2);
    this.sinT = new Float32Array(SIZE / 2);
    for (let i = 0; i < SIZE / 2; i++) { this.cosT[i] = Math.cos(-2 * Math.PI * i / SIZE); this.sinT[i] = Math.sin(-2 * Math.PI * i / SIZE); }
    this.bitRev = new Uint16Array(SIZE);
    const bits = Math.log2(SIZE);
    for (let i = 0; i < SIZE; i++) {
      let r = 0;
      for (let b = 0; b < bits; b++) r |= ((i >> b) & 1) << (bits - 1 - b);
      this.bitRev[i] = r;
    }
    this.spectrum = new Float32Array(BINS);
  }

  reset() { this.ring.fill(0); this.writePos = 0; this.spectrum.fill(0); }

  /**
   * Appends `frames` samples and refreshes `spectrum`. `elapsed` (samples) sets the release; it defaults to
   * `frames`. The TV passes the analyser's newest 1024 samples each frame (overlapping) with `elapsed` = the
   * samples that passed since the last frame, so the release runs in real time whatever the frame rate.
   */
  process(samples, frames, sampleRate, elapsed = frames) {
    if (frames <= 0) return;
    const { ring, re, im, window, bitRev } = this;
    const start = frames > SIZE ? frames - SIZE : 0;
    for (let i = start; i < frames; i++) {
      ring[this.writePos] = samples[i];
      this.writePos = (this.writePos + 1) & (SIZE - 1);
    }
    for (let i = 0; i < SIZE; i++) {
      re[bitRev[i]] = ring[(this.writePos + i) & (SIZE - 1)] * window[i];
      im[bitRev[i]] = 0;
    }
    this.fft();
    const decay = Math.exp(-elapsed / (Math.max(sampleRate, 1) * RELEASE_SEC));
    const out = this.spectrum;
    for (let k = 0; k < BINS; k++) {
      const mag = Math.sqrt(re[k] * re[k] + im[k] * im[k]) * this.normGain;
      const db = 20 * (Math.log(mag + 1e-9) * LOG10_INV);
      const v = Math.min(1, Math.max(0, (db - MIN_DB) / -MIN_DB));
      out[k] = Math.max(v, out[k] * decay);
    }
  }

  fft() {
    const { re, im, cosT, sinT } = this;
    for (let len = 2; len <= SIZE; len <<= 1) {
      const half = len >> 1;
      const step = SIZE / len;
      for (let i = 0; i < SIZE; i += len) {
        let t = 0;
        for (let k = 0; k < half; k++) {
          const c = cosT[t], s = sinT[t];
          const a = i + k, b = a + half;
          const tr = re[b] * c - im[b] * s;
          const ti = re[b] * s + im[b] * c;
          re[b] = re[a] - tr; im[b] = im[a] - ti;
          re[a] += tr; im[a] += ti;
          t += step;
        }
      }
    }
  }
}

/**
 * What the shaders read: `spectrum` (0..1) and `wave` (the newest 512 samples, -1..1), filled by dsp.js each
 * frame. render_check.html writes a test signal here directly (there is no audio in a headless browser).
 */
export const audioFrame = {
  spectrum: new Float32Array(BINS),
  wave: new Float32Array(BINS),
};

/** The three GL textures, refreshed from `audioFrame` once per frame by the renderer. */
export class AudioTextures {
  constructor(gl) {
    this.gl = gl;
    this.stacked = this.make(2);
    this.fft = this.make(1);
    this.wave = this.make(1);
    this.rows = new Float32Array(BINS * 2);
  }

  make(height) {
    const { gl } = this;
    const tex = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, tex);
    // R16F is filterable in core WebGL2 (R32F needs OES_texture_float_linear) and accepts FLOAT uploads.
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.R16F, BINS, height, 0, gl.RED, gl.FLOAT, new Float32Array(BINS * height));
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    return tex;
  }

  update(frame = audioFrame) {
    const { gl, rows } = this;
    for (let i = 0; i < BINS; i++) {
      rows[i] = Math.min(1, Math.max(0, frame.spectrum[i]));
      rows[BINS + i] = Math.min(1, Math.max(0, frame.wave[i] * 0.5 + 0.5));
    }
    gl.bindTexture(gl.TEXTURE_2D, this.stacked);
    gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 0, BINS, 2, gl.RED, gl.FLOAT, rows);
    gl.bindTexture(gl.TEXTURE_2D, this.fft);
    gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 0, BINS, 1, gl.RED, gl.FLOAT, rows, 0);
    gl.bindTexture(gl.TEXTURE_2D, this.wave);
    gl.texSubImage2D(gl.TEXTURE_2D, 0, 0, 0, BINS, 1, gl.RED, gl.FLOAT, rows, BINS);
    gl.bindTexture(gl.TEXTURE_2D, null);
  }

  dispose() {
    for (const t of [this.stacked, this.fft, this.wave]) this.gl.deleteTexture(t);
  }
}
