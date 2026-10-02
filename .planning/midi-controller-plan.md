# MIDI controller support: command registry + controller profiles

Status (updated: shipped profile now relative binary-offset after user switched the device): Phases 1-2 implemented and verified on the real Twister 2026-10-02 (registry, device tagging, profiles; control pages, knob gestures, banks->tabs, modifiers, acceleration). Phases 3-5 open. Phase 2 notes: curves deferred (relative deltas have no useful curve; macros already have MacroCurve); only `shift` (CC 10, left-bottom) is bound among side buttons; side.1-3 are free for phase 3. First hardware target: Midi Fighter Twister (MFT).
Builds on `docs/developer/unified_control_mapping.md` and ROADMAP "Unified Control".

## Decisions (from brainstorm)

- MFT should drive almost everything in Perform mode: deck SRC knobs, FX chain knobs,
  focused FX slot knobs, and the SRC picker (browse/try generators).
- Parameters/Properties (classic view) are out of scope. Perform mode is the surface.
- Layers: hardware banks only for v1.0. No software shift layer yet.
- LED/ring feedback is IN scope for v1.0, but built after input works end to end.
- Build order: command registry + profile format first, MFT is the proof.
- Declarative JSON profiles, no scripting in v1.0 (script escape hatch is a v1.1 option).

## Current state (verified by exploration)

- Input: `midi/MidiEngine.kt` (javax.sound.midi), all devices share one receiver, no device id on events,
  only ShortMessage (CC/Note/PitchBend). Dispatch on render thread: `MidiMappingManager.processGlobalMidiEvents`.
- Bindings: `MidiControlMapping` per path string, saved in `library/midi/<name>.json`.
  Soft takeover and relative decoding (binary offset, signed bit, two's complement) exist.
- Missing: MIDI output, device identity, layers/banks, command layer, multi-source bindings,
  per-input response curves, acceleration.
- No UI command layer. Navigation state is mutated directly (`UITheme.performanceMatrixTab`,
  `LibraryPanel.*`, `FxMacroSync.focusSlot/stepParamPage`, `PerformanceUiContext.navigateMacroPanelTo`).
- Closest feedback hook: `MacroFeedbackListener` in `macro/MacroOscBridge.kt`.

## Architecture

### 1. Command registry (`control/CommandRegistry.kt`)

Stable string ids. Kinds: Trigger, Toggle, Scalar (0..1 absolute), Relative (signed delta), Choice/Step.
Executed on the render thread from the existing dispatch, so no new threading.

Id families (initial):
- `knob.<n>` : "the Nth knob of the current control page" (see Pages)
- `knob.<n>.press`, `knob.<n>.reset`
- `nav.page.next/prev`, `nav.page.set.<name>` (deck A src, deck A fx chain, deck A fx slot focus, deck B ..., master mix, master fx, global)
- `nav.view.perform/edit/library`, `nav.tab.decks/master`
- `browse.next/prev/step`, `browse.accept`, `browse.back` (SRC picker)
- `fx.focus.slot.<n>`, `fx.focus.exit`, `fx.param.page.next/prev`
- `mixer.queue_next/prev`, `clock.tap_tempo`, `mixer.auto_crossfade_trigger`, `mixer.crossfade_snap_a/b` etc. (the former `Global/*` actions, ids per `docs/developer/unified_control_mapping.md`; `Global/*` paths are aliases)

Existing `Global/*` hard-coded actions become registry entries. Old profile paths keep loading
(migration shim maps `Global/x` and `Macro/bank/knob_N` to command ids or keeps legacy path bindings).

### 2. Control pages (the key idea)

Perform mode already resolves what each of the 16 knobs means (PerfKnobSpec / KnobSpec resolver /
PerfRowGeometry). A **control page** = the current 16 `KnobSpec`s (label, value, color, curve, kind)
for whatever the user is focused on. Hardware knobs bind to `knob.<n>` and never to a fixed target, so
switching page retargets all 16 knobs with no mapping changes. The page is chosen by:
- explicit `nav.page.*` commands (side buttons / bank buttons), or
- follow-UI-focus (default): the page tracks Perform-row focus changes.

TO VERIFY in phase 1: that KnobSpec resolution is usable headless (without drawing) and exposes
value get/set + display label. If not, extract a `ControlPage` interface from it.

### 3. Controller profile (`library/controllers/*.json`, built-ins in resources `controllers/`)

```
{ "id": "midi-fighter-twister", "name": "...", "match": ["Midi Fighter Twister"],
  "inputs": [ {"id":"enc1","type":"encoder","ch":1,"cc":0,"mode":"relative-binary-offset",
               "resolution":7,"curve":"linear","accel":{...},"press":{"ch":2,"cc":0}} ...],
  "bindings": { "enc1":"knob.1", "enc1.press":"knob.1.press", "side.left1":"nav.page.prev", ... },
  "layers": { "bank1": {...overrides...}, ... },     // hardware banks, v1.0
  "output": { "ring": {...}, "color": {...}, "rules": [...] } }
```
- Built-in read-only; user copies to edit. User override by same id wins.
- User learn-based bindings stay as a per-user layer on top and bind to command ids.
- Device identity: tag every MidiEvent with the source device; match profile by port name; allow manual
  assignment in settings when two identical devices exist.

### 4. Input processing pipeline

raw msg -> device tag -> profile input lookup -> decode (relative modes, 14-bit pairs where declared)
-> acceleration -> response curve -> layer resolve -> command execute.
Curves: linear, log, exp, s-curve, custom points (reuse `MacroCurve`). Acceleration: velocity multiplier
on relative deltas, per input, configurable.

### 5. MIDI output + feedback (phase 4)

- `MidiOutputManager`: open matching output ports, send on a dedicated writer thread via queue.
- Feedback engine subscribes to page value changes and page/focus changes, diffs against last sent,
  and sends only changes (avoid flooding). Full resync on page change, profile load, device (re)connect.
- MFT: ring value on ch1, RGB color on ch2, brightness/animation on ch3. Colors come from `TangoPalette`.
  Confirm exact CC semantics against the MFT manual during implementation.

## Phases

1. Registry + profile loader + device tagging. Port `Global/*` actions. Tests: registry execution,
   profile parse/validation, legacy binding migration. No behavior change for existing users.
2. Control pages + MFT input profile: 16 relative encoders, curves, acceleration, press actions,
   bank/side buttons for page nav. Manual test on real hardware.
3. Navigation + browse commands: view/tab/page, FX focus, SRC picker browse/accept/back.
4. MIDI output + ring/RGB feedback for MFT.
5. Profile UX: pick profile/device in settings, copy built-in, edit, learn-into-profile; docs.

Each phase ends with: tests, tooltips, both release-notes files, user_guide + developer docs, DECISIONS.md.

## MFT facts (reported by user from the device, 2026-10-01)

- Encoder turn: CC X, value Y. X = knob 0..15 + 16 * bank, so 0..63 across 4 banks. Y = 0..127.
- 6 side buttons, 3 per side. The two middle ones are bank prev/next. The other 4 send 0/127 and their
  CC ids also shift with the bank (4 ids per bank).
- Consequence: the active bank is inferable from the CC number of any knob/button event, so the app can
  treat the device as a flat 64-encoder input space. Whether the bank-change buttons themselves send a
  message is unconfirmed (check with `aseqdump`).
- The app only learns the bank after the first event from it, so on connect the active bank is unknown.
  Feedback resync must write all 64 rings, not just the visible bank.
- Confirmed on hardware (user, 2026-10-01; channels are 0-based as the user reported them, so
  "channel 0" = MIDI ch1):
  - Knob switch: channel 1, CC 0..63 (= knob number across banks), value 127 down / 0 up.
    Turning while pressed sends the same values as turning unpressed, so the app must track the
    pressed state itself (press+turn = fine is app-side).
  - Side buttons: CC 8, 10, 11, 13 in bank 1, +4 per bank (bank 2: 12, 14, 15, 17; bank 3: 16, 18, 19, 21;
    bank 4: 20, 22, 23, 25). Value 127 down / 0 up. Always channel 3 (confirmed by user).
  - Bank switch buttons: channel 3, CC = bank index 0..3. Leaving a bank sends (CC = old bank, value 0),
    then entering sends (CC = new bank, value 127). Example 1->2: (3,0,0) then (3,1,127). So the active
    bank is announced explicitly; use the value-127 message as "bank is now N".
  - Unknown: whether the device accepts a bank change from the app (try sending ch3 CC n value 127 later,
    in phase 4 when output exists).

## Design updates from these facts

- Hardware bank = page selector. Bank b is bound to a control page (profile-configurable; defaults e.g.
  bank 1 = follow focus). The UI follows the bank when a bank event is seen.
- Modifiers: profile inputs can be declared `"type":"modifier"` (held state). Bindings are keyed by
  modifier set (`shift+enc3.press`). The 4 non-bank side buttons per bank give shift and nav buttons.
  This is a minimal software layer, a deliberate change to the earlier "no software layers" decision.
- Knob gestures, proposed default (UX still open): turn = normal, press+turn = fine, tap = the page's
  primary action (FX slot: bypass/mute, param: reset), shift+tap = secondary, shift+turn = alternate
  target. App tracks knob-pressed state to detect press+turn.
- Absolute mode is viable once feedback exists: the app writes the value back to the ring and the
  encoder position stays in sync, so no takeover problem. Relative + acceleration is better for fine
  control and for pre-feedback phases. The profile supports both.

## Open questions

- Does the user's relative-mode change (via Midi Fighter Utility under WINE/VM) work? Until then phase 2
  can be tested in absolute mode with soft takeover.
- Can the app set the bank via MIDI input? (Bank-change buttons do send messages, see facts.) Try in phase 4.
- Final knob gesture map (see above) and which of the 4 side buttons are shift vs nav.
- Whether follow-UI-focus or explicit page selection is the default on bank 1.
