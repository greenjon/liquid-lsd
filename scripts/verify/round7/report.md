# Round 7 (2026-11-14, evening): more checklist items

Test build: `tests/web/.cache/app/rt` reset to 8a9a458 plus probes 2-6. Cases in `round7/cases11.py`, run with
`CASES=cases11 CASE_DIR=$PWD/round7 RESULTS_DIR=$PWD/round7 python3 round6/ops6.py <work dir> [case...]`. Raw lines in `results/ops.txt`.

## Verified (scripted, 1280x720 unless said, NVIDIA)
- Recent lists a renamed preset under its new name (it has to be loaded through the Library: tour `load_preset` doesn't add to Recent).
- Bound list: ✕ unbinds; Ctrl+click applies one row, no multi-selection.
- Main output monitor click binds the Master FX chain.
- Preset with a source that isn't installed: `[!]` + ⚠ "... missing" row, toast on load, deck unchanged.
- Eject keeps the chain; Edit's Add Source opens Browse bound to the deck's source.
- FX browser Load to Deck A (saved chain) and Load to Deck A > Slot 2 (stock filter, `pixelate`; `bloom` collides with the factory chain of that name) undo.
- Tree playlist menu: Add to the bottom of A/B and of BG, each undone by Ctrl+Z.
- Macros tab Save bank from... lists Deck A/B/BG/PV and Master only.
- Saving a transition: row Save, Edit OUT > TRANS ⋮ > Save Transition As..., bound list's save icon.
- Clock RAND at 1920x1080: RAND A rolls, Ctrl+Z restores all values. Transitions row Save turns amber after a change.

## Finding
- At 1280x720 the Clock row's RAND block is not drawn at all (it is the first block to give way). Needs a decision, not a fix by me.

## Could not do
- Arrow-key cursor across tree / list / queues: the tour `key` action only takes enter/escape/tab/space/backspace/letters, so `Q` with the cursor in a queue is untested (the attempt just added the list row).
