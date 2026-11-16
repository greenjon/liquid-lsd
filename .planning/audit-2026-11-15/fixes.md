# Fix results

## F1 (branch worktree-agent-ab581c04f587cc68d, ae22ce2) — all 10 fixed, 0 false positives
Open: Main.kt shutdown must call ExternalVideoDiscovery.stopPolling() (L1a-13 rest). web/beattrack.js not affected by L1a-3.
Release notes:
- Quitting while a recording was still being saved no longer freezes the app; the recording and your session are saved as normal.
- Linux PipeWire video output no longer shows the occasional torn frame, and can no longer crash when the output size changes or the output is turned off.
- Tempo detection no longer sometimes picks the wrong beat grouping from an earlier estimate.
- A PipeWire video input that switches from black-and-white to colour now shows colour.
- If the sound can't be added to a recording, it is now saved next to the video as a .wav with the same name and you're told.
- A crash during a file's first save no longer leaves an empty file behind.
- When a damaged file can't be backed up, you're now told it will be replaced on the next save.
- Two PipeWire video sources with the same name keep their numbering between scans and replugs; a stuck pw-dump no longer stops source discovery.
App checklist:
1. Start a recording, quit: app closes cleanly, MP4 and session saved.
2. PipeWire output on, change output size with a reader (OBS) connected, turn off: no crash, no torn frames.
3. Failed mux: toast names the kept .wav.
4. PipeWire source switching GRAY8→colour shows colour.

## D2 (main tree, uncommitted) — lane 4, L6 docs, L7 docs done
False positives: L4-10 `.patch` (still loads), L4-11 live thumbnail (exists). Not done: L7-8 licence table rows, L7-9 ledger rows (→ audit-docs follow-up). L4-9: old preset/source notes survive in files but are never shown.

## L7-8 / L7-9 follow-up (Sonnet, uncommitted) — done
SHADERS_LICENSE.md: 6 source rows + Bundled Transitions section; licence "Unlabelled, pending L7-1". Ledger: 5 rows (cymatics, BeatPhaseLock, TransientDetector/HitScale, BeatHopper, web tempo port), all basis "To confirm" — owner to confirm own design, no Mixxx code read, WKB Bessel source.

## D1 (main tree, uncommitted) — lane 2 + lane 3 + L1b-9 + L1b-5 done
Deviation: rule written as "macro/ imports nothing from ui/; presets/ takes only UITheme prefs, ToastOverlay, FileSystemManager roots, favorites keys" (presets/ → ui/ imports remain).
Merge-time todo: TransientDetector.kt KDoc names nonexistent WebTransientVectorsTest; Renderer.kt:298 comment says "bloom"; recount files after merges.
(D1 claim that RealtimeRecorder.isFinishing is not @Volatile is wrong: it is @Volatile.)

## F3 (branch worktree-agent-a0d8ea818957e895a, 96f0e34) — all fixed, 0 false positives
Merge-time todo: FXBgQueueActionsPanel.kt:47,49 "background FX queue" (regex widened); grep docs for ui.ParamLabels → rendering/isf; DECISIONS theme-fork list drop FxSlotCell/QueuesPane/ParametersRenderer.
Release notes:
- Tooltips on Audio Response, Input Trim, System Volume, Master BPM, Rate Limit and the step sequencer's Length, Phase, Step Hold and Offset now appear (they never showed before).
- Rows on the Master CTRL and LEVELS tabs show a description in their tooltip, and their names match the queue names used elsewhere.
- The Audio Response tooltip names the Hits signals and the Hi-Mid band.
- A modulator's power button now says Mute / Unmute.
- The Q and Shift+Q descriptions match what the keys do.
- On macOS the trackpad console shows "Not supported on macOS yet" instead of Ready.
- New tooltips across OSC and MIDI preferences, Video Export, Preferences and several Edit controls; Go Live explains itself whether enabled or not.
App checklist:
- Hover the 9 sliders above: explanation under the name in label and track tooltips.
- MST > CTRL and LEVELS: hover row labels, descriptions + new names.
- Light theme: FX slot cell backgrounds (filled/empty), Queues pane column shading look as before.
- Macro/Metaknob-driven row labels still cyan (dark) / teal (light).
- Spot-check new OSC/MIDI prefs, Video Export, Go Live tooltips.
- Mac: Preferences says "Not supported on macOS yet"; CapsLock does nothing.

## F2 (branch worktree-agent-a41f5a3a369b905a2, 347d2db) — all fixed, 0 false positives
Skipped on purpose: export-queue-as-playlist modals + dropped .lsdplay read still on Thread 0; AppPreferencesStore.savePreferences and MacroBankSerializer saves still on Thread 0; legacy .json preset relink (L1a-10 part).
Release notes:
- Adding to, dropping onto, editing and creating playlists no longer make the output stutter: the files are written in the background.
- The 60-second session autosave, favorites, recent items and key bindings are written in the background, so they no longer cause a hitch.
- Creating a playlist that can't be written now says so instead of silently doing nothing.
- A preset you just saved or renamed no longer briefly drops out of the Sources list.
- Adding to an FX or transition playlist keeps any extra fields in the file.
- After a failed preset save, unsaved macro-knob changes still count as unsaved.
- A multi-selection in the FX or Transitions list is no longer narrowed by a stray click later.
App checklist:
1. Add to Playlist from the row menu on all three tabs: toast, playlist updates at once.
2. New Playlist... with rows selected creates, selects and fills it; a blocked folder shows an error toast.
3. Drop rows onto a tree playlist; reorder/remove in open Sources and FX playlists; quick repeated edits stick.
4. Save a preset, rename one: shows in the Sources list at once and stays.
5. Run past 60 s: last_session.json updated, no hitch; quit and restart restores the session.
6. Starring, recents and key rebinding survive a restart.
7. FX list, 3 selected: press one, move off, release, click elsewhere: selection stays.
8. FX/Transitions row tooltips unchanged. 9. Stock chain loads clean; editing a Metaknob link makes it dirty.
10. BG Queue Export/Clear tooltips say "BG Queue".
