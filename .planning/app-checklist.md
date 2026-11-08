# In-app check list (written 2026-10-14, merged 2026-10-22, updated 2026-10-29)

Everything here is code that exists and has passing tests but that nobody has looked at in the running app or on hardware. Tick a box when it works. Write what you saw next to anything that doesn't.

The 2026-10-22 merge added the Browse/Library/Edit review, the Clock row rework, the Super Knob / Metaknob review, the FX review and the sources review, plus everything else since 2026-10-17. It rewrote items the later work had made stale (the pair list, Metaknob "link modes", the crossfader's place, the Maps tab, the open gaps that have since been closed) and dropped three that no longer apply: the inline picker, the XF+CLK pair, and *Retarget Metaknob*.

Before starting: a build from current `main`, a fresh run, and a note of the screen size (min supported 1280x720). Do the whole list once in the dark theme, then the Light theme section. The title bar's `GPU buffers: n (MB)` readout is the quickest leak check.

---

## 1. Library and browser

Library tabs are now Sources, FX, Transitions, Queues, Macros.

- [x] Ctrl+F opens the Library on the current tab; Esc goes back to where you were (from Edit or a pair view: the same view, with the same slot still bound).
  !!needs ESC pressed twice. first time exits text box, second closes (could be by design)
- [x] Sources, FX and Transitions tabs: list scrolls, search works, typing a letter while the list has the keyboard starts a search (shortcut keys such as `Q` keep their shortcut). !!tooltip on transitions: "add to a/b queue" should be "add to transitions queue" right?
- [x] Rows: no empty box on stock items, long names end in "..." with the full name on hover, `hyperspace_crash_zoom` reads "Hyperspace crash zoom", heavy shaders show a muted gauge with an explanation on hover.
- [x] Loaded-where chips: `A`/`B`/`BG`/`PV`/`M` on sources; `A2`/`M1` on effects; a deck playing a saved preset marks the preset row, not the generator; many slots collapse to `×n` with the full list on hover. !!loading the same source to all 4 decks was fine up to A+B+BG  but then when I added PV also, I had 4 colored chips with no text in them. same with effects. no xn, nothing on hover
  !!auto: reproduced at 1280x720: Bloom in three slots draws three blank bars with no text. Your earlier note stands.
- [x] Recent folder in Sources, FX and Transitions: last 20 loads, newest first, still there after a restart.
- [x] Menus match across tabs (right-click works on FX and transition rows too): Rename..., Clone, Delete..., Add to playlist, **Reveal in File Manager**. !!we should make "add to playlist"" open a side menu with the playlists and the subfolders of playlists
- [x] Deleting a saved effect or transition also removes it from queues and playlists.
- [ ] Rename a saved FX single, an FX chain and a transition that are in a playlist and a queue: the playlist and queue rows still play, Favorites/Recent still list it. Rename a preset loaded on Deck A, then Save: it writes the renamed file (no old-name file reappears) and the preset's garden thumbnail is still shown.
  auto: FX single, chain and transition renames work and their playlists follow; preset rename keeps every field but name and tags. Renaming a loaded preset, then Save: no old-name file comes back. Playlists now keep short paths (fixed 2026-11-06). Left for you: queue rows playing, Favorites/Recent, the garden thumbnail.
- [ ] Delete a preset loaded on a deck: it drops out of Recent/Favorites; Save on that deck asks for a name. Delete an FX chain loaded on Master: Master's chain header no longer offers overwrite.
  auto: fixed 2026-11-06: Save wrote the file back without asking; it now asks for a name. Left for you: Recent/Favorites, the Master chain case.
- [ ] Folders: folder-plus button and right-click New/Rename/Delete Folder on Saved presets, Saved single FX, Saved chains, Saved transitions (not on stock sections); an empty folder shows in the tree; with FX and no saved section selected the button is greyed with a tooltip.
  auto: New, Rename and Delete Folder on Saved presets, empty folder shows, no menu on stock sections, FX button greyed with its tooltip. Saved FX, chains and transitions not run.
- [ ] Drag rows (one, and a multi-selection) onto a folder, and use Move to: they move, playlists/queues still play them, a loaded deck still saves back. Moving onto a same-named item asks Keep both / Skip.
  auto: one row by drag and by Move to: file moves, playlist follows. Same name asks Keep both / Skip / Cancel. Left for you: multi-selection, a loaded deck saving back.
- [ ] Drag a folder onto another folder and onto its section; renaming a folder you're inside keeps it selected; deleting it selects its parent. Delete Folder shows the item count and the playlists/queues they're in.
  auto: folder onto folder moves it and the playlist follows; Delete Folder shows the item count and the playlist. Left for you: dropping on a section, selection after rename and delete.
- [x] Delete (file or folder) lands in the system trash on this desktop (Linux: `gio trash`); the dialog says so.
  auto: on a real disk, file and folder both land in the trash, and the dialog says so. Where the trash refuses (seen on /tmp), the file still goes for good, but now a toast says so and the dialog says "where the drive has one".
- [ ] Save As (deck, FX slot, chain, transition): the Folder dropdown starts in the loaded item's folder, else the selected tree folder, else top level, and the file lands where it says.
  auto: deck Save As starts in the loaded preset's folder and lands there; chain and transition Save As write the file. Left for you: FX slot, the dropdown on chain and transition.
- [ ] Tags group (folded at first) on Sources, FX and Transitions: counts right, picking a tag lists its items, stock categories don't show. Add Tag on one row and on a multi-selection (existing-tag buttons fill the field), Remove Tag submenu, drag rows onto a tag, Rename Tag / Delete Tag on a tag row.
  auto: Add Tag on a multi-selection, the Remove Tag submenu, a drag onto a tag, Rename Tag and Delete Tag all change the right files. Left for you: existing-tag buttons, stock categories hidden.
- [ ] Tag a preset loaded on a deck, then Ctrl+S: the new tags survive. Star a saved single FX and a chain: ★ in the list, listed under Favorites; slot ◀ / ▶ still steps stock filters only.
  auto: the new tag survives Ctrl+S. Left for you: stars and slot stepping.
- [ ] Missing items: move a preset in the file manager, open a playlist that has it: ⚠ row with Find (pick the moved file), Locate..., Remove; Find all fixes the unique ones and the queue/other playlists follow.
  auto: the ⚠ line is above the rows, Remove works, Find all repoints a moved preset. Left for you: Find, Locate.
- [ ] Playlist folders on all three tabs: New Folder on Playlists, drag a playlist onto a folder (stays selected), rename/delete/nest folders.
  auto: the Playlists right-click menu offers New Folder; with a playlist selected the folder-plus button now makes the folder among playlists (fixed 2026-11-06). The Playlists heading itself isn't selectable. Rest not run.
- [x] Enter runs the row's default action on every tab; Delete follows Preferences > Shortcuts (Backspace works too); Alt+click a Sources row previews it on PV.
  auto: Enter on all three tabs, Delete then Esc keeps the item, Backspace then Enter deletes it, Alt+click loads on PV.
- [x] Rename, Delete, New Playlist and Export dialogs: field focused, Enter confirms, Esc cancels without leaving the Library. Esc that closes a menu doesn't also go back.
  auto: focus and Enter on all five; Esc cancels and stays in the Library on Rename, Delete, New Playlist and Export; Esc on a menu only closes it.
- [ ] Queues tab: an empty queue says how to fill it, five matching button strips (FX queues' Auto greyed), Export / Clear under each column's ⋮, no dead play button on the FX queues. The controller steps the columns left to right.
  auto: empty text, five strips and no dead play button on the FX queues all seen. Left for you: Export / Clear under the ⋮, the controller.
- [ ] Queues under the browser (Library maximized, Sources/FX/Transitions tabs): both halves usable at 1280x720; the divider drags, clamps, double-click resets, the ratio and the rows-icon toggle survive a restart; toggle off looks like before; Perform view's Library unchanged.
- [ ] Drag a source between two A/B queue items: lands at the line, the playing marker stays on its row. Drag it over the FX queues: dimmed, no drop. Drag a mixed selection: right ones go in, toast counts the skipped. Drag a playlist from the tree into a queue: its items go in at the line.
  auto: lands at the line, FX queue refuses it, a dragged playlist puts its items in. Left for you: playing marker, the mixed-selection toast.
- [ ] Drag to the bottom edge of a long queue (and of the browser list/tree): it scrolls. `Q` / `Shift+Q` and drops flash the receiving queue's border (dark and light theme).
- [ ] Arrow keys / controller cursor on the split: tree, list, then the five queues; `Q` with the cursor in a queue does nothing.
- [ ] Queue edits undo: drag-reorder, row-menu Remove and Delete in each of the five queues, then Ctrl+Z: items, playing marker and shuffle order come back; Remove toasts what went. Drag a BG queue item down: it lands where the line was.
  auto: reorder, Remove, Delete and Clear each undo on all five queues, and a BG item dragged down lands at the line. Left for you: playing marker, shuffle order.
- [ ] Macros tab: macro banks and Perform pages are listed; loading one works.
  auto: tab opens with Banks and Pages, and says when nothing is saved. Loading not run.
- [x] Transitions view: the Q key queues.
  auto: Q queues.
- [ ] Double-click a preset in the queue: auto-fades and advances. Accept from MIDI/keyboard: same.
  auto: moves the position, loads the item, the crossfader moves. Left for you: Accept from MIDI or keyboard.
- [ ] Load a single `.lsdfx` into a full chain by double-click, by audition latch, and from the FX queue / FX playlist: one consistent placement rule.
- [ ] Drop targets: deck badge takes presets, stock sources, external video, `.lsdfxchain` and `.lsdfx`. A deck monitor takes presets and sources, not FX. Slot drop in Edit accepts the same drags as the slot cell.
  auto: deck badge takes presets, stock sources, chains and saved FX (a full chain says so), a slot takes a stock effect. A deck monitor refuses FX, as intended. Left for you: external video, Edit's slot drop.
- [x] A failed FX or transition load shows a toast.
  auto: a broken saved FX and a broken saved transition each show a toast.
- [ ] Queue jumps of more than one step from MIDI/CV (+3) move three items. The keyboard queue keys still step once per press (they now run separately from MIDI/CV).
- [ ] Unsaved-changes prompt: Enter saves, Esc cancels (Esc did nothing there before 2026-10-23), buttons the same width, "Don't ask again" says loads are undoable. Eject and Copy/Move/Swap are not undoable and the Preferences tooltip says so.
  auto: Enter saves and loads, Esc cancels. Left for you: button widths, wording, the Preferences tooltip.
- [ ] Closing Video Export while it waits on the unsaved-changes prompt does NOT start the export.
- [x] Click a preset then a source on the same deck within a blink: the last click wins.
  auto: preset then source one frame apart: the source wins.
- [ ] Save a transition from the Transitions row's Save, from Edit OUT > TRANS and from the Library.
- [ ] Star an FX in the Library: it's in your favorites, and the slot shortlist arrows land on it.
- [ ] Multi-select (2026-10-27) in Sources, FX and Transitions: Ctrl+click toggles, Shift+click ranges, Ctrl+A selects all shown rows (not while typing in search). Right-click inside the selection: Add N to queue / playlist, Delete N. `Q`/`Shift+Q` and Delete act on all. Drag several FX or transitions into a playlist and into their queues: all land, in order. A bound list (picking for a slot) stays single-select.
  auto: Ctrl+click, Shift+click, Ctrl+A, the menu, Q, Shift+Q, Delete and drags into queues. Fixed 2026-11-06: dragging a selection of FX or transitions took only one. Left for you: drags into playlists, bound list.

## 2. Slots bound in place, pair view and Edit

- [ ] With the rows on the current page, click a source name, a chain name, an FX slot and the transition name: nothing moves, the slot is outlined, the Library's tab line shows its name, buttons and ✕, the list border takes the row's colour.
  auto: source, chain and FX slot bind in place with nothing moving. Left for you: outline, tab line and border colour.
- [ ] Breathing (2026-10-28): the bound slot's outline (source badge, chain name, FX slot, transition name) breathes slowly and never vanishes; the Library tab-line name and the Edit view header sit in a row-coloured pill breathing in step with it. Easy to spot on a busy screen, not mistakable for the amber OSC-learn pulse, not annoying over a long browse. Paused binding: grey, no pill.
- [ ] In a bound list: a click applies (Ctrl+Z undoes), a double-click applies and returns to the plain Library; Esc, ✕ or clicking the slot again stops without applying; changing page stops.
  auto: a click applies, Ctrl+Z undoes, Esc stops, a double-click applies and returns to the plain Library, clicking the slot again stops without applying, changing page stops. Left for you: ✕.
- [ ] Deck monitors, the main output monitor and the Master `M` badge bind their default half (a deck's source, the Master FX chain).
  auto: a deck monitor binds its source, the `M` badge the Master FX chain. Left for you: the main output monitor.
- [x] Space or maximizing the Library pauses the binding; Esc or Space again resumes.
  auto: Space maximizes, Space again and Esc come back still bound.
- [ ] The pair view opens only for rows off the page (e.g. the BG monitor from the A/B page, or a Twister send to such a deck).
  auto: the BG monitor from the A/B page opens the pair; on-page rows bind in place. Left for you: a real Twister send.
- [ ] Pairs: each deck's SRC + FX; the Master pair is Transitions + Master FX and browses the Master FX chain until you touch the transition. MST + Clock pair only in Edit (Browse greyed with a tooltip).
- [ ] Pair view and Edit share one header line: Back, `Browse | Params`, target and its buttons, queue next-up; switching moves nothing. On the Transitions pair, Params opens the transition editor.
- [x] Trying sources on a deck with unsaved changes asks once while it stays bound; Ctrl+Z still undoes each load.
  auto: asks once, Enter answers, the next source doesn't ask, Ctrl+Z undoes each load.
- [ ] Saved presets, effects and transitions show as applied (row tint) in a bound list.
- [ ] Touching a controller knob doesn't pull the browser off the Queues or Macros tab unless it moves the binding.
- [ ] Learn inside a pair view drops to the 4-row layout (accepted for now). Learn finishes, state sane.
- [x] Page tabs close the pair view. Esc leaves it and drops the dock selection.
  auto: both.
- [ ] Scope, search and cursor memory are kept per kind (FX, then Sources, then back to FX).
- [ ] Badge, slot and monitor tooltips say the list below applies and a second click stops.
- [ ] Edit shows the open unit's pair of rows (deck SRC + FX; OUT: Transitions + Master FX; MST: MST + Clock). Switching SRC/FX doesn't redraw the rows. The lit gear marks the editor's row; the other row's gear moves the editor; the lit gear closes Edit.
- [ ] Edit side rail reads `MST OUT A B BG PV`, nothing clipped. MST has LEVELS and CTRL; OUT has FX and TRANS and returns to the last one. Gears: MST row → MST > LEVELS, Master FX → OUT > FX, Transitions → OUT > TRANS.
  auto: fixed 2026-11-06: an empty deck had no rail at all. All six buttons click. Left for you: labels and gears.
- [ ] Edit FX tab: one slot open at a time (the first filled slot opens first), collapsed slots show Dry/Wet, the SUPER KNOB & METAKNOBS block folds on its own chevron.
- [ ] Edit shows readable names ("Cell scale", the shader's LABEL); Properties is never empty when you open a deck or switch tabs; CTRL/TRANS labels in sentence case; the transition selector fits its name; Max isn't cut off in the binding editors.
- [x] Ctrl+Z after picking an FX and after picking a transition in Edit.
  auto: both undo through the real picker.
- [ ] Library loads, queues and macro changes are not undoable (as designed); nothing odd happens.
- [ ] Edit gear exits (Esc, gear, menu Close Edit, Ctrl+F) keep each row's sub-tab and selected cell. The Edit module survives a restart.

## 3. Perform rows, Transitions row and Clock row

- [ ] Two-line deck rows with the two-tone SRC/FX badge, readable at 1280x720. Switching modes changes content, never geometry.
  auto: readable at 1280x720 in the dark theme. Left for you: switching modes keeps the geometry.
- [ ] Button chrome / bevel looks fine on every button type.
- [ ] Knob captions carry whole readable names ("TRACKING JITTER"), "..." only when too narrow.
  auto: fixed 2026-11-06. Transitions row now reads `BURN SPREAD`, `GLOW INTEN...` at 1280x720. Other rows not all read.
- [ ] Transitions row: picker and gear on line 1; `[⋮] [Save] [◀] [▶] [A] [B] [AUTO] [time]` in equal cells on line 2; Save turns amber when changed; queue position in the ◀ ▶ tooltips.
  auto: layout right at 1280x720. Left for you: Save turning amber, queue position in the ◀ ▶ tooltips.
- [x] Transitions at rest (2026-10-27): with the crossfader fully on A (then B), switch through all 8 transitions: the picture does not change at all. Start a slow fade from rest on each: no jump on the first frame of the fade.
  auto: all 8 with music: frame hashes identical to Linear Crossfade on 330 of 330 frames, resting on A and on B. No jump in the first 0.5 s of a 6 s fade.
- [x] Cyber Datamosh at rest (2026-10-27 fix): select it, push the crossfader fully to B (and let an auto fade land on B): B's colours look normal, no red/blue smear. Same on A. Check on the web TV too if you test it.
  auto: identical frames fully on A and fully on B, fader set directly. An auto fade landing on B and the web TV not checked.
- [x] MIXER page order: Transitions, Master FX, Master MST, Clock. The crossfader is Transitions knob 1 (and the Mixer panel), not on the MST row. MST pill reads `MST`.
  auto: seen at 1280x720.
- [x] Clock row at 1280 px: tempo grid `MAN AUDIO LINK` over `TAP SNAP [-|+]`, BPM and beat dots beside it, LINK dim while off.
  auto: seen at 1280x720.
- [x] Tempo tracking (2026-11-04, clock source Audio): play techno, rock, reggae and drum and bass, and watch the BPM readout. It should lock in about 2 s and hold steady through a track, at the right tempo or an octave off. Try a low JACK buffer (64 or 128) if you can: the tempo should be the same as at 512, with no new xruns. Watch beat-synced LFOs for a jump when a track changes. Downbeat Auto should behave as before.
- [ ] Bar & Downbeat (2026-10-23, Preferences > Tempo & Sync): Beats per bar 2..8 changes the bar dots (still fit the cell at 8) and the Twister clock knob's orange flash; with Link on it also changes the quantum. SNAP: the cyan dot lands on the beat you pressed on, with a jump of at most half a bar; the first TAP of a run does the same, later taps leave the bar line alone. Downbeat Auto with the Audio Beat Tracker on real dance music: confidence rises over ~4-8 bars, the bar line moves onto the kick/bass accent once and stays; a breakdown does not wander it; SNAP then holds for 16 bars. Note which genres it fails on (flat four-on-the-floor stays undecided by design). Bar-length LFO/sequence jump once when the bar line moves, nothing else.
- [ ] Clock QUEUE block: `A/B | A/B FX | BG | BG FX | TRN` captions over ◀ ▶ pairs; play/pause on A/B, BG and TRN toggles auto-advance (lit while running); faint group tints show which go together.
  auto: captions and ◀ ▶ pairs fit. Left for you: play/pause behaviour.
- [ ] Clock RAND block at the right edge (randomization on): `A B ALL` over `BG PV`; Ctrl+Z undoes.
  auto: layout right. Left for you: Ctrl+Z after a Clock RAND press.
- [ ] Every Clock button is also on MST > CTRL; right-click Learn MIDI/OSC works from both. A MIDI CC drives the FX queue steps.
- [ ] Narrow the window: a block that doesn't fit is left out, nothing overlaps.
- [ ] FX slot rows (2026-10-27, Chain view part 1): in Preferences > MIDI Controls > Perform Pages make a page of `deck.A.fx1`, `deck.A.fx2`, `deck.A.fx3`, `deck.A.src`. Badges read FX1/FX2/FX3; each row's knob 1 is its slot (name + bypass), knobs 2-4 its parameters, whatever the Deck A FX row's focus. Left block: effect name (click = Browse that slot), `[⋮] [Wet] [◀ Px/y ▶]`; die + FX ON only on the FX1 row. Drop an effect on an FX2 badge: it lands in slot 2. Page arrows page only their own slot; focusing that slot on the FX row opens on the same page. Geometry matches the other rows at 1280x720. On a Twister: the three slot rows are three distinct shades.
- [ ] Chain view (2026-10-27): ⋮ menu of Deck A's FX row > Chain View: rows read `FX1`, `FX2`, `FX3`, `A SRC` over the Browse list (source row at the bottom since 2026-10-28), same knob size as Perform, nothing clipped at 1280x720. Touch a knob on FX2: the list targets slot 2. **Load** on the Browse header: row 4 becomes the load row (highlighted effect's name, → FX1/→ FX2/→ FX3, LIST); rows 1-3 don't move. Highlight an effect and click → FX3: it loads into slot 3 with the fade, Ctrl+Z undoes it, the list now targets slot 3. Load again brings the SRC row back. Chain toggle and Esc both leave in one press (Esc even with a slot focused on the FX row). Master: Chain View from the Master FX ⋮ menu shows Transitions at the bottom. A page tab click leaves Chain view.
  auto: rows read FX1, FX2, FX3, A SRC, nothing clipped at 1280x720. Chain View from the menu, Load, → FX3, Ctrl+Z and Esc all work with the mouse. A plain click on a list row also applies to the targeted slot. Left for you: knob touch retargeting, Master.
- [ ] Chain view on the Twister (2026-10-27): on A/B, shift + tap a Deck A SRC knob: Chain view opens, all 16 knobs move the four rows (FX1-3, then SRC on 13-16), FX rows in three shades. Right-bottom: Load on, knobs 13-15 glow in the slot colours (dark with nothing highlighted), knob 16's ring is the list position; turning knob 16 moves the list, tapping knob 14 loads the highlighted effect into slot 2 and knobs 5-8 then play it straight away. Right-bottom again: SRC row back. Bank buttons: Deck B's Chain view, Load state kept. Left-top or shift + tap on the SRC row (13-16) leaves. Does the source row at the bottom read OK?
- [x] Monitor drag onto another monitor: nine-choice menu (Move / Copy / Swap × Source / FX / Source + FX), each does what it says.
  auto: all nine actions do what they say, and all nine rows are on screen at 1280x720.

## 4. Sources (2026-10-22 review)

- [ ] Load a curated flame (garden): no `*`, Save not red. Load something else over it: no unsaved-changes prompt.
- [ ] Badge with a preset reads `curated-0001 · Fractal Flame` (source dimmed); a bare source shows just its name; narrow the window and the source part goes first. The tooltip always names both.
- [ ] Browse, saved presets: the info column starts with the source (`Fractal Flame · curated, flame`); hover shows "Plays: ...".
- [ ] Load a preset from `garden/` or `imported-flames/`, change a knob, Save: that file changes, and no new copy appears at the top of `presets/`. Save As with a new name goes to `presets/`.
  auto: Save on a preset in a subfolder writes that file with no new copy at the top. Left for you: Save As going to `presets/`.
- [ ] Missing source: rename a user shader that a saved preset uses, Rescan. The preset's row shows ⚠ "... missing" with a tooltip; loading it gives a toast and the deck keeps what it had. A session saved with that source restores the deck empty and lists it under missing items.
- [ ] Click the stock source of the preset you're playing: its defaults load. Click it again: nothing happens.
- [ ] SRC tab rows: Gain, **Deck Zoom**, **Deck Rotate**, then the source's own. On Reaction Diffusion / Escape Time the two Zooms are clearly different; 3D sources (KIFS, Gyroid) hide Deck Zoom / Rotate. Tooltips explain them.
  auto: row order right, and KIFS hides Deck Zoom / Rotate. Left for you: the two Zooms differ, tooltips.
- [ ] Set Deck Zoom, pick another source: it resets. Load a preset: it keeps its saved framing.
- [ ] Deck Zoom and Deck Rotate now move Isometric Voxels and Wave Ripples.
- [ ] MIDI-learn Gain on Deck A, change source several times: the mapping still drives Gain. A mapping saved before (path `Deck A/<Source>/Gain`) still works. MST > LEVELS shows only level faders.
- [x] Leak check: load ~30 sources and flame presets in a row on one deck (or run an A/B queue of flames for a while); `GPU buffers` in the title bar stays roughly flat.
- [ ] Linux, NVIDIA: run the app for an hour on your normal desktop; memory in a system monitor should stop climbing, and the frame rate should be the same as before. auto: 8 hours under Xvfb grew 900 MB to 5.8 GB, driver threaded optimizations; launchers now turn them off, flat over 40 min in a test run (not the launcher itself, not a real screen).
  auto: 90 loads of 10 heavy sources and 30 flames on Deck A: GPU buffers 25 at start, then moving between 30 and 42 by source, 33 at the end (249 MB). No growth.
- [x] Ctrl+Z after a source change brings the old one back (a flame or simulation restarts fresh; expected).
  auto: brings the old source back, after a die roll too.
- [ ] Eject empties the deck and keeps its FX chain; tooltip says so. Empty-deck card in Edit: one **Add Source** button that opens Browse.
  auto: FX chain kept, tooltip right, empty-deck card has one Add Source button. Left for you: clicking it.
- [ ] External video deck: Save greyed with a tooltip. Switch the deck to a generator: the stream is released (the sending app's receiver count drops, if it shows one).
- [ ] Preferences > Shader Locations: Add Folder and Rescan Now with a new `.fs` in it. No crash, the new source appears, its presets load, FX and transitions in use keep working.
- [x] A shader with a syntax error: red checkerboard on the deck, ⚠ "failed to compile" in Browse, the first compiler error on hover.
  auto: checkerboard, ⚠ in Browse and the compiler error on hover all seen with a broken test shader.
- [x] Two user shaders with the same file name: one loads, the log warns about the other.
  auto: one row in Browse, and the log says Skipping for the other.
- [ ] A shader that reads `iMouse` doesn't follow the cursor.
- [ ] Queue ◀ ▶ tooltips on the A and B rows name the off-air deck the item goes to. The die's tooltip lists what it randomizes.
  auto: `MIDI: CC -1` fixed 2026-11-06. Wording right on the B row, and the die lists what it rolls. A row not read.
- [x] Old presets and sessions still load (they carry the removed deck feedback and 3D-view values, which are now ignored).
  auto: 27 desktop presets from two points in git history load, and a session with the removed deck feedback and 3D-view values restores in a real run.
- [ ] On a packaged install over an older version: unedited bundled shaders are updated, an edited one is kept (log line). Needs a real install to test.
  auto: real Linux zips, v0.9.162 then v0.9.164 unzipped over it: runs clean, your preset stays. Fixed 2026-11-07: the zip's own copy overwrote an edited shader. The fix only takes effect from the next release's zip; not retested on a built zip.

## 5. Off-air decks (2026-10-22)

- [ ] Crossfade fully to A: B's monitor runs at half rate; frame time drops with two heavy sources. Fade back: no hitch.
  auto: B's monitor changed on 60 of 120 frames with the fader on A, and every frame at centre and on B. Frame time, Nebula on A and Apollonian on B: 31-34 ms on A, 35-42 ms at centre, 39 ms on A with half rate off, with another run going. Left for you: no hitch on the way back.
- [ ] A deck at level 0, and the master at 0: half rate. BG at level 0: half rate.
- [ ] PV renders at half size and still looks fine in its monitor; send PV out over Spout/Syphon/PipeWire and it goes to full size and full rate.
- [ ] Linux, PipeWire video (rewritten 2026-11-07): turn on Master in Preferences > Video & Display > Live Video Sharing and read it in OBS (try Video Capture Device (PipeWire) and Screen Capture) or `gst-launch-1.0 pipewiresrc target-object=LiquidLSD-MASTER ! videoconvert ! autovideosink`; and load a camera or another app's stream from External video. auto: both directions verified with GStreamer in a private PipeWire, picture upright; OBS and real cameras untested.
- [ ] Preferences > General **Off-air decks at half frame rate** off: everything renders every frame.
- [ ] A simulation (Reaction Diffusion, Lenia) cued off-air runs at half speed, then normal speed on air. Decide whether that's acceptable.

## 6. FX chains (2026-10-22 review)

- [ ] `FX ON` (lit = on) instead of BYPASS; its state survives a restart.
- [ ] No chain shows the unsaved `•` at launch. It lights for a slot, parameter, Dry/Wet, link or Metaknob-target change, not for turning the Super Knob or a Metaknob.
  auto: fixed 2026-11-06: every chain showed unsaved at launch, on a fresh install too. Now clean on both, a parameter change marks it, the Super Knob doesn't. Left for you: slot, link and target changes, Metaknob moves.
- [x] Revert to Saved undoes parameter tweaks.
  auto: cleans a parameter tweak.
- [ ] One chain menu on the FX row ⋮ and the Edit tab: Focus, Save As, Revert to Saved, Clear All Slots (a new Untitled chain), Copy / Paste Chain. Save As starts in the chain's folder.
  auto: the FX row ⋮ menu has every item. Left for you: the Edit tab's menu, Save As folder.
- [x] Saving over a chain keeps its tags; the chain takes the saved name. A failed save (read-only file) shows a message and stays unsaved.
  auto: tags kept, chain takes the saved name, a read-only file shows a message and stays unsaved.
- [ ] Ctrl+Z undoes slot drags (swap / Ctrl copy), drops, Paste, Reset Parameters, Clear, Revert, Library loads and the FX browser's right-click "Load to ...".
  auto: swap, Ctrl copy, Paste, Clear, a Library chain load, Revert and Reset Parameters all undo. Left for you: the FX browser's Load to (the menu lists all five targets).
- [ ] Slot name tooltip shows the shortlist position (`★ 3/8`, `Blur 4/12`); stepping off a category's end lands in favorites.
- [x] A plain mouse wheel over a slot scrolls; Ctrl + wheel steps the effect.
  auto: the wheel leaves the effect, Ctrl + wheel steps it.
- [ ] Middle-click: Super Knob does nothing; a focused parameter's knob goes to its default.
- [x] A chain at Chain Dry/Wet below 100% shows `Wet 40%` in its name; at 0% the name is dimmed.
  auto: seen, `Wet 40%` and dimmed at 0%.
- [ ] Feedback / trail effects don't flash an old frame when a slot comes back from bypass, 0% wet or FX off.
- [ ] Eject leaves the deck's FX chain untouched; Clear with FX refreshes the FX row's knobs.
  auto: Eject keeps the chain. Left for you: Clear with FX refreshing the knobs.
- [ ] Replace the focused effect with one that has fewer parameters: the focus page follows, knobs 2-4 not blank.
- [ ] FX queue arrow tooltips name the deck and say A and B share one position.
- [ ] A MIDI/OSC mapping to `Deck X/FXChain/DryWet` needs relearning on `Deck X/FX/DryWet` (expected).

## 7. Super Knob and Metaknobs

- [ ] A linked slot's knob 2-4 moves its Metaknob (not dead); in Focus Mode a linked slot's knob 1 works. auto (round 5): dragging a linked slot's Perform knob moves its Metaknob. Focus Mode knob 1 not tried.
- [ ] Preferences: **Super Knob response** Relative (default): linked slots at 20% and 90% both rise, stay apart, reach 100% / 0% together. Pickup: a slot waits for the Super Knob, amber link button with both values in its tooltip.
  auto: Relative: 20% and 90% both rise, stay apart, reach 100% and 0% together. Pickup: each waits until the knob reaches it. Left for you: amber link button and its tooltip.
- [ ] Moving a linked Metaknob by hand (Edit row, MIDI, editing a target) isn't yanked back by the Super Knob.
  auto: a Metaknob set directly isn't yanked back. Left for you: Edit row, MIDI, editing a target.
- [ ] Default FX chain linking is Linked / Unlinked (an old Auto setting reads Linked).
- [ ] Metaknob editor: add targets, try each zone (Full, First Half, Second Half, Triangle, Bipolar), Invert Direction and each curve; Remove Target. A parameter's row menu in Edit has one **Metaknob Target** check. *Replace all links* is gone.
  auto: Add Target, ± Invert and the trash work, and the row menu's one Metaknob Target check toggles (fixed 2026-11-07: menus on lower rows were cut off, hiding it). Left for you: each zone and curve.
- [ ] Remove every target: the knob reads `NO TARGET` (slot tooltip explains), and it's still empty after save and reload (not Dry/Wet).
  !!auto: removing the last target works and the editor reads `Metaknob targets (0)`, but the knob keeps the effect's name. `NO TARGET` was removed from the code 2026-10-22 and the slot tooltip explains instead, so this item is out of date. Saving and reloading not run.
- [ ] A target naming a missing parameter shows `(missing)` and drives nothing. auto (round 5): a chain naming a missing target loads, Edit shows `no_such_param (missing)`, moving the Metaknob doesn't crash.
- [ ] A Metaknob on Dry/Wet owns it: the focus header's Wet slider moves the Metaknob; macro/MIDI/OSC writes are suspended.
- [ ] Focus from Edit's SUPER KNOB & METAKNOBS block focuses the chain everywhere; the block shows knobs 2-4, page arrows and Group Mode.
- [x] Swap effects between slots: each slot keeps its own link setting.
  auto: each slot keeps its link setting.
- [ ] Tap on the Super Knob's switch does nothing; Shift + tap is Chain Link.
- [ ] Rate / speed / decay parameters respond exponentially on focus knobs 2-4; turning focus on doesn't change a value. ⟲ and the Twister tap land on the default.
- [ ] A Metaknob with several targets starts where they're closest to their defaults when a slot loads or resets.
- [ ] Save an FX slot and a chain, reload: targets, zones and knob order restored. Knob layout right on Perform and in the pair view. auto (round 5): added a target, inverted one, saved the chain, loaded another, reloaded: targets, ranges and invert are the same. Knob order and the Perform layout are yours.
- [ ] ISF auto-bind: load a few filters; Super Knob and Metaknob do something sensible. auto (round 5): ten filters loaded one by one each got at least one Metaknob target. "Sensible" is yours.
- [ ] *Remove Saved Default for (effect)* and *Save as Default* explain what they do.

## 8. Macros, learn and locks

- [ ] Arm macro Learn, OSC Learn and MIDI Learn at once; Esc cancels all three. MIDI Learn on a parameter, and Learn into a controller profile command, still capture the next control you move (MIDI handling moved earlier in the frame, 2026-10-23).
  auto: OSC Learn arms from the row menu and Esc cancels it. Left for you: macro and MIDI Learn at once, capture from a real control.
- [ ] A MIDI/OSC mapping made before a knob binding is suspended while the knob drives the target, flagged in both tables. auto (round 5): MIDI mapping does nothing while a macro knob drives the parameter, the reason names the knob, and it works again once the binding goes. Left for you: the caption in the MIDI table (it is further down Preferences than the script scrolled), and OSC.
- [ ] Range sliders lock when `lfoMin/lfoMax` is bound; Randomize min/max inputs too. auto (round 5): with LFO Min bound to a knob, dragging its handle and typing into its box leave it alone. The lock caption showed `[lfoMin]`; now `Min` (also in the popup and Perform strip).
- [ ] Knob- or Metaknob-driven Value: dragging, scrolling or typing in Initial Range moves the knob (through its curve), other targets follow; a Metaknob on a macro knob passes it up. Lobes, Recipe, Max Points and the grid Value cell stay locked. auto (round 5): dragging Initial Range on a knob-driven value moves the knob, and the value follows it.
- [ ] Metaknob-driven rows show the cyan bar, tint, `[K1]` badge and "Linked to its Metaknob" caption; Randomize off.
- [ ] FX-bank bindings are read-only in Properties and slider popups. auto (round 5): screenshot shows "Driven by the FX chain; edit the slot's Metaknob targets instead" and no range editor.
- [ ] Select a knob by clicking a parameter, then click the knob: one selection.
- [ ] Bound Min/Max labels and Beat Division labels open the popup. auto (round 5): the bound Min label opens the binding popup. Beat Division label not reached (row below the fold), yours.
- [ ] Type a value into a knob with curve, invert and zone set: the result respects them. auto (round 5): typing 20 into a knob with Exponential, Invert and First Half gives 20%, and turning the knob to the same spot gives the same value.
- [ ] Macro-bank import onto another deck: paths remap, `Mixer/...` stays. auto (round 5): Save bank from Deck A, Apply to Deck B: deck paths become `Deck B/...`, `Mixer/crossfade` stays.
- [ ] Click a chip on a MASTER knob bound to a deck parameter: selects sensibly.
- [ ] Reorder or delete a modulator: bindings stay on the right one. auto (round 5): deleting the LFO leaves a knob bound to the audio modulator's Depth on that modulator. Reorder not tried.

## 9. Modulators and step sequencer

- [ ] Sequencer is on by default (an old settings file turns it on once).
  auto: Enable Step Sequencer is on with default preferences. Old settings file not tried.
- [ ] Bar editor: paint (5% snap, Shift free), scroll to nudge, double-click to type, right/middle-click resets; playing step outlined; 16 per row.
  auto: a drag paints, and one Ctrl+Z undoes the whole drag. Left for you: scroll, double-click, Shift, the outline.
- [ ] Toolbar: Randomize, Invert, Shift < >, Copy/Paste between sequencers, Fill..., Euclid... (Rotate and preview), Clear. Snap menu. Keyboard editing with the pointer over the bars.
  auto: fixed 2026-11-06: the toolbar never wrapped, so Clear was clipped and Snap off the edge at 1280x720. Randomize, Invert, Shift, Copy, Paste, Clear and Euclid's Apply work, Ctrl+Z undoes. Left for you: Fill, Snap, Euclid's Rotate, keyboard.
- [ ] Length 1-32 (odd lengths), Phase, Offset, Step Hold (randomize); Glide Curve greys when nothing glides; output trace and live dot; ghost steps past the length; bars dim when bypassed.
- [ ] Auto toggle: randomization re-rolls the pattern, morph slides between patterns.
- [ ] Changing Clock Unit converts the step duration.
- [ ] After the app has run 15+ minutes: sweep a Time-clocked LFO's period (and a sequencer's Time interval) by hand and from a macro. It speeds up or slows down, never jumps. A preset morph between two different LFO periods glides too (2026-10-23).
- [ ] Ctrl+Z undoes sequencer, LFO, Audio and MIDI CC edits (one step per drag).
  auto: sequencer and LFO edits undo one step at a time. Fixed 2026-11-07: one Ctrl+Z undid two steps in the pair view and Chain view, and Reset Parameter to Default couldn't be undone. Left for you: Audio, MIDI CC.

## 9b. Audio response (2026-10-25)

- [ ] Preferences > Audio Hardware (reworked 2026-10-28): no Auto Gain checkbox or Headroom slider any more; auto gain is always on. Feed quiet and loud music: the "Auto gain amp/bass/mid/high" readout under Audio Response adapts within a few seconds, and silence does not boost noise. Input Gain is now **Input Trim**, under Channel Routing; turning it down does not calm the picture for long. Turn the input (or Input Trim) right down with music playing: the Sync State goes SILENT and an amber "Input too quiet for beat detection" line appears; it should not appear with nothing playing. If you had changed AGC Headroom before, Audio Response was adjusted once so the picture moves as before.
  auto: measured with -20 dB and silence: silence stays at zero, and music after silence levels in at once. Left for you: the readout, Input Trim, the amber line.
- [ ] Breakdowns and drops (2026-10-28, auto gain against a long-term level frozen in breakdowns): play a track with a clear breakdown (low end drops out for 15 s or more). Bass-driven motion goes calm within a few seconds and stays calm the whole breakdown (watch the Bass scope: it used to climb back within ~12 s); mids may stay lively. The drop hits as hard as before the breakdown. Steady music should move about as much as before this change. Swap to a much quieter track: it reads quiet for up to a minute, then normal; a much louder one is normal within ~5 s. Pause the music a few seconds and restart at a different level: it levels in at once.
  auto: on synthesized music, bass 0.47 in the groove, 0.06 within 3 s of the breakdown and steady to its end (30 s), peak 1.0 before and after the drop. 20 dB quieter reads quiet for about 60 s, then normal. Real music not measured.
- [ ] Section modulators (2026-10-28): on a parameter's Audio tab, the detection mode has a third button, **Section**, with a Signal combo (Energy, the four band energies, Drop) and no Response Profile. (The three buttons fit at 1280x720: confirmed 2026-10-28.) **Energy**: give a visible parameter a wide range. During steady music it rests mid-range (nothing moves for the first ~20 s), sinks toward Min soon after a breakdown starts and stays there until the drop, and rises when a louder song comes in. **Drop**: on a track with a breakdown of 4 s or more, the parameter jumps to Max at the drop and falls back over 4 bars. Judge whether the hit lands on the beat the bass came back on, or visibly late or early. First test (one song, 107 bpm, before the 2026-10-28 fix) was 2-3 beats late; drops are now called about half a second after the bass returns, so expect at most about a beat late. The Sections line under the auto gain readout shows the state, the level against usual, the 3 s breakdown timer and the drop count. A breakdown that sits around half the usual level (the Skrillex test) is too mild to count, by design for now. The Preferences > Audio Hardware scopes show both under the band scopes. Changing Audio Response must not change either. Copy/paste an Audio cell holding a section modulator keeps it.
  auto: Drop is called 0.57 s after the bass returns, about 1.2 beats at 128 BPM, both times, with no false drops in the groove. Energy sits near 0 and reaches -1 in the breakdown. Audio Response doesn't change either. Left for you: how it looks.
- [x] Transient hits (2026-11-04, replaces the old flux signal under the same IDs): play sustained-bass music, then four-on-the-floor, on a few stock sources with kick responses. Also try rock and jazz: snares and hats should read about as strongly as on electronic music (hit sizing, TV v25). Hits should look punchier and less jittery. A held bass note should not pump anything Transient-driven, while a kick on top of it should. **Every stock Transient response changes here, with less motion on average: about 40-50% in the bass and less in the higher bands**: note any look that now feels too calm. At a low JACK buffer (64 or 128) kicks should look the same as at 512.
- [x] Hi-Mid band (2026-11-04): on a parameter's Audio tab, Frequency Band lists Hi-Mid (Crack), in Continuous and Transient. A snare or a bright vocal moves it more than the kick does. Its tooltip gives about 2-5 kHz. The Audio Hardware scopes show Hi-Mid Band and Crack Hits, and the panel still fits at 1280x720 with the extra rows.
- [x] Band energy (2026-11-04): in Section mode, Signal lists Bass, Mid, Hi-Mid and High Energy. Each needs about 20 s of music. Hats coming in lifts High Energy alone, and the bass leaving drops Bass Energy alone. An empty band sits mid-range. Four new scopes show them in Audio Hardware. Save and reload a preset that uses one.
- [ ] Modulation tour (2026-11-04): the button it points at is now `Transient (Hits)`, so the published video shows the old label until it is re-rendered.
- [ ] Audio slots (2026-10-28): the Audio tab shows Audio 1 and Audio 2 open, one above the other, each with a power button and trash, and no Enable button. With both off, turn on Audio 2: Audio 1 stays off and nothing collapses. Turn Audio 1 on and off: Audio 2 is untouched. Put both on the same band and change Audio 2's range: Audio 1 must not change. Trash Audio 1 while Audio 2 is on: Audio 2 stays in the lower slot. Reload the preset: both slots keep their state.
- [ ] Audio Invert (2026-10-28): on a parameter's Audio tab, an **Invert** toggle sits just above Modulation Range in all three detection modes. Put Punchy bass on Brightness with MUL and a negative Min, then invert: the picture should dim on each kick and ease back, and sit at full brightness in silence. Inverted Section Energy should rise in a breakdown. The ghost trace in the scope flips too. Save, reload and copy/paste the cell: Invert must survive. Check the button fits at 1280x720.
- [ ] Stock section responses (2026-10-28): every stock source now runs one Energy and one Drop modulator (named "breakdown ..." and "drop ..." in the parameter's list; the full set is in RELEASE_NOTES.md). Play a track with a clear breakdown and drop on a few sources of each kind and judge by eye: does the breakdown look read as the music receding (colour drains on most fractals; Isometric Voxels flattens, Thin Film calms, Nebula thins, Tunnel fogs, Mandelbox pulls back) without looking broken, and is the drop hit big enough to notice but not ugly (fly-through surges, punch-ins, Celestial shockwave, Escape Time lurch, Glitch crush)? Specific risks: Reaction-Diffusion's Regime moves in a breakdown, so the pattern may re-form as another species (wanted) or keep re-forming in normal playing (not wanted); Chladni's Frequency N drifts a little during normal playing; Kali Set's Iterations (cost) rise slightly in loud passages; Cyclic CA, Lenia and Reaction-Diffusion reseed once on the drop (Lenia no longer reseeds on hard kicks). Fluid Ink: the ink clears in a breakdown and is stirred on the drop. During steady music, nothing about any source should look different from before.
- [ ] Effect drop hits (2026-10-28): with a stock FX chain on a stock source, play into a drop. Bloom, Anamorphic Streak, Radial Blur (The Drop Weapon chain), Kaleidoscope, Pinch/Bulge and Vortex Swirl hit on the drop together with the source's own hit: judge whether the pile-up reads as one big moment or as mush, and which of the six should go back to bypassed. With an effect's main knob at zero it stays off on the drop. In a breakdown, Bloom spreads wider and Fluid Smear melts further. Switch on a bypassed one (RGB Split "drop split", VHS Glitch "drop tape wreck") and check it is worth having.
- [ ] Audio Response slider (2026-10-27, replaces Reactivity; 0-200%, saved): at 0% the band signals stop moving (LFOs, beat-synced motion and the Energy and Drop section signals keep going), 100% as tuned, 200% livelier with loud passages pinned at the top; a modulator you add yourself follows it too; beat detection/BPM unaffected; the band meters show the scaled signals.
  auto: 0% zeroes the band signals and 200% lifts bass from 0.47 to 0.69. Energy and Drop still run at 0%: kept as is 2026-11-06, tooltip and guide say so. Left for you: BPM unaffected, which the offline feed can't measure.
- [ ] Fractal Flame recipe (2026-10-27): load the source on a deck with music; Breathe pulses on bass, Warp follows mids, Spin sways over 32 beats, Magnify pushes on the kick, Exposure flashes (check it does not wash out or blink), Brightness lifts. Run a morph (Rand/die) and a Garden preset: the responses should not fight the morph; note that a preset with its own saved parameters replaces the recipe. Mandala recipe (2026-10-27): L1 amplitude jiggle, L2 8-beat sine, L3 16-beat sample-and-hold with 50% hold, L4 6-beat ramp; Hue Offset 1-beat cycle (check it is a smooth cycle, not strobing); Recipe Select every 32 beats, Lobes every 256 (4..~15). Judge the L depths. Batch 4 source recipes (2026-10-27: celestial_engine, chladni_cymatics, escape_time, girih_stars, gyroid_hyperspace, hyper_slice, isometric_voxels, quasicrystal, thin_film): same judging. Chladni's FrequencyM slides a fraction of a mode (check it stays a clean pattern); Hyper Slice SliceOffset sweep. Batch 3 (2026-10-26: reaction_diffusion, lenia, cyclic_ca, nebula_volume): on the sims check that the active responses never kill the pattern (reaction_diffusion Feed breathing, lenia over several minutes), and try the bypassed Reseed (drop) alternates: one reseed per hard kick, none at low Audio Response. Batch 2 (2026-10-26: mandelbulb, mandelbox, kifs, pseudo_kleinian, hyperbolic_tiling, icosa_h3): same judging as batch 1. Batch 1 (apollonian, dynamic_spiral, tunnel_scenes, kali_set, aurora_ribbons, domain_warp_fluid, voronoi_cells, wave_ripples, glitch_blocks, metaballs, halftone_matrix, video_feedback): load each on a deck with music. Judge by eye: is the active response tasteful, are depths too high or low, do the bypassed alternates (AUD/LFO cells) turn on cleanly and look good? Note changes per source.
- [ ] FX responses, batch 1 (2026-10-27: bloom, color_levels, retro_crt, luma_displace, fluid_smear, wave_displace, mirror_sphere, kaleidoscope, feedback, gradient_map, faceted_glass, rgb_split). The starter chains do NOT carry them (chains store their own slot parameters), so add each effect to an empty slot from the FX Library, with music. With the Metaknob at zero nothing should move (Luma Displace, Fluid Smear, Wave Displace, Feedback, RGB Split load at zero); turn it up and the hits should grow with it (MUL on the Metaknob input: Bloom, Luma Displace, Wave Displace, Mirror Sphere, Faceted Glass, RGB Split). Watch for: Feedback kick push (trails jump outward; too much?), Fluid Smear flow quickening when loud (gentle enough?), Gradient Map cycling only when loud (stops in quiet passages: good or odd?), Color Levels saturation swell and Retro CRT kick flare on the Master chain (too busy for a master grade?), Kaleidoscope kick zoom. Try the bypassed alternates (AUD/LFO cells). Also: Save as Default on one of them, reload it (yours wins), Remove Saved Default (shipped response back); the Vortex Swirl transition shows its own transition response (Rotations on bass), never the effect's.
- [ ] FX responses, batch 2 (2026-10-27: 3d_elevation, anamorphic_streak, directional_blur, halftone, invert, luma_key, neon_edge, pinch_bulge, pixelate, polar_tunnel, radial_blur, thermal_scanner, vhs_glitch, video_strobe, vortex_swirl). Same method and Metaknob-at-zero check as batch 1. Watch for: Pixelate (kick makes 2-pixel blocks even at size 1, the off position: acceptable or bypass it?), Radial Blur and VHS kick hits (too violent?), Pinch/Bulge bass bulge in both directions, Polar Tunnel kick push (and its wrap seam now sits at the bottom instead of the left: check the tunnel looks balanced left to right; same for Kaleidoscope and Feedback Kaleido at 3 or 5 segments, and Chladni Cymatics with Plate Shape at Circular: no seam on the left), Thermal edge heat. Invert, Luma Key and Video Strobe have only bypassed responses: switch each on once and decide whether one should run by default. After both batches are approved: rebuild all stock FX chains so they carry the responses (agreed 2026-10-27).
- [ ] Transition responses (2026-10-27: luminous_flash, film_burn, cyber_datamosh, kinetic_zoom, liquid_displacement, noise_dissolve, vortex_swirl; linear_crossfade has none). With music playing, rest on A, then on B, on every transition: nothing may move. Then slow manual fades (knob 1 or the Mixer slider) on each: no pop on the first frame of the fade (watch Luminous Flash and Film Burn, and the Inferno Film Burn preset, Burn Spread now 0.45), and judge the running responses mid-fade: Flash punching on the kick, Burn ember flare, Datamosh hat tears and kick colour shear, Zoom kick punch and loudness streak, Liquid bass melt and high dispersion, Dissolve kick fringe and loudness haze, Vortex bass surge and kick fringe. Too busy, too subtle? Try the bypassed alternates (Datamosh Blockiness flips every beat; Noise Scale on mids may boil). Cyber Datamosh Blockiness now reads 0-100 and looks the same as before at the old default. The stock presets (AutoVJ, Festival Elite) carry the same responses: your library still has the old copies, so delete `library/transitions/*.lsdtrans` (or Restore Factory Presets) first. A fresh stock transition shows no `*` and Save stays grey; change a value and it turns amber. Save as Default / Remove Saved Default in the Transitions row ⋮ and the Mixer TRANS kebab: re-pick the transition in the same session and your values come back (no restart); Remove resets the running one to the shipped response.
  auto: fully mid-fade, Luminous Flash clips to white, so colour and spread can't be seen at the exact midpoint. Judge colour either side of it.

## 9c. Random by default (2026-10-27)

Preferences: randomization on. For each source: pick it fresh from Browse (not a saved preset, which keeps its own values), press the deck die 5-10 times, then turn the deck's Rand knob slowly end to end a few times. Judge: does every roll look good, are there duds (blank, mush, too dark, broken), is the variety too timid or too wild? Note per source what to widen, narrow or drop. Ranges are in `src/main/resources/stock_defaults/sources/<id>.json` (`baseMin`/`baseMax`).

- [ ] Randomization is on by default since 2026-11-06 (an old settings file turns it on once; turning it off afterwards sticks). auto: fresh run and unit test confirm; check it suits you.
- [ ] Mechanics, on any source (KIFS is a good one): a roll moves the four source knobs on screen (and the Twister rings), and turning a knob afterwards picks up from the rolled value without a jump. Stepped values (KIFS Fractal Type, Color Mode) switch once, halfway through a Rand sweep, never flicker through the types in between. Ctrl+Z after a roll brings the previous look and knob positions back (Fractal Flame excepted: its values stay). The Initial Range slider and its dice now show on a knob-driven parameter (open its Properties); dragging the range there works.
  auto: a die roll and Ctrl+Z through the real buttons work on KIFS. Left for you: knob pickup, stepped values during a Rand sweep.
- [ ] Second pass (2026-10-27, from your first look): Cyclic CA rolls (every roll alive? a roll restarts from noise and organises within a second or two), Gyroid (slower, wider, more varied), Mandala (recipe changes on a roll), Mandelbox/Mandelbulb brightness, Fractal Flame picked from Browse (was black), Reaction Diffusion (fewer flooded solid-colour rolls). Fluid Ink: a roll changes the colour of new ink only, so the change shows over a few seconds as old ink fades; say if that is too slow.
  !!auto: 1010 rolls, all sheets in .planning/auto-verify-2026-11-06/round2/sheets/. Cyclic CA still has noise-only rolls.
- [ ] Batch 1: apollonian, aurora_ribbons, celestial_engine, chladni_cymatics, cyclic_ca, domain_warp_fluid, dynamic_spiral, escape_time, fluid_ink, girih_stars. Watch: Cyclic CA with many states and a high threshold may freeze (no spirals); Chladni Invert and Plate Shape full range; Escape Time Julia angle full circle (some constants are mostly empty).
  auto: Domain Warp washed out 6 of 20, lower Gloss 2026-11-06: 6 of 40, 5 of them palette 1. Dynamic Spiral flags are false: it is sparse by design. See sheets.
- [ ] Batch 2: glitch_blocks, gyroid_hyperspace, halftone_matrix, hyperbolic_tiling, hyper_slice, icosa_h3, isometric_voxels, kali_set, kifs, lenia. Watch: Hyperbolic P/Q (the shader raises Q when the pair is not hyperbolic, so some rolls repeat); Lenia blobs dying out (kick reseeds them); Hyper Slice and Icosa camera angles.
  auto: KIFS Storm white at low Scale, fixed 2026-11-06 with a higher minimum: 3 of 40. Icosa H3's flags are false: a small object by design, though alike on every roll. KIFS 3 of 20 specks. See sheets.
- [ ] Batch 3: mandelbox, mandelbulb, metaballs, nebula_volume, pseudo_kleinian, quasicrystal, reaction_diffusion, thin_film, tunnel_scenes, video_feedback, voronoi_cells, wave_ripples. Watch: Mandelbox stays in the positive-scale family (crossing zero jumps); Reaction Diffusion regimes that blank out; Video Feedback whiteout or blackout from decay/zoom; Tunnel side counts.
  auto: Quasicrystal 6 of 20 near black (Style 3) and Wave Ripples mostly black on Style 2: fixed 2026-11-06 by taking those styles out of the rolls. Now 3 of 40 (white, palette 1) and 1 of 40. See sheets.
- [ ] Effects (2026-10-27): load one effect into a deck's FX slot from the FX tab (or a stock FX chain: they carry the ranges and responses now; delete old library copies first), turn its Metaknob up so it shows, press the deck die 5-10 times. The source and the effect both change; the Metaknob never moves; Ctrl+Z puts the effect's values back too. Batch 1: 3d_elevation, anamorphic_streak, bloom, color_levels, directional_blur, faceted_glass, feedback, fluid_smear, gradient_map, halftone. Batch 2: kaleidoscope, luma_displace, mirror_sphere, neon_edge, pinch_bulge, pixelate, polar_tunnel, radial_blur. Batch 3: retro_crt, rgb_split, thermal_scanner, vhs_glitch, video_strobe, vortex_swirl, wave_displace. Watch: Bloom and Fluid Smear rolls look alike (too timid?); Feedback and Fluid Smear over several seconds after a roll (no smear-out or freeze); 3D Elevation never shrinks to a speck; Video Strobe's Flash Mode includes White Flash (is that OK as a roll?); Wave Displace tight ripples at high frequency. Turn the deck's Rand knob end to end: effects fade with the source, modes switch once halfway. Rand All also rolls the Master FX slots.
  !!auto: Anamorphic Streak washes out 8 of 10 over Quasicrystal, and raising its threshold range didn't help (15 of 20), so ranges left alone: the cause looks like a bright source, not checked on a darker one. Other effects' flags are mixed up with Quasicrystal's own dark rolls, so use the sheets.
- [ ] Transitions (2026-10-27): for each of the 8 (MIXER page), press the transition die a few times, then fade A to B by hand. Each roll looks different mid-fade (Film Burn tint, Luminous Flash colour, Kinetic Zoom strength, Linear Crossfade curve); fully on A or B the picture never shows the transition. The TRANS knobs 2-4 move with the roll; the die no longer touches inputs without a range. Rand All rolls the transition as well (a jump, not a fade). The stock transition presets roll the same ranges (delete old library copies or Restore Factory Presets first).

## 10. Mixer, output and app

- [ ] Deck-tile level faders: dragging doesn't snap back with a bound knob (the knob follows); the handle shows the fader's own value, the bar the live level.
- [ ] Output > Output Monitor: Automatic and a named monitor; the output reopens on the chosen screen (try Wayland + projector).
- [x] New version available: menu-bar download icon instead of a startup popup.
  auto: fresh real run: green download icon in the menu bar, no popup.
- [ ] During Video Export (offline render), controller knobs and MIDI queue steps still work.
- [x] Kill the app (`kill -9`) after changing things: preferences and session from the last minute come back.
  auto: real app, kill -9 about a minute after the changes: source and FX ON state came back, badge and Library chip right (badge fixed 2026-11-06).
- [x] Web Broadcast tab hidden until Show Advanced Preferences (or already configured).
  auto: not in the Preferences list with default settings. Turning on Advanced not tried.
- [ ] A Shadertoy-style shader reading `audioFFT` / `iChannel0` row 0 reacts to audio.

## 11. Discrete parameters and units

- [ ] Discrete params snap while dragging; ring ticks show on DISCRETE knobs; ISF `STEP` honoured; a controller knob steps cleanly.
- [ ] Angles read in degrees; unit-range values read 0-100.

## 12. Flame source

- [x] Garden stock (158 curated) loads, no black frames.
  auto: all 158 loaded with music on the NVIDIA card. No black frames (darkest frame anywhere 6.3 of 255) and none blown out. 4 barely move: curated-0127, 0262, 0308, 0374.
- [ ] Unit/Smooth change: several flames, no popping or wrong scale. `pre_blur` flames look right.
- [x] Black is transparent: a flame on A over BG shows BG through the dark parts.
  auto: BG shows through the dark parts (mean change 73) and bright parts barely change (3).

## 13. Generators

Open each: renders, animates, parameters do something. Note anything black, flickering or slow.

auto 2026-11-06: all render and animate on the NVIDIA card with music. Every parameter was moved through a knob and compared frame by frame against an untouched render, so boxes with nothing else to judge are ticked. Parameters that only work with another setting: 3D Elevation Separation (3D mode), Wave Displace centre (ripple mode), Chladni Hue Offset (palette 2), Icosa H3 spike values (Spike Mode), KIFS Vertex (type 4), KIFS Storm Repeat 3D and Normal Frequency, Scope Ring Size (Mode), Tunnel Symmetries (surface 1). Seed Density on Cyclic CA and Lenia only acts on a reseed, by reading the shaders. Fractal Flame's XF values and Mutation were not retried. **mandelbox** opens square, but saved looks using it turn 45°. **gyroid_hyperspace** no longer goes black; passing through a wall is still a hard cut, so judge that.

Heavy: - [ ] mandelbox (opens on a face, square) - [ ] kifs (static and face-on by default, Color Spread gives several colours, Twist/Morph reshape inside without turning the object) - [ ] kifs_storm (2026-11-03, the original KIFS restored): load it with music; it should tumble by itself (Tumble Speed), fill much of the frame, and feel like the old storm of cubes. Roll it a few times (Fold Angles roll the full circle). Check the RTX A2000 frame rate on a frame-filling roll and in a lattice (Repeat Spacing about 0.3, Repeat 3D on, Fly Speed about 0.15). Judge the stock responses (loudness tumble, drop spin, kick push on Fold X, kick glow, breakdown dims the crease neon) and the NEON knob (Trap Glow floods above about half) - [ ] kifs Icosahedral type (2026-11-03): Fractal Type 4; sweep Vertex 0 to 1 (icosahedron, football, icosidodecahedron, truncated dodecahedron, dodecahedron outlines); Vertex 0.2 + Color Mode 1 + Hue Offset 0.08 = copper frames over blue recessed panels; Flight Speed pops at the wrap like the octahedral type; Rand rolls type 4 and Vertex - [x] mandelbulb - [x] pseudo_kleinian - [x] apollonian - [x] escape_time - [x] nebula_volume - [ ] reaction_diffusion (pushing Regime / trims never goes black; reseeds) - [x] fluid_ink - [ ] lenia (Growth Center high then back: recovers)

Light wave 1: - [x] quasicrystal - [x] thin_film - [x] tunnel_scenes - [x] video_feedback - [x] hyperbolic_tiling

Light wave 2: - [x] cyclic_ca - [x] metaballs - [x] wave_ripples - [x] aurora_ribbons - [x] isometric_voxels - [x] halftone_matrix - [x] kali_set - [x] girih_stars - [x] glitch_blocks - [x] voronoi_cells

- [ ] Speeds never jump (2026-10-23): after the app has run 15+ minutes, sweep each source's speed knob (Speed, Flight/Morph/Wave/Orbit/Drift Speed...) fast, and put an LFO or audio on one. The motion speeds up and slows down, never jumps or stutters. Also Dynamic Spiral Shear and Wave Amp, Harmonograph Detune, and the Wave Displace, Luma Displace, Fluid Smear, Gradient Map cycle and Video Strobe rate effects. A freshly loaded source looks as before.
  auto: 33 sources, first speed knob swept fast after 15 simulated minutes, no music: no jumps except a 0.1 s blip on Quasicrystal Spin Speed, cause not found. Gyroid's spikes are its black frames, Glitch Blocks steps by design. Left for you: LFO/audio on a speed, the FX rates.
- [ ] Frame time on the integrated GPU is acceptable (notes in `.planning/light-sources-notes`).
  auto: ms per frame on the Intel GPU, 1080p with readback, resolution about 5 ms: light sources 11-28. Nebula 133, Apollonian, Pseudo-Kleinian and Hyper Slice 89, KIFS 78, Mandelbox 67, Mandelbulb 50, KIFS Storm, Lenia, Reaction Diffusion and Icosa H3 39. Acceptable is your call.
- [x] Every-ISF binding audit (v1.0 scope): a sample of filters, every parameter binds to a knob.
  auto 2026-11-06: all 717 parameters of 37 sources, 27 effects and 8 transitions (Fractal Flame's X1-style transform values left out) bind to a knob and the knob moves them. One failed, now fixed: Luminous Flash Intensity.
- [ ] Spectrogram and Scope (2026-11-01), with music playing: Spectrogram in all four Layouts, Mirror, each Palette; notes and kicks readable; Scroll Speed sweeps smoothly with no jump; a quiet input needs Gain, a loud one doesn't burn to solid colour at the defaults. Scope: all four Traces; a held synth note stands still on Wave Line; trails zoom/turn/warp, the kick punches them out; no spokes at the centre of Spectrum Ring. Both: the die rolls varied looks; frame time fine on the integrated GPU.
- [ ] Spectrogram full axis (2026-11-01, desktop + web TV v9 CH 128): on bass-heavy music the Ring lights most of the way round, not just 11-to-1 o'clock; Tilt 0 brings back the bass-only look, Tilt 1 leans to the highs; Log Scale 0.8 gives the bass real room; no hot speckle at the top; at defaults loud music doesn't burn to solid colour and a -20 dBFS track still shows something; frame time still fine on the integrated GPU (8 taps per pixel now).
- [ ] Third-party audio visualizers (2026-11-01): put the Vidvox ISF-Files audio set (FFT Spectrogram, Radial Spectrogram, FFT Color Lines, FFT Filled Waveform, Audio Waveform Shape; `gh api repos/Vidvox/ISF-Files/...` or the scratch copies) in a user ISF folder, play music: each moves with the audio, Radial Spectrogram sweeps round, no knob called fftImage/audio. Also Waveform Displace as an FX. Stock multipass sources (Reaction Diffusion, Lenia, Fluid Ink, Cyclic CA, Video Feedback) look as before.

## 14. Twister hardware

- [ ] Clock knob (13): beat-pulse LED, tempo ring, push to tap.
  auto (simulated Twister): LED pulses, ring shows tempo, push taps. Left for you: on hardware.
- [ ] Perform pages: banks cycle ab / bgpv / mixer, wrap works, LEDs follow. MIXER bank: knobs 1-4 Transitions, 9-12 Master MST.
  auto (simulated): ab, bgpv, mixer, wrap, `B3 0n 7F` sent, lights change; MIXER knob 1 crossfader, 9 MST. Left for you: on hardware.
- [ ] Native mode (`midi-fighter-twister-xt`) on a FRESH profile (delete the old saved one first). Ring styles, RGB LEDs.
  auto (simulated): SysEx in, rings per kind, SysEx out at exit, in again on replug. Left for you: the XT firmware, looks.
- [ ] Pickup blink: Super Knob response Pickup, a waiting slot's LED strobes amber/dark until picked up (stock firmware also dims the ring).
  auto (simulated): a waiting slot's LED changes repeatedly. Left for you: on hardware.
- [ ] Bank press in the pair view walks A, B, BG, PV, Master. Knob 13 send / quick switch is the Master pair.
  auto (simulated): walks A, B, BG, PV, Master, A. Left for you: knob 13 quick switch.
- [ ] Bound slot or pair view: knob 16 is the cursor (tap applies, Shift + tap queues), knobs 1-8 live; touching one moves the binding; Shift + tap on a send knob moves it to that deck. Right-middle steps the tab, right-bottom the panes (Shift + right-bottom clears the slot).
  auto (simulated): tap applies, Shift + tap queues, knobs 1-8 live, Shift + tap on a send moves the binding. Left for you: right-bottom panes, clearing the slot.
- [ ] Library send-to-deck knobs: in Library FULL a send only loads. Shift + tap on a send knob (9-13) switches pair without sending, not intermittent.
  auto (simulated): a send only loads, the view stays; in the pair view Shift + tap switches without sending. Left for you: intermittence on hardware.
- [ ] Library view (queues under the browser), Sources tab: knob 14 moves the A/B queue highlight (first turn lands on the playing item), knob 15 the BG queue's; both columns outlined mint/rose; rings show the place. Hold + turn carries an item, releasing doesn't play it. Tap plays it now; Shift + tap removes (toast, Ctrl+Z). FX tab: the FX queues; Transitions: knob 15 dark. Hide the queues: both dark.
  auto (simulated): knob 14 highlight, hold + turn carries without playing, Shift + tap removes with a toast. Left for you: knob 15, FX and Transitions tabs, outlines, rings.
- [ ] Shift + tap knob 16 after turning knob 14 to the playing item: the item plays next; a second one goes after it. Without touching a queue knob: end of the A/B queue. A playlist under the tree cursor goes in whole.
  auto (simulated): fixed 2026-11-06: turning knob 16 cleared knob 14's row, and a second item landed before the first. Now in order. Left for you: a whole playlist.
- [ ] Knobs 1-8 in the Library view play Deck PV (label on the tab line); send to Deck A with knob 9: they move to A and light in A's colours; knob 13 send: Master's rows.
  auto (simulated): a send moves them to that deck, PV after a PV send. Left for you: the label, Master's rows.
- [ ] Auto-preview: Shift + tap knob 12, knob 12 blinks; scroll Sources and stop: PV loads after ~0.4 s, not while scrolling. Watch the output while scrolling past heavy (gauge) sources and Flames on the laptop iGPU: stutter? Tweak PV with knobs 1-8, scroll on: it pauses with a message instead of a prompt. Leave the Library: it's off.
  auto (simulated): PV loads once the cursor stops. Left for you: the blink, stutter on the iGPU, the paused message.
- [x] Controller FX tap with nothing bound applies to the live deck (first vacant slot, last when full; Shift + tap queues).
  auto (simulated): first vacant slot, then the next; Shift + tap on a saved effect queues.
- [ ] Link All on the Twister; Chain Link: bank change via `B3 0n 7F`.
  auto (simulated): chain link toggles and restores. Left for you: hardware.
- [ ] MIDI input now applies before the frame renders (2026-10-23): knob turns feel at least as tight as before; bank switch, browse cursor (no skipped or lost steps on entering/leaving browse), the unsaved-deck prompt answered from the Twister. Minimise the window and turn knobs: LEDs follow, and on restore nothing replays in a burst.
  auto (simulated): the unsaved-deck prompt answers from the buttons. Left for you: feel, minimising.
- [ ] The controller's knob layout is now cached between frames (2026-10-23): right after each of these, the Twister knobs drive the rows now on screen, not the old ones: change page, open and close Edit, switch a row between SRC and FX in Edit, enter and leave a pair view, focus an FX slot, and load a chain with fewer slots filled.
  auto (simulated): knobs follow page change, Edit open and close, pair enter and leave. Left for you: FX focus, a chain with fewer slots.

## 15. Light theme (take notes, do not guess; you decide the fixes)

Known remaining literals: dropdown selected text, ParametersRenderer fills and grey border, grey button triple in PropertiesPanel / AudioModulatorSection / ParametersTabs / ModulatorHeaderRow, MenuBar, MacroBindingEditor mini-slider, TempoSyncPanel, scope and meter text, UIThemeStyler neutrals, die button/fader in MixerPanel vs DeckControlPanel, `CvTheme`, slider literals.

- [ ] Switch to Light. Walk every view (Perform, Edit, Library, pair views, bound lists).
  !!auto: 13 views measured, in `.planning/auto-verify-2026-11-06/round4/results/contrast.txt` with frames beside it. Unreadable or nearly: Edit's sub-tabs and Value/LFO/SEQ tabs (dark on dark), the value plot (dark panel), `128.0 BPM` and fade time, FX link and power buttons. Low: cyan linked row labels (about 1.7:1), FX slot captions (2.1:1), sequencer toolbar (1.8:1), Library tabs (1.9:1). You decide the fixes.
- [ ] New since the last pass: the badge's dimmed source text, the amber ⚠ lines in Browse tooltips, Clock row group tints, Library deck chips, the amber pickup link button, the empty-deck card, `Wet 40%` / dimmed chain names, button bevels.
- [ ] The breathing bound-slot pill behind the Library / Edit header name: label readable, pill visible.
- [ ] Note unreadable text, invisible borders, hover/press overlays.
- [ ] Dark theme unchanged after switching back.

## 16. Web client (if you test it)

auto 2026-11-06, headless Firefox, local web/ (TV v26, same version as live): ui_check 175 of 176, tape_check 78 of 78, shader_check 70 of 70; beat_check and embed_check ended ok, with no failure in the lines read. That covers the logic of keys, MENU, power-off pages, theme, listeners, schedule, chat with a fake room, reduced motion, breakdown and drop cuts, and tapes. The one failure is the check's own assumption: run just after midnight, its on-air fixture started the day before, so the first heading is not TODAY. render_check did not finish in 280 or 300 s, so it gives no result. Still yours: looks, real radio, phones, Safari, install, real chat.

- [ ] Preset pack (125 presets, 35 FX chains; the raymarched fractals should stay fluid on a laptop and a phone) loads and looks like the app; every web source now has a groove, hard and ambient look (2026-10-23).
  auto: all 132 channels on the NVIDIA, no console errors, 60 fps. CH 34 web-build-drop-spiral read nearly black on the moment sampled (fine, by your call 2026-11-06); CH 17, 18, 23, 27 are sparse and dark. Left for you: looks.
- [ ] Spectrogram and Scope on the web TV (2026-11-01, local server, not yet published): with the radio playing (and once with the microphone), both move with the music like on the desktop; the spectrum isn't much hotter or dimmer than the desktop's on the same track; no console errors; a phone (Android WebView via ?embed too) shows them. Their scenes get suitable chains (Scope is `sparse`, Spectrogram `fullframe`).
  auto: microphone input works and the badge says MIC on a fake device; both channels render without errors. Left for you: how they look against the desktop.
- [ ] Leave the web TV running 20+ minutes: the picture no longer jitters (speeds are now integrated, so even a modulated speed is smooth).
  auto: 25 min, NVIDIA, 50 scene changes: 60 fps, DOM nodes and listeners flat, no errors, JS heap 4.3 to 4.9 MB, GL textures move with the scene (29-52) with no trend, compiled programs level off at 62. This measures leaks and frame pacing, not picture jitter, which is yours.
- [ ] Mandala recipe roll (2026-11-01, TV v12): tune through the mandala scenes (web-bass/bloom/groove/hard/build-drop-mandala, stock-mandala) several times: each comes on with a different shape, often 3-lobed, and holds it for the whole scene (arms, thickness, colour still move); web-mandala-lace and -heart look as before.
- [ ] Viewer controls v13 (2026-11-01, local server, unpublished): ROLL (`D`) on a few scenes: values glide in over about a bar and land on the beat, stepped ones (colour modes, petal counts) change once on the bar line; Lenia / Reaction Diffusion don't break on a roll. Tune through ~20 curated scenes: each still looks like itself, none blank or blown out. MENU › AUTO ROLL 4 bars with HOLD on: keeps rolling. `B` and MENU › BACKGROUND change the background (visible on scenes with dark areas); BG CHANGES HOLD keeps it, OFF fades it out and back on fades one in. MENU › EFFECTS OFF: no FX, `F` says EFFECTS OFF, back to AUTO brings chains back. FOLLOW MUSIC OFF: no drop cut. `X` skips and moves on, `X` again undoes; MENU › SKIPPED lists, picking one tunes there, `X` restores; reload keeps skips; digits `0`-`9` tune. ROLL button fits the cabinet at 1280x720 and on a phone; the 21-row MENU scrolls with the arrows.
- [x] Full-frame looks (Cyclic CA, Kali Set, Voronoi, Ripples, Quasicrystal...) never get trail or bloom chains, and don't wash out.
  auto: 53 fullframe channels, none on a trail or bloom chain. Brightest: CH 12 and 16, mean 226-230 of 255.
- [ ] Cyclic CA hard look reseeds on the biggest bass hits and recovers within a few seconds.
- [ ] Drop strobe chain flashes negative once per loud bass hit (no random strobe).
- [ ] With the radio playing, beat pulses land on the kicks within a few seconds of power-on and stay there through track changes.
  auto: real radio, 10 min: sound throughout, locked 58% of the time, mostly at 140. Synthesized music holds a steady offset after about 100 s: -70 ms at 128 BPM, +110 ms at 174. Whether it looks on the kick is yours.
- [x] Looks change on the beat, about every 16 bars; the background about every 32.
  auto: fades end on a bar line, every 17 bars, at 128, 170 and 174 BPM. Backgrounds set to WITH SCENES change every 33 bars.
- [x] GPU tier: on the NVIDIA (or a Mac) `LSD.gpu` in the console says heavy on and Mandelbulb/Nebula looks appear; on the Intel GPU they never do.
  auto: fixed 2026-11-06: Chromium passed every GPU, with the probe reading 0.3 ms. Now NVIDIA 4.4 ms heavy on, Intel 61 ms heavy off. Firefox picks the Intel GPU here and its readout wasn't checked.
- [ ] Leave the TV on for 15 min on a laptop and a phone: the screen never dims or locks; after power off it sleeps normally.
  auto: wake lock taken at power on and released at power off. Left for you: real phone and laptop.
- [ ] Phone lock screen: shows the show and the look; play/pause powers the TV (on a tape it pauses the tape); next/previous change the look. Laptop media keys do the same.
  auto: play/pause powers, next and previous change the look, metadata set. Left for you: a real lock screen, tapes.
- [ ] Install the TV from Chrome/Edge (desktop) and Add to Home Screen on a phone: it opens fullscreen with the icon; with the network off it still opens (no signal).
  auto: Chromium finds it installable. Fixed 2026-11-06: one visit left six code files uncached, so it wouldn't open offline; now it does, with NO SIGNAL. Left for you: real install, phone.
- [x] `?kiosk` on a spare screen: picture only, turns on by itself (or asks for a click for sound), pointer hides.
  auto: picture only, turns on by itself, with autoplay blocked sound starts on the first click. Fixed 2026-11-06: the pointer stayed visible over the picture.
- [ ] MENU on a laptop and a phone: readable, every row changes with arrows and taps, Esc closes, settings survive a reload.
  auto: fits at seven sizes from 360x640 to 1920x1080, no sideways scroll. Phone label font is 10 px. CH buttons are 28x20, small for a finger. Left for you: readability.
- [ ] With Bluetooth headphones/speaker: TAP SYNC lands near their delay (150-250 ms) and the pulses line up with the kicks afterwards.
- [x] Turn on your OS's reduce-motion setting: MENU shows Reduced motion AUTO (ON), no hard looks or flashes appear, changes are slow.
  auto: MENU shows AUTO (ON), no hard scene in 30 changes, on a real media-query emulation.
- [ ] A slow show (hip-hop, dub) mostly brings calm/groove looks; a fast one (techno, drum and bass) mostly hard/kick looks, but not only those.
  !!auto: 39 of your tracks that Mixxx reads under 95 BPM: only 18% read at that tempo and 79% with octaves, so most read double. Those get fast-music looks. 110-135 BPM reads 97-100% exact. Your call whether to tune it; you said it looks fine so far.
- [ ] Set Preferences › Web Broadcast › Broadcaster name, go live: the web TV shows the LIVE title card with the name; clear the name mid-broadcast: the next viewer joining sees just LIVE.
  auto, faked relay: title card with the name, cleared name, controls locked, back to autopilot, reconnect in 5 s. Left for you: a real broadcast.
- [ ] Branding: nameplate opens the site in a new tab (and doesn't power the TV off); ident at first power-on; ABOUT's QR code scans to liquid-lsd.greenjon.com; paste https://spaz.org/tv/ into a chat app and see the preview.
- [ ] Web TV theme and listener count (2026-11-04, v22): MENU › THEME flips dark/light at once and survives a reload with no dark flash; light is comfortable to look at with the knobs, lit LEDs (LIVE, HOLD, CHAT) and blackboard all easy to read, on a phone too; Add to Home Screen / browser bar colour follows. Under the badge, `N mutants watching TV.` matches the count in the Spaz Radio app and updates within 30 seconds (`1 mutant` when alone; blank at zero).
  auto: no dark flash on reload, the bar colour follows. Cabinet text contrast: light only `by greenjon` at 4.45:1; dark: `LIQUID LSD` nameplate 1.73:1 (raised lettering), `by greenjon` 3.1:1. Left for you: real count, phone.
- [ ] Viewer controls on a real browser and a phone: CH up/down, FX, HOLD, TIME, REACT, swipe, long-press, keyboard; OSD readable; fullscreen keeps the OSD.
  auto: swipe and long-press work on an emulated touch screen, button row fits at 7 sizes. Left for you: a real phone.
- [x] SCHEDULE (2026-10-31, after publishing; TV v4): the button, `G` and MENU › SCHEDULE open Spaz Radio's upcoming shows in your local time zone (header says e.g. TIMES IN PDT), the show playing now marked ON AIR, matching the Spaz Radio app's Schedule tab. Any key or a tap closes it; it scrolls with a finger on a phone without closing. The track line on screen is followed by NEXT: <show> · <time>. Fullscreen: the schedule fills the picture too.
  !!works in a new tab; a plain reload of an open tab kept the old code (button did nothing). Phone scroll not checked yet.
  auto: scrolls under an emulated finger without closing, 60 shows, 412 px wide.
- [x] Reload after a publish (2026-10-31, TV v5): with the TV open in a tab, after the next publish that changes code, one or two plain reloads show the new version in the MENU title (the first visit after v5 installs the fixed service worker).
  auto: after a simulated publish, the first plain reload shows the new version.
- [ ] Web TV beat tracking (2026-11-05, TV v26, after publishing): on the radio, the beat light should stay lit most of the time on music and beat-synced motion should sit on the music. Try drum and bass and something with tempo changes. Watch for visible speed changes when the tempo moves and for the beat jumping. It takes about 5 seconds to settle on a new tempo. On a talk show the light should mostly stay off and motion should stay calm. A phone should not stutter: the estimate costs nearly twice as much now.
  auto: 156 of your tracks against Mixxx: 71% exact, 87% with octaves, locked 77%, 0.41 jumps a minute. On the real radio for 10 min: no NO SIGNAL, no console errors. Left for you: how it looks, a phone.
- [ ] Audio clock beat lock (2026-11-05, clock source Audio, Link off): play a steady track with clear kicks and watch something beat-synced, or the beat dots. After about 10 s it should sit on the kicks and stay there through the track, without needing TAP or SNAP. Press SNAP on an off-beat: after a few seconds it should keep that off-beat. Play speech: the clock should stay calm. With Downbeat: Auto, beat 1 may now behave differently.
- [ ] Web TV with the power off (2026-11-04, TV v24, after publishing): with the TV off, MENU, SCHEDULE, TAPES and `?` open and leave it off; picking a tape turns it on and plays, with sound on the first click after a fresh page load. In the menu, the wheel over a label scrolls, the wheel directly on a value changes it, and the stripes make rows easier to follow. The badge always shows the channel, and in the light theme it reads as dark text on a light window. The source badge above it follows STREAM, LIVE SHOW, TAPE and MIC (the mic needs a real browser). The menu's three pages read well, with the help line always visible, on a phone too.
- [ ] Web TV keys (2026-11-03, TV v21, after publishing): `R` rolls, `J` jumps to a random channel, `E` next FX, `F` fullscreen, `S` schedule, `U` mutes and restores the volume; `Space` holds with no tape in and pauses a tape. The `?` page reads well in its two grouped columns at your usual window size and in fullscreen.
- [ ] TAPES (2026-11-03, TV v20, after publishing), on desktop and a phone, **and iPhone Safari** (Ogg playback and seeking there are unproven): LIVE is lit on the radio, TAPES while a tape is in or the list is open; `T` or the button opens the archive; search; RANDOM TAPE; ON THIS DAY; play a show: the badge says TAPE and the picture follows the show; drag the waveform, `,` `.` `<` `>`, `K`; reload and RESUME returns to your place; power off and on keeps the tape; LIVE/`L` goes back to the radio; lock-screen seek works. During a real broadcast or scheduled show: the tape plays on, LIVE pulses red, `LIVE NOW` comes up once, and going back shows the broadcast's picture. The chat board shows the tape note. The button row still fits at 1280x720 and on a phone.
- [ ] CHAT blackboard (2026-10-31, after publishing; TV v6), at 1280x720 and on a phone in portrait: the board is open on a first visit beside the TV (under it on the phone), with the recent conversation and "N here: …" matching the Spaz Radio app; the X closes it and the TV grows back; a reload keeps it closed; CHAT / `C` / MENU › CHAT bring it back. Write a name, then post one line (with the user's OK, it's the real room): it shows on the phone app with a time, and the phone's reply shows on the board within a second or two, name in its chalk colour, yours in yellow. Typing `n`, `m`, `c` or Space on the board doesn't touch the TV. Close the board, have someone post: the CHAT light blinks. Close the tab: your name leaves the phone's online list. Wi-Fi off for a minute while someone posts, back on: "offline · reconnecting…", then the missed line appears once. Fullscreen shows no chat. Caveat 700 legible on the slate (else try Caveat Brush).
- [ ] While broadcasting from the desktop, the web TV shows LIVE and its controls are locked. The desktop uses the rotated relay token (Preferences > Web Broadcast); a wrong token shows [LIVE ERR].
- [x] Pull the network while the TV plays: snow and NO SIGNAL within a few seconds; plug it back: the radio and the picture come back within ~10 s.
  auto: local fake radio, stalled and dropped, Chromium, stalled on Firefox: NO SIGNAL in 4.4-5.4 s, back in 0.1-9.7 s, no errors. Real radio not tried.
- [x] On a track with a breakdown and drop, a calm look arrives in the breakdown and a hard look cuts in on the drop (not on every quiet moment).
  auto: synthesized track. Calm look 1 s after the breakdown is detected, hard look 0.7 s after the drop. Fixed 2026-11-06: a breakdown starting during a fade lost its calm look.
- [ ] Auto gain on the web TV (2026-10-28): REACT starts at 100% (range 25-200%). The picture should move about as much on a quiet show as on a loud one, and a lot more than before on quiet shows (a 90 s sample of the stream measured bass about 5x livelier than the old 200% default). Judge whether 100% is right for the radio or too busy, especially the `web-*` looks. Breakdowns still bring calm looks and drops still cut (they use the real level); pulling the network still gives snow. Reduced motion caps REACT at 60%.
- [ ] Web breakdowns (2026-10-28): in a radio breakdown the picture's bass motion goes calm and stays calm, and the calm look the autopilot brings in is not undone by the gain climbing back; the drop still cuts to a hard look. `LSD.cvState.energy` in the console shows the watcher (`section`, `holding`, `short`, `long`).
- [ ] Web section modulators (2026-10-28): the stock presets (every `stock-*.lsd` now uses `audio_energy` and `audio_drop`) breathe out in a radio breakdown and hit on the drop as on the desktop. With SYNC set, the drop hit is delayed with the sound. With reduced motion, the drop hit is softer.
- [ ] Phase 8 CI is not done yet, so nothing to check there.
- [ ] Photo gallery background (2026-11-03, TV v14): a fresh visit (or after RESET) starts with the old radio page's photos behind the scene, turning and growing slowly, a new one on the beat every few beats; BG CHANGES reads HOLD. `B` steps to the shader backgrounds and back to the gallery. First photo within a few seconds; no console errors; a phone and the Android app's WebView (once it ships the new web) keep a steady frame rate. Look out for any photo you'd rather drop (the list is the 200 earliest uploads, `web/gallery.json`, until you curate it).

## 16b. Audit fixes (2026-10-29)

- [ ] Copy / Move / Swap a deck "Source + FX" onto another deck: the target's FX row, Chain view and Twister knobs show the new effects and drive them; the chain dips like a chain load; a saved chain still says Save (not Save As). After a Move the source deck's chain is "Untitled".
  auto: slots copy correctly; after a Move the origin is Untitled. Left for you: Chain view, Twister knobs, the dip, Save wording.
- [ ] Macros tab: Apply to / Save from offer only Decks A/B/BG/PV and Master; the Transitions row's K1 still mirrors the crossfader.
- [ ] Clear each queue (A/B, BG, both FX queues, Transition), drop files onto one, add a playlist from its right-click menu: Ctrl+Z undoes each (the Clear toast says so).
  auto: A/B Clear undoes, in both views. Left for you: the other four, file drops, adding a playlist.
- [x] Rename a preset and edit its tags: the file keeps everything else (open it in a text editor).
  auto: only `name` and `tags` differ afterwards.
- [ ] Fractal Flame with Pool or Breed: no hitch at morph boundaries; Keep and Import Flame don't hitch, and the import toast gives the real count. Put a macro on the variation choice: the combo greys out. Editor tabs look right in both themes; Skip's tooltip says Skip.
- [ ] Twister: Ctrl-C the app in native mode, the Twister leaves native mode and goes dark; unplug it while playing, the picture doesn't freeze; plug it back in, it comes back.
  auto (simulated): nothing sent while unplugged, app keeps running, lights rewritten and native mode re-entered on replug. Left for you: Ctrl-C, hardware.
- [ ] Tooltips: hover the new ones (System Volume, Master BPM and presets, Library tabs, MIDI mapping columns, Broadcast buttons) and a few converted knob/slider tooltips (what / gestures / MIDI layout). Menus say "MIDI Learn (X)" / "OSC Learn (X)"; MIDI Preferences column says Control; the transition on/off checkbox no longer says Bypass; colours of the menu bar, About, Link button and Clock row group tints look unchanged (dark) and sensible (light).

## 17. Before release

- [x] Camera-relative light: Mandelbulb, Mandelbox and KIFS: turn Rotate X/Y/Z (and let Mandelbulb's Flight Speed spin) through a full circle; the lit side stays toward you and no side goes dark. KIFS: try every start face. Icosa H3, Pseudo-Kleinian and Apollonian look as before.
  auto: Mandelbulb, Mandelbox and KIFS through a full turn on each axis: no frame below half the median brightness. Every KIFS start face and the look of Icosa H3, Pseudo-Kleinian and Apollonian not checked.
- [x] `ARCHITECTURE.md` refreshed (verified against the code 2026-10-29).
- [ ] Walkthrough videos re-rendered where `scripts/tour_staleness.py` says (much of the UI they show changed).
- [ ] Website deployed (last item).
- [ ] MIDI phase 5 slice 1 (separate agent): Controller Profiles section, copy / delete / reload, validation problems, "Learned mappings" retitle.
- [x] Black-is-transparent sources (30 generators + Renderer separate-alpha blend): put Video Feedback and a few others (Kali Set, Lenia, Fluid Ink, Hyper Slice) on a deck over a coloured BG deck; dark areas should show the BG, bright areas cover it; Chladni / Dynamic Spiral / Icosa H3 should look as before (alpha now linear, not squared).
  auto: BG shows through Video Feedback, Lenia, Fluid Ink, Hyper Slice, Icosa H3 and partly Chladni. Dynamic Spiral stays opaque black, by its shader. Kali Set has no dark areas to test. `as before` could not be compared.

---

Notes:
