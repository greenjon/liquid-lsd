// energy.js
// Song sections from the band levels, for the autopilot: a *breakdown* is the low end falling well below
// where it has been for the last ~20 s; a *drop* is it coming back after a breakdown of at least 4 s.
// The long-term level freezes during a breakdown (and while one is being confirmed), so the drop is measured against the music before it.
// The same long-term level is what auto gain (AudioAgc in dsp.js) normalises against, so a breakdown also
// reads quiet. `energy` and `dropLag` feed the section modulators (audio_energy, and audio_drop through
// DropEnvelope below). A port of the desktop's audio/SectionWatch.kt and cv/SectionDrop.kt: keep them identical.
// Pure, so it is unit-tested in node (web/tools/energy.test.mjs).

const SHORT_TAU = 1.5;     // s, the "now" level
const LONG_TAU = 20;       // s, the reference level
const WARMUP = 20;         // s of audio before any section is reported
const SILENCE = 0.03;      // a reference level below this is silence, not a breakdown
const BREAK_RATIO = 0.45;  // now below this fraction of the reference...
const BREAK_HOLD = 3;      // ...for this long (s) is a breakdown
const DROP_RATIO = 0.85;   // back above this fraction of the pre-breakdown level is a drop...
const DROP_MIN = 4;        // ...if the breakdown lasted at least this long (s; short breakdowns are common on the radio)
const BREAK_MAX = 60;      // a "breakdown" this long is just a quieter song: adopt it as the new normal
const LOUDER_RATIO = 1.6;  // now above this multiple of the reference...
const LOUDER_HOLD = 4;     // ...for this long (s) is a louder song: adopt it at once
const FAST_TAU = 0.1;      // s, the level that says when a drop really came back...
const FAST_GAP = 0.7;      // ...unless it falls under the drop ratio for longer than this (s)
const DROP_CONFIRM = 0.5;  // s the fast level must stay back to call a drop (or the "now" level gets there)
const MAX_DROP_LAG = 4;    // s, the most a drop is dated back

export class EnergyWatch {
  constructor() {
    this.short = 0;
    this.long = 0;
    this.time = 0;
    this.below = 0;        // s the level has been under the breakdown ratio
    this.section = 'normal';
    this.sectionTime = 0;  // s in the current section
    this.drops = 0;        // count of drops so far
    this.louder = 0;       // s the level has been over the louder ratio
    this.rebased = false;  // true on the frame the reference jumped to the current level (a new normal)
    this.fast = 0;
    this.backAt = -1;      // when the fast level climbed back over the drop ratio; -1 while it is under
    this.fastBelow = 0;
    this.dropLag = 0;      // s between the low end coming back and the latest drop being called
  }

  /**
   * How loud the music is now against the reference, as log2(short / long) clamped to -1..1: 0 is the usual
   * level, -1 half of it or less (a breakdown), +1 twice it. 0 during the warm-up and in silence.
   */
  get energy() {
    if (this.time < WARMUP || this.long < SILENCE) return 0;
    return Math.max(-1, Math.min(1, Math.log2(Math.max(this.short, 1e-4) / this.long)));
  }

  /** `long` is not following the music: a breakdown, or the level has just fallen under the breakdown ratio. */
  get holding() { return this.section === 'breakdown' || this.below > 0; }

  reset() {
    this.short = this.long = this.time = this.below = this.louder = this.sectionTime = 0;
    this.section = 'normal';
    this.rebased = false;
    this.fast = 0;
    this.backAt = -1;
    this.fastBelow = 0;
  }

  /** One frame of band levels (0..1) over `dt` seconds. Returns 'breakdown', 'drop' or null. */
  update(bass, amp, dt) {
    const level = 0.7 * bass + 0.3 * amp;
    this.time += dt;
    this.sectionTime += dt;
    this.rebased = false;
    this.short += (level - this.short) * Math.min(1, dt / SHORT_TAU);
    this.fast += (level - this.fast) * Math.min(1, dt / FAST_TAU);
    // A running mean until LONG_TAU has passed, so the reference is right from the first seconds. It
    // stops as soon as the level dips under the breakdown ratio, so a breakdown does not drag it down
    // while it is being confirmed.
    if (!this.holding) this.long += (level - this.long) * Math.min(1, dt / Math.min(LONG_TAU, this.time));
    if (this.time < WARMUP || this.long < SILENCE) { this.below = this.louder = 0; return null; }

    if (this.section === 'normal') {
      this.below = this.short < BREAK_RATIO * this.long ? this.below + dt : 0;
      if (this.below >= BREAK_HOLD) return this.#enter('breakdown');
      this.louder = this.short > LOUDER_RATIO * this.long ? this.louder + dt : 0;
      if (this.louder >= LOUDER_HOLD) this.#rebase();
      return null;
    }
    // breakdown. The fast level dips between kicks, so it counts as back until it has been under for FAST_GAP.
    if (this.fast > DROP_RATIO * this.long) {
      this.fastBelow = 0;
      if (this.backAt < 0) this.backAt = this.time;
    } else {
      this.fastBelow += dt;
      if (this.fastBelow > FAST_GAP) this.backAt = -1;
    }
    // Back for DROP_CONFIRM and above right now: two loud hits in a row, not one hit and its tail.
    const backFor = this.backAt >= 0 && this.fastBelow === 0 ? this.time - this.backAt : 0;
    if (this.short > DROP_RATIO * this.long || backFor >= DROP_CONFIRM) {
      if (this.sectionTime >= DROP_MIN) {
        this.dropLag = this.backAt >= 0 ? Math.min(this.time - this.backAt, MAX_DROP_LAG) : 0;
        this.drops++;
        return this.#enter('normal', 'drop');
      }
      this.#enter('normal'); // only a short dip
      return null;
    }
    if (this.sectionTime > BREAK_MAX) { this.#rebase(); this.#enter('normal'); }
    return null;
  }

  #rebase() {
    this.long = this.short;
    this.louder = 0;
    this.rebased = true;
  }

  #enter(section, event = section) {
    this.section = section;
    this.sectionTime = 0;
    this.below = 0;
    this.backAt = -1;
    this.fastBelow = 0;
    return event;
  }
}

export const DROP_BARS = 4;

/** The beat a drop called at `beats`, `lagSec` after it happened, is dated to (never in the future). */
export function dropBeat(beats, lagSec, bpm) {
  return Math.min(Math.round(beats - lagSec * bpm / 60), beats);
}

/** 1 at the drop, (1 - t)^2 down to 0 after `lengthBeats`. */
export function dropEnvelope(beatsSince, lengthBeats) {
  if (beatsSince < 0 || beatsSince >= lengthBeats) return 0;
  const left = 1 - beatsSince / lengthBeats;
  return left * left;
}

/**
 * audio_drop: 1 on a drop, falling to 0 over DROP_BARS bars, dated back to the beat the drop was on.
 * `report` takes the watch's drop count with the analysis-time beat; `value` reads at the heard beat, so
 * SYNC delays the hit with the rest of the picture.
 */
export class DropEnvelope {
  constructor() { this.seen = 0; this.anchor = NaN; }

  report(drops, lagSec, beats, bpm) {
    if (drops > this.seen) this.anchor = dropBeat(beats, lagSec, Math.max(bpm, 1));
    this.seen = drops; // a new watch counts from 0 again: follow it down without a hit
  }

  value(beats, beatsPerBar = 4) {
    return Number.isNaN(this.anchor) ? 0 : dropEnvelope(beats - this.anchor, DROP_BARS * beatsPerBar);
  }
}
