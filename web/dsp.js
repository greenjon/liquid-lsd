// Liquid LSD Web Audio DSP & spaz.org Integration (Phase 2)
// Real-time Web Audio analysis, beat detection, and CV output

export const cvState = {
  audio_amp:      0.0,   // broadband RMS, range 0..1
  audio_bass:     0.0,   // low-frequency RMS (< 180 Hz), range 0..1
  audio_mid:      0.0,   // mid-frequency RMS (~1 kHz), range 0..1
  audio_high:     0.0,   // high-frequency RMS (> 5 kHz), range 0..1
  beatPhase:      0.0,   // current position in beat cycle, range 0..1
  beatSine:       0.0,   // sin wave locked to beat, range -1..1 (zero-centered bipolar)
  trigger_onset:  0.0,   // 1.0 on beat onset frame, decays to 0 over ~100ms
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

// Peak follower state for amplitude normalization
let peakAmp  = 0.01;
let peakBass = 0.01;
let peakMid  = 0.01;
let peakHigh = 0.01;
const PEAK_DECAY = 0.999;

function updateAmplitudes() {
  const rawAmp  = calcRms(broadbandAnalyser, broadBuf);
  const rawBass = calcRms(bassAnalyser,      bassBuf);
  const rawMid  = calcRms(midAnalyser,       midBuf);
  const rawHigh = calcRms(highAnalyser,      highBuf);

  peakAmp  = Math.max(peakAmp  * PEAK_DECAY, rawAmp, 0.001);
  peakBass = Math.max(peakBass * PEAK_DECAY, rawBass, 0.001);
  peakMid  = Math.max(peakMid  * PEAK_DECAY, rawMid, 0.001);
  peakHigh = Math.max(peakHigh * PEAK_DECAY, rawHigh, 0.001);

  cvState.audio_amp  = Math.min(rawAmp  / peakAmp,  1.0);
  cvState.audio_bass = Math.min(rawBass / peakBass, 1.0);
  cvState.audio_mid  = Math.min(rawMid  / peakMid,  1.0);
  cvState.audio_high = Math.min(rawHigh / peakHigh, 1.0);
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
      cvState.trigger_onset = 1.0;

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

  cvState.beatPhase = totalBeats % 1.0;
  cvState.beatSine  = Math.sin(totalBeats * 2 * Math.PI);

  // Decay onset trigger (~100ms decay)
  cvState.trigger_onset *= 0.85;
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
    broadbandAnalyser.fftSize = 2048;
    broadbandAnalyser.smoothingTimeConstant = 0.8;

    // Bass Filter & Analyser
    const bassFilter = audioCtx.createBiquadFilter();
    bassFilter.type = 'lowpass';
    bassFilter.frequency.value = 180;
    bassFilter.Q.value = 0.7;

    bassAnalyser = audioCtx.createAnalyser();
    bassAnalyser.fftSize = 256;
    bassAnalyser.smoothingTimeConstant = 0.85;

    // Mid Filter & Analyser
    const midFilter = audioCtx.createBiquadFilter();
    midFilter.type = 'bandpass';
    midFilter.frequency.value = 1000;
    midFilter.Q.value = 1.0;

    midAnalyser = audioCtx.createAnalyser();
    midAnalyser.fftSize = 256;
    midAnalyser.smoothingTimeConstant = 0.85;

    // High Filter & Analyser
    const highFilter = audioCtx.createBiquadFilter();
    highFilter.type = 'highpass';
    highFilter.frequency.value = 5000;
    highFilter.Q.value = 0.7;

    highAnalyser = audioCtx.createAnalyser();
    highAnalyser.fftSize = 256;
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
  cvState.trigger_onset = 0.0;
}

// volume: 0.0 (muted) to 1.0 (full) — uses squared curve for perceptual linearity
export function setVolume(volume) {
  currentVolumeRatio = volume;
  if (gainNode && audioCtx) {
    gainNode.gain.setTargetAtTime(volume * volume, audioCtx.currentTime, 0.05);
  }
}
