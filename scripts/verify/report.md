# Automated checklist pass, 2026-11-06

Built from `main` at 0aaa3ab in a throwaway worktree, with a copy of `library/`. The only code difference was a test probe (GPU buffer count printed each second, and a window size override for tour renders). Music was a 15-minute slice of `magical_bass_8.mp3` from 5:00.

Results are also written into `.planning/app-checklist.md`, as `auto:` lines under each item.

## Update: fixes and the Library pass

Fixed and re-rendered to confirm (`after-fixes/`): Gyroid, Mandelbox, Transitions captions, menus clipped at the right edge, `MIDI: CC -1`. The Library pass found five more, all fixed the same way: a Save As crash on a preset with a full path, preset Rename/Clone/Save As ignoring focus, Enter and Esc, Ctrl+Z dead with the Library maximized, Save after deleting a loaded preset rewriting the file unasked, and a playlist's missing items hidden below the list.

The Library pass (`library/`) ran each operation against a fresh copy of the library, through the real UI at 1280x720, then compared files on disk.

Then decided and done on the same branch: delete warns when the trash refuses, FX and transition playlists keep short paths, the folder button follows a selected playlist, no red `[!]` for a preset that merely uses something switched off, the restart badge, Save after a restart finding the preset's file, and randomization on by default (the sequencer already was).

Still open:
- Gyroid: passing through a wall is no longer black but is still a hard cut.
- A delete the trash refuses is still permanent, with a toast afterwards rather than a choice before.
- Playlists holding full paths from before keep them until the item is next renamed or moved.
- `WebEvaluatorVectorsTest` failed once in a full run (a last-bit float difference in one expected value) and passes alone. `AbletonLinkEngineTest` fails where the real Link native library is present, and passes on a checkout without it. `PresetDirtyLoadingTest` needs a populated `library/presets`.

The images this report names (`sheets/`, `screens/`, `after-fixes/`) are kept out of git, beside this file in the main checkout.

## Problems found

| Where | What | Evidence |
|---|---|---|
| Gyroid Hyperspace | Goes fully black many times in 8 s at default settings, with or without music, for up to 0.7 s each. The camera flies through a wall: the first march step hits at `t = 0`, and `hitDist > 0.0` then treats it as no hit. Cause read from the shader, fix not tried. | `sheets/gyroid-mandelbox.jpg` |
| Mandelbox | Opens as a diamond, even in silence. Rotate Z -45° squares it, so the fixed `rotZ(0.78539816)` is the cause. | `sheets/gyroid-mandelbox.jpg` |
| Wave Ripples rolls | 3 of 6 rolls read nearly black in the first run. In a second run with other rolls, 1 of 6 stayed black for 7 s. | `sheets/rolls-1.jpg` |
| Other rolls | Quasicrystal 1 whiteout and 1 near black of 6. Cyclic CA 2 of 6 noise. KIFS 1 of 6 a speck. Icosa H3 small and much the same each roll. Nebula mostly white on 3 of 6. KIFS Storm, Gyroid and Domain Warp some washed out. | `sheets/rolls-*.jpg` |
| Transitions row | Knob captions show raw names cut short: `BURNSPREAD`, `GLOWINTENS`. | `screens/dark-mixer.png` |
| Monitor drag menu | Runs off the window's right edge at 1280x720, cutting off the Source + FX rows. | `screens/monitor-menu.png` |
| Queue tooltips | End with `MIDI: CC -1` when nothing is mapped. | `screens/queue-tooltip.png` |
| After a restart | A stock source's badge reads `Deck A · Aurora ribbons`, and its A chip in the Library is gone. Seen once, after kill -9. | `screens/before-kill.png`, `screens/after-restart.png` |
| Library chips | Reproduces your note: Bloom in three slots draws three blank bars. | `screens/bound-fx-slot.png` |
| Light theme | Unreadable: BPM and fade time (dark on dark), FX link and power icons (plain squares). Low contrast: chain names, slot names, CLK badge, MAN/AUDIO/LINK. | `screens/light-*.png` |
| Toast on load | Every stock source load shows `Deck A macro knobs replaced by ... defaults (previous targets replaced)`, and it also appears over the Output View. May be intended. | `screens/load-toast.png` |

## Passed

- Transitions at rest: frame hashes identical to Linear Crossfade, resting on A and on B, with music. A repeat render of the baseline matched itself exactly, so the comparison is sound.
- GPU buffers: no growth over 90 loads.
- Garden flames: all 158 load with no black frames. 4 barely move.
- Black is transparent, off-air half rate, camera-relative light, broken and duplicate shaders, new-version icon.
- kill -9: source and FX ON state came back, apart from the badge problem above.

## Not reliable

`params-shortlist.txt` lists 71 of 328 parameters with no clear effect, but the test compares brightness only. It cannot see hue or saturation changes, its Rotate tests go from -180° to +180° (the same view), and fast motion hides small effects. Use it as a place to look, not as a list of bugs. The boxes for `parameters do something` stay open.

iGPU timings come from a clock with 1 s resolution and include readback, so they are rough.

## Limits of the method

- Tour renders drive the real UI and shaders, but on a simulated clock with preferences off, and MIDI, OSC and Link off. Only the kill -9 test ran the app normally, and that was on software GL.
- Each dice roll on the sheets is one frame. Duds were flagged from these frames and their brightness, except the Wave Ripples re-run.
- Files, folders, tags, playlists, undo of Library actions and drag-and-drop were not exercised beyond what is listed.

## Still needs you

Twister and other hardware, JACK buffers, Wayland and projector output, real phones and Safari, real radio and broadcast, and every judgement of taste (response depths, whether a roll looks good).

`scripts/` holds the generators and analysers used for this pass.
