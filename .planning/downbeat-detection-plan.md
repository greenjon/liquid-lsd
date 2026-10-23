# Downbeat (bar start) detection (plan)

Started 2026-10-23. Status: steps 1-4 BUILT 2026-10-23 (desktop, tests pass, unseen in app); 5 skipped; 6 waits for real-music tuning. Target: v1.1 (v1.0 feature freeze). Desktop first, web port after (see `web-tv-overnight-batch.md`).

## Problem

The tracker finds tempo and beat phase, but not which beat is beat 1. A "bar" is `floor(totalBeats) mod 4` (`ClockKnob.BEATS_PER_BAR`, `GenUnit.BEAT` with `subdivision = 4`, web `autopilot.js` 4 beats per bar), and beat 0 is wherever the tracker started or the last tap/resync landed. Bar-length LFOs, sequencers, the clock knob's downbeat flash and the web autopilot's bar-line switching are therefore only as aligned as that accident.

## What exists (verified by survey 2026-10-23)

- `audio/BeatTrackerEngine.kt`: complex spectral-difference ODF (512 FFT), multi-band autocorrelation tempo, causal DP beat tracker, NCO phase (`getPhase`). Multi-band path: `processMultiBand`, `processBlock`, `processOdfSample`. Write-up: `docs/developer/beat_sync.md`.
- `audio/AudioEngine.kt`: `registerTap`, `resyncDownbeat` set alignment by hand. `audio/TapTempoController.kt` rounds `totalBeats` to a whole beat and calls `CVRegistry.alignBeatPhase`.
- `cv/CVRegistry.kt`: the single clock. `updateBeatAnchor`, `alignBeatPhase`, forward-only `getSynchronizedTotalBeats` (absorbs backward corrections under 0.25 beat).
- `cv/BeatClock.kt`: `beatPhase` and `BeatSine` outputs. `link/AbletonLinkEngine.kt`: `quantum = 4.0`, `getPhaseAtTime(timeUs, quantum)` already gives a bar phase when Link drives the clock.
- No confidence output on desktop. No MIDI clock input. Web has split-half confidence in `beattrack.js`.

## Design

**Rule: the offset is state, the detector only proposes.** Any consumer reads one bar phase; where the offset comes from (manual, tap, Link, detector) is a separate question.

### Step 1: bar plumbing (small, no detection)
- `CVRegistry`: add `downbeatOffsetBeats` (integer 0..beatsPerBar-1) and `beatsPerBar` (default 4; 3, 4, 5, 6, 7 allowed).
- New CV source `barPhase = ((beats - offset) / beatsPerBar) mod 1` and `barIndex`, next to `BeatClock`. Register it with the evaluators and `web/evaluator.js` golden vectors (regenerate with `UPDATE_WEB_VECTORS=1`).
- `registerTap` and `resyncDownbeat` set the offset (a tap means "this is beat 1"), instead of only rounding beats.
- Offset changes apply on a bar boundary where possible so LFOs and sequencers don't jump. A manual tap applies immediately.
- When Link is the clock, derive the offset from `getPhaseAtTime(quantum = beatsPerBar)`.
- `ClockKnob` and `PerformanceClockControls` read `beatsPerBar` and the offset instead of the constant.

### Step 2: per-beat features
- At each tracked beat, record band energies (bass weighted most, from the multi-band path) and onset strength into a ring of the last 8 bars.

### Step 3: downbeat scoring
- For each of N candidate phases, accumulate "downbeat-ness" (low-band energy, ODF strength, low-band novelty vs the previous beat) over the last 4-8 bars.
- Propose a phase only when the best beats the runner-up by a margin; hold it with hysteresis. Never re-pick every bar.
- Expose `downbeatConfidence` (0..1), like the web tracker's split-half confidence.
- Expected: roughly 70-85% on four-on-the-floor electronic music, worse on sparse or non-electronic material.

### Step 4: control and override
- Preferences: Downbeat detection Off / Auto, beats per bar (default 4). Default Off until tuned, so existing sets behave as now.
- A tap or resync is authoritative: it locks the detector's proposals out for a while (e.g. 16 bars) and re-seeds the scoring window to the tapped phase.
- Low confidence = hold the last offset. Show confidence on the clock row (dim downbeat dot when unsure).
- On a tempo-lock loss or large tempo change, keep the offset but drop confidence and rebuild the window.

### Step 5: meter detection (optional)
- Compare 3-beat and 4-beat periodicity in the per-beat feature autocorrelation. Default 4, suggestion only, never auto-switch.

### Step 6: web port
- Port steps 1-4 to `beattrack.js` / `beatclock.js`; `autopilot.js` uses the real downbeat for look lengths and bar-line crossfades. Desktop `BeatFlywheel` anchors should carry the offset.

### Out of scope
- Chroma/harmonic-change features (large effort, least gain). Phrase (8/16/32-bar) detection. Revisit after measuring step 3.

## Testing
- A small set of labelled clips (4-6, a few genres, downbeat times annotated) in `src/test/resources`, plus a test that reports downbeat accuracy over each clip's last half, and fails below a floor chosen after the first run. Synthetic click-track cases (accent on 1) for the unit tests.
- Unit tests: offset applied on a bar boundary without a phase jump; tap overrides and locks out the detector; Link quantum path; `barPhase` golden vectors Kotlin vs web.

## Risks
- Breakdowns, sparse intros and tempo changes slip a beat. Mitigations: margin, hysteresis, tap override, confidence hold.
- Anchor jumps: keep forward-only `getSynchronizedTotalBeats`; offsets change only on bar lines.
- Tuning needs real tracks, not only synthetic ones.

## Order
1 (cheap, useful alone, gives manual and Link bars a home) -> 2, 3, 4 as one piece -> 6. 5 optional.

## Done means
Code + both release-notes files + user guide and developer docs (`beat_sync.md`) + tooltips; `.planning/app-checklist.md` entry for what is unseen in the app.

## As built (2026-10-23) -- differences from the plan
- **No `downbeatOffset`.** Bars are kept by renumbering the beat count by whole beats (`totalBeats += shift`), so every consumer (LFOs, sequencers, clock knob, dots, web serializer) sees the same bar line with nothing to forget. Costs: bar-length LFOs jump once when the bar line moves.
- **No `barPhase` CV source.** A LFO/sequence with a cycle of `beatsPerBar` beats already is one, and a new source would touch ~8 lists plus the web evaluator. `CVRegistry.beatInBar()` / `barPhase()` serve the UI.
- **Tap semantics:** only the first tap of a run claims beat 1 (every tap would move it); later taps keep the bar line.
- **Step 5 (meter detection) skipped:** 3-vs-4 from beat-level accent features is unreliable without harmonic features and there are no waltz clips to validate against; beats-per-bar is a setting.
- Code: `audio/DownbeatDetector.kt`, hooks in `audio/AudioEngine.kt`, `cv/CVRegistry.kt`; UI in `TempoSyncPanel` (Bar & Downbeat), `PerformanceClockControls`, `ClockKnobFeed`. Docs: `docs/developer/beat_sync.md` section 6.
- Next: collect 4-6 labelled real clips and measure accuracy before step 6.
