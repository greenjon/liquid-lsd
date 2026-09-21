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

When you modify a loaded preset, a `*` appears next to the deck name to show there are unsaved changes.

### The `.lsd` format

Presets are plain JSON files stored in `library/presets/` (and subfolders). You can open them in any text editor, back them up, share them, or paste them as JSON using **Copy Preset JSON** / **Paste Preset JSON** from the right-click context menu.

### Copying settings between decks

Right-clicking in the **VALUE** column (or in Deck Controls) gives you copy and paste options to clone parameters from one deck to another. You can also copy individual modulator cells and paste them onto other parameters — see the [Modulation](modulation.md) page for details. You can also use the keyboard shortcuts CTRL-c and CTRL-v to copy and paste (or Command-c and -v on Macs)

### Unsaved changes & the Auto-VJ queue

If Auto-VJ switches presets while a deck has unsaved changes, you can control what happens in **Preferences → General**:

- **Skip** — Don't load the next preset until changes are saved or discarded.
- **Auto-Save** — Automatically save the current state before switching.
- **Auto-Discard** — Discard changes and switch immediately.

---

## The Library Panel

The Library panel spans the left and middle columns and has three height modes. Press **`Space`** (when the cursor isn't in a text field) to cycle between them:

- **Full Height** — The Parameters and Properties panels are hidden. Use this when you're fully focused on building or editing playlists and queues.
- **Half Height** — The Library sits in the lower half of the workspace, with Parameters and Properties still visible above it. Good for tweaking modulation while keeping your setlist in view.
- **Docked** — The Library collapses to a slim toolbar. Use this during a performance when you don't need to manage playlists or queues.

In Half Height mode, drag the Library's title bar to resize it freely — the height is remembered. Double-click the title bar to snap back to a 50/50 split. The window buttons on the right of the title bar also let you jump between heights directly.

### Library View Mode (`[ Presets ]` / `[ FX ]`)

Toggle between full visual presets and FX presets using the segmented mode button in the top-left of the Library menu bar:

- **`[ Presets ]` Mode**:
  - **Column 1 (Presets Pool)**: Shows all full visual deck presets (`.lsd`) in `library/presets/`.
  - **Column 2 (Playlists Editor)**: Setlists (`.lsdplay`) in `library/playlists/`.
- **`[ FX ]` Mode**:
  - **Column 1 (FX Browser)**: A unified, filterable list combining stock ISF filters, saved single-slot FX presets (`.lsdfx`) in `library/fx/`, and saved 4-slot FX chains (`.lsdfxchain`) in `library/fx_chains/`.
  - **Column 2 (FX Playlists Editor)**: Curated FX playlist sequences (`.lsdfxplay`) in `library/fx_playlists/`.
  - Columns 3 and 4 (the Background Queue and Play Queue columns) swap to the **Live FX Queues** described below.

### Preset Browser (All Presets)

The left column shows every preset saved in `library/presets/`. 

- **Search** — Type to filter by name or tag. `Ctrl+F` or `/` jumps focus to the search box from anywhere in the app.
- **Double-click** — Loads the preset into whichever deck is currently inactive on the crossfader.
- **Number keys 1–4** — Route the selected preset to Deck A, B, BG, or PV respectively.
- **Right-click or ⋮** — Rename, retag, duplicate, add to a queue, or delete.
- **`[!]` badge** — Appears when a preset uses a subsystem that's currently offline (e.g. MIDI or audio). The preset still loads fine; hover the badge to see what's missing.

### The Unified FX Browser

Column 1 of `[ FX ]` mode lists three kinds of row side by side, each marked with its own icon:

- **Stock ISF Filters** — Built-in filters with no saved parameters. They can only be loaded directly to a deck (first vacant FX slot); they can't be added to a playlist or live queue since there's no reproducible state to save.
- **Saved Single FX Presets (`.lsdfx`)** — One FX slot's full parameter state, captured from a deck.
- **Saved FX Chains (`.lsdfxchain`)** — A complete 3-slot FX pipeline, captured from an FX bank chain. Loading a chain replaces all 3 slots on the target chain.

Use the **`[⋮]`** filter menu above the list to show/hide each tier (**All / Stock / Singles / Chains**). Use **`[+]`** to save the current FX state of any deck slot (or all 3 slots as a chain) into a new preset.

- **Drag-and-Drop**: Drag a saved single or chain onto Slot 1–3 in the Parameters panel `FX` subtab, onto the FX Playlist editor (Column 2), or onto a Live FX Queue (Columns 3/4).
- **Double-click**: Loads into the dominant deck's first vacant slot (singles) or overwrites all 3 slots (chains).
- **Right-click menu**: `Load to > Deck [A|B|BG|PV] > Slot [1|2|3]` (singles) or `Load to Deck [A|B|BG|PV] > Chain [1|2|3]` (chains), plus **Add to Live FX Queue (A/B)**, **Add to BG FX Queue**, **Add to '<playlist>' Playlist**, Rename, Clone, Delete, and Reveal in File Manager.

#### Bundled Stock FX Chains

Liquid LSD includes 9 pre-calibrated 3-slot FX chains in `library/fx_chains/` designed for live club and festival VJ performance:

1. **Hyperspace Trip** (`hyperspace_trip.lsdfxchain`):
   - *Slots*: `kaleidoscope` → `radial_blur` → `color_levels`
   - *Vibe*: Classic psychedelic trance / festival tunnel breakdown with multi-axis kaleidoscopic folding, dithered zoom burst, and filmic Oklab contrast roll-off.
2. **The Drop Weapon** (`the_drop_weapon.lsdfxchain`):
   - *Slots*: `directional_blur` → `video_strobe` → `rgb_split`
   - *Vibe*: High-intensity EDM/bass music drop impact with directional motion streaks, rhythmic beat flash gating, and chromatic aberration impact.
3. **Cyberpunk 1984** (`cyberpunk_1984.lsdfxchain`):
   - *Slots*: `pixelate` → `retro_crt` → `gradient_map`
   - *Vibe*: Retro-futuristic dystopian terminal aesthetic with honeycomb pixel lattice, curved CRT phosphor scanlines, and duotone cyberpunk neon grading.
4. **Liquid Mercury** (`liquid_mercury.lsdfxchain`):
   - *Slots*: `wave_displace` → `fluid_smear` → `luma_displace`
   - *Vibe*: Viscous organic fluid and metallic chrome distortion with concentric liquid ripples, curl advection smearing, and surface normal refraction.
5. **Neon Wireframe** (`neon_wireframe.lsdfxchain`):
   - *Slots*: `neon_edge` → `pinch_bulge` → `bloom`
   - *Vibe*: Glowing electric neon vector graphics with orientation-to-hue Sobel edge glow, spherical optical lens bulging, and soft high-pass bloom.
6. **Wormhole Flight** (`wormhole_flight.lsdfxchain`):
   - *Slots*: `polar_tunnel` → `radial_blur` → `feedback`
   - *Vibe*: Deep space infinite warp portal with aspect-preserving log-polar vortex, zoom burst trails, and recursive feedback rotation.
7. **FLIR Predator Vision** (`flir_predator_vision.lsdfxchain`):
   - *Slots*: `thermal_scanner` → `directional_blur` → `pixelate`
   - *Vibe*: Military reconnaissance and sci-fi tactical optics with FLIR Ironbow heat mapping, sensor noise grain, horizontal smear, and quantized block mosaic.
8. **Liquid Chrome Dimension** (`liquid_chrome_dimension.lsdfxchain`):
   - *Slots*: `mirror_sphere` → `wave_displace` → `color_levels`
   - *Vibe*: 3D raytraced floating chrome orb reflecting the scene with liquid surface turbulence and punchy high-contrast color grading.
9. **2D to 3D Elevation with Feedback** (`2d_to_3d_elevation_with_feedback.lsdfxchain`):
   - *Slots*: `3d_elevation` → `feedback` → `color_levels`
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

#### Bundled Multi-Chain FX Banks (`.lsdfxbank`)

Liquid LSD bundles 5 fully configured 3-chain rack presets in `library/fx_banks/` (and version-controlled in `defaults/fx_banks/`) designed to instantly load across **FX1**, **FX2**, or **MFX (Master FX)**:

1. **Club Master Finishers** (`club_master_finishers.lsdfxbank` — Optimized for `MFX`):
   - *Chain 1 (Subtle Optical Warmth)*: `color_levels` → `retro_crt` → `bloom` (Gentle tape/phosphor warming with contrast roll-off).
   - *Chain 2 (Stage Laser Glare)*: `anamorphic_streak` → `bloom` → `color_levels` (Concert lighting highlight streaks).
   - *Chain 3 (Drop Weapon Strobe)*: `directional_blur` → `video_strobe` → `rgb_split` (High-impact beat flash drop impact).
2. **Psychedelic Warp & Flow** (`psychedelic_warp_and_flow.lsdfxbank` — Optimized for `FX1`):
   - *Chain 1 (Liquid Mercury)*: `wave_displace` → `fluid_smear` → `luma_displace` (Viscous organic fluid deformation).
   - *Chain 2 (Hyperspace Trip)*: `kaleidoscope` → `radial_blur` → `color_levels` (Symmetric fractal zoom tunnel).
   - *Chain 3 (2D to 3D Elevation)*: `3d_elevation` → `feedback` → `color_levels` (Iconic topological mesh feedback).
3. **Analog Tape & Retro Terminal** (`analog_tape_and_retro_terminal.lsdfxbank`):
   - *Chain 1 (Grindhouse VHS Bootleg)*: `vhs_glitch` → `retro_crt` → `color_levels` (Helical scan tracking error & head switching).
   - *Chain 2 (Pop-Art Comic Print)*: `neon_edge` → `halftone` → `gradient_map` (CMYK lithographic dot rosettes & paper tint).
   - *Chain 3 (Cyberpunk 1984)*: `pixelate` → `retro_crt` → `gradient_map` (Honeycomb crystal mosaic & green terminal phosphor).
4. **Glitch, Strobe & Tactical Recon** (`glitch_strobe_and_tactical_recon.lsdfxbank` — Optimized for `FX2`):
   - *Chain 1 (The Drop Weapon)*: `directional_blur` → `video_strobe` → `rgb_split` (Transient beat flash gate).
   - *Chain 2 (FLIR Predator Vision)*: `thermal_scanner` → `directional_blur` → `pixelate` (Ironbow heat bloom & tactical sensor grain).
   - *Chain 3 (Digital Bitcrush Mosaic)*: `pixelate` → `rgb_split` → `video_strobe` (Lattice quantization & spectral glitch).
5. **Liquid Chrome & Dimensional Prisms** (`liquid_chrome_and_prisms.lsdfxbank`):
   - *Chain 1 (Liquid Chrome Dimension)*: `mirror_sphere` → `wave_displace` → `color_levels` (3D raytraced floating chrome orb).
   - *Chain 2 (Prismatic Crystal Kaleidoscope)*: `faceted_glass` → `kaleidoscope` → `rgb_split` (Cellular Voronoi gem facet refractions).
   - *Chain 3 (Cosmic Black Hole Vortex)*: `vortex_swirl` → `luma_displace` → `polar_tunnel` (Accretion disk swirl & infinite warp).

### FX Playlists (`.lsdfxplay`)

Column 2 of `[ FX ]` mode is a dedicated FX playlist editor, working like the preset Playlists column: switch between playlists with the dropdown, drag singles/chains in from the FX Browser to insert or reorder them, and double-click an entry to apply it. Missing files show the same red `[!] (missing)` indicator as preset playlists.

### Live FX Queues (A/B and BG)

While in `[ FX ]` mode, the Library's Background Queue and Play Queue columns (3 and 4) swap to two independent **live FX queues** — volatile, RAM-only sequences of FX singles/chains you can improvise with mid-set:

- **`<` / `>`** — Step to the previous/next queued FX item and apply it.
- **🔁 Repeat** — Cycle back to the start when the bottom of the queue is reached.
- **🔀 Shuffle** — Play items in random order.
- **Export** — Save the current live queue as a new `.lsdfxplay` playlist.
- **Clear** — Empty the queue.
- **Drag-and-drop** — Reorder items within a queue, or drag a preset/chain from the FX Browser to append or insert it.
- **Double-click** an item to jump straight to it.

The **A/B queue** applies to whichever of Deck A/B is currently dominant on the crossfader; the **BG queue** always applies to Deck BG.

### Audition Latch

Click **`[ Lock ]`** in the toolbar to enable audition mode. While latched, clicking any preset (or pressing `↑` / `↓`) immediately loads it into the preview deck (Deck PV) so you can see it without touching the live output. Click `A`, `B`, or `BG` while latched to target a different deck. Click the lock again to return to normal selection.

---

## Playlists (Setlists)

The playlist column is where you build your setlist. Playlists are `.lsdplay` files — simple ordered lists of presets.

- **Switch between playlists** — Use the dropdown at the top of the playlist column.
- **Reorder** — Drag and drop presets within the list. Changes save automatically.
- **Playlist actions (⋮ menu)**:
  - **Play Now** — Replaces the live queue with this playlist and starts playback.
  - **Insert After Current** — Slides the playlist into the live queue right after the currently playing preset.
  - **Add to Bottom** — Appends it to the end of the queue.

If a playlist references a preset that's been moved or deleted, the row appears in red with a `[!] (missing)` indicator. Right-click it to remove the dead link.

---

## The Play Queue (Auto-VJ)

The play queue drives the main crossfader automatically, sequencing through presets with transitions between Deck A and Deck B.

- **Transport controls** — `<` (previous), `▶/⏸` (play/pause), `>` (next).
- **Loop / Shuffle** — `🔁` for continuous looping, `🔀` for random order.
- **Add to queue** — Select a preset in the browser and press `Q`, or right-click and choose **Add to Queue**.
- **Export** — Save an improvised live queue as a permanent playlist file.

When the queue is playing, the crossfader moves automatically between decks as presets transition. Grabbing the crossfader manually immediately pauses Auto-VJ so you have direct control.

---

## Background Queue (Deck BG)

The Background Queue works the same way as the main queue but drives Deck BG independently. Add presets with `Shift+Q`. Background transitions use dip-to-black fades — double-click or right-click a queued item to choose between an instant cut or a fade.

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

### Transition Presets (`.lsdtrans`) & Playlists (`.lsdtransplay`)

- **Transition Presets (`.lsdtrans`)**: Stored in `library/transitions/`. Save dialed-in transition configurations (including parameter values, dry/wet, and modulation bindings) using the **[+]** button in the Transition Presets browser or right-clicking in the Mixer panel.
- **Transition Playlists (`.lsdtransplay`)**: Stored in `library/transition_playlists/`. Group transitions into ordered setlists for the Transition Queue. A factory playlist, `festival_elite.lsdtransplay`, is bundled out of the box.
- **AutoVJ Integration**: The Transition Queue automatically advances to the next staged transition preset or stock transition shader each time the crossfader cycles between decks.

---

## Drag & Drop

| From                       | To                             | Result                   |
| -------------------------- | ------------------------------ | ------------------------ |
| Preset Browser             | Playlist (between items)       | Inserts at that position |
| Preset Browser             | Empty space at playlist bottom | Appends to the end       |
| Playlist item              | Up / down in the same playlist | Reorders                 |
| Preset Browser or Playlist | Queue                          | Adds to the live queue   |
| FX Preset (`.lsdfx`)       | FX Slot 1–3 in Parameters      | Loads into target slot   |
| FX Chain (`.lsdfxchain`)   | FX Slot / Chain in Parameters  | Overwrites 3-slot chain  |
| FX Browser row              | FX Playlist (Column 2)         | Inserts/appends to playlist |
| FX Browser row              | Live FX Queue (A/B or BG)      | Appends/inserts into that queue |
| Live FX Queue item          | Up / down in the same queue    | Reorders                 |

---

## MIDI Mapping

> MIDI is disabled by default. Enable it in **Preferences → MIDI Controls** ("Enable MIDI Subsystem") or via the **⋮** kebab menu next to the Parameters panel's column headers ("MIDI Column").

Liquid LSD keeps hardware controller maps separate from visual presets, so you can swap physical controllers without touching your preset files.

**There are two levels of MIDI mapping:**

1. **Hardware Profile** (stored in `library/midi/default.json`) — Maps physical knobs and faders to global controls like the master crossfader, deck gain, and level faders. These bindings follow you regardless of which preset is loaded.

2. **Grid Cell Modulators** (stored inside each `.lsd` file) — Binds MIDI CC numbers to specific parameters as dynamic modulation sources in the CV grid. These travel with the preset.

### MIDI Learn

1. Click the **MIDI Learn** button in the Parameters header or next to any parameter slider.
2. Move a knob, fader, or button on your controller.
3. Liquid LSD captures the CC and confirms the binding automatically.
4. To unbind, right-click the mapped control and clear the MIDI assignment.
