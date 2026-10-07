# Presets & Library

Presets are the core unit of work in Liquid LSD. A preset captures the complete state of a deck — every parameter, every modulation connection, every note you've added — into a single `.lsd` file. The library and playlist tools are built around organizing and performing with those files.

---

## Presets

### What a preset contains

- All visual parameter values (geometry, colour, view, feedback)
- Every modulation setup (LFOs, audio connections, sequencer patterns, MIDI assignments)
- Preset notes and per-parameter notes (if you've added any)

### Saving a preset

- **`Ctrl+S`** — Saves the active deck's preset to disk immediately. If the deck doesn't have a saved file yet, it opens a **Save As** dialog instead.
- **`Shift+Ctrl+S`** — Always opens the Save As dialog, even for already-saved presets. The suggested name gets `_copy` appended so you don't accidentally overwrite the original.

When you modify a loaded preset, a `*` appears next to the deck name to show there are unsaved changes. A generator you picked without loading a preset is tracked the same way: tweak it and the deck shows `*`. Editing a knob's label or its targets counts as a change; turning a knob does not.

### Loading onto a deck with unsaved changes

Loading a preset, picking a generator, ejecting, or using Copy / Move / Swap onto a deck that has unsaved changes asks first: **Save**, **Discard** or **Cancel**. Tick **Don't ask again** on the prompt to always discard from then on (Ctrl+Z still undoes preset and generator loads). You can change this in **Preferences → General → Manual Load Dirty Behavior**:

- **Prompt** (default) — Ask each time.
- **Discard** — Overwrite without asking.
- **Auto-Save** — Save the deck's preset first, then load.

Press **Ctrl+Z** after loading a preset or changing a generator to put back the previous deck, its knobs and its preset name. Picking or clearing an FX slot, FX chain or the mixer transition in the Edit bay can be undone the same way. Eject, Copy / Move / Swap and Library loads of FX or transitions can't be undone yet. Copy, Move and Swap carry the knobs along, so the knobs on the target deck control the copied parameters. A preset saved without knobs gets the generator's default knobs.

### The `.lsd` format

Presets are plain JSON files stored in `library/presets/` (and subfolders). You can open them in any text editor, back them up, share them, or paste them as JSON using **Copy Preset JSON** / **Paste Preset JSON** from the right-click context menu.

### Copying settings between decks

Right-clicking in the **VALUE** column (or in Deck Controls) gives you copy and paste options to clone parameters from one deck to another. You can also copy individual modulator cells and paste them onto other parameters — see the [Modulation](modulation.md) page for details. You can also use the keyboard shortcuts CTRL-c and CTRL-v to copy and paste (or Command-c and -v on Macs)

### Unsaved changes & the Auto-VJ queue

The prompt above is for loads you start yourself. If Auto-VJ switches presets while a deck has unsaved changes, you can control what happens in **Preferences → General**:

- **Skip** — Don't load the next preset until changes are saved or discarded.
- **Auto-Save** — Automatically save the current state before switching.
- **Auto-Discard** — Discard changes and switch immediately.

---

## The Library Panel

The Library panel spans the left and middle columns. That column has three views:

- **Perform** — The Performance rows sit above the Library, which fills the rest of the window. This is the everyday view for playing a set.
- **Edit** — Open a row's **Edit** and the Library goes away completely. That row and its parameters fill the column, so you have room to fine-tune presets and FX. The row's **Browse** tab is the same browser as the Library (same tabs, toolbar and queues), applying to that row; swap a deck's source or FX there or from the controls on the row itself. Press **Esc** or click the row's **Collapse** button to get back to Perform.
- **Library** — The Library fills the whole column and the Performance rows are hidden. Use this when you're building or editing playlists and queues.

Press **`Space`** (when the cursor isn't in a text field) or the button at the right of the Library's title bar to switch between Perform and Library. From Edit, `Space`, `Ctrl+F` or `/` closes Edit (cancelling any armed Add Target or Learn) and brings the Library back.

The Performance rows have a fixed height, so the Library gets whatever window height they leave: a taller window means a taller Library.

### Library View Mode (`[ Sources ]` / `[ FX ]` / `[ Transitions ]` / `[ Macros ]`)

Toggle between sources, FX, transitions and banks (macro banks and Perform pages) using the segmented mode button in the top-left of the Library menu bar. Sources, FX and Trans share one three-part browser:

- **Folder tree** (left) — **All**, **Favorites** (FX), the kind's sections (Sources: stock sources, saved presets and live external video; FX: stock filters, saved single FX and saved chains; Trans: stock transitions and saved transitions), and a **Playlists** group with one entry per playlist. Each entry shows how many rows it holds. Click one to list it. A playlist is just another tree entry: select it to see and edit its rows.
- **List** (middle) — The rows of the selected tree entry, with a search box on top (`Ctrl+F` or `/` focuses it) and a **`[+]`** button for the kind's "new" action.
- **Queues tab** — All five queues side by side: A/B, A/B FX, BG, BG FX and Transition, in that order (which the crossfader advances through automatically). Items get in from the other tabs (`Q`, `Shift+Q`, the toolbar buttons, the row menu or drag-and-drop); this tab is where you watch, reorder and play them.

The mode buttons pick what the three parts show:

- **`[ Sources ]`**: stock visual sources and saved full deck presets (`.lsd`, in `library/presets/`); setlists (`.lsdplay`) live in `library/playlists/`.
- **`[ FX ]`**: stock ISF filters, saved single-slot FX presets (`.lsdfx`, in `library/fx/`) and saved 3-slot FX chains (`.lsdfxchain`, in `library/fx_chains/`); curated FX playlists (`.lsdfxplay`) live in `library/fx_playlists/`.
- **`[ Transitions ]`**: stock ISF transition shaders and saved transition presets (`.lsdtrans`, in `library/transitions/`); setlists (`.lsdtransplay`) live in `library/transition_playlists/`.

The same browser opens inside the Edit row's **Browse** tabs (source, Chain, FX1-3, transition); there a click applies the row to that deck, slot, chain or the mixer transition.

### The Unified Sources Browser

The `[ Sources ]` list shows two kinds of row side by side, each marked with its own icon:

- **Stock Sources** — The 8 bundled built-in visual sources: Mandala, Dynamic Spiral, Icosa H3, Domain Warp Fluid, Gyroid Hyperspace, Celestial Engine, Hyper Slice, and Chladni Cymatics (plus external video streams and any custom sources dropped into your configured shader directories). They load straight to a deck's source stage with their shader's default parameters; they can't be added to a playlist or queue.
- **Saved Presets (`.lsd`)** — A full deck preset: the visual source plus every parameter value, modulation connection, and note.

- **External Video** — Live Spout, Syphon or PipeWire streams running right now, one row each in the **External video** folder (empty when none is active). Load one like a stock source; it appears and disappears as the stream starts and stops. Streams are not files, so they have no favorites, playlists or queues and cannot be saved as presets.

Pick a section in the folder tree to show only stock sources, saved presets or live streams. Use **`[+]`** to eject a deck to blank so you can build a new preset on it (see [Saving a preset](#saving-a-preset) below).

- **Search** — Type to filter by name, tag, or (for stock sources) category. `Ctrl+F` or `/` jumps focus to the search box of the open tab (Sources, FX or Transitions) from anywhere in the app.
- **Multi-Selection (Shift-Click & Ctrl/Cmd-Click)**:
  - **Click** — Selects a single preset, clearing existing selections.
  - **Ctrl+Click** (or **Cmd+Click** on macOS) — Toggles selection of an individual item without affecting others.
  - **Shift+Click** — Extends selection from the anchor/lead item to the clicked item across all visible rows.
  - **Batch Queueing** — When multiple presets are selected, pressing `Q` (or the row menu's *Add to A/B Queue*) enqueues all selected presets in order into the A/B Queue. Pressing `Shift+Q` (or *Add to BG Queue*) enqueues them into the BG Queue. The hotkeys and row menu act on the tab you picked the item on: in the FX tab they feed the FX queues, and in the Trans tab `Q` feeds the transition queue (there is no background queue for transitions).
  - **Batch Context Menu** — Right-clicking with multiple items selected presents options like **Add N Presets to Playlist**, **Add N Presets to Queue**, and **Delete N Presets...**.
  - **Multi-Item Drag & Drop** — Dragging any item from a multi-selection carries all selected presets simultaneously into Playlists or Queues.
  - **Batch Deletion** — Pressing `Delete` or selecting Delete from the context menu opens a confirmation modal detailing the exact count and list of presets to be deleted.
- **Double-click** — Loads the item into whichever deck is currently inactive on the crossfader. For a stock source this replaces only the deck's visual source, leaving its FX chain untouched; for a saved preset it loads the full deck state.
- **Right-click or ⋮ (saved presets)** — Rename, retag, duplicate, load to a specific deck, add to a queue or playlist, or delete.
- **Right-click or ⋮ (stock sources)** — **Load to Deck A/B/BG/PV** only. Stock sources have no persisted parameter state, and their bundled defaults and Metaknob auto-mappings haven't all been individually audited yet, so they're intentionally left out of playlists and the live/background queues — an unattended queue firing an unverified default has no performer there to catch it. Loading straight to a deck is fine, since you're present to dial it in.
- **Drag-and-drop** — Drag any row (or multi-selection) onto a deck's monitor in the Mixer panel or onto its row in the Performance Matrix to load it (source-only for stock rows, full preset for saved rows). Saved presets can also be dragged into the playlist in the tree or a queue; stock sources can't, for the same reason the context menu omits those options.
- **`[!]` badge** — Appears when a saved preset uses a subsystem that's currently offline (e.g. MIDI or audio). The preset still loads fine; hover the badge to see what's missing.

### The Unified FX Browser

The `[ FX ]` list shows three kinds of row side by side, each marked with its own icon:

- **Stock ISF Filters** — Built-in filters with no saved parameters. A deck or Master has several FX chain slots, so a bare stock filter is ambiguous without knowing which slot it targets — double-click and drag-and-drop resolve this by loading into the first vacant slot, and the right-click menu lets you pick a specific slot explicitly (**Load to Deck/Master > Slot 1–3**). That same ambiguity is why stock filters can't be added to a playlist or live queue: there's no interactive slot-picker available when a queued item gets applied automatically later.
- **Favorites** — Right-click a stock filter and choose **Add to Favorites** (or **Remove from Favorites**) to star it. Starred filters show a ★ in the list, and the **Favorites** folder in the tree lists only them. They are the same favorites the inline FX picker and the slot shortlist use.
- **Saved Single FX Presets (`.lsdfx`)** — One FX slot's full parameter state, captured from a deck.
- **Saved FX Chains (`.lsdfxchain`)** — A complete 3-slot FX pipeline, captured from a deck's or Master's FX chain. Loading a chain replaces all 3 slots on the target chain.

Pick a section in the folder tree to show only stock filters, singles or chains. Use **`[+]`** to save the current FX state of any deck or Master FX slot (or all 3 slots as a chain) into a new preset.

- **Drag-and-Drop**: Drag a saved single or chain onto Slot 1–3 in a deck's Edit `FX` subtab, onto an FX playlist in the tree, or onto a A/B FX Queue.
- **Double-click**: Loads into the dominant deck's first vacant slot (singles) or overwrites all 3 slots (chains). If all 3 slots are full, a single effect asks which slot to overwrite. FX always goes to the deck the crossfader is on (it is audible now); presets and generators go to the other deck (the next look). At exactly the middle of the crossfader, FX goes to Deck A and presets to Deck B.
- **Right-click menu**: `Load to > Deck [A|B|BG|PV] / Master FX > Slot [1|2|3]` (stock filters and singles) or `Load to Deck [A|B|BG|PV] / Master FX` (chains), plus **Add to A/B FX Queue (A/B)**, **Add to BG FX Queue**, **Add to '<playlist>' Playlist**, Rename, Clone, Delete, and Reveal in File Manager.

#### Bundled Stock FX Chains

Liquid LSD includes 16 pre-calibrated 3-slot FX chains in `library/fx_chains/` designed for live club and festival VJ performance:

1. **Hyperspace Trip** (`hyperspace_trip.lsdfxchain`):
   - *Slots*: `kaleidoscope` → `feedback` → `gradient_map`
   - *Vibe*: Classic psychedelic trance / festival tunnel breakdown with multi-axis kaleidoscopic folding, dithered zoom burst, and filmic Oklab contrast roll-off.
2. **The Drop Weapon** (`the_drop_weapon.lsdfxchain`):
   - *Slots*: `radial_blur` → `video_strobe` → `rgb_split`
   - *Vibe*: High-intensity EDM/bass music drop impact with directional motion streaks, rhythmic beat flash gating, and chromatic aberration impact.
3. **Cyberpunk 1984** (`cyberpunk_1984.lsdfxchain`):
   - *Slots*: `pixelate` → `retro_crt` → `rgb_split`
   - *Vibe*: Retro-futuristic dystopian terminal aesthetic with honeycomb pixel lattice, curved CRT phosphor scanlines, and duotone cyberpunk neon grading.
4. **Liquid Mercury** (`liquid_mercury.lsdfxchain`):
   - *Slots*: `luma_displace` → `fluid_smear` → `bloom`
   - *Vibe*: Viscous organic fluid and metallic chrome distortion with concentric liquid ripples, curl advection smearing, and surface normal refraction.
5. **Neon Wireframe** (`neon_wireframe.lsdfxchain`):
   - *Slots*: `neon_edge` → `directional_blur` → `feedback`
   - *Vibe*: Glowing electric neon vector graphics with orientation-to-hue Sobel edge glow, spherical optical lens bulging, and soft high-pass bloom.
6. **Wormhole Flight** (`wormhole_flight.lsdfxchain`):
   - *Slots*: `polar_tunnel` → `pinch_bulge` → `bloom`
   - *Vibe*: Deep space infinite warp portal with aspect-preserving log-polar vortex, zoom burst trails, and recursive feedback rotation.
7. **FLIR Predator Vision** (`flir_predator_vision.lsdfxchain`):
   - *Slots*: `thermal_scanner` → `retro_crt` → `video_strobe`
   - *Vibe*: Military reconnaissance and sci-fi tactical optics with FLIR Ironbow heat mapping, sensor noise grain, horizontal smear, and quantized block mosaic.
8. **Liquid Chrome Dimension** (`liquid_chrome_dimension.lsdfxchain`):
   - *Slots*: `wave_displace` → `mirror_sphere` → `color_levels`
   - *Vibe*: 3D raytraced floating chrome orb reflecting the scene with liquid surface turbulence and punchy high-contrast color grading.
9. **2D to 3D Elevation with Feedback** (`2d_to_3d_elevation_with_feedback.lsdfxchain`):
   - *Slots*: `3d_elevation` → `feedback` → `bloom`
   - *Vibe*: The iconic Liquid LSD topological displacement chain featuring heightmap mesh rendering, recursive rotation feedback, and ACES filmic grading.
10. **Laser Concert Anamorphic** (`laser_concert_anamorphic.lsdfxchain`):
    - *Slots*: `anamorphic_streak` → `bloom` → `color_levels`
    - *Vibe*: Ultra-widescreen concert stage lighting with horizontal laser glare, soft volumetric bloom, and high-contrast filmic punch.
11. **Pop-Art Comic Print** (`pop_art_comic_print.lsdfxchain`):
    - *Slots*: `neon_edge` → `halftone` → `gradient_map`
    - *Vibe*: Authentic Roy Lichtenstein / vintage comic book print with black ink contours, 4-plate CMYK dot rosettes, and duotone pop grading.
12. **Grindhouse VHS Bootleg** (`grindhouse_vhs_bootleg.lsdfxchain`):
    - *Slots*: `vhs_glitch` → `retro_crt` → `color_levels`
    - *Vibe*: Gritty 80s horror / VHS bootleg tape with magnetic tracking jitter, head-switching bars, phosphor scanlines, and tape saturation.
13. **Prismatic Crystal Kaleidoscope** (`prismatic_crystal_kaleidoscope.lsdfxchain`):
    - *Slots*: `faceted_glass` → `kaleidoscope` → `rgb_split`
    - *Vibe*: Shattered diamond prism reflection with polyhedral symmetry and spectral dispersion.
14. **Cosmic Black Hole Vortex** (`cosmic_black_hole_vortex.lsdfxchain`):
    - *Slots*: `vortex_swirl` → `luma_displace` → `polar_tunnel`
    - *Vibe*: Gravitational accretion singularity with logarithmic space swirl, surface normal marbling, and infinite log-polar warp.
15. **Subtle Optical Warmth** (`subtle_optical_warmth.lsdfxchain` — the default Master FX):
    - *Slots*: `color_levels` → `retro_crt` → `bloom`
    - *Vibe*: Gentle tape/phosphor warming with contrast roll-off, subtle enough to leave on the master all night.
16. **Digital Bitcrush Mosaic** (`digital_bitcrush_mosaic.lsdfxchain`):
    - *Slots*: `pixelate` → `rgb_split` → `video_strobe`
    - *Vibe*: Lattice quantization and spectral glitch.

On a fresh install, Master FX starts with *Subtle Optical Warmth*, Deck A with *Liquid Mercury*, Deck B with *Liquid Chrome Dimension*, Deck BG with *Hyperspace Trip*, and Deck PV with *Prismatic Crystal Kaleidoscope*.


### FX Playlists (`.lsdfxplay`)

FX playlists are entries under **Playlists** in the FX tree. Select one to edit it like a preset playlist: drag singles/chains in from the list to insert them, drag rows to reorder, and double-click an entry to apply it. Missing files show the same red `[!] (missing)` indicator as preset playlists.

### A/B FX Queues (A/B and BG)

On the **Queues** tab the two FX columns are independent **live FX queues** — volatile, RAM-only sequences of FX singles/chains you can improvise with mid-set:

- **`<` / `>`** — Step to the previous/next queued FX item and apply it.
- **🔁 Repeat** — Cycle back to the start when the bottom of the queue is reached.
- **🔀 Shuffle** — Play items in random order.
- **⤓ Export** (download icon) — Save the current live queue as a new `.lsdfxplay` playlist.
- **🗑 Clear** (trash icon) — Empty the queue.
- **Drag-and-drop** — Reorder items within a queue, or drag a preset/chain from the list to append or insert it.
- **Double-click** an item to jump straight to it. Pressing the MIDI controller's accept on a queue item does the same: it moves the queue position, fades to the loaded deck and advances the transition queue.

The **A/B queue** applies to whichever of Deck A/B is currently dominant on the crossfader; the **BG queue** always applies to Deck BG.

---

## Playlists (Setlists)

Playlists are where you build your setlist; they appear under **Playlists** in the Sources folder tree. Playlists are `.lsdplay` files — simple ordered lists of presets.

- **Switch between playlists** — Click another playlist in the tree.
- **Reorder** — Drag and drop presets within the list. Changes save automatically.
- **Playlist actions (⋮ menu)**:
  - **Play Now** — Replaces the live queue with this playlist and starts playback.
  - **Insert After Current** — Slides the playlist into the live queue right after the currently playing preset.
  - **Add to Bottom** — Appends it to the end of the queue.

If a playlist references a preset that's been moved or deleted, the row appears in red with a `[!] (missing)` indicator. Right-click it to remove the dead link.

---

## The A/B Queue (Auto-VJ)

The play queue drives the main crossfader automatically, sequencing through presets with transitions between Deck A and Deck B.

- **Transport controls** — `<` (previous), `▶/⏸` (play/pause), `>` (next).
- **Loop / Shuffle** — `🔁` for continuous looping, `🔀` for random order.
- **Add to queue** — Select a preset in the browser and press `Q`, or right-click and choose **Add to Queue**.
- **Export / Clear** — The download icon saves an improvised live queue as a permanent playlist file; the trash icon empties the queue. Every queue column has the same header: a small caption naming the queue, then one row of icon buttons (hover for a tooltip).

When the queue is playing, the crossfader moves automatically between decks as presets transition. Grabbing the crossfader manually immediately pauses Auto-VJ so you have direct control.

---

## BG Queue (Deck BG)

The BG Queue works the same way as the main queue but drives Deck BG independently. Add presets with `Shift+Q`. Background transitions use dip-to-black fades — double-click or right-click a queued item to choose between an instant cut or a fade.

---

## Curated Transitions Suite & Transition Presets

Liquid LSD features a curated suite of 8 club-grade ISF transition shaders designed for high-impact live mixing between Deck A and Deck B:

1. **Linear Crossfade (`linear_crossfade.fs`)** — Pristine, transparent dissolve with Perceptual Cosine S-curve, Linear, or Equal Power curve modes.
2. **Luminous Flash (`luminous_flash.fs`)** — Midpoint exposure flare and Gaussian bloom overdrive with tunable color temperature (-1.0 icy strobe to +1.0 warm amber) and spread, designed for high-energy beat drops.
3. **Film Burn (`film_burn.fs`)** — 35mm celluloid burn with multi-octave procedural fractal noise erosion and glowing chromatic combustion contours (Fiery Ember, Electric Violet, Acid Green).
4. **Noise Dissolve (`noise_dissolve.fs`)** — Multi-octave domain-warped fractal noise erosion with soft feathered contours and chromatic fringing.
5. **Liquid Displacement (`liquid_displacement.fs`)** — Interactive cross-deck vector morphing where Deck A and Deck B dynamically displace each other's UV coordinates based on luminance gradient fields.
6. **Kinetic Zoom (`kinetic_zoom.fs`)** — High-speed camera crash zoom with multi-tap radial velocity streak blur, edge chromatic dispersion, and exponential acceleration curves.
7. **Vortex Swirl (`vortex_swirl.fs`)** — Gravitational singularity twisting Deck A into a spiraling vortex at the frame center, peaking at midpoint, and unwinding into Deck B with chromatic flare.
8. **Cyber Datamosh (`cyber_datamosh.fs`)** — Digital video compression breakdown emulating I-frame/P-frame corruption, macroblock displacement, horizontal sync tear, and chromatic shear.

### The Unified Transition Browser

The `[ Transitions ]` list shows two kinds of row side by side, each marked with its own icon:

- **Stock ISF Transition Shaders** — The curated suite above, plus any custom `.fs` transitions found in your configured shader directories. Unlike FX, there's only ever one active transition — no chain of slots to disambiguate — so a bare stock transition id is a complete, unambiguous instruction. Combined with sane bundled defaults, that makes them safe to apply, queue, or add to a playlist just like saved presets.
- **Saved Transition Presets (`.lsdtrans`)** — A dialed-in transition configuration (parameters, dry/wet, modulators) captured from the mixer.

Pick a section in the folder tree to show only stock shaders or only saved presets. Use **`[+]`** to save the mixer's current transition as a new preset.

- **Double-click**: Applies the transition to the mixer immediately.
- **Keyboard**: **Enter** applies the selected transition; the *Add to A/B Queue* shortcut (**Q** by default, rebindable in Preferences → Shortcuts) adds it to the transition queue.
- **Failures**: a transition or FX file that can't be loaded or applied now shows a toast instead of failing silently.
- **Right-click menu**: **Apply to Mixer**, **Add to A/B Queue**, **Add to '<playlist>' Playlist**, plus Rename/Clone/Delete for saved presets (stock shaders have no file to rename or delete).

### The Macros Tab (Macro Banks and Perform Pages)

The **`[ Macros ]`** tab has two lists, switched with the **Banks** / **Pages** radio buttons. It has no playlists or queues, so it uses the whole Library width.

- **Banks** lists the saved macro banks (`.knobpreset.json` files in `library/knobpresets`). **Save bank from...** saves the knobs of a deck, Master or Transition row under a name you choose. Right-click a bank to **Apply to** one of those rows (deck banks retarget to the deck they land on; targets that row can't take are dropped, and a toast says if parameters were missing) or **Delete** it. FX banks are rewritten from the FX chain, so they can't be saved or applied here. This is the same as the bank kebab menu in the Edit row, without the file browser.
- **Pages** lists the Perform pages with where each comes from (built-in, user, or user override). Click a page to show it in Perform. Right-click for **Hide from / Show in tab strip**, **Copy to user file** (built-ins) or **Delete user file**. Editing a page's rows stays in **Preferences → MIDI Controls**.

### Transition Presets (`.lsdtrans`) & Playlists (`.lsdtransplay`)

- **Transition Presets (`.lsdtrans`)**: Stored in `library/transitions/`. Save dialed-in transition configurations (including parameter values, dry/wet, and modulators) from the Mixer's TRANS tab (**[⋮] > Save Transition As...**), from the **Save current as preset...** button under the transition list in Browse, or with the **[+]** button above the Trans list or right-clicking in the Mixer panel.
- **Transition Playlists (`.lsdtransplay`)**: Stored in `library/transition_playlists/`. Group transitions into ordered setlists for the Transition Queue. A factory playlist, `festival_elite.lsdtransplay`, is bundled out of the box.
- **AutoVJ Integration**: The Transition Queue automatically advances to the next staged transition preset or stock transition shader each time the crossfader cycles between decks.

---

## Drag & Drop

| From                       | To                             | Result                   |
| -------------------------- | ------------------------------ | ------------------------ |
| Sources list (saved preset) | Playlist (between items)       | Inserts at that position |
| Sources list (saved preset) | Empty space at playlist bottom | Appends to the end       |
| Playlist item              | Up / down in the same playlist | Reorders                 |
| Sources list (saved preset) or Playlist | Queue                          | Adds to the live queue   |
| Sources list row (stock or saved) | Deck monitor (Mixer) or deck row (Performance Matrix) | Loads source (stock) or full preset (saved) to that deck |
| FX Preset (`.lsdfx`)       | FX Slot 1–3 in Parameters      | Loads into target slot   |
| Stock FX filter            | FX Slot in Parameters or the Performance Matrix | Loads into that slot |
| FX Preset (`.lsdfx`)       | Deck badge in the Performance Matrix | Loads into the first vacant slot (a toast says so if all 3 are full; drop on a slot instead) |
| FX Chain (`.lsdfxchain`)   | FX Slot / Chain in Parameters, or the deck badge | Overwrites 3-slot chain  |

A deck's monitor takes presets and generators only, not FX.
| FX list row                 | FX playlist in the tree        | Inserts/appends to playlist |
| FX list row                 | A/B FX Queue (A/B or BG)      | Appends/inserts into that queue |
| A/B FX Queue item          | Up / down in the same queue    | Reorders                 |
| Transition list row         | Transition playlist in the tree | Inserts/appends to playlist |
| Transition list row         | Transition Queue          | Appends/inserts into the queue |

---

## MIDI Mapping

> MIDI is disabled by default. Enable it in **Preferences → MIDI Controls** ("Enable MIDI Subsystem") or via the **⋮** kebab menu next to Edit's column headers ("MIDI Column").

Liquid LSD keeps hardware controller maps separate from visual presets, so you can swap physical controllers without touching your preset files.

**There are two levels of MIDI mapping:**

1. **Hardware Profile** (stored in `library/midi/default.json`) — Maps physical knobs and faders to global controls like the master crossfader, deck gain, and level faders. These mappings follow you regardless of which preset is loaded.

2. **Grid Cell Modulators** (stored inside each `.lsd` file) — Maps MIDI CC numbers to specific parameters as dynamic modulation sources in the CV grid. These travel with the preset.

### MIDI Learn

1. Click the **MIDI Learn** button in the Parameters header or next to any parameter slider.
2. Move a knob, fader, or button on your controller.
3. Liquid LSD captures the CC and confirms the mapping automatically.
4. To remove a mapping, right-click the mapped control and choose **Clear MIDI Mapping**.
