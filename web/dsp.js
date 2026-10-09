// Liquid LSD Web Audio DSP & spaz.org Integration (Phase 2)
// Real-time Web Audio analysis, beat detection, and CV output

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
  totalBeats:     0.0,   // beats integrated at the estimated tempo (standalone clock)
  bpm:            120.0, // estimated BPM
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

// Median helper
function median(arr) {
  if (arr.length === 0) return 0;
  const s = [...arr].sort((a, b) => a - b);
  const m = Math.floor(s.length / 2);
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
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

// Beat detection state
let shortTermEnergy = 0;
let longTermEnergy  = 0;
let lastOnsetTime   = 0;
const ioiHistory    = [];
let totalBeats      = 0;
let bpmEstimate     = 120;

function updateBeat(dt) {
  if (analysisReady) {
    const raw = calcRms(bassAnalyser, bassBuf);

    // Dual-average onset detection
    shortTermEnergy = shortTermEnergy * 0.8 + raw * 0.2;
    longTermEnergy  = longTermEnergy  * 0.99 + raw * 0.01;

    const THRESHOLD = 1.4;
    const MIN_IOI   = 250; // ms — prevents double-triggers (max 240 BPM)

    const now = performance.now();
    if (shortTermEnergy > THRESHOLD * longTermEnergy &&
        shortTermEnergy > 0.01 &&
        (now - lastOnsetTime) > MIN_IOI) {

      const ioi = now - lastOnsetTime;
      lastOnsetTime = now;

      // Update BPM estimate (ignore anomalous intervals)
      if (ioi > 0 && ioi < 3000) {
        ioiHistory.push(ioi);
        if (ioiHistory.length > 8) ioiHistory.shift();
        const medianIoi = median(ioiHistory);
        if (medianIoi > 0) {
          bpmEstimate = 60000 / medianIoi;
          bpmEstimate = Math.max(60, Math.min(200, bpmEstimate));
          cvState.bpm = bpmEstimate;
        }
      }
    }
  }

  // Advance beat clock using current BPM estimate (dt in seconds)
  totalBeats += (bpmEstimate / 60) * dt;

  cvState.totalBeats = totalBeats;
}

// Per-frame tick called from renderer.js rAF loop
export function tick(dt) {
  if (analysisReady) {
    updateAmplitudes();
  }
  updateBeat(dt);
}

let currentVolumeRatio = 0.8;

// User-gesture startup sequence
export async function startAudio() {
  const audioEl = document.getElementById('lsdAudio');

  if (!audioEl) {
    console.error('lsdAudio element not found');
    return;
  }

  // Set stream src on user gesture
  audioEl.src = 'https://radio.spaz.org:8060/radio.ogg';

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
