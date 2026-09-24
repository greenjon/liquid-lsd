# Visual Sources

Each deck runs one visual source at a time. Sources range from the built-in procedural generators to ISF shaders you drop in from the internet or write yourself. On top of the source, each deck has four effect slots and a feedback loop for building up trails and texture.

---

## Picking a Source

Click the source selector in the **SRC** tab of any deck to open the shader picker. You can search by name, tag, or author. The picker automatically filters to show only sources appropriate for what you're assigning — generators for the main source slot, effects for the FX slots, and transitions for the mixer.

To remove the current source, click **Detach** or **None**.

---

## Built-In Generators

Liquid LSD includes a curated suite of 8 built-in procedural generators spanning harmonic geometry, 3D raymarching, 4D polytopes, organic fluid dynamics, and acoustic wavefields.

### Mandala (`mandala`)

The signature parametric harmonic curve engine. Mandala generates intricate symmetrical geometry using over 300 curated harmonic ratios. Built-in size normalization guarantees the outer boundary stays stable and fills your frame cleanly no matter how aggressively you modulate parameters.

- **Lobes**: Selects the petal-count harmonic group (3 to 26 lobes).
- **Recipe Select**: Browses individual frequency ratios within the active Lobes group.
- **Arm Lengths (L1–L4)**: Sets harmonic amplitude from primary outer petals down to inner detail loops.
- **3D Elevation**: Pair with the `3d_elevation` filter in any FX slot to wrap Mandala into a spherical gyro, cube cage, 6-plane intersection, or 24-chamber kaleidoscope.

### Dynamic Spiral (`dynamic_spiral`)

A high-performance particle dynamics system where particles spiral outward under radial wave, shear, and velocity damping forces.

- **Max Points**: Particle density across the visible spiral.
- **Wave Freq & Wave Amp**: Modulates particle trajectories into undulating ripple formations.
- **Shear**: Applies tangential twisting to particle velocity, creating spiral vortices.
- **Trail Decay**: Governs the longevity of particle streaks. Low decay produces luminous, silky light trails.

### Icosa-H3 (`icosa_h3`)

A native 3D Coxeter $H_3$ raymarcher combining a continuous Icosahedron/Dodecahedron duality morph with an independent spike-and-blocker stellation CSG.

- **Morph**: Continuous 4-stage cyclic Coxeter morph:
  - `0.00 – 0.25`: Icosahedron (20 triangular faces) $\to$ Dodecahedron (12 pentagonal faces).
  - `0.25 – 0.50`: Dodecahedron $\to$ Great Stellated Dodecahedron.
  - `0.50 – 0.75`: Great Stellated Dodecahedron $\to$ Great Icosahedron.
  - `0.75 – 1.00`: Great Icosahedron $\to$ Icosahedron.
- **Support H**: Wythoff facet cutting: negative values truncate vertices (e.g. $-0.15$ yields the truncated icosahedron / Buckyball), while positive values bevel edges.
- **Opacity**: Translucency control ($0.6 \dots 0.8$ reveals internal self-intersecting facets).
- **Camera Controls**: Native 3D pitch (`RotateX`), yaw (`RotateY`), roll (`RotateZ`), and `Zoom` exposed in the View tab.

### Domain Warp Fluid (`domain_warp_fluid`)

A multi-scale domain-warped fluid simulation with fractional Brownian motion (fBm) curl noise, dynamic vorticity, surface specular normals, and iridescent liquid marbling. Evokes 1960s liquid light overheads, oil-on-water marbling, and liquid metal.

- **Warp Strength & Swirl**: Dial from gentle chromatic ripples to turbulent psychedelic marbling.
- **Specular Gloss**: Computes finite-difference surface normals and specular highlights for a wet, embossed liquid look.
- **Palette Modes**:
  - `0`: Psychedelic Neon (Cyan / Magenta / Gold / Emerald)
  - `1`: Liquid Chrome / Mercury
  - `2`: Oil Slick / Prismatic Iridescence
  - `3`: Opal Sunset / Warm Fire & Dusk
  - `4`: Deep Ocean Bioluminescence

### Gyroid Hyperspace (`gyroid_hyperspace`)

A native 3D raymarcher rendering Triply Periodic Minimal Surfaces (TPMS) with continuous morphing between Gyroid, Schwarz P, and Neovius minimal surfaces, volumetric internal radiance, and camera flight.

- **Surface Type**: Smoothly morphs the minimal surface geometry between Gyroid (`0.0`), Schwarz P (`1.0`), and Neovius (`2.0`).
- **Wall Thickness & Hole Scale**: Transitions from delicate skeletal lattices to dense porous bio-sponges.
- **Wireframe Mode**: Carves open coordinate lattice struts to reveal an architectural cage.
- **Flight Speed**: Camera flight forward through the infinite labyrinth.
- **Core Glow**: Volumetric radiance accumulating inside internal chambers.

### Celestial Engine (`celestial_engine`)

A multi-symmetry sacred geometry and op-art generator combining Flower of Life overlapping circles, concentric harmonic rings, phase twisting, and moiré fringes.

- **Symmetries**: Discrete rotational symmetry fold count from 3 to 24.
- **Ring Density & Moiré Strength**: Cross-modulates radial harmonic rings to produce shimmering optical interference fringes.
- **Flower Fold**: Folds the coordinate space into intersecting Flower of Life geometry.
- **Pulse Wave**: Expanding radial shockwave ring, ideal for kick/snare CV modulation.

### 4D Hyper-Slice (`hyper_slice`)

A raymarched 3D cross-section (MRI scan) through 4-dimensional 120-cell (600 vertices) and 600-cell (120 vertices) polytopes using the $H_4$ Coxeter reflection group (order 14,400).

- **Slice Offset W**: Sweeps the 3D cutting hyperplane along the 4th dimension ($W$ axis), revealing shifting geometric cross-sections.
- **4D Hyper-Rotations (`RotateXW`, `RotateYW`, `RotateZW`)**: Rotates the polytope through 4-space, turning geometry inside-out in 3D.
- **Polychoron Morph**: Smoothly interpolates the facet normals between the 600-cell and 120-cell.
- **Support H**: Adjusts Wythoff facet cutting distance.
- **Face Opacity & Edge Highlight**: Crystal interior reveal with glowing ridge boundaries.

### Chladni Cymatics (`chladni_cymatics`)

A physical 2D acoustic plate resonance simulation computing standing wave nodal harmonics across square and circular boundaries, complete with particle accumulation physics.

- **Frequency M, N, L**: Acoustic modal frequencies governing standing wave harmonic interference patterns.
- **Plate Shape**: Blends smoothly from square plate resonance (`0.0`) to circular Bessel vibration (`1.0`).
- **Sand Accumulation & Node Sharpness**: Simulates sand grains migrating away from kinetic antinodes and gathering along quiet nodal lines.
- **Invert Mode**: Swaps behavior from quiet nodal sand gathering (`0.0`) to kinetic fluid pooling in energetic antinodes (`1.0`).
- **Palette Modes**: Obsidian & Gold Sand, Electric Cymatic Blue, Prismatic Spectrum, and Bioluminescent Emerald.

---

## Deck Transform & Source Parameters (SRC Tab)

Every deck has an **SRC** tab that combines universal canvas framing with the active generator's native parameters:

- **Gain** — Global source opacity / intensity level (positioned at row 0).
- **Zoom** — Scales from 0.1× to 5.0× (for 2D sources). At 1.0, the source fills the vertical frame height exactly.
- **Rotate Z** — Roll the image clockwise or counter-clockwise (for 2D sources).
- **Generator Parameters** — All ISF generator inputs (lobes, frequencies, noise scale, color palette) follow immediately after the transform controls.

For **native 3D sources** (e.g. Gyroid, Hyper-Mesh, Icosahedron), the shader handles camera transforms internally, so the deck-level 2D Zoom/Rotate Z sliders are automatically omitted in favor of the generator's internal camera rotation (`Rotate X`, `Rotate Y`, `Rotate Z`) and zoom controls.

To elevate any flat 2D source into 3D geometry (Tri-Axial, Cube Cage, Hex-Planar, or Tetrahedral Kaleidoscope), select the **3D Elevation** filter in any FX slot under the **FX** tab.

---

## Effect Slots (FX1–FX4)

Each deck has four chained post-processing slots that run after the source and before the feedback loop:

- **FX Slot 1** — Typically used for color processing: grading, inversion, posterization, luma keying, degradation.
- **FX Slot 2** — Typically used for spatial effects: bloom, chromatic aberration, digital glitch, plane elevation.
- **FX Slot 3 & FX Slot 4** — Additional chained slots for stacking further processing.

The order is intentional but not enforced — you can put anything in any slot. Each slot has an independent bypass toggle and a Dry/Wet blend knob. Bypassed slots add zero overhead.

---

## Feedback Loop

Each deck has an internal feedback loop that feeds the current frame back into the next render. This is what creates the liquid trails, echo tunnels, and smeared colour effects that give Liquid LSD its name.

The feedback controls live in the **FBK** tab of each deck:

- **Decay** — How quickly the trail fades. Higher values = longer trails.
- **Gain** — Amplifies the feedback signal. Push it high for glowing, blooming trails.
- **Zoom** — Scales the feedback frame slightly each cycle, creating zoom-in or zoom-out tunnel effects.
- **Rotate** — Rotates the feedback frame each cycle. Combine with Zoom for spiral tunnels.
- **Hue Shift** — Rotates the hue of the feedback frame each cycle.
- **Blur** — Softens the feedback trail.
- **Chroma Offset** — Separates RGB channels in the feedback trail for prismatic fringing.

Feedback is one of the most expressive parts of the system. It rewards experimentation.

---

## Loading Your Own ISF Shaders

Liquid LSD uses the standard **Interactive Shader Format (ISF v2.0)** for custom generators, effects, and transitions. Any ISF shader from [editor.isf.video](https://editor.isf.video) or similar sources will load directly into the app.

### Where to put your shaders

Drop `.fs` (and optional `.vs`) shader files into the right folder:

- **Generators** → `library/sources/` (or your system's standard ISF directory)
- **Post-processing effects** → `library/filters/`
- **Mixer transitions** → `library/transitions/`

The app reads the ISF JSON header inside each shader file, automatically sets up parameter sliders in the Cell Config panel, and exposes those parameters to the CV modulation matrix. No configuration required.

### Standard ISF search paths

Liquid LSD also scans these locations at startup:

| Platform | Path |
|----------|------|
| macOS | `/Library/Graphics/ISF/` and `~/Library/Graphics/ISF/` |
| Windows | `C:\ProgramData\ISF\` and `%LOCALAPPDATA%\ISF\` |
| Linux | `/usr/share/isf/`, `/usr/local/share/isf/`, and `~/.local/share/isf/` |

### Adding custom folders

Go to **Preferences → Shader Locations** to add any folder at runtime — handy for USB drives or git repos full of shaders. The status column shows whether each folder is active, missing (e.g. a USB stick that's not plugged in), or unreadable. Hit **Rescan Now** to pick up new files without restarting.

### Live coding

If you're writing or tweaking shaders while the app is running, just save your file — Liquid LSD detects the change and recompiles in the background. If the shader has a syntax error, the current frame keeps running while the error is logged. No interruptions on stage.

---

## External Video Input

You can also use a live video feed from another app as a deck source. External video streams appear directly inside the **Shader Picker** alongside procedural generators:

- **macOS:** Syphon servers
- **Windows:** Spout2 senders
- **Linux:** PipeWire video nodes (webcams, OBS virtual cameras, desktop capture, etc.)

Click the source selector button in the deck header (e.g. `[Mandala ▾]`), select the **External Sources** category pill (positioned immediately beside **All**), and click your stream (highlighted in emerald green with a live activity icon). Once selected, the deck header button directly displays the name of the active stream (e.g. `[OBS-Camera ▾]`).

The incoming video runs through the full deck pipeline — you get 3D transforms, dual effect slots, audio-reactive modulation, and the feedback loop, all applied to the external feed.
