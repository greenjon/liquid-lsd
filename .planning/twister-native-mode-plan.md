# Twister native mode (XT firmware): dynamic ring type, full RGB

Written 2026-10-08. Source: https://github.com/Trinitou/MidiFighterTwisterXT and its firmware repo
https://github.com/Trinitou/Midi_Fighter_Twister_Open_Source (branch `nativeMode`, `doc/NativeMode.md`, `src/native_mode.c`).
Read `.planning/midi-controller-handoff.md` and `.planning/twister-led-midi-notes.md` first.

## Goal

Ring style follows the bound parameter: unipolar = bar, bipolar (-1..1) = bar with centre detent, endless/discrete = dot.
It must update live as FX, generators and pages change. Bonus: true RGB LED colours (no hue-wheel quantising).

## Protocol facts (from the firmware source, not yet verified on our hardware)

- Needs the custom firmware (build 2022_08_10/11). Native mode is OFF until the host enters it; all native state is lost on USB disconnect.
- SysEx header `F0 00 01 79 05`, then:
  - `00 <0|1>`: native mode off/on. Entering resets every knob's indicator config and colour to default (dot, no detent, black).
  - `01 00 <knob> <type> <detent> <detentColor>`: indicator config. type 0 dot, 1 bar, 2 blended bar, 3 blended dot; detent 0/1; detent colour 0..127 (red..blue). Several configs may be concatenated in one message.
  - `01 01 <knob> <r> <g> <b>`: switch LED colour, 7 bits per channel.
  - closed with `F7`. Configs are ignored unless native mode is active.
- CC, no banks, knobs numbered 0..15 row by row:
  - ring position: ch1 (0-based 0), CC = knob, value 0..127 (host to device).
  - knob turn: ch1, CC = knob, value 64 + delta (same as RELATIVE_BINARY_OFFSET today).
  - knob press: ch2 (0-based 1), CC = knob, 127/0.
  - side buttons: ch3 (0-based 2), CC 0..5 = left top/middle/bottom, right top/middle/bottom, 127/0. All six exist (same layout as the `midi-fighter-twister-6btn` experiment).
- Not available in native mode: ring brightness/animation (ch6; the firmware renders at 0x7F), hardware banks and the bank buttons.

## Design

One new built-in profile, `midi-fighter-twister-xt` (name "Midi Fighter Twister (XT firmware, native mode)"), alongside the untouched stock profile.
Same device name, so profile choice cannot be automatic: the user picks it (step 5).

### Profile model additions (ControllerProfile.kt; schema version stays 1, fields are optional)

- `OutputConfig.native: NativeModeDef?`. Null = today's CC-only behaviour. Holds:
  - `enterSysex` / `leaveSysex` as hex strings, so no magic bytes live in Kotlin and another controller could reuse it;
  - `indicatorStyles: Map<MeterType, IndicatorStyle(type, detent, detentColor)>` with the defaults below, tunable in the JSON;
  - `colorSysex` template (`F0 00 01 79 05 01 01 {knob} {r} {g} {b} F7`) and `indicatorSysex` template.
  Keep it as a small typed model plus builder functions in one new file `control/NativeMode.kt`, unit-tested byte for byte.
- `BankConfig.virtual: Boolean` (default false). With no hardware bank, the "banks" are the app's own pages. The profile has `banks.count` = number of pages, no `switch`, and inputs have `bankStride` 0.

Default styles (all unverified, the table is data so hardware testing only edits JSON):

| MeterType | type | detent |
|---|---|---|
| MONOPOLAR | bar | none |
| BIPOLAR | bar | yes, colour 127 |
| ENDLESS | dot | none |
| DISCRETE | dot | none |

### Code changes

1. `MidiSink.sendSysex(bytes: ByteArray)` (default no-op for tests). `MidiOutputPorts`: a small FIFO for SysEx next to `CcQueue`, drained before CCs each iteration, so "enter native mode" always precedes the first ring/colour and a sysex is never coalesced away. Send with `javax.sound.midi.SysexMessage`. `TracingSink` logs sysex as hex.
2. `ControllerFeedback` native branch (selected when `output.native != null`):
   - targets: one per knob, ring on the knob's own CC with channel 0, no per-bank copies, no ch6 brightness;
   - per knob remember `lastStyle` (a small int key of type/detent/colour) and `lastRgb`; `forget()` clears them;
   - on each `update`, a changed `light.meterType` sends the indicator sysex (before the ring CC, so the new position is drawn in the new style); a changed colour sends the colour sysex with `r,g,b` x 127 from `KnobLight` (off = 0,0,0);
   - ring value unchanged: `(value * 127).roundToInt()`. For BIPOLAR the knob value is already 0..1 with 0.5 at the centre = 64, which is where the detent sits;
   - `KnobLight.ringBrightness` cannot be honoured. Send knobs (Library, knobs 9-13) keep the full ring; decide on hardware whether dot-at-127 reads better than a full bar there (see open question 3).
3. Lifecycle (`ControllerManager.scanDevices`, `ControllerFeedback.close`): on open, send enter, then `resync()` (indicators for all 16, colours, rings). On close or `reset()` send leave. Add a JVM shutdown hook so quitting the app returns the device to stock behaviour. A crash leaves the device in native mode until it is replugged; document that.
4. Virtual banks in `ControllerRuntime`:
   - with `banks.virtual`, `stepBank` must find its position from the surface's current page (index in `banks.pages`) rather than `activeBank`, which stays null because no input is bank-aware;
   - `enterBank` is never reached (no bank switch inputs);
   - `ControllerFeedback.update` with `activeBank == null` already writes all targets, which is right.
   - `syncActiveBank` returns early without `banks.switch`: no change.
5. Profile selection. Both Twister profiles match the same device name. Add a per-device "use profile" preference (stored with the other controller prefs, not in the profile) read by `ControllerProfileStore.matchFor(deviceName, preferredId)`; fall back to today's first match. UI: a selector in the existing profile section of `MidiPreferencesPanel` (slices 1-3 of phase 5 exist; check what it already has before adding anything). Changing it calls `ControllerManager.reset()`.
6. `src/main/resources/controllers/midi-fighter-twister-xt.json` (+ `BUILT_IN_NAMES`). Derived from the 6btn profile: knob ch0 CC0 x16 (press ch1), side buttons ch2 CC0..5 mapped LT=nav, LM=chain link, LB=shift (modifier), RT=bank step, RM and RB=nav, same bindings as the stock profile. Pages `ab, bgpv, mixer, master` with `wrapPages`, `virtual: true`. No `utilityFile` (native mode ignores the Utility settings; tell the user which Utility steps still matter: none).

## Order of work

0. **Hardware probe first (no app code), after the user flashes.** With `amidi` (or a throwaway script kept outside the repo): enter native mode, set knob 0 to bar+detent and sweep CC 0..127 on ch1, then dot, blended, colours via sysex, confirm turn/press/side messages and their channels, send 16 configs in one sysex, leave native mode and confirm stock behaviour returns. Record results in this file; they decide the style table and the open questions.
1. `NativeMode.kt` builders + tests; `MidiSink.sendSysex` + `MidiOutputPorts` FIFO.
2. Profile model fields + compile validation (e.g. `virtual` needs `pages`, native needs `output.knobs`), tests.
3. `ControllerFeedback` native branch, tests with a recording sink: style resent only on change, enter precedes ring/colour, bank-less targets, forget on resync.
4. Lifecycle (enter, leave, shutdown hook) and runtime virtual-bank stepping, tests.
5. Built-in XT profile, preference + selector UI.
6. Hardware check with the app running; tune the style table.
7. Definition of done: both release-notes files, `DECISIONS.md` entry (newest first, in place), user guide + `docs/developer/unified_control_mapping.md` controller section (flashing the XT firmware, how to go back, the licence note below), tooltip on the selector, `./gradlew generateDocs --offline -q`, `./gradlew test --offline -q`.

## Open questions (answer in step 0)

1. What does `bar + detent` draw at positions off-centre: a bar growing from the centre (what we want) or from the left with a centre marker? If the latter, try `blended dot + detent` or dot + detent for BIPOLAR.
2. Is the detent colour visible enough on rings that are also showing a bar?
3. Send knobs: full bar at full brightness vs a dot at 127. Without ch6 there is no half-brightness.
4. Do rapid colour sysex writes need pacing beyond `minIntervalMs` (the stock device dropped bursts)? Is a 16-config single message accepted?
5. Does the older note that "XT firmware showed banks 2-4 stale" (handoff, feedback history 2) apply? That was almost certainly outside native mode; there are no banks in native mode, so it should not matter, but confirm which build was flashed then.

## Risks

- Firmware licence (DJTT, in the file header): modified source and builds may not be redistributed. Link to the fork; never bundle the hex.
- Native mode is host-controlled and non-persistent, so a missing "leave" only matters until replug. The stock profile and stock Utility setup stay as they are, and the user can switch back by selecting the stock profile (and flashing back if they want the stock firmware).
- v1.0 is in feature freeze: this is optional, off by default and touches the stock path only through the shared `MidiSink` and `ControllerFeedback`. Keep the stock tests green and the branch separate until the hardware check passes.

## Step 0 hardware probe results (2026-10-08, XT firmware flashed, amidi/aseqdump)

- Enter (`00 01`) works. Indicator config `01 00 <knob> <type> <detent> <color>` and colour `01 01 <knob> r g b` work, RGB order is r,g,b (red/green/blue confirmed; full white looks pink).
- **Concatenating several configs in one SysEx is unreliable**: a second config appended to the first made knob 1 show a detent and a wrong (blue) LED. Send ONE config per SysEx message (answers open question 4 for the single-message case: not accepted).
- type 0 dot and type 1 bar confirmed. Types 2/3 (blended) look identical to 1/0 on the ring: leave them out of the style table.
- Bar + detent shows a centre detent (user confirmed). Not yet confirmed: whether the bar grows from the centre or from the left.
- Inputs match the doc (0-based channels): turn ch0 CC=knob 64+-delta; press ch1 CC=knob 127/0; side buttons ch2 CC 0..5 127/0, all six present.
- Leave (`00 00`) sent at end of probe; stock behaviour return still to be confirmed by the user.
- Plan impact: `NativeMode.kt` must emit one SysEx per knob config/colour; the SysEx FIFO must not merge messages.
