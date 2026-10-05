# Twister Library "send to deck" knobs (2026-10-04)

## Idea
In Library FULL, knobs 1-15 are inert. Make a knob **tap** send the cursor item to a target:

| Knob | Target |
|---|---|
| 1 | Deck A |
| 5 | Deck B |
| 9 | Deck BG |
| 13 | Deck PV |
| 2 | Master FX (FX tab only) |

Column one = decks top to bottom; knob 2 (top of column two) = master bus, sitting next to Deck A.
Knob 16 is unchanged (cursor / load to inactive deck / enqueue).

## Behaviour (cursor item in the list pane, Sources or FX tab)
- Sources tab: preset file -> `DeckOps.request(slot, DeckChange.Preset)`; stock generator -> `DeckChange.Source`.
  Dirty-deck guard / undo / modal come from DeckOps (the controller already answers the modal).
  Knob 2 does nothing (master has no source).
- FX tab: chain (`FX_CHAIN`) -> `FxOps.loadChain` replaces all 3 slots; single (`FX_PRESET` / `FX_STOCK`) -> first vacant slot
  (`FxOps.firstVacantSlot`); chain full -> overwrite the last slot (no popup on a controller). Knob 2 targets `mixer.masterFxChain`.
- Transitions tab, Maps tab, tree/queue panes: taps do nothing (stay inert, dark).
- Turning knobs 1-15 stays inert. Shift+tap = same as tap (no alt gesture in v1).
- Picker (Edit bay) is untouched: knobs 1-4 still drive the row being filled there.

## Changes
1. `NavSurface`: `fun browseSend(target: SendTarget)` + `val sendTargets: Set<SendTarget>` (which knobs are live right now) with defaults.
   `SendTarget` enum (A, B, BG, PV, MASTER) with its Twister knob index, in `control/`.
2. `KnobCommands.press`: in browse mode, if `knob` maps to a send target and `nav.sendTargets` contains it -> `nav.browseSend`.
   Turns stay inert.
3. `LibraryNavigation.send(target, session, mixer)`: the item-type dispatch above. Extract the FX single/chain logic from
   `FXBrowserPanel.applyToDeck` into `FxOps` / a shared helper so the panel and the controller use one path.
4. `NavigationSurface`: implement `sendTargets` (Library FULL, PRESETS-list cursor, by tab/asset type) and `browseSend`.
5. `PerformSurface.dimForBrowse`: light live send knobs in their deck colour (dim when not applicable).
6. Tests: `KnobCommandsBrowseTest` (taps route / inert elsewhere / picker unchanged), `NavigationSurfaceTest`/`LibraryNavigationTest` (dispatch).
7. Docs: both release-notes files, user guide MIDI section, handoff, DECISIONS.md entry, tooltips if any.

## Not in v1
Queue-item and tree-playlist sends; turn gestures; sending transitions.
