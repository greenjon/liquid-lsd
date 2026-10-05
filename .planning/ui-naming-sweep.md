# UI naming sweep (2026-10-04)

Goal: settle user-facing terms before public release. Code identifiers are out of scope (ship messy code, not messy UI); rename them last, if at all.

## Decided

| Was | Now | Notes |
| --- | --- | --- |
| Deep Edit (menu, tooltips, context menus) | Edit | The row/view is already "Edit" |
| Library tab "Sources" | keep "Sources" | Code enum `PRESETS` is internal; UI stays Sources |
| Library tab "Maps" | Banks | Holds macro banks + Perform pages |
| Library tab "Trans" | Transitions (if width allows) | |
| `TR` badge | `XFADE` / `XF` (fit permitting) | Row is crossfader + transition shader |

## Glossary (decided 2026-10-04)

- **Source**: a stock ISF shader or an external video input. Never saved as a preset when external (would go stale).
- **Preset**: a Source the user configured, named and saved.
- **W/D row**: per-deck FX chain wet/dry. Not sends; "send" wording and `FX_SENDS` are legacy. Keep the `W/D` badge, drop "Send" from user text.
- Agreed: A/B Queue + BG Queue, Transition Queue, Level (not Alpha), Record Output, Render Video (Offline)...

## Open inconsistencies found

1. **One queue, four names**: "Play Queue" (94 hits, src+docs), "Live Queue" (22), "A/B Queue" (16), plus "Queue". BG side is "BG Queue" / "Background Queue". Pick A/B Queue + BG Queue (matches decks) and drop Play/Live.
2. **Source vs Preset vs Gen**: DeckSourcePicker has "Add Source" and "Load Preset" side by side; New Preset menu; tooltips say "assign a source or preset". Define: Source = raw ISF/video; Preset = saved source+params+FX? Then use consistently. (Decision needed.)
3. **Mapping vs Learn vs Binding vs Bank**: "Clear MIDI Mapping", "Learn MIDI Mapping", "Address Mappings", "Macro Bank", "Macro Binding Editor". Mapping (hardware input -> param) and binding (macro -> param) are different things; keep them different and say so in docs.
4. **Level vs Alpha vs Mix**: menu says "Deck Alphas", "Master Mix Levels", "Master Level"; Master row pill is `MIX`; ParametersTabs side rail calls it "Mixer" / CTRL. Choose Level (user-facing), drop Alpha.
5. **Bypass vs Mute vs Enable**: "Bypass Transition Shader", "Unmute Modulator(s)", "Enable LFO 2 (Active)". Pick Bypass for FX/shaders, Mute for modulators, and apply it uniformly.
6. **Library surfaces**: View menu still has "Library Drawer" (Full/Half); tooltip "Restore Library (Half size)"; "Open Library Panel..."; the window is titled "Library". Reconcile with the three-view layout (Perform/Edit/Library).
7. **Parameters vs Properties vs Params**: tab "Parameters", "Reset Parameters", "Properties" panel, "Parameter Mappings". Properties edits modulators; consider "Modulator" or "Mod Source".
8. **Output menu**: "Record Master Output (REC)" vs "Export Video (Offline Studio)...". Suggest "Record Output" / "Render Video (Offline)...".
9. **Deck labels**: `BG` and `PV` appear in menus ("To Deck BG", "To Deck PV") and tooltips ("Deck PV (Preview Deck)"). Decide whether to spell out (Background / Preview) in at least menus and tooltips.
10. **SRC / FX / MIX pills and `W/D` badge**: confirm what `W/D` row actually controls (FX sends? wet/dry?) before renaming; `MIX` on master vs `SRC` on decks stays unless there is room for words.
11. **Transitions**: "Trans Queue", "Transition Queue", "Live Transition Queue" in tooltips/actions. Use "Transition Queue" everywhere.
12. **FBO telemetry** in the menu bar: GL jargon. Label it what a user cares about (e.g. buffers or GPU memory).

## Status (2026-10-04)

Done and committed: items 1-6, 8-12 (strings, user guide, release notes, glossary). Follow-up pass: Edit bay tab "Edit" -> "Parameters"; docs "Performance Mode"/"Performance panel" -> "Perform view", "Column 3" -> "Mixer column". Properties -> Modulation done. Done: Maps -> Macros tab (Banks/Pages inside); mapping (hardware) vs target (macro knob) wording; "binding" retired from user text. Still open: .planning/ARCHITECTURE.md terms.

## Next steps

- Remaining decisions: item 9 (spell out BG/PV), item 12 (FBO label), Bypass/Mute rule (item 5).
- Then do one pass over: MenuBar.kt, LibraryPanel.kt, browser/*, Performance*Controls.kt, ParametersTabs.kt, PropertiesPanel.kt, DeckSourcePicker.kt, tooltips, docs/user_guide, both release-notes files, and the hotkey/OSC label strings.
- Add a short glossary to docs/user_guide so the terms stay fixed.
