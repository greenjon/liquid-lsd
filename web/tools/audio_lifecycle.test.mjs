// node --test web/tools/audio_lifecycle.test.mjs : the microphone request lifecycle and the Safari volume, in dsp.js,
// with fake browser objects. No real capture device is ever opened.
import test from 'node:test';
import assert from 'node:assert/strict';

const param = () => ({ value: 0, setTargetAtTime() {}, cancelScheduledValues() {}, setValueAtTime() {}, linearRampToValueAtTime() {}, exponentialRampToValueAtTime() {} });
const node = () => new Proxy({ gain: param(), frequency: param(), Q: param(), connect() {}, disconnect() {}, port: {} }, {
  get: (t, k) => (k in t ? t[k] : undefined),
  set: (t, k, v) => { t[k] = v; return true; },
});
class FakeCtx {
  constructor() { this.state = 'running'; this.currentTime = 0; this.sampleRate = 48000; this.destination = node(); this.audioWorklet = { addModule: async () => { throw new Error('no worklet'); } }; }
  createMediaElementSource() { return node(); }
  createMediaStreamSource() { return node(); }
  createGain() { return node(); }
  createBiquadFilter() { return node(); }
  createAnalyser() { return Object.assign(node(), { fftSize: 1024 }); }
  async resume() { this.state = 'running'; }
  async suspend() { this.state = 'suspended'; }
}
const audioEl = { volume: 1, paused: true, currentTime: 0, addEventListener() {}, removeAttribute() {}, load() {}, pause() { this.paused = true; }, play: async () => {}, removeEventListener() {} };
globalThis.window = { AudioContext: FakeCtx, dispatchEvent() {}, addEventListener() {} };
globalThis.CustomEvent = class { constructor(type, init) { this.type = type; this.detail = init?.detail; } };
globalThis.document = { getElementById: () => audioEl };
Object.defineProperty(globalThis, 'navigator', { value: { userAgent: 'Mozilla/5.0 (Macintosh) AppleWebKit/605.1.15 Version/17 Safari/605.1.15' }, configurable: true });
globalThis.Audio = class { constructor() { this.volume = 1; } };

let pending = [];
const streams = [];
const fakeStream = () => {
  const s = { stopped: 0, getTracks() { return [{ stop: () => s.stopped++ }]; }, getAudioTracks() { return [{ addEventListener() {} }]; } };
  streams.push(s);
  return s;
};
navigator.mediaDevices = { getUserMedia: () => new Promise((resolve) => pending.push(() => resolve(fakeStream()))) };

const dsp = await import('../dsp.js');
await dsp.startAudio();

test('mic: power-off while the prompt is up stops the stream when it resolves', async () => {
  const p = dsp.setMic(true);
  await dsp.stopAudio();
  pending.shift()();
  await p;
  assert.equal(dsp.isMicOn(), false);
  assert.equal(streams.at(-1).stopped, 1);
  await dsp.startAudio();
});

test('mic: a second request during the prompt does not open a second stream', async () => {
  pending = [];
  const before = streams.length;
  const a = dsp.setMic(true);
  const b = dsp.setMic(true);
  assert.equal(pending.length, 1);
  pending.shift()();
  await Promise.all([a, b]);
  assert.equal(dsp.isMicOn(), true);
  assert.equal(streams.length, before + 1);
  assert.equal(streams.at(-1).stopped, 0);
  await dsp.setMic(false);
  assert.equal(streams.at(-1).stopped, 1);
});

test('mic: switching back while the prompt is up cancels it', async () => {
  pending = [];
  const p = dsp.setMic(true);
  await dsp.setMic(false);
  pending.shift()();
  await p;
  assert.equal(dsp.isMicOn(), false);
  assert.equal(streams.at(-1).stopped, 1);
});

test('safari: the dial applies to the element from the start', async () => {
  dsp.setVolume(0.5);
  await dsp.startAudio();
  assert.equal(audioEl.volume, 0.25);
});

test('side feed getter is false when none runs', () => {
  assert.equal(dsp.isSideFeedRunning(), false);
});
