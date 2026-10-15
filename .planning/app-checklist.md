# In-app check list (written 2026-10-14)

Everything here is code that exists and has passing tests but that nobody has looked at in the running app or on hardware. Tick a box when it works. Write what you saw next to anything that doesn't.

Before starting: a build from current `main`, a fresh run, and a note of the screen size (min supported 1280x720). Do the whole list once in the dark theme, then the Light theme section.

---

## 1. Library and browser (backlog items 10-12, unified browser step 7)

Not hand-checked since the classic browser code was deleted.

- [ ] Ctrl+F opens the Library in the Presets tab. Esc returns to Perform.
- [ ] Ctrl+F in the Sources tab. Screenshot looks right, list scrolls, search works.
- [ ] Ctrl+F in the Transitions tab. Same checks.
- [ ] Ctrl+F in the FX tab. Same checks.
- [ ] Maps tab opens. Macro banks and Perform pages are listed. Loading one works.
- [ ] Transitions: save a transition from the Library, from the Mixer TRANS tab and from the inline picker (item 10).
- [ ] FX favourites: star an FX in the Library FX browser and see it in the inline picker (item 12).
- [ ] Library Transitions view: the Q key queues (now a `ShortcutManager` binding).
- [ ] Double-click a preset in the queue: auto-fades and advances. Accept from MIDI/keyboard: same behaviour (item 3).
- [ ] Load a single `.lsdfx` into a full chain by double-click, by audition latch, and from the FX queue / FX playlist. One consistent placement rule (item 1).
- [ ] Drop targets: deck badge takes presets, stock sources, `.lsdfxchain` and `.lsdfx`. Monitor takes FX. Slot drop in ParametersTabs accepts the same drags as `FxSlotCell` (item 26).
- [ ] Failed FX or transition load shows a toast instead of failing silently (item 25).
- [ ] Queue jumps of more than one step from MIDI/CV (+3) move three items.
- [ ] Unsaved-changes prompt: "Don't ask again" label says loads are undoable. Eject and Copy/Move/Swap are not undoable and the Preferences tooltip says so.
- [ ] Closing Video Export while it waits on the unsaved-changes prompt does NOT start the export.
- [ ] Click a preset then a generator on the same deck within a blink: the preset (last click) wins.

## 2. Edit bay and undo (item 28)

- [ ] Pick an FX in the Edit bay, press Undo: previous FX returns.
- [ ] Pick a transition in the Edit bay, press Undo.
- [ ] Library loads, queues and macro changes are NOT undoable (as designed). Confirm nothing odd happens.
- [ ] FX browser right-click "Load to <chain> / Slot n" (known gap: not undoable yet, handoff section A).

## 3. Pair focus view

Owner-checked already: banks, send, shift-switch. Still to look at:

- [ ] Click a deck SRC badge: deck SRC + FX rows focus with Browse below.
- [ ] Click an FX chain, an FX slot, a transition name, a monitor: each opens the right pair.
- [ ] Master MIX+FX pair.
- [ ] Transitions + Clock (XF+CLK) pair.
- [ ] Monitor click.
- [ ] Learn inside a pair view drops to the 4-row layout (accepted for now). Learn finishes, state sane.
- [ ] Page tabs close the pair view. Esc leaves the pair and drops the dock selection.
- [ ] Scope, search and cursor memory is kept per kind (go FX, then Sources, then back to FX).
- [ ] Tooltips on badges, slots and monitors say they open the pair view.
- [ ] Edit gear exits: Esc, EDIT gear, menu "Close Edit", Ctrl+F. Decided behaviour (option A): module collapses, one click to get back, per-row sub-tab and selected cell kept.
- [ ] Edit module survives a restart.

## 4. Deck rows and Perform layout

- [ ] Two-line deck-row badge (two-tone SRC/FX caption). Readable at 1280x720.
- [ ] Switching modes changes content, never geometry (rows do not jump).
- [ ] Button chrome / bevel looks fine on all button types.
- [ ] Row title badges: every row has a badge + 2 lines.
- [ ] Crossfader sits on the Master MIX line and moves with Transitions K1.

## 5. Macros, learn and locks (items 5, 6, 7, 8, 13, 18-21, 23)

- [ ] Learn: arm macro Learn, OSC Learn and MIDI Learn at once, then Esc cancels all three.
- [ ] A MIDI/OSC mapping made before a knob binding is suspended while the knob drives the target, and flagged in both tables.
- [ ] Locked sliders: Min/Max range slider locks when `lfoMin/lfoMax` is bound. Randomize-mode min/max inputs are locked too.
- [ ] Sliders owned by a Metaknob link: do they show a lock? (Known open gap, handoff section A.)
- [ ] FX-bank bindings are read-only in Properties and slider popups (no delete, no edit that gets overwritten).
- [ ] Select a knob by clicking a parameter, then click the knob: selection stays in sync (one selection, item 7).
- [ ] Bound double-track Min/Max labels and Beat Division labels open the popup.
- [ ] Type a value into a knob with curve, invert and link mode set: result respects them (item 21).
- [ ] Macro-bank import onto a different deck: paths remap, `Mixer/...` is left alone (item 22).
- [ ] Click a chip on a MASTER knob bound to a Deck parameter: selects and behaves sensibly.
- [ ] Modulator IDs: reorder or delete a modulator, bindings stay on the right one (D7).

## 6. FX Metaknob editor and knob layout (implemented 2026-10-14)

- [ ] Open the Metaknob editor. Add a parameter link; pick each link mode (Full, First Half, Second Half, Triangle, Bipolar, Invert) and check the sweep.
- [ ] Unlink a parameter.
- [ ] Right-click a parameter in ParametersTabs to link/unlink.
- [ ] "Retarget Metaknob" menu: confirm whether it wipes the other links (suspected). Dry/Wet safety net comes back when the list is empty.
- [ ] Save an FX slot and a chain, reload: links and modes restored.
- [ ] Knob layout looks right on Perform and in the pair view.
- [ ] ISF auto-bind: load a few filters, Super Knob and Metaknob do something sensible.

## 7. Discrete parameters

- [ ] Discrete params snap to values while dragging (no in-between values).
- [ ] Ring ticks show on DISCRETE knobs.
- [ ] ISF `STEP` key honoured on a filter that uses it.
- [ ] Controller knob on a discrete param steps cleanly.

## 8. Flame source

- [ ] Garden starting stock (158 curated flames) loads, no black frames.
- [ ] Smooth/unit change: look at several flames, no popping or wrong scale.
- [ ] `pre_blur` flames look right.

## 9. Generators (heavy batch and light batches)

Open each, check it renders, animates, and the parameters do something. Note anything black, flickering, or slow.

Heavy: - [ ] mandelbox - [ ] kifs - [ ] mandelbulb - [ ] pseudo_kleinian - [ ] apollonian - [ ] escape_time - [ ] nebula_volume - [ ] reaction_diffusion - [ ] fluid_ink - [ ] lenia

Light wave 1: - [ ] quasicrystal - [ ] thin_film - [ ] tunnel_scenes - [ ] video_feedback - [ ] hyperbolic_tiling

Light wave 2: - [ ] cyclic_ca - [ ] metaballs - [ ] wave_ripples - [ ] harmonograph - [ ] aurora_ribbons - [ ] isometric_voxels - [ ] halftone_matrix - [ ] kali_set - [ ] girih_stars - [ ] glitch_blocks - [ ] voronoi_cells

- [ ] Frame time on the integrated GPU is acceptable (notes in `.planning/light-sources-notes`).
- [ ] Every-ISF binding audit (v1.0 scope): pick a sample of filters and check every parameter binds to a knob.

## 10. Twister hardware

- [ ] Clock knob (13): beat-pulse LED, tempo ring, push to tap.
- [ ] Perform pages: banks cycle ab / bgpv / mixer, wrap works, LEDs follow.
- [ ] Native mode (`midi-fighter-twister-xt` profile) on a FRESH profile (delete the old saved profile first). Ring styles, RGB LEDs.
- [ ] Library send-to-deck knobs: in Library FULL a send only loads.
- [ ] Shift + tap on a send knob (9-13) switches pair without sending. Check it is not intermittent.
- [ ] Controller FX tap with nothing bound: applies to live deck (first vacant slot, last when full; Shift+tap queues).
- [ ] Bank press in pair view walks A, B, BG, PV, Master, XF.
- [ ] Chain Link: Twister bank change via `B3 0n 7F`.

## 11. Light theme (take notes, do not guess; you decide the fixes)

Known remaining literals: dropdown selected text, ParametersRenderer fills and grey border, grey button triple in PropertiesPanel / AudioModulatorSection / ParametersTabs / ModulatorHeaderRow, MenuBar, MacroBindingEditor mini-slider, TempoSyncPanel, scope and meter text, UIThemeStyler neutrals, die button/fader in MixerPanel vs DeckControlPanel, `CvTheme`, slider literals.

- [ ] Switch to Light. Walk every view (Perform, Edit, Library, pair views).
- [ ] Note unreadable text, invisible borders, hover/press overlays.
- [ ] Dark theme unchanged after switching back.

## 12. Web client (if you test it)

- [ ] Preset pack (57 presets, 11 FX chains) loads and looks like the app.
- [ ] Phase 8 CI is not done yet, so nothing to check there.

## 13. Before release

- [ ] ROADMAP rows updated (Metaknob linking, UI Lab, expanded FX view). `ARCHITECTURE.md` refreshed.
- [ ] Website deployed (last item).
- [ ] MIDI phase 5 slice 1 (separate agent): Controller Profiles section, copy / delete / reload, validation problems, "Learned mappings" retitle.

---
Notes:
