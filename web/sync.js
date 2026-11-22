// sync.js
// SYNC: the picture follows the sound the viewer hears, which can reach them later than the browser
// thinks (Bluetooth and other delayed speakers). `syncMs` (the MENU's SYNC) delays the beat clock and
// the audio signals the picture reads by that much; a negative value shows the beat early instead
// (audio signals cannot be read from the future, so they are not advanced).
import { cvState } from './dsp.js';
import { autopilotState } from './autopilot.js';
// Array.at / findLast need Safari 15.4; the TV supports Safari 15.0 (tools/compat.test.mjs).
const last = (a) => a[a.length - 1];

/** Beat position the viewer hears at `nowMs`: the live (desktop) or standalone clock, `syncMs` earlier. */
export function heardBeats(nowMs, syncMs) {
  if (autopilotState.isLiveBroadcast && autopilotState.clock.valid) {
    // The flywheel advances once, at the newest time read; the SYNC delay is counted back from there, so
    // readers at now and at now - SYNC in the same frame do not fight its forward-only smoothing.
    const clock = autopilotState.clock;
    return clock.beatsAt(nowMs) - (syncMs / 1000) * (clock.bpm / 60);
  }
  const bpm = cvState.bpm || 120;
  return cvState.totalBeats + ((nowMs - (cvState.beatsAtMs ?? nowMs)) - syncMs) / 1000 * bpm / 60;
}

/** Recent values of the audio signals, read back `delayMs` late. */
export class DelayLine {
  constructor(keepMs = 600) { this.keepMs = keepMs; this.items = []; }

  push(t, values) {
    this.items.push({ t, values });
    while (this.items.length > 2 && this.items[0].t < t - this.keepMs) this.items.shift();
  }

  /** The newest entry at least `delayMs` old (the oldest kept if none is), or null when empty. */
  read(t, delayMs) {
    if (!this.items.length) return null;
    if (delayMs <= 0) return last(this.items).values;
    const due = t - delayMs;
    for (let i = this.items.length - 1; i >= 0; i--) if (this.items[i].t <= due) return this.items[i].values;
    return this.items[0].values;
  }
}

/**
 * Taps against the beat grid (`beatsAt(t)` for each tap time): the median error in ms (+ = the taps,
 * so the heard sound, are late) and the spread of the middle taps (one outlier at each end ignored).
 */
export function tapOffset(tapTimes, beatsAt, bpm, earliestMs = -100) {
  // Errors fold into [earliestMs, beat - earliestMs): speakers are late far more often than early, so a
  // 250 ms Bluetooth delay reads as +250, not as -250 ms + one beat.
  const period = 60000 / bpm;
  const errs = tapTimes.map((t) => {
    const ms = beatsAt(t) * period;
    return ((((ms - earliestMs) % period) + period) % period) + earliestMs;
  }).sort((a, b) => a - b);
  const m = Math.floor(errs.length / 2);
  const ms = errs.length % 2 ? errs[m] : (errs[m - 1] + errs[m]) / 2;
  const spread = errs.length > 3 ? errs[errs.length - 2] - errs[1] : last(errs) - errs[0];
  return { ms, spread };
}
