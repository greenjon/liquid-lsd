// Liquid LSD Web Audio DSP & spaz.org Integration (Phase 2)
// Real-time Web Audio analysis, beat detection, and CV output

import { BeatTracker, BeatClock } from './beattrack.js';
import { EnergyWatch } from './energy.js';
import { embed } from './embedmode.js';

export const cvState = {
  // The audio_* signals are scaled exactly as the desktop's AudioEngine publishes them, so a
  // preset's depths mean the same thing in both places.
  audio_amp:      0.0,   // broadband RMS / 0.25, range 0..1
  audio_bass:     0.0,   // low-frequency RMS (<= 150 Hz) / 0.25, range 0..1
  audio_mid:      0.0,   // mid-frequency RMS (~1 kHz) / 0.25, range 0..1
  audio_high:     0.0,   // high-frequency RMS (>= 5 kHz) / 0.25, range 0..1
  audio_flux_amp:  0.0,  // weighted onset strength (2 bass + 0.8 mid + 0.3 high flux) / 0.1
  audio_flux_bass: 0.0,  // half-wave rectified RMS growth since the last frame / 0.05
  audio_flux_mid:  0.0,
  audio_flux_high: 0.0,
  totalBeats:     0.0,   // standalone beat clock (beattrack.js): locked to the music's beats when it can be
  bpm:            120.0, // tracked tempo (120 until there is one)
  beatLocked:     false, // true while the clock follows a confidently tracked beat
  section:        'normal', // 'normal' | 'breakdown' (energy.js), for the autopilot
  drops:          0,     // drops detected so far (a breakdown ending with the low end back)
  noSignal:       false, // the TV is on but the stream is down or silent (renderer shows snow)
  isLive:         false, // true once AudioContext is running and stream is connected
};

// Analysis nodes and pre-allocated buffers
let audioCtx = null;
let gainNode = null;
let broadbandAnalyser = null;
let bassAnalyser = null;
let midAnalyser = null;
let highAnalyser = null;

let broadBuf = null;
let bassBuf = null;
let midBuf = null;
let highBuf = null;

let analysisReady = false;
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

/**
 * Turns this frame's band RMS values (and last frame's) into the desktop's CV signals
 * (AudioEngine: fixed scales, half-wave rectified flux). Pure, so it is unit-tested in node.
 */
export function bandSignals(rms, prev) {
  const bassFlux = Math.max(0, rms.bass - prev.bass);
  const midFlux  = Math.max(0, rms.mid  - prev.mid);
  const highFlux = Math.max(0, rms.high - prev.high);
  const onsetStrength = bassFlux * 2.0 + midFlux * 0.8 + highFlux * 0.3;
  return {
    audio_amp:  clamp01(rms.amp  / AMP_SCALE),
    audio_bass: clamp01(rms.bass / AMP_SCALE),
    audio_mid:  clamp01(rms.mid  / AMP_SCALE),
    audio_high: clamp01(rms.high / AMP_SCALE),
    audio_flux_amp:  clamp01(onsetStrength / FLUX_AMP_SCALE),
    audio_flux_bass: clamp01(bassFlux / FLUX_BAND_SCALE),
    audio_flux_mid:  clamp01(midFlux  / FLUX_BAND_SCALE),
    audio_flux_high: clamp01(highFlux / FLUX_BAND_SCALE),
  };
}

const prevRms = { bass: 0, mid: 0, high: 0 };

function updateAmplitudes() {
  const rms = {
    amp:  calcRms(broadbandAnalyser, broadBuf),
    bass: calcRms(bassAnalyser,      bassBuf),
    mid:  calcRms(midAnalyser,       midBuf),
    high: calcRms(highAnalyser,      highBuf),
  };
  Object.assign(cvState, bandSignals(rms, prevRms));
  prevRms.bass = rms.bass; prevRms.mid = rms.mid; prevRms.high = rms.high;
}

// Standalone beat clock (beattrack.js). The onset worklet's envelope feeds the tracker; every 250 ms its
// estimate pulls the clock onto the music's beats. With no audio (or no clear beat) the clock coasts at
// the last tempo, so beat-synced modulators keep running.
const tracker = new BeatTracker();
const energy = new EnergyWatch();
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
// fades to analog snow; every 10 s the stream is reconnected. `?nostatic` (headless checks, which have
// no audio) turns this off.
const STREAM_URL = 'https://radio.spaz.org:8060/radio.ogg';
const SILENT_AMP = 0.004;
let wantAudio = false;
let quietFor = 0;
let lastRetryMs = 0;
const noStatic = typeof location !== 'undefined' && new URLSearchParams(location.search).has('nostatic');

function updateSignal(dt) {
  if (embed) { cvState.noSignal = false; return; } // the host plays the radio; silence is just a quiet picture
  quietFor = analysisReady && cvState.audio_amp > SILENT_AMP ? 0 : quietFor + dt;
  cvState.noSignal = wantAudio && !noStatic && quietFor > 4;
  const now = performance.now();
  if (cvState.noSignal && !micOn && now - lastRetryMs > 10000) {
    lastRetryMs = now;
    reconnect();
  }
}

/** Reloads the stream (a fresh request, so a dead Icecast connection is not reused). */
function reconnect() {
  const audioEl = document.getElementById('lsdAudio');
  if (!audioEl || !audioCtx) return;
  audioEl.src = `${STREAM_URL}?t=${Date.now()}`;
  audioEl.play().then(() => { analysisReady = true; cvState.isLive = true; }).catch(() => { /* next try in 10 s */ });
}

export function tick(dt) {
  updateSignal(dt);
  if (analysisReady) {
    updateAmplitudes();
    energy.update(cvState.audio_bass, cvState.audio_amp, dt);
    cvState.section = energy.section;
    cvState.drops = energy.drops;
    cvState.energy = energy; // short / long levels, for tuning (window.LSD.cvState.energy)
    // Without the worklet, the per-frame onset strength is the envelope (coarser timing, same tracker).
    if (!onsetFromWorklet) tracker.push(cvState.audio_flux_amp, performance.now() / 1000);
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
export const micAvailable = () => !embed && !!navigator.mediaDevices?.getUserMedia;
export const isMicOn = () => micOn;

function releaseMic() {
  if (!micOn) return;
  micOn = false;
  micSource?.disconnect();
  micStream?.getTracks().forEach((t) => t.stop());
  micSource = micStream = null;
  window.dispatchEvent(new CustomEvent('lsd-mic', { detail: false }));
}

/** Switches the analysed input to the microphone (`on`) or back to the radio; rejects if the mic is refused. */
export async function setMic(on) {
  if (embed || on === micOn || !audioCtx) return micOn;
  if (on) {
    // Raw sound: the browser's speech clean-up would flatten the beat.
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false },
    });
    micStream = stream;
    micSource = audioCtx.createMediaStreamSource(stream);
    micSource.connect(analysisIn);
    micOn = true;
    stream.getAudioTracks()[0]?.addEventListener('ended', () => { if (micStream === stream) setMic(false).catch(() => {}); });
    document.getElementById('lsdAudio')?.pause();
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
  // Set stream src on user gesture (embed mode: the host app plays the radio and feeds us instead)
  if (!embed) audioEl.src = STREAM_URL;

  if (!audioCtx) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    audioCtx = new AudioContextClass();

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

    gainNode = audioCtx.createGain();
    gainNode.gain.value = currentVolumeRatio * currentVolumeRatio;

    // Wire graph
    if (radio) {
      radio.connect(source);
      radio.connect(gainNode);
      gainNode.connect(audioCtx.destination);
    }
    source.connect(broadbandAnalyser);
    source.connect(bassFilter);
    bassFilter.connect(bassAnalyser);
    source.connect(midFilter);
    midFilter.connect(midAnalyser);
    source.connect(highFilter);
    highFilter.connect(highAnalyser);

    if (embed) {
      // The host's PCM is the input. Nothing is audible from here; the analysers hang off a muted gain so
      // the graph keeps pulling them.
      await audioCtx.audioWorklet.addModule('feed-worklet.js');
      feedNode = new AudioWorkletNode(audioCtx, 'lsd-feed', { numberOfInputs: 0, outputChannelCount: [1] });
      feedNode.connect(source);
      const sink = audioCtx.createGain();
      sink.gain.value = 0;
      for (const a of [broadbandAnalyser, bassAnalyser, midAnalyser, highAnalyser]) a.connect(sink);
      sink.connect(audioCtx.destination);
      window.dispatchEvent(new CustomEvent('lsd-embed-ready'));
    }

    // Onset envelope at audio rate for the beat tracker. The worklet's output only goes to a muted gain,
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
        for (let i = 0; i < data.values.length; i++) tracker.push(data.values[i], ctxToPerfMs(data.t0 + i * data.hop) / 1000);
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
  }

  if (audioCtx.state === 'suspended') {
    await audioCtx.resume();
  }

  if (embed) { /* the host app plays the radio */ }
  else if (micOn) audioEl.pause();  // the microphone is the input; the radio waits
  else await audioEl.play();

  cvState.isLive = true;
  analysisReady = true;
}

// Stop audio stream and pause analysis on power off
export async function stopAudio() {
  wantAudio = false;
  releaseMic();
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
  cvState.audio_flux_amp = 0.0;
  cvState.audio_flux_bass = 0.0;
  cvState.audio_flux_mid = 0.0;
  cvState.audio_flux_high = 0.0;
  prevRms.bass = prevRms.mid = prevRms.high = 0;
}

// volume: 0.0 (muted) to 1.0 (full) — uses squared curve for perceptual linearity
export function setVolume(volume) {
  currentVolumeRatio = volume;
  if (gainNode && audioCtx) {
    gainNode.gain.setTargetAtTime(volume * volume, audioCtx.currentTime, 0.05);
  }
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
