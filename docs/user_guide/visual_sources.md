# Visual Sources

Each deck runs one visual source at a time. Sources range from the built-in procedural generators to ISF shaders you drop in from the internet or write yourself. On top of the source, each deck has a chain of three effect slots.

---

## Picking a Source

Click a deck's source badge to open its pair view with Browse (see [Your Workspace](your_workspace.md) / [Macros & the Modular Rack](macros_and_rack.md)). You can search by name, tag, or author. Browse automatically filters to show only sources appropriate for what you're assigning — generators for the main source slot, effects for the FX slots, and transitions for the mixer. Picks apply immediately and the list stays open, so you can try several in a row.

To remove the current source, click **Detach** or **None**.

---

## Live Video Input

Besides generating its own video, a deck can show live video from another app or device. Liquid LSD receives the same GPU-sharing protocols it [sends](output_and_recording.md#sending-video-to-other-apps-spout-syphon-pipewire):

- **Windows:** Spout senders
- **macOS:** Syphon servers
- **Linux:** PipeWire video streams

Every stream running right now is listed in Browse under **External video** (see [Presets & Library](presets_and_library.md)). The list refreshes every couple of seconds, so a stream appears shortly after its app starts sending and disappears when it stops. Load one like any other source. A live stream has a single **Gain** parameter in place of a generator's parameter list, and the deck's FX chain, levels and transitions work on it exactly as they do on a generator.

Streams are not files, so they cannot be saved as presets, added to playlists or queued.

---

## Built-In Generators

Liquid LSD includes a curated suite of 34 built-in procedural generators spanning harmonic geometry, 3D raymarching, 3D fractals, volumetric gas, 4D polytopes, organic fluid dynamics, living simulations, acoustic wavefields, and a set of light generators that run comfortably on integrated graphics.

The raymarchers (Icosa-H3, Gyroid Hyperspace, 4D Hyper-Slice, Nebula Volume and the fractals below) show a warning triangle in the browser: they are GPU-intensive and can run slowly on integrated graphics. The fractals are tuned for a mid-range discrete GPU (an RTX A2000 or better); every one has a **Quality** knob, and lowering it is the first thing to try if a deck stutters.

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

### Mandelbox (`mandelbox`)

A raymarched Mandelbox: space is repeatedly folded into a box, inverted in a sphere and scaled, producing cathedral-like chambers. The camera glides in and out and stays outside the solid.

- **Scale**: The fold scale. Right of centre gives the classic box, left of centre the inverted variants. It jumps at the centre, so avoid automating across it.
- **Fold Limit, Min Radius, Max Radius**: Box-fold size and the two sphere-fold radii; they reshape the chambers.
- **Quality**: Ray-march steps, fold iterations and ambient-occlusion taps together.
- **Flight Speed, Glow**: Signed in-and-out camera motion, and a halo from near-miss rays.
- **Color Mode**: Orbit trap (origin), orbit trap (planes), sphere-fold fraction, normal spectrum, depth.

### KIFS (`kifs`)

A kaleidoscopic iterated-function-system fractal: repeated folds, a scale and an offset toward a fixed vertex, with a rotation added at every iteration.

- **Fractal Type**: Menger-like, Sierpinski tetrahedron, octahedral, or a mixed construction.
- **Scale, Offset**: Look controls on the construction; away from the centre it stops tiling exactly.
- **Twist X/Y/Z, Morph Speed**: Per-iteration rotation, and a time-driven rotation that continuously reshapes the form.
- **Flight Speed**: Endless self-similar dolly toward the fixed vertex; negative flies outward.
- **Quality, Glow, Color Mode**: Detail versus speed, a grazing-ray halo, and five colourings.

### Mandelbulb (`mandelbulb`)

A raymarched Mandelbulb, the 3D analogue of the Mandelbrot set built on a power map in spherical coordinates, with a turntable camera.

- **Power**: Exponent from 2 to 16 (default 8). Non-integer values show a cut seam.
- **Power Swing**: A slow automatic oscillation of the power; 0 keeps it fixed.
- **Julia Mix**: Blends toward a Julia set.
- **Bailout**: Escape radius; higher keeps more of the set at higher cost.
- **Quality, Flight Speed, Glow, Color Mode**: Detail versus speed, turntable speed (signed), halo, five colourings.

### Pseudo Kleinian (`pseudo_kleinian`)

A fly-through of a Kleinian-style structure: a periodic mirror fold followed by repeated sphere inversion, with the camera travelling down an open channel.

- **Slab Size, Inversion Radius, Inversion Strength**: Spacing of the repeating structure, and how the inversion carves and magnifies it.
- **Offset X/Y/Z**: Shifts that reshape the channels and walls.
- **Core Size**: Wall thickness. Near the maximum the channel can fill in and the frame goes flat.
- **Quality, Flight Speed, Color Mode**: Detail versus speed, signed fly-through speed, four colourings.

### Apollonian (`apollonian`)

A fly-through of an Apollonian-style sphere gasket: space is tiled into cells and inverted in a sphere, packing smaller and smaller spheres into the gaps.

- **Cell Size, Inversion Radius, Inversion Strength**: Lattice spacing and the inversion that sets how the spheres touch. Very small radii collapse the gasket to flat walls.
- **Twist**: Per-iteration rotation that bends the lattice into spiralling forms.
- **Core Size**: Sphere thickness.
- **Quality**: Defaults low (0.08) for two-deck headroom; raise it for finer small spheres.
- **Flight Speed, Color Mode**: Signed fly-through speed, four colourings.

### Escape Time (`escape_time`)

High-iteration 2D escape-time fractals with smooth colouring and orbit traps.

- **Fractal Type**: 0 = Julia set, 1 = Burning Ship Julia, 2 = relaxed Newton on z^3 - 1 (one colour family per root), 3 = Mandelbrot.
- **Julia Radius, Julia Angle, Morph Speed**: Position of the Julia constant and how fast it orbits the origin, which morphs the set. In Newton they set the relaxation of the step. Mandelbrot ignores them.
- **Power**: Exponent of the iteration (2 to 5); fractional values show a seam.
- **Detail**: Iteration cap (48 to 400). Above 0.75 it also turns on 2x2 antialiasing, which costs four times as much.
- **Zoom, Center X/Y, Rotation**: Own camera, magnifying 0.3x to 3000x. fp32 breaks down at the top of the range (the image goes blocky); deep zoom is not supported.
- **Color Mode, Color Cycle, Trap Glow**: Smooth escape count, orbit trap, final angle or monochrome; hue cycling speed and trap glow strength.

### Nebula Volume (`nebula_volume`)

A volumetric nebula: the camera flies through glowing gas filaments that are lit by a drifting inner light and shadow themselves, with a starfield behind.

- **Density, Coverage, Scale, Turbulence**: Gas opacity, how much of the volume is filled, the size of the structures and the warp that twists them.
- **Detail**: Noise octaves, 1 to 5.
- **Quality, Lighting**: View-ray steps and self-shadowing taps (0 to 4). These are the cost knobs; lower them first if a deck stutters. The defaults are light, and Detail 4 with Quality and Lighting at maximum is very heavy.
- **Flight Speed, Drift**: Signed camera flight and the speed at which the gas evolves.
- **Glow, Color Mode**: Emission brightness and four colourings.

### Reaction Diffusion (`reaction_diffusion`)

A Gray-Scott chemical simulation on a persistent 512 x 512 grid that grows maze, worm, coral, spot and splitting-cell patterns, lit as relief. The grid is fixed, so it costs the same at any output size and does not show a heavy-shader triangle.

- **Regime, Feed Trim, Kill Trim**: Position along the feed/kill path through the pattern types, and fine trims. After a change the pattern needs a few seconds to re-form.
- **Diffusion**: Scale of the pattern.
- **Sim Speed**: Time step per frame, so the pattern evolves faster at higher frame rates.
- **Reseed**: Crossing the middle seeds the grid again once. An empty grid, for example after a resize, seeds itself.
- **Relief, Zoom, Color Mode**: Embossed lighting, magnification of the grid, four colourings.

### Fluid Ink (`fluid_ink`)

Ink in a persistent stable-fluids simulation (384 x 384): orbiting emitters stir and dye an incompressible flow that forms shear layers and curls.

- **Emitters, Stir Force, Swirl, Turbulence**: How many emitters (1 to 4), how hard they push, curl preservation and noise forcing.
- **Viscosity, Dye Fade**: Flow damping and how quickly the ink fades.
- **Projection**: How completely the flow is made incompressible; 1 is the proper setting.
- **Sim Speed**: Time step per frame. 0 freezes the simulation.
- **Color Mode**: Four colourings of the ink.

### Lenia (`lenia`)

A continuous cellular automaton on a persistent 256 x 256 grid. A smooth ring kernel and a bell-shaped growth rule produce soft blobs, rings and stripes. This version does not reliably produce the gliding creatures of the published Lenia; expect living textures rather than travelling organisms.

- **Growth Center, Growth Width**: The growth rule. Narrow widths at a high centre can kill the pattern; reseed if the field goes dark.
- **Kernel Radius**: Neighbourhood size, 5 to 13 cells; larger is bigger structures and costs more.
- **Sim Speed, Seed Density, Reseed**: Time step per frame, amount of seed material and a latch that reseeds when crossed.

### Quasicrystal (`quasicrystal`)

Five to eight rotated cosine gratings with drifting phases add up to Penrose-like quasi-periodic patterns. Light enough for integrated graphics, like the other generators in this group (no GPU-intensive triangle).

- **Gratings**: How many gratings, 5 to 8. Six gives a plain hexagonal lattice rather than a quasi-periodic one.
- **Style, Palette**: Smooth, banded, two-tone or neon-line rendering, and four cosine palettes.
- **Frequency, Bands, Sharpness**: Detail scale, number of contour bands and edge hardness.
- **Rotation, Spin Speed, Drift Speed**: Static rotation, continuous spin and phase drift. Negative speeds reverse.
- **Quality**: From half up, 2 x 2 supersampling.

### Thin Film (`thin_film`)

Oil-slick iridescence. A slowly flowing, domain-warped noise field is read as the thickness of a thin film and coloured by interference, with a shift by view angle and a specular sheen.

- **Flow Speed, Scale, Turbulence**: How fast, how large and how marbled the flow is.
- **Film Thickness, Thickness Range, Contrast**: Where in the colour sequence the film sits, how much it varies, and how strongly the relief bends the colours.
- **Sheen**: Strength of the highlight.
- **Quality**: Noise octaves, 1 to 4.

### Tunnel Scenes (`tunnel_scenes`)

Three analytic tunnel flythroughs with no ray marching.

- **Scene**: A synthwave grid floor and ceiling with a horizon sun, a Truchet-tiled tunnel, or an N-sided wormhole.
- **Sides**: Number of sides of the wormhole, 3 to 12.
- **Speed, Twist, Camera Sway**: Forward speed (negative reverses), spiral twist, and camera drift.
- **Glow, Fog, Line Width**: Line glow, distance fade and line thickness.
- **Quality**: Layer count and antialiasing width.

### Video Feedback (`video_feedback`)

Classic video feedback as a generator. A small persistent frame is re-sampled each frame with zoom, rotation and drift, decayed, hue-shifted and re-fed with moving seed shapes, giving endless tunnels, spirals and trails.

- **Zoom, Rotation, Hue Shift**: Per-second change applied to the previous frame; negative values reverse.
- **Decay**: How quickly trails fade.
- **Seed Shape, Seed Size, Drift**: The shape fed into the loop (ring, polygon, spinning line, soft blob), its size, and the wandering of the whole image.
- **Quality**: Blur taps in the loop.
- The buffer is square, so on a widescreen output about half of its height is visible.

### Hyperbolic Tiling (`hyperbolic_tiling`)

A Poincare-disk {P,Q} tiling built by mirror folds, in the manner of Escher's Circle Limit prints, with edge glow and a drifting view.

- **Tile Sides P, Tiles Per Vertex Q**: The tiling, 3 to 10 each. If the pair is not hyperbolic, Q is raised to the nearest valid value (3,3 shows 3,7).
- **Speed, Drift, Spin**: View movement across the disk and rotation of the tiling.
- **Edge Width, Glow, Subdivision Lines**: Edge styling.
- **Color Mode, Outside Disk**: Tile colouring and what fills outside the disk.
- **Quality**: Cap on fold iterations.

### Cyclic CA (`cyclic_ca`)

A cyclic cellular automaton (a Belousov-Zhabotinsky look) on a persistent 256 x 256 grid. Noise organises itself into rotating spirals and waves; allow a few seconds for the spirals to form.

- **States, Threshold, Neighbourhood**: How many cyclic states, how many neighbours one state ahead make a cell advance, and which cells count as neighbours. Few states with a low threshold make clean spirals; many states or a high threshold can freeze into static noise.
- **Speed, Seed Density, Reseed**: Generations per second, how much of the grid starts non-zero, and a latch that restarts from fresh noise.

### Metaballs (`metaballs`)

Four to twelve balls on Lissajous orbits merge into one field.

- **Balls, Ball Size, Size Variance**: How many, how big, and how different in size.
- **Style**: Shaded blobs, iso-line contours, neon outlines or a gooey two-tone.
- **Orbit Speed, Orbit Spread, Merge, Softness**: Motion and how readily the balls fuse.

### Wave Ripples (`wave_ripples`)

Interference of two to eight travelling circular waves from orbiting sources.

- **Sources, Layout**: How many sources, arranged on Lissajous paths, one ring or two counter-rotating rings.
- **Wavelength, Wave Speed, Orbit Speed, Falloff**: The wave and how the sources move.
- **Style**: Shaded height field, banded contours, neon crests or a thin-film ramp.

### Harmonograph (`harmonograph`)

Oscilloscope and harmonograph curves: a damped Lissajous figure drawn as a glowing phosphor line with a fading trail. Lower Quality first on integrated graphics.

- **Ratio X:Y, Detune**: The frequency ratio and a small offset that makes the figure slowly rotate and open up.
- **Phase Drift, Damping, Trail Length, Rotation**: How the curve evolves.
- **Line Width, Glow**: Line look.
- **Quality**: Number of line segments, 16 to 64.

### Aurora Ribbons (`aurora_ribbons`)

Aurora borealis: three to six layered curtains over a faint starfield.

- **Ribbons, Curtain Height, Wave Amplitude, Wave Speed, Shimmer**: Shape and motion of the curtains.
- **Sky Darkness, Horizon**: The sky, and whether it is sky only, a mountain silhouette or a water reflection.
- **Quality**: Noise octaves, 1 to 4.

### Isometric Voxels (`isometric_voxels`)

A Q*bert-style isometric cube field with face shading.

- **Wave**: Height pattern: ripple, noise or diagonal sweep.
- **Cell Size, Max Height, Wave Speed, Wave Scale**: Cube size and the height wave.
- **Edge Width, Face Contrast**: Line thickness and the shading difference between faces.

### Halftone Matrix (`halftone_matrix`)

A moving pattern drawn as a grid of dots.

- **Pattern, Dot Style**: Plasma, radial pulse, diagonal bands or noise, drawn as round dots, square LED pixels, line screens or a CMYK-style colour halftone.
- **Grid Density, Dot Sharpness, Pattern Speed, Pattern Scale, Grid Rotation, Contrast**: The grid and the pattern.

### Kali Set (`kali_set`)

A Kali-family inversion-fold fractal in the plane (z becomes |z| / (z . z) minus c), coloured by orbit traps, with the constant c orbiting.

- **Orbit Radius, Orbit Speed**: How far and how fast c moves; it changes the whole shape.
- **Iterations, Quality, Twist Per Step, Trap Type, Trap Scale**: Detail, per-step rotation and the colouring trap.
- **Zoom, Centre Drift**: View.

### Girih Stars (`girih_stars`)

Islamic star-and-polygon strapwork.

- **Star Points**: 4, 6 or 8 points per star; this also picks the square, hexagonal or 4.8.8 lattice.
- **Style**: Woven bands, thin lines, or coloured regions.
- **Tile Scale, Contact Angle, Angle Sweep, Morph Speed, Line Width, Interlace, Glow**: Geometry and animation.

### Glitch Blocks (`glitch_blocks`)

A glitch and datamosh look with no image input: block displacement, scanline offsets, tearing, RGB split, posterise and dropouts over a test pattern.

- **Base Pattern**: Gradient bars, test-card bars, diagonal stripes or noise.
- **Glitch Intensity, Block Size, Glitch Rate, Tear Amount, Channel Split, Crush**: The corruption. Changing Glitch Rate reshuffles the sequence.

### Voronoi Cells (`voronoi_cells`)

Animated Worley cells.

- **Style, Metric**: Flat cells, cracked-glass edges, distance rings or a faceted gem, with a Euclidean, Manhattan or Chebyshev distance.
- **Cell Scale, Jitter, Speed, Edge Width, Edge Glow**: The cells.
- **Relief, Zoom, Color Mode**: Embossed lighting, magnification, four colourings.

### Fractal Flame (`fractal_flame`, experimental)

A fractal-flame generator: millions of points are pushed through a handful of randomly chosen transforms (xforms), splatted into a density buffer and tone-mapped, which gives the feathered, glowing filaments the technique is known for. It is the one generator built around **evolving** a look rather than just tweaking it. The math follows Draves & Reckase, "The Fractal Flame Algorithm" (credited in the licenses page, under Algorithm Credits).

**The genome.** A flame is up to six xforms (`X1`..`X6`) plus an optional final xform (`XF`). Each xform has a weight and colour, a *pre* and a *post* affine (rotate, scale, shear, move) and four variation slots. A variation is one of 98 shape functions: the paper's 49 (linear, sinusoidal, spherical, swirl, julia, blob, ...), 46 more (exp, log, the trigonometric and hyperbolic family, wedge, curve, bipolar, flux, ...), hemisphere, plusrecip and pre_blur (which blurs the point before the other variations act, so pair it with one); slots with parameters show them under variation-specific names. Every field is an ordinary parameter, so modulators, macros and MIDI/OSC learn work on all of them.

**Editing.** Open the deck's Edit row, SRC sub-tab. The tab strip has **Look** (camera, tone, quality), **Mods** (the performance handles below and `Mutation`), one tab per active xform and **XF**. `+ Xn` at the end reveals the next unused xform.

- **Units.** Every flame control reads 0-100 or -100-100 (angles -180-180), like the bundled generators. The numbers are positions on the control's range, not the shader's own units: for the zoom-like controls (Zoom, Magnify, Exposure) 50 means "unchanged" (1x) and equal steps are equal ratios; for Breathe and Warp 50 is neutral too; scales and shears span -4x..4x, moves -10..10, Gamma 1-4, Decay 0-0.98, Softness 1-10 px, Sharpness 0.5-8, Toe 0-5, Brightness 0-4, xform and variation weights 0-2 and -4..4. Flame presets saved before this change (including Garden offspring and imported flames) read as different values and need re-importing or re-making.
- **Spin, Breathe, Warp, Drift** (Mods): one parameter moves every xform at once: Spin adds to every rotation, Breathe scales every xform, Warp blends the non-linear variations up against linear, Drift offsets colour speed. The default macro knobs are **ZOOM, SPIN, WARP, EVOLVE**. EVOLVE sets `Mutation`, how far the next morph target drifts from the current flame (Breathe is no longer on a knob; bind it from the Edit row). ZOOM drives **Magnify** (Look tab, 0.25-4x, shown 0-100 with 50 = 1x), a zoom on top of the flame's own framing: **Zoom** and the centre belong to the genome and drift with the morph, so a knob on them would be overwritten while the deck morphs.
- **Decay** keeps the density buffer between frames. 0 renders each frame clean; a Decay of about 0.75-0.9 (77-92 on the control) fills in fine filaments from few points and leaves soft trails. Brightness is normalised, so changing Decay changes smoothness, not brightness. **Toe** removes isolated single-hit speckle; **Exposure**, **Brightness**, **Gamma**, **Vibrancy** shape the tone map, **Sharpness** the falloff of each splat (see Softness for its size).
- **Softness** is the size of each point in pixels (1-10 px, shown 0-100, default 22). Bigger points smooth a flame that has too few hits per pixel, such as a flame with a wide spread or a large deck; brightness is kept. Imported flames set it from the original's smoothing filter.
- **Smooth** (0-1, default 0 = off) blurs the sparse parts of a flame and leaves dense parts sharp: where there are too few hits per pixel it falls back to a coarser, averaged version of the picture, so scattered dots merge into soft filaments while bright cores keep their detail. Total light is kept. It costs a little GPU time while above 0. Imported flames start at 50.

**Evolving a look.** The die beside the deck row makes a brand-new random genome and frames the camera on it. Blank, exploded, collapsed and fog-like candidates are rejected automatically and re-rolled. Next to the die, the **R / M / P / B** button picks how the *next* morph target is chosen, for the die and for the deck's Rand morph:

| | Strategy | Next target |
| :-- | :-- | :-- |
| R | Random | an unrelated new genome |
| M | Mutate (default) | a drift away from the current genome; the `Mutation` parameter (Mods tab) sets the strength, so it can be modulated or put on a macro |
| P | Pool | one of your kept offspring (the Garden) |
| B | Breed | a cross of two Garden offspring (whole xforms from one parent or the other), then mutated |

The Garden starts with about 150 curated flames (`library/presets/garden/curated`, installed on first run; *Restore Factory Presets* brings back any you deleted), so Pool and Breed have stock from day one. Delete or add files there like any other. Pool and Breed fall back to Random/Mutate while the Garden is empty. They favour newer offspring (the newest is twice as likely as the oldest) and ones you starred in the Sources browser (three times as likely). Every new target brings its own framing, so the camera drifts along with the flame instead of staying where the first flame left it. The choice is saved with the preset when it is not the default.

**Keep, Skip and the Garden.** At the right end of the editor's tab strip, the star **keeps** what the deck is playing as a new *offspring* (in the middle of a morph it saves the nearer end, a real genome, not the blend; the preview image shows the blend): a normal preset in `library/presets/garden/` (`fractal-flame-0001.lsd`, ...) tagged `garden`, `gen:N` and `parent:<name>` for the offspring it was bred from, with a small preview image beside it. Offspring show up in Browse under *Saved presets > garden*, hovering one shows its preview, and they can go into playlists and queues like any preset. **Skip** throws away the genome the morph is heading to and picks another with the current strategy (the picture jumps to the new target at the current morph position).

**Quality and performance.** **Flame Quality** (Low / Medium / High, *Preferences > General*) sets how many points, drawing passes per frame and what sprite size every flame deck uses on this machine; cost is roughly proportional to points times passes. It is a per-machine setting, not saved in presets, so a flame looks the same, only smoother or rougher, on a slower or faster GPU. Low suits integrated graphics. Under *Preferences > General*, **Flame GPU Budget (ms)** (default 3 ms per flame deck, 0 = off) lets the app lower the point count automatically when a deck runs over budget and win it back when there is room, with unchanged brightness, so a show holds its frame rate on integrated GPUs. the Flame Quality tier's point count stays the ceiling.

**Importing `.flame` files.** In the Sources browser, the `...` menu has **Import Flame...**. Pick a flame genome (your own file) and every flame in it becomes a Fractal Flame preset in `library/presets/imported-flames/`. Xforms, affines, the final xform, up to three variations per xform and the camera come across. Variations Fractal Flame does not have (super_shape, lazysusan and mobius are the ones skipped for now; `linear3D` and `spherical3D` come in as their flat twins `linear` and `spherical`, and the notes say so), values beyond a parameter's range, the tone settings, and any 3D camera do not (the palette does come across and is saved inside the preset, so it stays portable); the preset's notes say what was dropped. Imported flames come in with Toe 0 (the original has no toe, and a toe wipes out a sparse flame), a Softness from the original's filter, Smooth 0.5, and Decay 0.85, because they were made for millions of samples and this generator draws far fewer; lower it for crisp, trail-free frames. Flames whose attractor spreads far beyond the view (rings2-1.flame is one) still look sparse on a large deck even with Smooth (raise it, or Softness, for a softer look): a flame's shape is exact, but its smoothness needs samples. They are a starting point: tune Exposure, Decay and Toe to taste, and breed or mutate them like any other.

Fractal Flame is experimental in this build: it is not part of the stock generator set yet, and the web player version is not done.

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

## Effect Slots (FX1–FX3)

Each deck has a chain of three effect slots that run after the source, left to right: FX1 feeds FX2, which feeds FX3. Master FX is a fourth chain of three slots, applied to the final mix (see [Performance Controls](performance_controls.md)).

Any effect can go in any slot, but order matters: a blur before a kaleidoscope looks different from a kaleidoscope before a blur. Each slot has its own bypass toggle and Dry/Wet blend knob, and bypassed slots add zero overhead.

In the Edit bay's **FX** tab the slots form an accordion: one slot's parameters are open at a time, and opening another collapses the first. Collapsed slots still show their effect and Dry/Wet percentage, and the chain Wet/Dry stays at the top. The **Chain Macro** section (Super Knob and Metaknobs) has its own chevron, so you can fold it away when you're editing slot parameters.

---

## Feedback

Feedback is an effect like any other: put **Feedback** in any slot to feed each frame back into the next render. This is what creates the liquid trails, echo tunnels, and smeared colour effects that give Liquid LSD its name. Where it sits in the chain decides what gets trailed: the effects before it are fed back with the image, the effects after it process the trails.

Its main parameters:

- **Decay** — How quickly the trail fades. Higher values = longer trails.
- **Gain** — Amplifies the feedback signal. Push it high for glowing, blooming trails.
- **Zoom** — Scales the feedback frame slightly each cycle, creating zoom-in or zoom-out tunnel effects.
- **Rotate** — Rotates the feedback frame each cycle. Combine with Zoom for spiral tunnels.
- **Hue Shift** — Rotates the hue of the feedback frame each cycle.
- **Blur** — Softens the feedback trail.
- **Chromatic Aberration** — Separates RGB channels in the feedback trail for prismatic fringing.
- **Kaleidoscope Segments** — Mirrors the feedback frame into segments each cycle.
- **Blend Mode** — How the fed-back frame combines with the new one.

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

### Audio in your shaders

Shaders can read the live audio through the `audioFFT` sampler (Shadertoy-style `iChannel0` is the same texture), a 512x2 single-channel texture. Row 0 (`y = 0.25`) is the spectrum: 512 bins from 0 Hz to half the sample rate, 0 to 1 on a decibel scale (full scale is 1, -80 dB is 0), with a quick decay so bars fall smoothly. Row 1 (`y = 0.75`) is the waveform, centred on 0.5. Example: `float level = texture(audioFFT, vec2(pow(uv.x, 2.0), 0.25)).r;` gives a bar height with more resolution at the low end.

### Marking a shader as GPU-intensive

Heavy shaders (raymarchers, fractal folds with many iterations) can run slowly on integrated graphics. To warn users, add an optional `LSD_COST` key to the ISF header: `1` light, `2` medium, `3` heavy.

```json
"LSD_COST": 3,
```

Shaders declared `3` show a warning triangle in the browser, and their tooltip says they may run slowly on integrated graphics. The bundled raymarchers and fractals are all declared `3`. Other ISF programs ignore the key. If a shader is slow, lowering iteration or step-count parameters helps more than anything else.

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
