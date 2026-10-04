# MIDI phase 3: navigation/browse from the Twister (plan)

Decided 2026-10-02. Read `.planning/midi-controller-handoff.md` first. Explore pass results are summarised below.

## Decisions (user)

- Left-bottom (CC 10) stays `shift`. Fine-tune stays "hold a knob's switch and turn that knob" (x0.1). No change to either.
- Free side buttons: `side.1` (CC 8, left-top), `side.2` (CC 11, right-top), `side.3` (CC 13, right-bottom), plus `shift+side.N`.
- Out of scope: Parameters/Properties panel and Deep Edit editing (a separate project if ever), knob binding.
- In scope: switch to the Library, step through its tabs, load items into playlists/queues; open SRC and FX pickers and change
  the active source / FX.
- Buttons are **mode dependent** (a different set per context). Knob 1 has a **browse mode**: turn = step, press = accept
  ("accept with click"). Stepping moves a cursor only; nothing applies until accept.

## Status (2026-10-02): DONE, hardware-tested and committed by the user

Library slice implemented (suite green, docs written, not hardware-tested): `NavSurface`/`NavCommands`, `NavigationSurface`, `LibraryNavigation`, `BackNavigation`, profile bindings `side.*`/`shift+side.*`. Final Library layout: side.1 back (shift: enqueue BG), side.2 tab next (shift: prev), side.3 list next (shift: prev), knob 1 turn = step (4 ticks per item, a guess), tap = accept, shift + tap = enqueue A/B. Perform: side.1 back, side.2 open Library, side.3 unassigned (reserved for the picker). Picker slice implemented too (suite green, docs written, not hardware-tested): Perform side.3 = open the picker of the last-touched knob's row; picker context: knob 1 step/accept, side.2 category (shift back), shift+side.3 clear, side.1 back. **Next**: hardware check of both slices; FX queue transport; Library playlist panes of FX/Trans tabs; highlight of the currently applied item in pickers; decide knob 2-16 behavior in browse contexts (currently still Perform knobs).

## Contexts and button sets (first proposal, to tune on hardware)

| Context | side.1 | side.2 | side.3 | knob 1 |
|---|---|---|---|---|
| Perform | back (Esc stack) | open Library | open SRC/FX picker for the focused row | normal knob |
| Library | back | tab next | pane next | turn = step cursor, press = accept (load / apply) |
| Picker | back | category next | enqueue or chain-list toggle (decide) | turn = step cursor, press = accept |

Shift layer (`shift+side.N`): tab prev, pane prev, enqueue A/B, enqueue BG for Library; category prev for Picker. Knobs 2-16 are
inert in browse contexts (decided and implemented 2026-10-02).

## Work items (new code unless noted)

1. `NavSurface` interface + `navSurface` field on `CommandContext`; parameter on `MidiMappingManager.processGlobalMidiEvents`;
   built in `UIManager` next to the `PerformSurface` (needs session, ParametersState, UITheme, mixer).
2. `nav.*`/`browse.*` commands in the registry, profile bindings per context (profile needs a context concept, or the command
   itself dispatches on current context; prefer the latter, keeps the profile static).
3. `nav.back`: extract the Esc priority stack from `UIManager.processQueueKeyboardShortcuts` into a function shared with keyboard.
4. Library: tab step (`LibraryPanel.viewMode`), pane step (new: sets `activeSelectionSource` + matching index), cursor step
   (`navigateSelection`; add TRANSITION_PLAYLIST and FX_PLAYLIST handling), re-seed selection on tab change (stale
   `activeSelectionSource`), accept = extracted load-to-deck / enqueue functions (currently inline in `LibraryPanel` ~477,
   `PresetListPanel` ~295, `FXBrowserPanel` ~278, toolbar). Avoid `.lsdfx` overwrite popup (pick a slot explicitly).
5. Library queue transport: FX queue next/prev command (functions exist, need `session`).
6. Pickers: `ShaderPickerPopup` gets a cursor + `acceptCursor()` + highlight of current item; force flat view while navigating;
   category step needs a public wrapper over private `updateItems()`; chain list (`PerformanceBrowseBay.drawChainList`)
   needs the same. Source swaps go through `changeVisualSourceSafely`, whose dirty-deck prompt is an ImGui modal: decide how
   MIDI accept behaves then (probably leave the prompt up and let the user answer on screen).
7. Context detection: Perform vs Library (`UITheme.libraryMode`) vs Edit/Picker (`anyRackModuleExpanded` + `rackSectionMode`).
8. Feedback: LEDs/rings in browse mode (decide: keep page lights, or dim knobs 2-16).

## Order

1. Library first (cursor, tab, load/enqueue already mostly callable): items 1-5, 7.
2. Then pickers (item 6), which need the `ShaderPickerPopup` cursor work.
3. Hardware check after each; docs, both release-notes files, DECISIONS.md, tooltips, `generateDocs` per repo definition of done.

## Gotchas (from the Explore pass)

- `setDisclosure` (opening Edit/picker) drops FULL Library to HALF and persists prefs; tests must pin it (see `PerformSurfaceTest.setUp`).
- The picker applies immediately on click today and shows no current-item highlight.
- Deep Edit/Browse bay is solo: `browse.open` for another deck replaces the open one.
- Opening a deck's SRC browse calls `setDeckSubTab`, which can flip a neighbouring `srcfx` row's mode; pinned rows must not.
- The Edit view hides the Library: Library commands must `show` first or no-op.
- Learned mappings win over controller profiles on the same channel/CC; nothing runs if `midiEnabled` is false.
- Never write a slash-star inside a KDoc.

**Unified pane (2026-10-04):** with the beta "Unified" Library pane on, side.3 steps TREE > list > BG queue > A/B queue (FX/Trans: their own queues; no playlist pane). Fresh tab starts in the list; knob 1 in the tree moves a cursor, tap selects the scope. See DECISIONS.md "Controller Navigation of the Unified Browser".
