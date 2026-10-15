# MIDI phase 5: controller profile UI (plan)

Written 2026-10-02 after a recon pass. **Status 2026-10-14: slice 1 is DONE and committed; the binding editor and learn-into-profile are also done; the remaining items under "Later slices" are v1.1 (feature freeze).** Read `.planning/midi-controller-handoff.md` first (architecture map,
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

## Slice 1 (DONE)

Implemented: store writes (`copyBuiltInToUser`, `saveUser`, `deleteUser`, `reload`, `rejected()`, `warnings()`), tests in `ControllerProfileTest`,
`MidiPreferencesPanel.drawControllerProfiles`, legacy bar retitled "Learned Mappings". Beyond the plan: per-device profile choice persists
(`controllerProfileChoices` preference, `matchFor(deviceName, preferredId)`), `saveUser` refuses to overwrite a newer-schema file.
The text below is the original plan, kept for reference.

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

## Later slices

- ~~In-app binding editor~~ DONE (`b24c060`, `control/ProfileBindingEdit.kt`): global bindings only, with kind check and validated saves.
- ~~Learn into profile~~ DONE (`9aeb5ed`, `MidiLearnTarget.ProfileCommand`, `MidiMappingManager.learnIntoProfile`). Documented in both release-notes files and the user guide.
- ~~Optional manual per-device profile choice~~ done (see Slice 1).
- Still open (v1.1): per-bank bindings in the editor (JSON only today).
- Still open (v1.1): bind the unbound `fx.queue_next/prev` and `fx.bg_queue_next/prev` commands (no free Twister input; see DECISIONS.md).
- Still open (v1.1): perform-pages phase 4 (page editing); see `.planning/perform-pages-plan.md`.

## Open questions for the user

- ~~Manual per-device choice?~~ Resolved: implemented and persisted.
- Copy-to-user: keep the same `id` (overrides the built-in) or offer a new id/name? The recommendation is the same id for slice 1.
