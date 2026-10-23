// Liquid LSD Web Audio DSP & spaz.org Integration (Phase 2)
// Real-time Web Audio analysis, beat detection, and CV output

import { BeatTracker, BeatClock } from './beattrack.js';
import { EnergyWatch } from './energy.js';

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
  quietFor = analysisReady && cvState.audio_amp > SILENT_AMP ? 0 : quietFor + dt;
  cvState.noSignal = wantAudio && !noStatic && quietFor > 4;
  const now = performance.now();
  if (cvState.noSignal && now - lastRetryMs > 10000) {
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

// User-gesture startup sequence
export async function startAudio() {
  const audioEl = document.getElementById('lsdAudio');

  if (!audioEl) {
    console.error('lsdAudio element not found');
    return;
  }

  wantAudio = true;
  quietFor = 0;
  lastRetryMs = performance.now(); // the first reconnect waits for this attempt to fail
  // Set stream src on user gesture
  audioEl.src = STREAM_URL;

  if (!audioCtx) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    audioCtx = new AudioContextClass();

    const source = audioCtx.createMediaElementSource(audioEl);

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
    source.connect(gainNode);
    gainNode.connect(audioCtx.destination);
    source.connect(broadbandAnalyser);
    source.connect(bassFilter);
    bassFilter.connect(bassAnalyser);
    source.connect(midFilter);
    midFilter.connect(midAnalyser);
    source.connect(highFilter);
    highFilter.connect(highAnalyser);

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

  await audioEl.play();

  cvState.isLive = true;
  analysisReady = true;
}

// Stop audio stream and pause analysis on power off
export async function stopAudio() {
  wantAudio = false;
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
