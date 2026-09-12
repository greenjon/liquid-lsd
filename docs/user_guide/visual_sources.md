# Visual Sources

Each deck runs one visual source at a time. Sources range from the built-in procedural generators to ISF shaders you drop in from the internet or write yourself. On top of the source, each deck has two effect slots and a feedback loop for building up trails and texture.

---

## Picking a Source

Click the source selector in the **SRC** tab of any deck to open the shader picker. You can search by name, tag, or author. The picker automatically filters to show only sources appropriate for what you're assigning — generators for the main source slot, effects for the FX slots, and transitions for the mixer.

To remove the current source, click **Detach** or **None**.

---

## Built-In Generators

### Mandala

The most flexible built-in source. The Mandala engine generates intricate symmetrical geometry using around 300 curated harmonic ratios — basically: pick a harmonic ratio and it figures out the geometry. Built-in size normalization means the outer boundary stays stable and fills your vertical frame cleanly no matter how hard you push the parameters.

**3D modes** (set via the View tab): Mandala visuals can be wrapped spherically, projected inside a cube cage, mapped onto six intersecting planes, or unfolded into a 24-chamber kaleidoscope. These apply to any 2D source, not just Mandala.

**Good starting point:** Load a factory Mandala preset and use bass audio to drive the Lobes parameter. Even a single connection is immediately impressive.

### Icosa-Dodeca

A 3D polyhedron that morphs continuously between different geometric solids. The morph slider moves through:

| Value | Shape |
|-------|-------|
| 0.00 | Icosahedron (20 triangular faces) |
| 0.125 | Icosidodecahedron (20 triangles + 12 pentagons) |
| 0.25 | Dodecahedron (12 pentagonal faces) |
| 0.50 | Great Stellated Dodecahedron |
| 0.75 | Great Icosahedron |

**Tip:** Set Opacity to around 0.6–0.8 to make it translucent so you can see the internal facets.

### Dynamic Spiral

Radial curves that react well to audio. One of the easiest sources to get looking good quickly.

### Attractor Feedback

A strange attractor that builds up log-density trails over time. Rewards patience — let it run for a few bars and it develops complex structures. Great with the feedback loop turned up.

### Gyroid

A triply periodic minimal surface — it looks like a 3D foam or lattice structure. Still being refined, so some parameter combinations work better than others.

### Chladni

Visualizes acoustic nodal patterns — the geometric shapes that emerge when you vibrate a physical plate at resonant frequencies. Reacts interestingly to different frequency bands.

### Colors

A simple solid color field. Useful as a background layer or when you want total control over what's behind more complex foreground visuals.

---

## The 3D Stage (View Tab)

Every deck has a **View** tab that applies to whatever source is loaded. You can use it to position, scale, and project visuals before they hit the feedback loop:

- **Zoom** — Scales from 0.1× to 5.0×. At 1.0, the source fills the vertical frame height exactly.
- **Rotate Z** — Roll the image clockwise or counter-clockwise.

The **3D Display Mode** slider converts any flat 2D source into 3D geometry:

| Value | Mode |
|-------|------|
| 0.0 | Flat (standard 2D) |
| 1.0 | Tri-Axial Orthogonal — three intersecting planes forming a gyroscope shape |
| 2.0 | Cube Cage — maps visuals to six faces of a cube; increase Separation to explode them outward |
| 3.0 | Hex-Planar — six intersecting tetrahedral planes |
| 4.0 | Tetrahedral Kaleidoscope — 24-chamber space-folding kaleidoscope |

---

## Effect Slots (FX1 & FX2)

Each deck has two chained post-processing slots that run after the source and before the feedback loop:

- **FX Slot 1** — Typically used for color processing: grading, inversion, posterization, luma keying, degradation.
- **FX Slot 2** — Typically used for spatial effects: bloom, chromatic aberration, digital glitch, plane elevation.

The order is intentional but not enforced — you can put anything in either slot. Each slot has an independent bypass toggle and a Dry/Wet blend knob. Bypassed slots add zero overhead.

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

Go to **Settings → Shader Locations** to add any folder at runtime — handy for USB drives or git repos full of shaders. The status column shows whether each folder is active, missing (e.g. a USB stick that's not plugged in), or unreadable. Hit **Rescan Now** to pick up new files without restarting.

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
