// evaluator.js
// Standalone modulation & LFO evaluator for Liquid LSD Web Presets

import { cvState } from './dsp.js';

/**
 * Evaluates basic LFO waveforms at given normalized phase (0..1)
 */
export function evaluateWaveform(phase, waveform, slope = 0.5) {
  const p = ((phase % 1.0) + 1.0) % 1.0;

  switch (waveform) {
    case 'SINE':
    case 'sine':
      return Math.sin(p * 2.0 * Math.PI);

    case 'TRIANGLE':
    case 'triangle': {
      const s = Math.max(0.001, Math.min(0.999, slope));
      if (p < s) {
        return -1.0 + 2.0 * (p / s);
      } else {
        return 1.0 - 2.0 * ((p - s) / (1.0 - s));
      }
    }

    case 'SAW':
    case 'saw':
      return 2.0 * p - 1.0;

    case 'SQUARE':
    case 'square':
      return p < slope ? 1.0 : -1.0;

    case 'SAMPLE_AND_HOLD':
    case 'sample_and_hold': {
      // Deterministic pseudo-random stepped value based on integer cycle index
      const cycle = Math.floor(phase);
      const rand = Math.sin(cycle * 12.9898 + 78.233) * 43758.5453;
      return (rand - Math.floor(rand)) * 2.0 - 1.0;
    }

    case 'SMOOTH_RANDOM':
    case 'smooth_random': {
      const cycle = Math.floor(phase);
      const fract = p;
      const r0 = Math.sin(cycle * 12.9898 + 78.233) * 43758.5453;
      const v0 = (r0 - Math.floor(r0)) * 2.0 - 1.0;
      const r1 = Math.sin((cycle + 1) * 12.9898 + 78.233) * 43758.5453;
      const v1 = (r1 - Math.floor(r1)) * 2.0 - 1.0;
      // Smooth Hermite interpolation
      const t = fract * fract * (3.0 - 2.0 * fract);
      return v0 * (1.0 - t) + v1 * t;
    }

    default:
      return Math.sin(p * 2.0 * Math.PI);
  }
}

/**
 * Evaluates a single CvModulator for a given parameter
 */
export function evaluateModulator(mod, elapsedTime, totalBeats, frameCount) {
  if (mod.bypassed) return 0.0;

  let rawSignal = 0.0;

  // Direct Audio / CV Sources
  if (mod.sourceId === 'audio_amp' || mod.sourceId === 'amp') {
    rawSignal = cvState.audio_amp;
  } else if (mod.sourceId === 'audio_bass' || mod.sourceId === 'bass') {
    rawSignal = cvState.audio_bass;
  } else if (mod.sourceId === 'audio_mid' || mod.sourceId === 'mid') {
    rawSignal = cvState.audio_mid;
  } else if (mod.sourceId === 'audio_high' || mod.sourceId === 'high') {
    rawSignal = cvState.audio_high;
  } else if (mod.sourceId === 'trigger_onset' || mod.sourceId === 'onset') {
    rawSignal = cvState.trigger_onset;
  } else if (mod.sourceId === 'beatSine') {
    rawSignal = cvState.beatSine - 1.0;
  } else if (mod.sourceId === 'beatPhase') {
    rawSignal = cvState.beatPhase;
  } else {
    // LFO / Periodic Generator
    let phase = 0.0;
    const subdiv = Math.max(0.0001, mod.subdivision ?? 1.0);
    const unit = mod.genUnit ?? 'TIME';

    if (unit === 'BEAT' || unit === 'beat') {
      phase = (totalBeats / subdiv) + (mod.phaseOffset ?? 0.0);
    } else if (unit === 'FRAME' || unit === 'frame') {
      phase = (frameCount / subdiv) + (mod.phaseOffset ?? 0.0);
    } else {
      // TIME in seconds
      phase = (elapsedTime / subdiv) + (mod.phaseOffset ?? 0.0);
    }

    rawSignal = evaluateWaveform(phase, mod.waveform ?? 'SINE', mod.slope ?? 0.5);
  }

  return (rawSignal + (mod.dcOffset ?? 0.0)) * (mod.depth ?? 0.0);
}

/**
 * Evaluates a modulatable parameter into its final scalar value
 */
export function evaluateParameter(paramObj, elapsedTime, totalBeats, frameCount, fallback = 0.0) {
  if (typeof paramObj === 'number') return paramObj;
  if (!paramObj || typeof paramObj !== 'object') return fallback;

  const base = paramObj.baseValue ?? fallback;
  const modulators = paramObj.modulators;
  if (!Array.isArray(modulators) || modulators.length === 0) {
    return base;
  }

  let finalValue = base;

  for (const mod of modulators) {
    const modValue = evaluateModulator(mod, elapsedTime, totalBeats, frameCount);
    const op = mod.operator ?? 'ADD';

    switch (op) {
      case 'ADD':
      case 'add':
        finalValue += modValue;
        break;
      case 'SUB':
      case 'sub':
        finalValue -= modValue;
        break;
      case 'MUL':
      case 'mul':
        finalValue *= (1.0 + modValue);
        break;
      case 'MIN':
      case 'min':
        finalValue = Math.min(finalValue, modValue);
        break;
      case 'MAX':
      case 'max':
        finalValue = Math.max(finalValue, modValue);
        break;
      case 'OVERRIDE':
      case 'override':
        finalValue = modValue;
        break;
      default:
        finalValue += modValue;
        break;
    }
  }

  return finalValue;
}
