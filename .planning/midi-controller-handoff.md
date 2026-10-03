# MIDI controller support: handoff for a fresh session

Written 2026-10-02 at the end of a long session. Read this first, then `.planning/midi-controller-plan.md`
(original phased plan), the three `DECISIONS.md` entries at the top ("Command Registry and Declarative
Controller Profiles", "Twister Drives the Perform Grid...", "Controller Ring/LED Feedback...") and
`docs/developer/unified_control_mapping.md` section 6.

## Goal

Make a Midi Fighter Twister (MFT) drive the Perform view almost completely, with built-in controller profiles
and user-editable ones (Mixxx-style, declarative JSON, no scripting in v1.0). Other controllers will follow.
Feedback (rings/LEDs) is v1.0 scope.

## State

| Phase | What | Status |
|---|---|---|
| 1 | `CommandRegistry`, device tagging (`MidiEvent.deviceId`), controller profile model/loader/resolver, built-in Twister profile | done, committed (91e868e) |
| 2 | Perform-grid control: control pages, knob gestures, banks -> tabs, shift modifier, acceleration | done, committed, verified on hardware |
| 4 | Ring/LED feedback | done, committed (b64f1a2), working on all 4 banks with the stock 4-bank firmware (see "Feedback history") |
| 3 | Navigation/browse commands on the free side buttons | **not started** |
| 5 | Profile UI (pick profile, copy a built-in, edit, learn-into-profile) | not started |

Full suite: 778 tests, 0 failures (`./gradlew test --offline -q`).
Regenerate docs HTML with `./gradlew generateDocs --offline -q` after editing `docs/`.

## Architecture map (all under `src/main/kotlin/llm/slop/liquidlsd/`)

- `control/Command.kt`: `CommandRegistry`, `Command`, `CommandKind`, `CommandInput`, `CommandContext` (carries `knobSurface`).
  `control/GlobalCommands.kt`: the old hard-coded `Global/*` actions as commands (legacy paths are aliases).
- `control/ControllerProfile.kt`: profile JSON model (inputs, banks, bindings, `output`), `compile()` ->
  `CompiledController` (lookup table, `bindingFor(inputId, heldModifiers, bank)`, validation `problems`). Bindings support
  modifier prefixes (`shift+knob.3.press`), per-bank overrides, `*`/`{n}` wildcards.
- `control/ControllerProfileStore.kt`: built-ins from `src/main/resources/controllers/*.json` (listed in `BUILT_IN_NAMES`),
  user files in `library/controllers/*.json` (same `id` overrides). `matchFor(deviceName)` by case-insensitive substring.
- `control/ControllerRuntime.kt` (per device): tracks active bank, held modifiers, encoder state; decodes relative/absolute
  encoders, acceleration, routes inputs to bound commands; a button release goes to the command that got the press.
  `control/ControllerManager.kt`: runtimes per device + feedback lifecycle (`updateFeedback` once per frame).
- `control/KnobCommands.kt`: `knob.1..16`, `knob.<n>.press`, `knob.<n>.press_alt` + press/fine gesture state.
  `control/KnobSurface.kt` / `KnobLight.kt`: what the UI layer implements.
- `ui/PerfRows.kt`: row selection extracted from `PerformanceMatrixPanel` (single source of truth for "what rows are shown").
  `ui/PerformSurface.kt`: `PerformPages.resolve` (16-knob page from visible rows via `PerfKnobResolver`), `PerformSurface`
  (turn/primary/secondary/showPage/knobLights). Hooked up in `ui/UIManager.kt` next to `processGlobalMidiEvents`.
- Feedback: `control/ControllerFeedback.kt` (diffs; whole-bank rewrite on bank change), `control/CcQueue.kt` (one pending value per
  channel/CC), `midi/MidiOutputPorts.kt` (opens the output port by input device name, paced writer thread),
  `control/TracingSink.kt` (opt-in logging: profile `output.trace` or env `LSD_MIDI_TRACE=1`).
- `MidiMappingManager.processGlobalMidiEvents` offers each event to `controllers.handle(...)` first, unless the user has a
  learned mapping on that exact channel/CC (learned mappings stay on top).

## Twister facts (measured on the real device)

- Encoder turns: ch1 (0-based 0), CC = knob 0..15 + 16 * bank. Knob switches: ch2 (0-based 1), same CCs, 127 down / 0 up;
  holding a switch does not change the turn messages (the app tracks it). Side buttons: ch4 (0-based 3), CC 8, 10, 11, 13 +4
  per bank, 127/0. Bank buttons (middle ones): ch4 CC = bank 0..3; leaving sends (cc old, 0), entering sends (cc new, 127).
- Utility settings in use: stock **4-bank firmware**, encoder Action Type `Relative (ENC 3FH/41H)` on all encoders of all
  banks (clockwise 65, counter-clockwise 63), switches `CC Hold`, side corners `CC Hold`, middles Previous/Next Bank,
  Sensitivity `360`, Center Detent off, Super Knob off. Do not disable the bank buttons: the app learns the bank from them.
- Ring = send the encoder's own CC on ch1; LED colour = same CC number on ch2 with a hue-wheel value (default
  `HueWheel`: blue = 1, hue decreasing 2.88 degrees per value, 127 = white; DECKS colours were confirmed correct).
- Gesture map (profile + `PerformSurface`): turn = move; tap switch = FX slot bypass / reset to default (source knobs reset to
  0.5); shift + tap = focus slot / leave focus (knob 1 of a focused row) / next parameter page; hold switch + turn = fine
  (x0.1). Shift = side button CC 10 (left-bottom). Free side buttons: `side.1` (CC 8), `side.2` (CC 11), `side.3` (CC 13).
- Banks select Perform pages (profile `banks.pages`): bank 1 = `ab` (A SRC/FX, B SRC/FX), 2 = `bgpv`, 3 = `mixer`, 4 = `master` (since perform-pages phase 3; was DECKS, MASTER, repeated). See `.planning/perform-pages-plan.md`. The page always follows
  the UI tab, not the bank, so every bank's knob n controls page knob n.
- LED colours: row accents, except Master (`LED_MASTER` scarlet) and Global (`LED_GLOBAL` plum) because their on-screen
  greys have no hue. Dark LED = empty/bypassed slot or blank parameter position.

## Feedback history (so nobody repeats the experiments)

1. Original 2014 firmware: bank 1 rings fine, banks 2-4 partial/stale. Devices drop bursts of messages, hence the paced,
   coalescing writer (`minIntervalMs` default 2) and writes to the active bank only.
2. XT firmware (2022): bank 1 fully live, banks 2-4 correct on entry but never updated live. An `amidi` test proved the
   per-bank CC numbers (CC 16 on bank 2) are live and CC 0 is not "the bank on screen", so per-bank numbers are used
   (the `FeedbackAddressing` modes were removed 2026-10-02).
3. A replay of the real sequence (`ControllerFeedbackSequenceTest`) showed the app sends the right CC after turns on banks 2-4,
   so that failure was firmware-side.
4. Stock 4-bank firmware (no sequencer): everything works on all 4 banks. The user is staying on it.

## Suggested next steps

1. **Commit the feedback work** (after the user confirms it is stable). Commit messages end with the attribution line from
   the session's system reminder.
2. ~~Simplify feedback~~ done 2026-10-02: addressing modes, staged/settle/heartbeat rewrites removed (hardware-verified).
3. **Phase 3, navigation/browse from the Twister.** Ask the user what they reach for most. Candidates: toggle a row's SRC/FX
   mode, switch Edit/Perform/Library view, open the SRC picker and step through generators, accept/back. Only three free
   side buttons, so expect a shift layer (profile bindings already support `shift+side.1`). Needs an Explore pass over
   `PerformanceBrowseBay.kt`, `PerformanceDeepEditBay.kt`, `PerformanceDeckControls.kt` (row mode pills),
   `ParametersState.openParams/openGenBrowse/openFxChainBrowse`, `LibraryPanel`. Add commands such as `nav.*`/`browse.*`
   to the registry and bind them in the profile; `KnobSurface` can grow methods or a separate navigation surface.
4. **Phase 5, profile UI**: choose/copy/edit controller profiles, learn into a profile. (The MIDI Controls panel's profile list
   is the legacy learned-mapping list, `library/midi/*.json`, not controller profiles; this confused the user once.)

## Gotchas

- Never write `Global/*` (or any `/*`) inside a KDoc: Kotlin nests block comments and compilation breaks.
- `ParametersState` restores the persisted Deep Edit disclosure and `UITheme.performanceMatrixTab` from the real preferences
  file, so tests must pin them (see `PerformSurfaceTest.setUp`). Don't call `setDisclosure` in tests (it persists).
- `now - Long.MIN_VALUE` overflows; use explicit "never happened" flags (this once stopped the device scan from ever running).
- Relaxed `mockk<Mixer>` returns non-null defaults for nullable ints (e.g. a chain's `focusedSlot`); give tests real `FxChain`s.
- `FxOps.setSlotEnabled` is queued; tests call `FxOps.drainOnGlThread(mixer)`.
- Definition of done in this repo: code + tests + both release-notes files (`RELEASE_NOTES.md`, `docs/release_notes.md`) +
  `DECISIONS.md` entry (newest first) + user guide / developer docs + tooltips where UI-visible, then `generateDocs`.
- The user prefers: delegate architecture research to an Explore agent, then give a short recommendation before planning.
