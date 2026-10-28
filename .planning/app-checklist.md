# In-app check list (written 2026-10-14, merged 2026-10-22, updated 2026-10-27)

Everything here is code that exists and has passing tests but that nobody has looked at in the running app or on hardware. Tick a box when it works. Write what you saw next to anything that doesn't.

The 2026-10-22 merge added the Browse/Library/Edit review, the Clock row rework, the Super Knob / Metaknob review, the FX review and the sources review, plus everything else since 2026-10-17. It rewrote items the later work had made stale (the pair list, Metaknob "link modes", the crossfader's place, the Maps tab, the open gaps that have since been closed) and dropped three that no longer apply: the inline picker, the XF+CLK pair, and *Retarget Metaknob*.

Before starting: a build from current `main`, a fresh run, and a note of the screen size (min supported 1280x720). Do the whole list once in the dark theme, then the Light theme section. The title bar's `GPU buffers: n (MB)` readout is the quickest leak check.

---

## 1. Library and browser

Library tabs are now Sources, FX, Transitions, Queues, Macros.

- [ ] Ctrl+F opens the Library on the current tab; Esc goes back to where you were (from Edit or a pair view: the same view, with the same slot still bound).
- [ ] Sources, FX and Transitions tabs: list scrolls, search works, typing a letter while the list has the keyboard starts a search (shortcut keys such as `Q` keep their shortcut).
- [ ] Rows: no empty box on stock items, long names end in "..." with the full name on hover, `hyperspace_crash_zoom` reads "Hyperspace crash zoom", heavy shaders show a muted gauge with an explanation on hover.
- [ ] Loaded-where chips: `A`/`B`/`BG`/`PV`/`M` on sources; `A2`/`M1` on effects; a deck playing a saved preset marks the preset row, not the generator; many slots collapse to `×n` with the full list on hover.
- [ ] Recent folder in Sources, FX and Transitions: last 20 loads, newest first, still there after a restart.
- [ ] Menus match across tabs (right-click works on FX and transition rows too): Rename..., Clone, Delete..., Add to playlist, **Reveal in File Manager**.
- [ ] Deleting a saved effect or transition also removes it from queues and playlists.
- [ ] Enter runs the row's default action on every tab; Delete follows Preferences > Shortcuts (Backspace works too); Alt+click a Sources row previews it on PV.
- [ ] Rename, Delete, New Playlist and Export dialogs: field focused, Enter confirms, Esc cancels without leaving the Library. Esc that closes a menu doesn't also go back.
- [ ] Queues tab: an empty queue says how to fill it, five matching button strips (FX queues' Auto greyed), Export / Clear under each column's ⋮, no dead play button on the FX queues. The controller steps the columns left to right.
- [ ] Macros tab: macro banks and Perform pages are listed; loading one works.
- [ ] Transitions view: the Q key queues.
- [ ] Double-click a preset in the queue: auto-fades and advances. Accept from MIDI/keyboard: same.
- [ ] Load a single `.lsdfx` into a full chain by double-click, by audition latch, and from the FX queue / FX playlist: one consistent placement rule.
- [ ] Drop targets: deck badge takes presets, stock sources, external video, `.lsdfxchain` and `.lsdfx`. Monitor takes FX. Slot drop in Edit accepts the same drags as the slot cell.
- [ ] A failed FX or transition load shows a toast.
- [ ] Queue jumps of more than one step from MIDI/CV (+3) move three items. The keyboard queue keys still step once per press (they now run separately from MIDI/CV).
- [ ] Unsaved-changes prompt: Enter saves, Esc cancels (Esc did nothing there before 2026-10-23), buttons the same width, "Don't ask again" says loads are undoable. Eject and Copy/Move/Swap are not undoable and the Preferences tooltip says so.
- [ ] Closing Video Export while it waits on the unsaved-changes prompt does NOT start the export.
- [ ] Click a preset then a source on the same deck within a blink: the last click wins.
- [ ] Save a transition from the Transitions row's Save, from Edit OUT > TRANS and from the Library.
- [ ] Star an FX in the Library: it's in your favorites, and the slot shortlist arrows land on it.

## 2. Slots bound in place, pair view and Edit

- [ ] With the rows on the current page, click a source name, a chain name, an FX slot and the transition name: nothing moves, the slot is outlined, the Library's tab line shows its name, buttons and ✕, the list border takes the row's colour.
- [ ] In a bound list: a click applies (Ctrl+Z undoes), a double-click applies and returns to the plain Library; Esc, ✕ or clicking the slot again stops without applying; changing page stops.
- [ ] Deck monitors, the main output monitor and the Master `M` badge bind their default half (a deck's source, the Master FX chain).
- [ ] Space or maximizing the Library pauses the binding; Esc or Space again resumes.
- [ ] The pair view opens only for rows off the page (e.g. the BG monitor from the A/B page, or a Twister send to such a deck).
- [ ] Pairs: each deck's SRC + FX; the Master pair is Transitions + Master FX and browses the Master FX chain until you touch the transition. MST + Clock pair only in Edit (Browse greyed with a tooltip).
- [ ] Pair view and Edit share one header line: Back, `Browse | Params`, target and its buttons, queue next-up; switching moves nothing. On the Transitions pair, Params opens the transition editor.
- [ ] Trying sources on a deck with unsaved changes asks once while it stays bound; Ctrl+Z still undoes each load.
- [ ] Saved presets, effects and transitions show as applied (row tint) in a bound list.
- [ ] Touching a controller knob doesn't pull the browser off the Queues or Macros tab unless it moves the binding.
- [ ] Learn inside a pair view drops to the 4-row layout (accepted for now). Learn finishes, state sane.
- [ ] Page tabs close the pair view. Esc leaves it and drops the dock selection.
- [ ] Scope, search and cursor memory are kept per kind (FX, then Sources, then back to FX).
- [ ] Badge, slot and monitor tooltips say the list below applies and a second click stops.
- [ ] Edit shows the open unit's pair of rows (deck SRC + FX; OUT: Transitions + Master FX; MST: MST + Clock). Switching SRC/FX doesn't redraw the rows. The lit gear marks the editor's row; the other row's gear moves the editor; the lit gear closes Edit.
- [ ] Edit side rail reads `MST OUT A B BG PV`, nothing clipped. MST has LEVELS and CTRL; OUT has FX and TRANS and returns to the last one. Gears: MST row → MST > LEVELS, Master FX → OUT > FX, Transitions → OUT > TRANS.
- [ ] Edit FX tab: one slot open at a time (the first filled slot opens first), collapsed slots show Dry/Wet, the SUPER KNOB & METAKNOBS block folds on its own chevron.
- [ ] Edit shows readable names ("Cell scale", the shader's LABEL); Properties is never empty when you open a deck or switch tabs; CTRL/TRANS labels in sentence case; the transition selector fits its name; Max isn't cut off in the binding editors.
- [ ] Ctrl+Z after picking an FX and after picking a transition in Edit.
- [ ] Library loads, queues and macro changes are not undoable (as designed); nothing odd happens.
- [ ] Edit gear exits (Esc, gear, menu Close Edit, Ctrl+F) keep each row's sub-tab and selected cell. The Edit module survives a restart.

## 3. Perform rows, Transitions row and Clock row

- [ ] Two-line deck rows with the two-tone SRC/FX badge, readable at 1280x720. Switching modes changes content, never geometry.
- [ ] Button chrome / bevel looks fine on every button type.
- [ ] Knob captions carry whole readable names ("TRACKING JITTER"), "..." only when too narrow.
- [ ] Transitions row: picker and gear on line 1; `[⋮] [Save] [◀] [▶] [A] [B] [AUTO] [time]` in equal cells on line 2; Save turns amber when changed; queue position in the ◀ ▶ tooltips.
- [ ] Transitions at rest (2026-10-27): with the crossfader fully on A (then B), switch through all 8 transitions: the picture does not change at all. Start a slow fade from rest on each: no jump on the first frame of the fade.
- [ ] Cyber Datamosh at rest (2026-10-27 fix): select it, push the crossfader fully to B (and let an auto fade land on B): B's colours look normal, no red/blue smear. Same on A. Check on the web TV too if you test it.
- [ ] MIXER page order: Transitions, Master FX, Master MST, Clock. The crossfader is Transitions knob 1 (and the Mixer panel), not on the MST row. MST pill reads `MST`.
- [ ] Clock row at 1280 px: tempo grid `MAN AUDIO LINK` over `TAP SNAP [-|+]`, BPM and beat dots beside it, LINK dim while off.
- [ ] Bar & Downbeat (2026-10-23, Preferences > Tempo & Sync): Beats per bar 2..8 changes the bar dots (still fit the cell at 8) and the Twister clock knob's orange flash; with Link on it also changes the quantum. SNAP: the cyan dot lands on the beat you pressed on, with a jump of at most half a bar; the first TAP of a run does the same, later taps leave the bar line alone. Downbeat Auto with the Audio Beat Tracker on real dance music: confidence rises over ~4-8 bars, the bar line moves onto the kick/bass accent once and stays; a breakdown does not wander it; SNAP then holds for 16 bars. Note which genres it fails on (flat four-on-the-floor stays undecided by design). Bar-length LFO/sequence jump once when the bar line moves, nothing else.
- [ ] Clock QUEUE block: `A/B | A/B FX | BG | BG FX | TRN` captions over ◀ ▶ pairs; play/pause on A/B, BG and TRN toggles auto-advance (lit while running); faint group tints show which go together.
- [ ] Clock RAND block at the right edge (randomization on): `A B ALL` over `BG PV`; Ctrl+Z undoes.
- [ ] Every Clock button is also on MST > CTRL; right-click Learn MIDI/OSC works from both. A MIDI CC drives the FX queue steps.
- [ ] Narrow the window: a block that doesn't fit is left out, nothing overlaps.
- [ ] Monitor drag onto another monitor: nine-choice menu (Move / Copy / Swap × Source / FX / Source + FX), each does what it says.

## 4. Sources (2026-10-22 review)

- [ ] Load a curated flame (garden): no `*`, Save not red. Load something else over it: no unsaved-changes prompt.
- [ ] Badge with a preset reads `curated-0001 · Fractal Flame` (source dimmed); a bare source shows just its name; narrow the window and the source part goes first. The tooltip always names both.
- [ ] Browse, saved presets: the info column starts with the source (`Fractal Flame · curated, flame`); hover shows "Plays: ...".
- [ ] Load a preset from `garden/` or `imported-flames/`, change a knob, Save: that file changes, and no new copy appears at the top of `presets/`. Save As with a new name goes to `presets/`.
- [ ] Missing source: rename a user shader that a saved preset uses, Rescan. The preset's row shows ⚠ "... missing" with a tooltip; loading it gives a toast and the deck keeps what it had. A session saved with that source restores the deck empty and lists it under missing items.
- [ ] Click the stock source of the preset you're playing: its defaults load. Click it again: nothing happens.
- [ ] SRC tab rows: Gain, **Deck Zoom**, **Deck Rotate**, then the source's own. On Reaction Diffusion / Escape Time the two Zooms are clearly different; 3D sources (KIFS, Gyroid) hide Deck Zoom / Rotate. Tooltips explain them.
- [ ] Set Deck Zoom, pick another source: it resets. Load a preset: it keeps its saved framing.
- [ ] Deck Zoom and Deck Rotate now move Isometric Voxels and Wave Ripples.
- [ ] MIDI-learn Gain on Deck A, change source several times: the mapping still drives Gain. A mapping saved before (path `Deck A/<Source>/Gain`) still works. MST > LEVELS shows only level faders.
- [ ] Leak check: load ~30 sources and flame presets in a row on one deck (or run an A/B queue of flames for a while); `GPU buffers` in the title bar stays roughly flat.
- [ ] Ctrl+Z after a source change brings the old one back (a flame or simulation restarts fresh; expected).
- [ ] Eject empties the deck and keeps its FX chain; tooltip says so. Empty-deck card in Edit: one **Add Source** button that opens Browse.
- [ ] External video deck: Save greyed with a tooltip. Switch the deck to a generator: the stream is released (the sending app's receiver count drops, if it shows one).
- [ ] Preferences > Shader Locations: Add Folder and Rescan Now with a new `.fs` in it. No crash, the new source appears, its presets load, FX and transitions in use keep working.
- [ ] A shader with a syntax error: red checkerboard on the deck, ⚠ "failed to compile" in Browse, the first compiler error on hover.
- [ ] Two user shaders with the same file name: one loads, the log warns about the other.
- [ ] A shader that reads `iMouse` doesn't follow the cursor.
- [ ] Queue ◀ ▶ tooltips on the A and B rows name the off-air deck the item goes to. The die's tooltip lists what it randomizes.
- [ ] Old presets and sessions still load (they carry the removed deck feedback and 3D-view values, which are now ignored).
- [ ] On a packaged install over an older version: unedited bundled shaders are updated, an edited one is kept (log line). Needs a real install to test.

## 5. Off-air decks (2026-10-22)

- [ ] Crossfade fully to A: B's monitor runs at half rate; frame time drops with two heavy sources. Fade back: no hitch.
- [ ] A deck at level 0, and the master at 0: half rate. BG at level 0: half rate.
- [ ] PV renders at half size and still looks fine in its monitor; send PV out over Spout/Syphon/PipeWire and it goes to full size and full rate.
- [ ] Preferences > General **Off-air decks at half frame rate** off: everything renders every frame.
- [ ] A simulation (Reaction Diffusion, Lenia) cued off-air runs at half speed, then normal speed on air. Decide whether that's acceptable.

## 6. FX chains (2026-10-22 review)

- [ ] `FX ON` (lit = on) instead of BYPASS; its state survives a restart.
- [ ] No chain shows the unsaved `•` at launch. It lights for a slot, parameter, Dry/Wet, link or Metaknob-target change, not for turning the Super Knob or a Metaknob.
- [ ] Revert to Saved undoes parameter tweaks.
- [ ] One chain menu on the FX row ⋮ and the Edit tab: Focus, Save As, Revert to Saved, Clear All Slots (a new Untitled chain), Copy / Paste Chain. Save As starts in the chain's folder.
- [ ] Saving over a chain keeps its tags; the chain takes the saved name. A failed save (read-only file) shows a message and stays unsaved.
- [ ] Ctrl+Z undoes slot drags (swap / Ctrl copy), drops, Paste, Reset Parameters, Clear, Revert, Library loads and the FX browser's right-click "Load to ...".
- [ ] Slot name tooltip shows the shortlist position (`★ 3/8`, `Blur 4/12`); stepping off a category's end lands in favorites.
- [ ] A plain mouse wheel over a slot scrolls; Ctrl + wheel steps the effect.
- [ ] Middle-click: Super Knob does nothing; a focused parameter's knob goes to its default.
- [ ] A chain at Chain Dry/Wet below 100% shows `Wet 40%` in its name; at 0% the name is dimmed.
- [ ] Feedback / trail effects don't flash an old frame when a slot comes back from bypass, 0% wet or FX off.
- [ ] Eject leaves the deck's FX chain untouched; Clear with FX refreshes the FX row's knobs.
- [ ] Replace the focused effect with one that has fewer parameters: the focus page follows, knobs 2-4 not blank.
- [ ] FX queue arrow tooltips name the deck and say A and B share one position.
- [ ] A MIDI/OSC mapping to `Deck X/FXChain/DryWet` needs relearning on `Deck X/FX/DryWet` (expected).

## 7. Super Knob and Metaknobs

- [ ] A linked slot's knob 2-4 moves its Metaknob (not dead); in Focus Mode a linked slot's knob 1 works.
- [ ] Preferences: **Super Knob response** Relative (default): linked slots at 20% and 90% both rise, stay apart, reach 100% / 0% together. Pickup: a slot waits for the Super Knob, amber link button with both values in its tooltip.
- [ ] Moving a linked Metaknob by hand (Edit row, MIDI, editing a target) isn't yanked back by the Super Knob.
- [ ] Default FX chain linking is Linked / Unlinked (an old Auto setting reads Linked).
- [ ] Metaknob editor: add targets, try each zone (Full, First Half, Second Half, Triangle, Bipolar), Invert Direction and each curve; Remove Target. A parameter's row menu in Edit has one **Metaknob Target** check. *Replace all links* is gone.
- [ ] Remove every target: the knob reads `NO TARGET` (slot tooltip explains), and it's still empty after save and reload (not Dry/Wet).
- [ ] A target naming a missing parameter shows `(missing)` and drives nothing.
- [ ] A Metaknob on Dry/Wet owns it: the focus header's Wet slider moves the Metaknob; macro/MIDI/OSC writes are suspended.
- [ ] Focus from Edit's SUPER KNOB & METAKNOBS block focuses the chain everywhere; the block shows knobs 2-4, page arrows and Group Mode.
- [ ] Swap effects between slots: each slot keeps its own link setting.
- [ ] Tap on the Super Knob's switch does nothing; Shift + tap is Chain Link.
- [ ] Rate / speed / decay parameters respond exponentially on focus knobs 2-4; turning focus on doesn't change a value. ⟲ and the Twister tap land on the default.
- [ ] A Metaknob with several targets starts where they're closest to their defaults when a slot loads or resets.
- [ ] Save an FX slot and a chain, reload: targets, zones and knob order restored. Knob layout right on Perform and in the pair view.
- [ ] ISF auto-bind: load a few filters; Super Knob and Metaknob do something sensible.
- [ ] *Remove Saved Default for (effect)* and *Save as Default* explain what they do.

## 8. Macros, learn and locks

- [ ] Arm macro Learn, OSC Learn and MIDI Learn at once; Esc cancels all three. MIDI Learn on a parameter, and Learn into a controller profile command, still capture the next control you move (MIDI handling moved earlier in the frame, 2026-10-23).
- [ ] A MIDI/OSC mapping made before a knob binding is suspended while the knob drives the target, flagged in both tables.
- [ ] Range sliders lock when `lfoMin/lfoMax` is bound; Randomize min/max inputs too.
- [ ] Knob- or Metaknob-driven Value: dragging, scrolling or typing in Initial Range moves the knob (through its curve), other targets follow; a Metaknob on a macro knob passes it up. Lobes, Recipe, Max Points and the grid Value cell stay locked.
- [ ] Metaknob-driven rows show the cyan bar, tint, `[K1]` badge and "Linked to its Metaknob" caption; Randomize off.
- [ ] FX-bank bindings are read-only in Properties and slider popups.
- [ ] Select a knob by clicking a parameter, then click the knob: one selection.
- [ ] Bound Min/Max labels and Beat Division labels open the popup.
- [ ] Type a value into a knob with curve, invert and zone set: the result respects them.
- [ ] Macro-bank import onto another deck: paths remap, `Mixer/...` stays.
- [ ] Click a chip on a MASTER knob bound to a deck parameter: selects sensibly.
- [ ] Reorder or delete a modulator: bindings stay on the right one.

## 9. Modulators and step sequencer

- [ ] Sequencer is on by default (an old settings file turns it on once).
- [ ] Bar editor: paint (5% snap, Shift free), scroll to nudge, double-click to type, right/middle-click resets; playing step outlined; 16 per row.
- [ ] Toolbar: Randomize, Invert, Shift < >, Copy/Paste between sequencers, Fill..., Euclid... (Rotate and preview), Clear. Snap menu. Keyboard editing with the pointer over the bars.
- [ ] Length 1-32 (odd lengths), Phase, Offset, Step Hold (randomize); Glide Curve greys when nothing glides; output trace and live dot; ghost steps past the length; bars dim when bypassed.
- [ ] Auto toggle: randomization re-rolls the pattern, morph slides between patterns.
- [ ] Changing Clock Unit converts the step duration.
- [ ] After the app has run 15+ minutes: sweep a Time-clocked LFO's period (and a sequencer's Time interval) by hand and from a macro. It speeds up or slows down, never jumps. A preset morph between two different LFO periods glides too (2026-10-23).
- [ ] Ctrl+Z undoes sequencer, LFO, Audio and MIDI CC edits (one step per drag).

## 9b. Audio response (2026-10-25)

- [ ] Preferences > Audio Hardware: Auto Gain (AGC) is on; feed quiet and loud music and the band meters/boost readout adapt within a few seconds, silence does not boost noise. Turn AGC off: old fixed scale. Headroom slider hotter/cooler.
- [ ] Audio Response slider (2026-10-27, replaces Reactivity; 0-200%, saved): at 0% nothing follows the music (LFOs and beat-synced motion keep going), 100% as tuned, 200% livelier with loud passages pinned at the top; a modulator you add yourself follows it too; beat detection/BPM unaffected; the band meters show the scaled signals. Input Gain tooltip explains why gain does not calm the picture with AGC on.
- [ ] Fractal Flame recipe (2026-10-27): load the source on a deck with music; Breathe pulses on bass, Warp follows mids, Spin sways over 32 beats, Magnify pushes on the kick, Exposure flashes (check it does not wash out or blink), Brightness lifts. Run a morph (Rand/die) and a Garden preset: the responses should not fight the morph; note that a preset with its own saved parameters replaces the recipe. Mandala recipe (2026-10-27): L1 amplitude jiggle, L2 8-beat sine, L3 16-beat sample-and-hold with 50% hold, L4 6-beat ramp; Hue Offset 1-beat cycle (check it is a smooth cycle, not strobing); Recipe Select every 32 beats, Lobes every 256 (4..~15). Judge the L depths. Batch 4 source recipes (2026-10-27: celestial_engine, chladni_cymatics, escape_time, girih_stars, gyroid_hyperspace, hyper_slice, isometric_voxels, quasicrystal, thin_film): same judging. Chladni's FrequencyM slides a fraction of a mode (check it stays a clean pattern); Hyper Slice SliceOffset sweep. Batch 3 (2026-10-26: reaction_diffusion, lenia, cyclic_ca, nebula_volume): on the sims check that the active responses never kill the pattern (reaction_diffusion Feed breathing, lenia over several minutes), and try the bypassed Reseed (drop) alternates: one reseed per hard kick, none at low Audio Response. Batch 2 (2026-10-26: mandelbulb, mandelbox, kifs, pseudo_kleinian, hyperbolic_tiling, icosa_h3): same judging as batch 1. Batch 1 (apollonian, dynamic_spiral, tunnel_scenes, kali_set, aurora_ribbons, domain_warp_fluid, voronoi_cells, wave_ripples, glitch_blocks, metaballs, halftone_matrix, video_feedback): load each on a deck with music. Judge by eye: is the active response tasteful, are depths too high or low, do the bypassed alternates (AUD/LFO cells) turn on cleanly and look good? Note changes per source.
- [ ] FX responses, batch 1 (2026-10-27: bloom, color_levels, retro_crt, luma_displace, fluid_smear, wave_displace, mirror_sphere, kaleidoscope, feedback, gradient_map, faceted_glass, rgb_split). The starter chains do NOT carry them (chains store their own slot parameters), so add each effect to an empty slot from the FX Library, with music. With the Metaknob at zero nothing should move (Luma Displace, Fluid Smear, Wave Displace, Feedback, RGB Split load at zero); turn it up and the hits should grow with it (MUL on the Metaknob input: Bloom, Luma Displace, Wave Displace, Mirror Sphere, Faceted Glass, RGB Split). Watch for: Feedback kick push (trails jump outward; too much?), Fluid Smear flow quickening when loud (gentle enough?), Gradient Map cycling only when loud (stops in quiet passages: good or odd?), Color Levels saturation swell and Retro CRT kick flare on the Master chain (too busy for a master grade?), Kaleidoscope kick zoom. Try the bypassed alternates (AUD/LFO cells). Also: Save as Default on one of them, reload it (yours wins), Remove Saved Default (shipped response back); the Vortex Swirl transition shows its own transition response (Rotations on bass), never the effect's.
- [ ] FX responses, batch 2 (2026-10-27: 3d_elevation, anamorphic_streak, directional_blur, halftone, invert, luma_key, neon_edge, pinch_bulge, pixelate, polar_tunnel, radial_blur, thermal_scanner, vhs_glitch, video_strobe, vortex_swirl). Same method and Metaknob-at-zero check as batch 1. Watch for: Pixelate (kick makes 2-pixel blocks even at size 1, the off position: acceptable or bypass it?), Radial Blur and VHS kick hits (too violent?), Pinch/Bulge bass bulge in both directions, Polar Tunnel kick push (and its wrap seam now sits at the bottom instead of the left: check the tunnel looks balanced left to right; same for Kaleidoscope and Feedback Kaleido at 3 or 5 segments, and Chladni Cymatics with Plate Shape at Circular: no seam on the left), Thermal edge heat. Invert, Luma Key and Video Strobe have only bypassed responses: switch each on once and decide whether one should run by default. After both batches are approved: rebuild all stock FX chains so they carry the responses (agreed 2026-10-27).
- [ ] Transition responses (2026-10-27: luminous_flash, film_burn, cyber_datamosh, kinetic_zoom, liquid_displacement, noise_dissolve, vortex_swirl; linear_crossfade has none). With music playing, rest on A, then on B, on every transition: nothing may move. Then slow manual fades (knob 1 or the Mixer slider) on each: no pop on the first frame of the fade (watch Luminous Flash and Film Burn, and the Inferno Film Burn preset, Burn Spread now 0.45), and judge the running responses mid-fade: Flash punching on the kick, Burn ember flare, Datamosh hat tears and kick colour shear, Zoom kick punch and loudness streak, Liquid bass melt and high dispersion, Dissolve kick fringe and loudness haze, Vortex bass surge and kick fringe. Too busy, too subtle? Try the bypassed alternates (Datamosh Blockiness flips every beat; Noise Scale on mids may boil). Cyber Datamosh Blockiness now reads 0-100 and looks the same as before at the old default. The stock presets (AutoVJ, Festival Elite) carry the same responses: your library still has the old copies, so delete `library/transitions/*.lsdtrans` (or Restore Factory Presets) first. A fresh stock transition shows no `*` and Save stays grey; change a value and it turns amber. Save as Default / Remove Saved Default in the Transitions row ⋮ and the Mixer TRANS kebab: re-pick the transition in the same session and your values come back (no restart); Remove resets the running one to the shipped response.

## 9c. Random by default (2026-10-27)

Preferences: randomization on. For each source: pick it fresh from Browse (not a saved preset, which keeps its own values), press the deck die 5-10 times, then turn the deck's Rand knob slowly end to end a few times. Judge: does every roll look good, are there duds (blank, mush, too dark, broken), is the variety too timid or too wild? Note per source what to widen, narrow or drop. Ranges are in `src/main/resources/stock_defaults/sources/<id>.json` (`baseMin`/`baseMax`).

- [ ] Mechanics, on any source (KIFS is a good one): a roll moves the four source knobs on screen (and the Twister rings), and turning a knob afterwards picks up from the rolled value without a jump. Stepped values (KIFS Fractal Type, Color Mode) switch once, halfway through a Rand sweep, never flicker through the types in between. Ctrl+Z after a roll brings the previous look and knob positions back (Fractal Flame excepted: its values stay). The Initial Range slider and its dice now show on a knob-driven parameter (open its Properties); dragging the range there works.
- [ ] Second pass (2026-10-27, from your first look): Cyclic CA rolls (every roll alive? a roll restarts from noise and organises within a second or two), Gyroid (slower, wider, more varied), Mandala (recipe changes on a roll), Mandelbox/Mandelbulb brightness, Fractal Flame picked from Browse (was black), Reaction Diffusion (fewer flooded solid-colour rolls). Fluid Ink: a roll changes the colour of new ink only, so the change shows over a few seconds as old ink fades; say if that is too slow.
- [ ] Batch 1: apollonian, aurora_ribbons, celestial_engine, chladni_cymatics, cyclic_ca, domain_warp_fluid, dynamic_spiral, escape_time, fluid_ink, girih_stars. Watch: Cyclic CA with many states and a high threshold may freeze (no spirals); Chladni Invert and Plate Shape full range; Escape Time Julia angle full circle (some constants are mostly empty).
- [ ] Batch 2: glitch_blocks, gyroid_hyperspace, halftone_matrix, hyperbolic_tiling, hyper_slice, icosa_h3, isometric_voxels, kali_set, kifs, lenia. Watch: Hyperbolic P/Q (the shader raises Q when the pair is not hyperbolic, so some rolls repeat); Lenia blobs dying out (kick reseeds them); Hyper Slice and Icosa camera angles.
- [ ] Batch 3: mandelbox, mandelbulb, metaballs, nebula_volume, pseudo_kleinian, quasicrystal, reaction_diffusion, thin_film, tunnel_scenes, video_feedback, voronoi_cells, wave_ripples. Watch: Mandelbox stays in the positive-scale family (crossing zero jumps); Reaction Diffusion regimes that blank out; Video Feedback whiteout or blackout from decay/zoom; Tunnel side counts.
- [ ] Effects (2026-10-27): load one effect into a deck's FX slot from the FX tab (a stock FX chain does not carry the ranges yet), turn its Metaknob up so it shows, press the deck die 5-10 times. The source and the effect both change; the Metaknob never moves; Ctrl+Z puts the effect's values back too. Batch 1: 3d_elevation, anamorphic_streak, bloom, color_levels, directional_blur, faceted_glass, feedback, fluid_smear, gradient_map, halftone. Batch 2: kaleidoscope, luma_displace, mirror_sphere, neon_edge, pinch_bulge, pixelate, polar_tunnel, radial_blur. Batch 3: retro_crt, rgb_split, thermal_scanner, vhs_glitch, video_strobe, vortex_swirl, wave_displace. Watch: Bloom and Fluid Smear rolls look alike (too timid?); Feedback and Fluid Smear over several seconds after a roll (no smear-out or freeze); 3D Elevation never shrinks to a speck; Video Strobe's Flash Mode includes White Flash (is that OK as a roll?); Wave Displace tight ripples at high frequency. Turn the deck's Rand knob end to end: effects fade with the source, modes switch once halfway. Rand All also rolls the Master FX slots.
- [ ] Transitions (2026-10-27): for each of the 8 (MIXER page), press the transition die a few times, then fade A to B by hand. Each roll looks different mid-fade (Film Burn tint, Luminous Flash colour, Kinetic Zoom strength, Linear Crossfade curve); fully on A or B the picture never shows the transition. The TRANS knobs 2-4 move with the roll; the die no longer touches inputs without a range. Rand All rolls the transition as well (a jump, not a fade). The stock transition presets roll the same ranges (delete old library copies or Restore Factory Presets first).

## 10. Mixer, output and app

- [ ] Deck-tile level faders: dragging doesn't snap back with a bound knob (the knob follows); the handle shows the fader's own value, the bar the live level.
- [ ] Output > Output Monitor: Automatic and a named monitor; the output reopens on the chosen screen (try Wayland + projector).
- [ ] New version available: menu-bar download icon instead of a startup popup.
- [ ] During Video Export (offline render), controller knobs and MIDI queue steps still work.
- [ ] Kill the app (`kill -9`) after changing things: preferences and session from the last minute come back.
- [ ] Web Broadcast tab hidden until Show Advanced Preferences (or already configured).
- [ ] A Shadertoy-style shader reading `audioFFT` / `iChannel0` row 0 reacts to audio.

## 11. Discrete parameters and units

- [ ] Discrete params snap while dragging; ring ticks show on DISCRETE knobs; ISF `STEP` honoured; a controller knob steps cleanly.
- [ ] Angles read in degrees; unit-range values read 0-100.

## 12. Flame source

- [ ] Garden stock (158 curated) loads, no black frames.
- [ ] Unit/Smooth change: several flames, no popping or wrong scale. `pre_blur` flames look right.
- [ ] Black is transparent: a flame on A over BG shows BG through the dark parts.

## 13. Generators

Open each: renders, animates, parameters do something. Note anything black, flickering or slow.

Heavy: - [ ] mandelbox (opens on a face, square) - [ ] kifs (static and face-on by default, Color Spread gives several colours, Twist/Morph reshape inside without turning the object) - [ ] mandelbulb - [ ] pseudo_kleinian - [ ] apollonian - [ ] escape_time - [ ] nebula_volume - [ ] reaction_diffusion (pushing Regime / trims never goes black; reseeds) - [ ] fluid_ink - [ ] lenia (Growth Center high then back: recovers)

Light wave 1: - [ ] quasicrystal - [ ] thin_film - [ ] tunnel_scenes - [ ] video_feedback - [ ] hyperbolic_tiling

Light wave 2: - [ ] cyclic_ca - [ ] metaballs - [ ] wave_ripples - [ ] aurora_ribbons - [ ] isometric_voxels - [ ] halftone_matrix - [ ] kali_set - [ ] girih_stars - [ ] glitch_blocks - [ ] voronoi_cells

- [ ] Speeds never jump (2026-10-23): after the app has run 15+ minutes, sweep each source's speed knob (Speed, Flight/Morph/Wave/Orbit/Drift Speed...) fast, and put an LFO or audio on one. The motion speeds up and slows down, never jumps or stutters. Also Dynamic Spiral Shear and Wave Amp, Harmonograph Detune, and the Wave Displace, Luma Displace, Fluid Smear, Gradient Map cycle and Video Strobe rate effects. A freshly loaded source looks as before.
- [ ] Frame time on the integrated GPU is acceptable (notes in `.planning/light-sources-notes`).
- [ ] Every-ISF binding audit (v1.0 scope): a sample of filters, every parameter binds to a knob.

## 14. Twister hardware

- [ ] Clock knob (13): beat-pulse LED, tempo ring, push to tap.
- [ ] Perform pages: banks cycle ab / bgpv / mixer, wrap works, LEDs follow. MIXER bank: knobs 1-4 Transitions, 9-12 Master MST.
- [ ] Native mode (`midi-fighter-twister-xt`) on a FRESH profile (delete the old saved one first). Ring styles, RGB LEDs.
- [ ] Pickup blink: Super Knob response Pickup, a waiting slot's LED strobes amber/dark until picked up (stock firmware also dims the ring).
- [ ] Bank press in the pair view walks A, B, BG, PV, Master. Knob 13 send / quick switch is the Master pair.
- [ ] Bound slot or pair view: knob 16 is the cursor (tap applies, Shift + tap queues), knobs 1-8 live; touching one moves the binding; Shift + tap on a send knob moves it to that deck. Right-middle steps the tab, right-bottom the panes (Shift + right-bottom clears the slot).
- [ ] Library send-to-deck knobs: in Library FULL a send only loads. Shift + tap on a send knob (9-13) switches pair without sending, not intermittent.
- [ ] Controller FX tap with nothing bound applies to the live deck (first vacant slot, last when full; Shift + tap queues).
- [ ] Link All on the Twister; Chain Link: bank change via `B3 0n 7F`.
- [ ] MIDI input now applies before the frame renders (2026-10-23): knob turns feel at least as tight as before; bank switch, browse cursor (no skipped or lost steps on entering/leaving browse), the unsaved-deck prompt answered from the Twister. Minimise the window and turn knobs: LEDs follow, and on restore nothing replays in a burst.
- [ ] The controller's knob layout is now cached between frames (2026-10-23): right after each of these, the Twister knobs drive the rows now on screen, not the old ones: change page, open and close Edit, switch a row between SRC and FX in Edit, enter and leave a pair view, focus an FX slot, and load a chain with fewer slots filled.

## 15. Light theme (take notes, do not guess; you decide the fixes)

Known remaining literals: dropdown selected text, ParametersRenderer fills and grey border, grey button triple in PropertiesPanel / AudioModulatorSection / ParametersTabs / ModulatorHeaderRow, MenuBar, MacroBindingEditor mini-slider, TempoSyncPanel, scope and meter text, UIThemeStyler neutrals, die button/fader in MixerPanel vs DeckControlPanel, `CvTheme`, slider literals.

- [ ] Switch to Light. Walk every view (Perform, Edit, Library, pair views, bound lists).
- [ ] New since the last pass: the badge's dimmed source text, the amber ⚠ lines in Browse tooltips, Clock row group tints, Library deck chips, the amber pickup link button, the empty-deck card, `Wet 40%` / dimmed chain names, button bevels.
- [ ] Note unreadable text, invisible borders, hover/press overlays.
- [ ] Dark theme unchanged after switching back.

## 16. Web client (if you test it)

- [ ] Preset pack (125 presets, 35 FX chains; the raymarched fractals should stay fluid on a laptop and a phone) loads and looks like the app; every web source now has a groove, hard and ambient look (2026-10-23).
- [ ] Leave the web TV running 20+ minutes: the picture no longer jitters (speeds are now integrated, so even a modulated speed is smooth).
- [ ] Full-frame looks (Cyclic CA, Kali Set, Voronoi, Ripples, Quasicrystal...) never get trail or bloom chains, and don't wash out.
- [ ] Cyclic CA hard look reseeds on the biggest bass hits and recovers within a few seconds.
- [ ] Drop strobe chain flashes negative once per loud bass hit (no random strobe).
- [ ] With the radio playing, beat pulses land on the kicks within a few seconds of power-on and stay there through track changes.
- [ ] Looks change on the beat, about every 16 bars; the background about every 32.
- [ ] GPU tier: on the NVIDIA (or a Mac) `LSD.gpu` in the console says heavy on and Mandelbulb/Nebula looks appear; on the Intel GPU they never do.
- [ ] Leave the TV on for 15 min on a laptop and a phone: the screen never dims or locks; after power off it sleeps normally.
- [ ] Phone lock screen: shows the show and the look; play/pause powers the TV; next/previous change the look. Laptop media keys do the same.
- [ ] Install the TV from Chrome/Edge (desktop) and Add to Home Screen on a phone: it opens fullscreen with the icon; with the network off it still opens (no signal).
- [ ] `?kiosk` on a spare screen: picture only, turns on by itself (or asks for a click for sound), pointer hides.
- [ ] MENU on a laptop and a phone: readable, every row changes with arrows and taps, Esc closes, settings survive a reload.
- [ ] With Bluetooth headphones/speaker: TAP SYNC lands near their delay (150-250 ms) and the pulses line up with the kicks afterwards.
- [ ] Turn on your OS's reduce-motion setting: MENU shows Reduced motion AUTO (ON), no hard looks or flashes appear, changes are slow.
- [ ] A slow show (hip-hop, dub) mostly brings calm/groove looks; a fast one (techno, drum and bass) mostly hard/kick looks, but not only those.
- [ ] Set Preferences › Web Broadcast › Broadcaster name, go live: the web TV shows the LIVE title card with the name; clear the name mid-broadcast: the next viewer joining sees just LIVE.
- [ ] Branding: nameplate opens the site in a new tab (and doesn't power the TV off); ident at first power-on; ABOUT's QR code scans to liquid-lsd.greenjon.com; paste https://spaz.org/tv/ into a chat app and see the preview.
- [ ] Viewer controls on a real browser and a phone: CH up/down, FX, HOLD, TIME, REACT, swipe, long-press, keyboard; OSD readable; fullscreen keeps the OSD.
- [ ] While broadcasting from the desktop, the web TV shows LIVE and its controls are locked. The desktop uses the rotated relay token (Preferences > Web Broadcast); a wrong token shows [LIVE ERR].
- [ ] Pull the network while the TV plays: snow and NO SIGNAL within a few seconds; plug it back: the radio and the picture come back within ~10 s.
- [ ] On a track with a breakdown and drop, a calm look arrives in the breakdown and a hard look cuts in on the drop (not on every quiet moment).
- [ ] Phase 8 CI is not done yet, so nothing to check there.

## 17. Before release

- [ ] Camera-relative light: Mandelbulb, Mandelbox and KIFS: turn Rotate X/Y/Z (and let Mandelbulb's Flight Speed spin) through a full circle; the lit side stays toward you and no side goes dark. KIFS: try every start face. Icosa H3, Pseudo-Kleinian and Apollonian look as before.
- [ ] `ARCHITECTURE.md` refreshed (frame loop, queues, control surfaces and UI sections updated 2026-10-23 for the MIDI timing change; the rest dates from 2026-10-07).
- [ ] Walkthrough videos re-rendered where `scripts/tour_staleness.py` says (much of the UI they show changed).
- [ ] Website deployed (last item).
- [ ] MIDI phase 5 slice 1 (separate agent): Controller Profiles section, copy / delete / reload, validation problems, "Learned mappings" retitle.
- [ ] Black-is-transparent sources (30 generators + Renderer separate-alpha blend): put Video Feedback and a few others (Kali Set, Lenia, Fluid Ink, Hyper Slice) on a deck over a coloured BG deck; dark areas should show the BG, bright areas cover it; Chladni / Dynamic Spiral / Icosa H3 should look as before (alpha now linear, not squared).

---
Notes:
