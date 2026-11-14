# Round 6 (2026-11-14): remaining checklist items

Test build: `tests/web/.cache/app/rt` at c9bb7b1 plus probes 2-5 and `round6/apply_probe6.py` (LSD_TOUR_STARTUP, audit_chain, midi_map_range). Cases in `round6/cases10.py`, run with
`CASES=cases10 CASE_DIR=$PWD/round6 RESULTS_DIR=$PWD/round6 python3 round6/ops6.py <work dir> [case...]`. Raw lines in `results/ops.txt` (early lines include script mistakes later fixed).

## Bugs found and fixed
- Every factory FX chain (web TV chains, 3ea7b52) showed unsaved at launch: the files have no `superKnob`, and `computeIsDirty` counted null vs the live Super Knob as a change. Fixed in `FxChain.computeIsDirty`, unit test `fileWithNoSuperKnobIsCleanOnLoad`. Found with `fresh_install` (first run with PREVIOUS_SESSION startup on an empty library).
- Rows dropped on a playlist in the tree did nothing (the drag showed "Moving 2 presets" and dropped nowhere), though the user guide says it works. Added `BrowserPane.playlistDropTarget`. NOTE: another session's commit 59a03ba picked this edit up from the shared tree, so it is already in main.

## Verified (scripted, 1280x720, NVIDIA)
- All five queues: ⋮ has Export and Clear; Clear empties, Ctrl+Z restores; Clear toast mentions Ctrl+Z.
- A/B queue: Remove from queue moves the playing marker with its item, toasts the name, Ctrl+Z restores items and marker.
- New Folder under Saved single FX, Saved chains and Saved transitions; transitions folder menu has Rename/Delete Folder; Stock transitions has no folder menu.
- Star a saved single FX and a chain: both under Favorites.
- Multi-selection dropped on a playlist in the tree (Sources and FX), in order (after the fix).
- Rename a queued, starred preset: queue row keeps it under the new name and plays the renamed file; Favorites keeps it.
- Delete a loaded, starred preset: gone from Favorites and Recent; delete a chain loaded on Master: Master's chain file is cleared.
- Multi-selection dragged into a folder; the deck playing one of them saves back into the folder, clean afterwards.
- Folder dragged into another folder and back onto Saved presets; rename then delete of a folder.
- Unsaved-deck prompt: three equal buttons, wording mentions Ctrl+Z; Esc cancels the load.
- FX chain dirty: Super Knob and Metaknob moves don't count; unlink, swap and a new effect do; reload is clean.
- Clicking the stock source of the playing preset loads defaults; a second click changes nothing.
- A MIDI CC (with the slider's range, as Learn makes it) steps KIFS Color Mode onto whole steps 0-4.
- Fresh install: groove-mandala / tunnel-synthwave / ambient-tides / julia-groove on A/B/BG/PV.
- Your real `library/last_session.json` restores without being flagged unreadable. The ten `last_session.json.unreadable*` copies in `library/` (2026-11-08 and 2026-11-12) are old test fallout; the newest also loads fine now.

## Not done
- Vidvox audio set: the files in a USER_STANDARD folder never registered as sources in the tour build (unknown source); not determined whether that is the test setup or the app.
- Everything else on the list: hardware, other OSes, phones, taste calls, or not reached.
