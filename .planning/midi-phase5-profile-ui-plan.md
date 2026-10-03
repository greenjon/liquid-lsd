# MIDI phase 5: controller profile UI (plan)

Written 2026-10-02 after a recon pass. Nothing is built yet. Read `.planning/midi-controller-handoff.md` first (architecture map,
gotchas, definition of done). Phases 1-4 and the phase 3 leftovers are done and committed.

## Goal

Let the user see which controller profile each device uses, copy a built-in profile to a user file, delete it again (reverting to
the built-in), reload after editing, and see validation problems. Later: edit bindings in the app and learn into a profile.

## What exists today (verified in code, 2026-10-02)

- `control/ControllerProfileStore.kt`: `load()` builds a cached list. Built-ins come from `/controllers/<name>.json` (`BUILT_IN_NAMES`,
  only `midi-fighter-twister`); user files from `library/controllers/*.json` (`userDir`, injectable for tests), sorted by name. A user
  profile with the same `id` overrides the built-in; user profiles come first, so they win when two match one device.
  `parse()` skips a profile that fails to decode or has `compile().problems` and only logs it, so the UI must surface problems itself.
  `matchFor(deviceName)` = first profile whose `match` strings are a case-insensitive substring of the device name.
  `ControllerProfileStore.default` is the shared singleton, used by `MidiMappingManager.controllers` (`ControllerManager(commands)`).
  The store's `Json` (prettyPrint, encodeDefaults, ignoreUnknownKeys) is private: a write needs a `save` method on the store.
- `control/ControllerManager.kt`: runtimes are created lazily on a device's first event (`handle`) and cached, including "no profile";
  `scanDevices` matches for feedback. There is no persisted choice and no manual per-device override: matching is automatic.
  `store.reload()` and `ControllerManager.reset()` exist but nothing calls them. After an edit call both, in that order.
- `ControllerProfile` is `@Serializable`. Validation is `compile().problems` (bad id, bank/channel/cc range, overlapping CCs, unknown
  inputs or modifiers in binding keys, unmatched wildcards, bad feedback settings); `unknownCommands(registry)` lists binding targets
  that are not registered commands. Binding keys are `[mod+...]inputId` -> command id (`knob.3`, `knob.3.press`, `shift+side.1`);
  group inputs are 1-based, `.press` is auto-added, `*` / `{n}` are wildcards; `bankBindings` is keyed by the 1-based bank string;
  explicit keys beat wildcards.
- UI: `ui/MidiPreferencesPanel.kt` (reached from `PreferencesPanel.kt` category `MIDI_CONTROLLER`). Sections: enable + rescan + connected
  devices, "Active Mapping Profile" (the LEGACY learned-mapping list from `midiMappingManager.listProfiles()`, not controller profiles),
  Live MIDI Monitor, Global Action Triggers (Learn/Cancel/Clear row pattern), parameter mappings table.
  Patterns: `uiTheme.caption` / `captionColored`, `uiTheme.withFont(H3)`, `Icons.*` labels with `##id` suffixes, `ImGui.combo` + `ImInt`,
  `ImString` fields, modals as in `AboutModal.kt` (`openPopup` + `beginPopupModal`), context menus as in `FxChainHeader.kt`.
- Learn today: `ParametersState.midiLearnTarget` (`MidiLearnTarget` sealed class), 15 s timeout, consumed in
  `MidiMappingManager.processGlobalMidiEvents` before `controllers.handle`. `CompiledController.resolve(event)` maps a raw event to a
  `ResolvedInput` (`inputId`, `kind`, `bank`) and already handles bank-stride CCs.
- Tests: `ControllerProfileTest` has store tests (override by id, user preferred, broken user file skipped, missing dir) using an
  injectable `userDir` and `builtInNames`. No tests for store writes, reload or UI.

## Slice 1 (recommended first)

1. **Store writes** in `ControllerProfileStore`:
   - `copyBuiltInToUser(id)`: writes `library/controllers/<id>.json` (it overrides the built-in by id with no extra work); refuses if a
     user file already exists.
   - `saveUser(profile)`: validates first, refuses (returns the problems) when `compile().problems` is non-empty.
   - `deleteUser(id)`: removes the file, reverting to the built-in.
   - Each ends with `reload()`; the UI then calls `controllers.reset()`.
   - Tests with a temp `userDir`: copy, refuse duplicate, save valid, refuse invalid, delete reverts, reload picks up a hand edit.
2. **"Controller Profiles" section** in `MidiPreferencesPanel`, a `collapsingHeader` between the device status and the legacy bar:
   - Profile list: name, source (built-in / user / user override), the connected device (if any) that currently matches it.
   - Buttons: Copy to user, Delete user file, Reload. A hint with the `library/controllers/` path (hand-editing the JSON + Reload is the
     working edit loop for this slice).
   - Validation `problems` per profile. Broken user files are skipped by `parse()` today, so the store needs to keep them (name +
     problem text) in a separate list for the UI instead of only logging.
   - Retitle the legacy "Active Mapping Profile" bar "Learned mappings" to remove the confusion that already bit the user once.
3. Done = code + tests + both release-notes files (`RELEASE_NOTES.md`, `docs/release_notes.md`) + `DECISIONS.md` entry (newest first) +
   user guide controller section + tooltips, then `./gradlew generateDocs --offline -q`; suite `./gradlew test --offline -q`.

## Later slices (not started)

- In-app binding editor: a table of binding key -> command with a command picker from `CommandRegistry.all()`; `saveUser` on change.
- Learn into profile: new `MidiLearnTarget.ProfileCommand(profileId, commandId, modifiers)`; a branch in `processGlobalMidiEvents` that
  does `compiled.resolve(event)`, writes `bindings[key]`, saves. For inputs a profile does not define (new device) the captured
  channel/type/cc becomes a new `InputDef`. Learn already swallows the event before `controllers.handle`, which is what is wanted.
- Optional manual per-device profile choice (needs a persisted setting; today matching is automatic only).
- Bind the unbound `fx.queue_next/prev` and `fx.bg_queue_next/prev` commands (no free Twister input; see DECISIONS.md).
- Perform-pages phase 4 (page editing) is paired with this phase; see `.planning/perform-pages-plan.md`.

## Open questions for the user

- Should the section also allow choosing a profile per device manually, or is automatic matching enough for v1.0?
- Copy-to-user: keep the same `id` (overrides the built-in) or offer a new id/name? The recommendation is the same id for slice 1.
