# In-app check list: ticked items (moved out of app-checklist.md 2026-11-14)

Notes marked `!!` are problems found on items that were ticked anyway.


## 1. Library and browser

- [x] Ctrl+F opens the Library on the current tab; Esc goes back to where you were (from Edit or a pair view: the same view, with the same slot still bound).
  !!needs ESC pressed twice. first time exits text box, second closes (could be by design)
- [x] Sources, FX and Transitions tabs: list scrolls, search works, typing a letter while the list has the keyboard starts a search (shortcut keys such as `Q` keep their shortcut). !!tooltip on transitions: "add to a/b queue" should be "add to transitions queue" right?
- [x] Rows: no empty box on stock items, long names end in "..." with the full name on hover, `hyperspace_crash_zoom` reads "Hyperspace crash zoom", heavy shaders show a muted gauge with an explanation on hover.
- [x] Loaded-where chips: `A`/`B`/`BG`/`PV`/`M` on sources; `A2`/`M1` on effects; a deck playing a saved preset marks the preset row, not the generator; many slots collapse to `×n` with the full list on hover. !!loading the same source to all 4 decks was fine up to A+B+BG  but then when I added PV also, I had 4 colored chips with no text in them. same with effects. no xn, nothing on hover
  !!auto: reproduced at 1280x720: Bloom in three slots draws three blank bars with no text. Your earlier note stands.
- [x] Recent folder in Sources, FX and Transitions: last 20 loads, newest first, still there after a restart.
- [x] Menus match across tabs (right-click works on FX and transition rows too): Rename..., Clone, Delete..., Add to playlist, **Reveal in File Manager**. !!we should make "add to playlist"" open a side menu with the playlists and the subfolders of playlists
- [x] Deleting a saved effect or transition also removes it from queues and playlists.
- [x] Delete (file or folder) lands in the system trash on this desktop (Linux: `gio trash`); the dialog says so.
  auto: on a real disk, file and folder both land in the trash, and the dialog says so. Where the trash refuses (seen on /tmp), the file still goes for good, but now a toast says so and the dialog says "where the drive has one".
- [x] Enter runs the row's default action on every tab; Delete follows Preferences > Shortcuts (Backspace works too); Alt+click a Sources row previews it on PV.
  auto: Enter on all three tabs, Delete then Esc keeps the item, Backspace then Enter deletes it, Alt+click loads on PV.
- [x] Rename, Delete, New Playlist and Export dialogs: field focused, Enter confirms, Esc cancels without leaving the Library. Esc that closes a menu doesn't also go back.
  auto: focus and Enter on all five; Esc cancels and stays in the Library on Rename, Delete, New Playlist and Export; Esc on a menu only closes it.
- [x] Transitions view: the Q key queues.
  auto: Q queues.
- [x] A failed FX or transition load shows a toast.
  auto: a broken saved FX and a broken saved transition each show a toast.
- [x] Click a preset then a source on the same deck within a blink: the last click wins.
  auto: preset then source one frame apart: the source wins.

## 2. Slots bound in place, pair view and Edit

- [x] Space or maximizing the Library pauses the binding; Esc or Space again resumes.
  auto: Space maximizes, Space again and Esc come back still bound.
- [x] Trying sources on a deck with unsaved changes asks once while it stays bound; Ctrl+Z still undoes each load.
  auto: asks once, Enter answers, the next source doesn't ask, Ctrl+Z undoes each load.
- [x] Page tabs close the pair view. Esc leaves it and drops the dock selection.
  auto: both.
- [x] Ctrl+Z after picking an FX and after picking a transition in Edit.
  auto: both undo through the real picker.

## 3. Perform rows, Transitions row and Clock row

- [x] Transitions at rest (2026-10-27): with the crossfader fully on A (then B), switch through all 8 transitions: the picture does not change at all. Start a slow fade from rest on each: no jump on the first frame of the fade.
  auto: all 8 with music: frame hashes identical to Linear Crossfade on 330 of 330 frames, resting on A and on B. No jump in the first 0.5 s of a 6 s fade.
- [x] Cyber Datamosh at rest (2026-10-27 fix): select it, push the crossfader fully to B (and let an auto fade land on B): B's colours look normal, no red/blue smear. Same on A. Check on the web TV too if you test it.
  auto: identical frames fully on A and fully on B, fader set directly. An auto fade landing on B and the web TV not checked.
- [x] MIXER page order: Transitions, Master FX, Master MST, Clock. The crossfader is Transitions knob 1 (and the Mixer panel), not on the MST row. MST pill reads `MST`.
  auto: seen at 1280x720.
- [x] Clock row at 1280 px: tempo grid `MAN AUDIO LINK` over `TAP SNAP [-|+]`, BPM and beat dots beside it, LINK dim while off.
  auto: seen at 1280x720.
- [x] Tempo tracking (2026-11-04, clock source Audio): play techno, rock, reggae and drum and bass, and watch the BPM readout. It should lock in about 2 s and hold steady through a track, at the right tempo or an octave off. Try a low JACK buffer (64 or 128) if you can: the tempo should be the same as at 512, with no new xruns. Watch beat-synced LFOs for a jump when a track changes. Downbeat Auto should behave as before.
- [x] Monitor drag onto another monitor: nine-choice menu (Move / Copy / Swap × Source / FX / Source + FX), each does what it says.
  auto: all nine actions do what they say, and all nine rows are on screen at 1280x720.

## 4. Sources (2026-10-22 review)

- [x] Leak check: load ~30 sources and flame presets in a row on one deck (or run an A/B queue of flames for a while); `GPU buffers` in the title bar stays roughly flat.
- [x] Ctrl+Z after a source change brings the old one back (a flame or simulation restarts fresh; expected).
  auto: brings the old source back, after a die roll too.
- [x] A shader with a syntax error: red checkerboard on the deck, ⚠ "failed to compile" in Browse, the first compiler error on hover.
  auto: checkerboard, ⚠ in Browse and the compiler error on hover all seen with a broken test shader.
- [x] Two user shaders with the same file name: one loads, the log warns about the other.
  auto: one row in Browse, and the log says Skipping for the other.
- [x] Old presets and sessions still load (they carry the removed deck feedback and 3D-view values, which are now ignored).
  auto: 27 desktop presets from two points in git history load, and a session with the removed deck feedback and 3D-view values restores in a real run.

## 6. FX chains (2026-10-22 review)

- [x] Revert to Saved undoes parameter tweaks.
  auto: cleans a parameter tweak.
- [x] Saving over a chain keeps its tags; the chain takes the saved name. A failed save (read-only file) shows a message and stays unsaved.
  auto: tags kept, chain takes the saved name, a read-only file shows a message and stays unsaved.
- [x] A plain mouse wheel over a slot scrolls; Ctrl + wheel steps the effect.
  auto: the wheel leaves the effect, Ctrl + wheel steps it.
- [x] A chain at Chain Dry/Wet below 100% shows `Wet 40%` in its name; at 0% the name is dimmed.
  auto: seen, `Wet 40%` and dimmed at 0%.

## 7. Super Knob and Metaknobs

- [x] Swap effects between slots: each slot keeps its own link setting.
  auto: each slot keeps its link setting.

## 9b. Audio response (2026-10-25)

- [x] Transient hits (2026-11-04, replaces the old flux signal under the same IDs): play sustained-bass music, then four-on-the-floor, on a few stock sources with kick responses. Also try rock and jazz: snares and hats should read about as strongly as on electronic music (hit sizing, TV v25). Hits should look punchier and less jittery. A held bass note should not pump anything Transient-driven, while a kick on top of it should. **Every stock Transient response changes here, with less motion on average: about 40-50% in the bass and less in the higher bands**: note any look that now feels too calm. At a low JACK buffer (64 or 128) kicks should look the same as at 512.
- [x] Hi-Mid band (2026-11-04): on a parameter's Audio tab, Frequency Band lists Hi-Mid (Crack), in Continuous and Transient. A snare or a bright vocal moves it more than the kick does. Its tooltip gives about 2-5 kHz. The Audio Hardware scopes show Hi-Mid Band and Crack Hits, and the panel still fits at 1280x720 with the extra rows.
- [x] Band energy (2026-11-04): in Section mode, Signal lists Bass, Mid, Hi-Mid and High Energy. Each needs about 20 s of music. Hats coming in lifts High Energy alone, and the bass leaving drops Bass Energy alone. An empty band sits mid-range. Four new scopes show them in Audio Hardware. Save and reload a preset that uses one.

## 10. Mixer, output and app

- [x] New version available: menu-bar download icon instead of a startup popup.
  auto: fresh real run: green download icon in the menu bar, no popup.
- [x] Kill the app (`kill -9`) after changing things: preferences and session from the last minute come back.
  auto: real app, kill -9 about a minute after the changes: source and FX ON state came back, badge and Library chip right (badge fixed 2026-11-06).
- [x] Web Broadcast tab hidden until Show Advanced Preferences (or already configured).
  auto: not in the Preferences list with default settings. Turning on Advanced not tried.

## 12. Flame source

- [x] Garden stock (158 curated) loads, no black frames.
  auto: all 158 loaded with music on the NVIDIA card. No black frames (darkest frame anywhere 6.3 of 255) and none blown out. 4 barely move: curated-0127, 0262, 0308, 0374.
- [x] Black is transparent: a flame on A over BG shows BG through the dark parts.
  auto: BG shows through the dark parts (mean change 73) and bright parts barely change (3).

## 13. Generators

Light wave 1: - [x] quasicrystal - [x] thin_film - [x] tunnel_scenes - [x] video_feedback - [x] hyperbolic_tiling
Light wave 2: - [x] cyclic_ca - [x] metaballs - [x] wave_ripples - [x] aurora_ribbons - [x] isometric_voxels - [x] halftone_matrix - [x] kali_set - [x] girih_stars - [x] glitch_blocks - [x] voronoi_cells
- [x] Every-ISF binding audit (v1.0 scope): a sample of filters, every parameter binds to a knob.
  auto 2026-11-06: all 717 parameters of 37 sources, 27 effects and 8 transitions (Fractal Flame's X1-style transform values left out) bind to a knob and the knob moves them. One failed, now fixed: Luminous Flash Intensity.

## 14. Twister hardware

- [x] Controller FX tap with nothing bound applies to the live deck (first vacant slot, last when full; Shift + tap queues).
  auto (simulated): first vacant slot, then the next; Shift + tap on a saved effect queues.

## 16. Web client (if you test it)

- [x] Full-frame looks (Cyclic CA, Kali Set, Voronoi, Ripples, Quasicrystal...) never get trail or bloom chains, and don't wash out.
  auto: 53 fullframe channels, none on a trail or bloom chain. Brightest: CH 12 and 16, mean 226-230 of 255.
- [x] Looks change on the beat, about every 16 bars; the background about every 32.
  auto: fades end on a bar line, every 17 bars, at 128, 170 and 174 BPM. Backgrounds set to WITH SCENES change every 33 bars.
- [x] GPU tier: on the NVIDIA (or a Mac) `LSD.gpu` in the console says heavy on and Mandelbulb/Nebula looks appear; on the Intel GPU they never do.
  auto: fixed 2026-11-06: Chromium passed every GPU, with the probe reading 0.3 ms. Now NVIDIA 4.4 ms heavy on, Intel 61 ms heavy off. Firefox picks the Intel GPU here and its readout wasn't checked.
- [x] `?kiosk` on a spare screen: picture only, turns on by itself (or asks for a click for sound), pointer hides.
  auto: picture only, turns on by itself, with autoplay blocked sound starts on the first click. Fixed 2026-11-06: the pointer stayed visible over the picture.
- [x] Turn on your OS's reduce-motion setting: MENU shows Reduced motion AUTO (ON), no hard looks or flashes appear, changes are slow.
  auto: MENU shows AUTO (ON), no hard scene in 30 changes, on a real media-query emulation.
- [x] SCHEDULE (2026-10-31, after publishing; TV v4): the button, `G` and MENU › SCHEDULE open Spaz Radio's upcoming shows in your local time zone (header says e.g. TIMES IN PDT), the show playing now marked ON AIR, matching the Spaz Radio app's Schedule tab. Any key or a tap closes it; it scrolls with a finger on a phone without closing. The track line on screen is followed by NEXT: <show> · <time>. Fullscreen: the schedule fills the picture too.
  !!works in a new tab; a plain reload of an open tab kept the old code (button did nothing). Phone scroll not checked yet.
  auto: scrolls under an emulated finger without closing, 60 shows, 412 px wide.
- [x] Reload after a publish (2026-10-31, TV v5): with the TV open in a tab, after the next publish that changes code, one or two plain reloads show the new version in the MENU title (the first visit after v5 installs the fixed service worker).
  auto: after a simulated publish, the first plain reload shows the new version.
- [x] Pull the network while the TV plays: snow and NO SIGNAL within a few seconds; plug it back: the radio and the picture come back within ~10 s.
  auto: local fake radio, stalled and dropped, Chromium, stalled on Firefox: NO SIGNAL in 4.4-5.4 s, back in 0.1-9.7 s, no errors. Real radio not tried.
- [x] On a track with a breakdown and drop, a calm look arrives in the breakdown and a hard look cuts in on the drop (not on every quiet moment).
  auto: synthesized track. Calm look 1 s after the breakdown is detected, hard look 0.7 s after the drop. Fixed 2026-11-06: a breakdown starting during a fade lost its calm look.

## 16b. Audit fixes (2026-10-29)

- [x] Rename a preset and edit its tags: the file keeps everything else (open it in a text editor).
  auto: only `name` and `tags` differ afterwards.

## 17. Before release

- [x] Camera-relative light: Mandelbulb, Mandelbox and KIFS: turn Rotate X/Y/Z (and let Mandelbulb's Flight Speed spin) through a full circle; the lit side stays toward you and no side goes dark. KIFS: try every start face. Icosa H3, Pseudo-Kleinian and Apollonian look as before.
  auto: Mandelbulb, Mandelbox and KIFS through a full turn on each axis: no frame below half the median brightness. Every KIFS start face and the look of Icosa H3, Pseudo-Kleinian and Apollonian not checked.
- [x] `ARCHITECTURE.md` refreshed (verified against the code 2026-10-29).
- [x] Black-is-transparent sources (30 generators + Renderer separate-alpha blend): put Video Feedback and a few others (Kali Set, Lenia, Fluid Ink, Hyper Slice) on a deck over a coloured BG deck; dark areas should show the BG, bright areas cover it; Chladni / Dynamic Spiral / Icosa H3 should look as before (alpha now linear, not squared).
  auto: BG shows through Video Feedback, Lenia, Fluid Ink, Hyper Slice, Icosa H3 and partly Chladni. Dynamic Spiral stays opaque black, by its shader. Kali Set has no dark areas to test. `as before` could not be compared.

## 13. Generators (Heavy line)

- [x] mandelbulb
- [x] pseudo_kleinian
- [x] apollonian
- [x] escape_time
- [x] nebula_volume
- [x] fluid_ink

## Moved out 2026-11-14 after the scripted run

- [x] Delete a preset loaded on a deck: it drops out of Recent/Favorites; Save on that deck asks for a name. Delete an FX chain loaded on Master: Master's chain header no longer offers overwrite.
  auto: fixed 2026-11-06: Save wrote the file back without asking; it now asks for a name. Left for you: Recent/Favorites, the Master chain case.
  auto 2026-11-14: gone from Favorites and Recent; a chain loaded on Master and deleted leaves Master with no file to overwrite.
- [x] Folders: folder-plus button and right-click New/Rename/Delete Folder on Saved presets, Saved single FX, Saved chains, Saved transitions (not on stock sections); an empty folder shows in the tree; with FX and no saved section selected the button is greyed with a tooltip.
  auto: New, Rename and Delete Folder on Saved presets, empty folder shows, no menu on stock sections, FX button greyed with its tooltip. Saved FX, chains and transitions not run.
  auto 2026-11-14: New Folder on Saved single FX, Saved chains and Saved transitions; Rename/Delete Folder on a transitions folder; none on Stock transitions.
- [x] Drag rows (one, and a multi-selection) onto a folder, and use Move to: they move, playlists/queues still play them, a loaded deck still saves back. Moving onto a same-named item asks Keep both / Skip.
  auto: one row by drag and by Move to: file moves, playlist follows. Same name asks Keep both / Skip / Cancel. Left for you: multi-selection, a loaded deck saving back.
  auto 2026-11-14: a two-row selection dragged into a folder moves both; the deck playing one saves back into the folder.
- [x] Click the stock source of the preset you're playing: its defaults load. Click it again: nothing happens.
  auto 2026-11-14: defaults load (no preset file); a second click changes nothing.
- [ ] Phase 8 CI is not done yet, so nothing to check there.
  dropped 2026-11-14: not a check.
- [ ] A MIDI/OSC mapping to `Deck X/FXChain/DryWet` needs relearning on `Deck X/FX/DryWet` (expected).
  dropped 2026-11-14: a note that this is expected, not a check.
