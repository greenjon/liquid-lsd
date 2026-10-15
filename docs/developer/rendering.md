# OpenGL Rendering Pipeline & Visual Sources

This document details the OpenGL graphics rendering pipeline, Framebuffer Object (FBO) ping-pong architecture, shader management, `SourceDocRegistry`, and visual source math in Liquid LSD.

---

## Per-Deck FX Chain & ISF Transition Pipeline

Following the 100% ISF Pipeline Migration (see `ARCHITECTURE.md`'s "100% ISF Pipeline & Modular Effects Engine"), each Deck's render path is a single generator pass into `cleanFBO` followed by a chain of up to 4 modular ISF FX slots. There is no separate hardcoded 3D-projection pass and no Deck-level ping-pong feedback loop — both are now ordinary ISF filters a performer loads into any FX slot (`default_filters/3d_elevation.fs`, `default_filters/feedback.fs`). The legacy `tri_planar.*`/`tetra_kaleido.*` shaders and the old `Deck.fbGain`/`fbDecay`/etc.-driven `feedback.frag` ping-pong stage were retired from the render path in Phase 6; their `.vert`/`.frag` files remain on disk under `src/main/resources/shaders/` but are no longer loaded by `Renderer.kt` or `Deck.kt`.

```
[VisualSource (Mandala / DynamicVisualSource / ISFVisualSource / ExternalVideoSource)]
                 │
                 ▼
     [Renderer.render()] — 2D View Pass: uZoom / uRotateZ applied directly in vertex space
     (skipped for native-3D sources — Renderer.renderDeck() forces zoom=1.0/rotateZ=0.0
     when deck.source.is3D, since those sources handle their own camera transform)
                 │
                 ▼
            [deck.cleanFBO]  (Composited clean source frame)
                 │
                 ▼
     [FX Slot 1] (deck.fxSlots[0] -> deck.fxFBOs[0] — any ISF filter: Kaleidoscope, Radial Blur,
                  RGB Split, Polar Tunnel, Color Levels, Gradient Map, Bloom, Glitch, Mirror, Feedback, etc.)
                 │
                 ▼
     [FX Slot 2] (deck.fxSlots[1] -> deck.fxFBOs[1])
                 │
                 ▼
     [FX Slot 3] (deck.fxSlots[2] -> deck.fxFBOs[2])
                 │
                 ▼
     [FX Slot 4] (deck.fxSlots[3] -> deck.fxFBOs[3])
                 │
                 ▼
     [Mixer.kt Transition Stage] — Renderer.renderMixer()
     [mixer.transitionFilter (ISF)] -> [mixer.blendFBO] -> [mixer.frag composite over Deck BG]
                 │
                 ▼
            [mixer.masterFBO] ──► Screen
```

### Execution Steps
1. **Source Render & View Stage** (`Renderer.render()` / `Renderer.renderDeck()`):
   - **2D Sources (`!source.is3D`)**: The active 2D source renders directly into `cleanFBO` via `blit.vert` (or the source's own multi-pass topology), passing `uZoom` (`deck.viewZoom`), `uRotateZ` (`deck.viewRotateZ`), and `uAspectRatio` directly into the vertex shader.
   - **Native 3D Sources (`source.is3D == true`)**: e.g. `icosa_h3`, `hyper_mesh`. These handle their own rotation (`Rotate X/Y/Z`) and camera zoom internally and render straight into `cleanFBO`; `renderDeck()` forces the Deck's `viewZoom`/`viewRotateZ` to identity (`1.0`/`0.0`) for these sources rather than routing through a separate projection FBO.
   - **External Video Ingest Sources (`ExternalVideoSource`)**: Ingest live video streams from external applications via Spout2 (Windows), Syphon (macOS), or PipeWire 0.3 (Linux) (`PipeWireReceiverImpl` in `TextureReceiver.kt` and `fetchPipeWireStreams()` in `ExternalVideoDiscovery.kt`). `Renderer.renderExternalVideoSource()` blits the active incoming texture (`currentTextureId`) via `view2DShader` straight into `cleanFBO`, applying the same `uZoom`/`uRotateZ`/`uAspectRatio` uniforms as 2D sources.
   - **Persistent-history generators**: sources with `hasFeedback == true` (e.g. trail-based generators like Dynamic Spiral) maintain their own `fb1`/`fb2` ping-pong pair directly on the `DynamicVisualSource` instance (not on `Deck`), lazily (re)allocated by `Renderer.render()` to match `cleanFBO`'s dimensions.
2. **FX Slot Chain (Serial Processing Stage)**: `Renderer.renderDeck()` loops over `deck.fxSlots` (`Deck.FX_SLOT_COUNT` = 4). For each enabled slot with `dryWet > 0.0`, it renders the running texture (starting from `cleanFBO.texture`) through that slot's `ISFFilter` into `deck.fxFBOs[i]`, then blends dry (previous stage's output) against wet (this stage's output) via `glBlendColor(..., 1.0 - dryWet)` when `dryWet < 1.0`. The final enabled slot's texture becomes the deck's effective output (`Deck.getOutputTexture()`); if no slots are enabled, `cleanFBO` passes through unchanged.
3. **Mixer Transition & Compositing** (`Renderer.renderMixer()`): Deck A and Deck B's effective output textures feed the active ISF transition filter (`mixer.transitionFilter` — `Renderer.kt` caches a `linear_crossfade` instance so an unassigned transition never allocates/disposes filters mid-frame) into `mixer.blendFBO`, then a composite pass blends that over Deck BG with channel levels, bloom, and master alpha into `mixer.masterFBO`.

---

## Dynamic Render Resolution & Viewport Scaling

Liquid LSD supports arbitrary user-defined render resolutions and aspect ratios (e.g. 1080p, 720p, 540p, 4K, 4:3 UXGA 1600x1200, 1:1 Square 800x800, or Custom) configured via `UITheme`:

- **Dynamic Pipeline Resizing**: `Mixer.resize(width, height)` and `Deck.resize(width, height)` reallocate `cleanFBO`, `fxFBOs`, and `masterFBO` on the main OpenGL thread without interrupting playback or dropping preset states; each `DynamicVisualSource`'s own persistent-history `fb1`/`fb2` pair (see above) is disposed and lazily recreated on next render rather than eagerly resized.
- **Shader Aspect Awareness**: Generative fragment shaders evaluate `float aspect = uResolution.x / uResolution.y;` from `targetFBO` dimensions, rendering undistorted geometry across any aspect ratio.
- **Display Scaling with `ViewportHelper`**: [`ViewportHelper.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/rendering/ViewportHelper.kt) computes letterbox, pillarbox, and fill coordinates for secondary monitor outputs and background video blits:
  - `FIT`: Preserves exact content aspect ratio with letterboxing or pillarboxing.
  - `FILL`: Centers and crops edges to completely fill the target screen.
  - `STRETCH`: Stretches content to fill the target viewport.
- **Aspect-Adaptive UI Previews**: `MixerLayoutCalculator` dynamically adjusts Deck A, Deck B, Deck PV, and Master preview monitor heights to match the active render aspect ratio.

---

## FBO-Count & GPU-Memory Telemetry

`FBO.kt`'s companion object tracks every live `FBO` instance app-wide in a `ConcurrentHashMap<framebufferId, estimatedByteSize>`, populated on construction and cleared on `dispose()`. Byte size is format-aware (`GL_RGBA8` = 4 bytes/px, `GL_RGBA16F` = 8, `GL_RGBA32F` = 16). This is exposed as `PerformanceStats.fboCount` / `PerformanceStats.fboMemoryMB` and rendered in the menu bar telemetry HUD as `FBO: N (XMB)` (`MenuBar.kt`), covering all live FBOs in the core render pipeline (`cleanFBO`, `fxFBOs`, `masterFBO`, etc.) app-wide. This instrumentation was added deliberately *before* any pooled FBO allocator, so a real GPU-memory bottleneck would show up in this readout first rather than being addressed speculatively.

> The 19" Modular Video Rack chassis UI (`rack/`, `rack/ui/`), including its `RackMicroMonitor` confidence-preview downscaling and `RackPipeline` ping-pong stage buffers, was removed when the workspace was replaced by Performance Mode (`PerformanceMatrixPanel.kt`); see `docs/developer/modular_video_rack_proposal.md` for retired design history.

---

## Source Documentation Registry (`SourceDocRegistry.kt`)

[`SourceDocRegistry.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/rendering/SourceDocRegistry.kt) is an immutable singleton repository storing documentation for visual sources and parameters:

- **Source Descriptions**: `sourceDescriptions: Map<String, String>` keyed by `sourceId`. Covers the currently-bundled built-in engines (`mandala`, `dynamic_spiral`, `icosa_h3`) plus stale dead-code entries (`colors`, `gyroid`, `chladni`, `attractor_feedback`, `hyper_mesh`, `hyper_slice`) left over from the legacy-source removal (see below) that no `VisualSourceRegistry` entry resolves to anymore — harmless (map lookups simply never hit them) but worth pruning next time this file is touched.
- **Parameter Descriptions**: `paramDescriptions: Map<String, String>` keyed by `"<sourceId>/<paramName>"`, `"feedback/<paramName>"`, or `"mixer/<paramName>"`.
- **UI Lookup API**: Surfaced by `ParametersRenderer` and `DeckControlPanel` to draw rich tooltips.

---

## Pluggable Dynamic Visual Sources (`VisualSourceRegistry.kt`)

Beyond hardcoded generators, the engine loads dynamic shaders from `library/sources/`:

- **`VisualSourceRegistry`**: Scans subfolders on startup, parses `meta.json`, compiles `shader.frag` against standard vertex shaders (`blit.vert`), and builds `DynamicVisualSource` templates.
- **Shader Ownership**: The master template in `VisualSourceRegistry` owns the OpenGL shader program (`ownsShader = true`). Deck clones share shader program handles safely (`ownsShader = false`) to eliminate duplicate compilation overhead.
- **Polymorphic Draw Topology**: Visual source rendering in `Renderer.render()` is fully polymorphic with zero source-type checks (`is Mandala`, `is HyperMesh`). Each visual generator overrides `DynamicVisualSource.drawTopology()`:
  - Default: Renders a fullscreen quad via `Geometry.drawFullscreenQuad()`.
  - `Mandala`: Binds ribbon VAO and draws triangle strips via `glDrawArrays(GL_TRIANGLE_STRIP, 0, (POINTS + 1) * 2)`.
  - `HyperMesh`: Binds 4D polychoron strut and node VAOs and renders indexed triangles via `glDrawElements(GL_TRIANGLES, ...)`.
- **Error Fallbacks**: If custom GLSL fails compilation, a fallback checkerboard shader is bound so the application avoids crashing.

---

## $H_3$ Coxeter Icosahedral Raymarcher (`icosa_h3`)

The `icosa_h3` visual source is a single ISF shader ([`icosa_h3.fs`](file:///home/gj/projects/liquid-lsd/library/sources/icosa_h3/icosa_h3.fs)) that combines what were previously three separate visual sources (`icosa_dodeca`, `icosahedron`, `icosa-v3`) into one **$H_3$ Coxeter Reflection Group Iterated Function System (IFS)** raymarcher. It evaluates two independent stellation families sharing the same folded-chamber substrate and crossfades between them with the `Spike Mode` parameter, so the whole design space of the three predecessors is reachable from one shader:

- **Smooth duality-morph family** (from `icosa_dodeca`): a continuous 4-phase Icosahedron ↔ Dodecahedron ↔ Great Stellated Dodecahedron ↔ Great Icosahedron cycle, with truncation/cantellation duals.
- **Spike + blocker stellation CSG** (from `icosa-v3`): an independently-phased pole sweep that extrudes pyramid spikes and blunts their tips with a chopping plane.
- The brute-force 60-plane orbit approach of the original `icosahedron` source (which required `Icosahedron.kt` to generate the plane orbit on the CPU every frame) was dropped in favor of the GPU-only domain-folding technique below — it only ever covered the icosahedron's own stellations, not the icosahedron/dodecahedron duality, and its CPU dependency doesn't fit the ISF model.

### 1. Fundamental Mirror Planes
The $H_3$ Coxeter group (icosahedral symmetry $I_h$, order 120) is defined by three normalized mirror planes with dihedral angles $(\pi/5, \pi/3, \pi/2)$:
$$n_0 = (1, 0, 0)$$
$$n_1 = \left(-\frac{\phi}{2}, -\frac{1}{2}, \frac{1}{2\phi}\right)$$
$$n_2 = (0, 1, 0)$$
where $\phi = \frac{1+\sqrt{5}}{2} \approx 1.61803398875$.

### 2. Chamber Vertices & Slerp Generator $v(t)$
The corners of the fundamental spherical triangle represent the primary symmetry axes:
- **3-Fold Axis $C_3$** (Icosahedron face normal / Dodecahedron vertex): $C_3 = \text{normalize}(n_1 \times n_2) = \left(\frac{\phi - 1}{\sqrt{3}}, 0, \frac{\phi}{\sqrt{3}}\right)$
- **5-Fold Axis $C_5$** (Dodecahedron face normal / Icosahedron vertex): $C_5 = \text{normalize}(n_0 \times n_1) = \left(0, \frac{1}{\sqrt{\phi + 2}}, \frac{\phi}{\sqrt{\phi + 2}}\right)$
- **2-Fold Axis $C_2$** (Edge midpoint): $C_2 = (0, 0, 1)$

The generator vector $v(t)$ continuously slerps between $C_3$ and $C_5$:
$$v(t) = \frac{\sin((1-t)\Omega)}{\sin\Omega} C_3 + \frac{\sin(t\Omega)}{\sin\Omega} C_5,\quad \Omega = \arccos(C_3 \cdot C_5) \approx 0.652358\text{ rad}$$

### 3. Kaleidoscopic Symmetry Folding
Any raymarching sample point $p \in \mathbb{R}^3$ is folded iteratively into the fundamental chamber:
```glsl
vec3 foldH3(vec3 p) {
    for (int i = 0; i < 16; ++i) {
        p -= 2.0 * min(0.0, dot(p, n0)) * n0;
        p -= 2.0 * min(0.0, dot(p, n1)) * n1;
        p -= 2.0 * min(0.0, dot(p, n2)) * n2;
    }
    return p;
}
```

### 4. Signed Distance Field & Continuous Stellation Plane Tilting
In folded chamber space ($p' = \text{foldH3}(p)$), the generator vector $v(t)$ defines the face normal.
Its adjacent reflection normal vector $v_{\text{adj}}(t)$ (derived from $C_3$ reflected across $n_0$ and $C_5$ reflected across $n_2$) defines the sloping stellation facet:
$$C_3^{\text{adj}} = (-C_3.x, 0, C_3.z),\quad C_5^{\text{adj}} = (0, -C_5.y, C_5.z)$$
$$v_{\text{adj}}(t) = \text{slerp}(C_3^{\text{adj}}, C_5^{\text{adj}}, t)$$

The continuous stellation plane vector $v_{\text{morph}}$ tilts continuously between the flat face $v(t)$ at $s=0$ and the Kepler-Poinsot star facets $v_{\text{adj}}(t)$ at $s=1$:
$$v_{\text{morph}} = \text{normalize}(\text{mix}(v(t), v_{\text{adj}}(t), s))$$
$$SDF(p) = p' \cdot v_{\text{morph}} - h$$

- **Convex Core ($s=0$)**: Evaluates flat Platonic/Archimedean faces.
- **Kepler-Poinsot Stellations ($s=1$)**: Tilts face planes into intersecting star pyramids with exact sharp apexes at $r = h / (v \cdot v_{\text{adj}})$.
- **Smooth Emergence ($0 < s < 1$)**: Spikes grow out of the face centers with continuous facet tilting.

### 5. Independent Spike + Blocker Stellation CSG
A second stellation family shares the same `foldH3` substrate but is driven independently of `Morph`: `Spike Phase` sweeps a pole `corePole` around the $C_3$–$C_5$ arc (via a general `slerpArc(p0, p1, t)` valid for $t$ outside $[0,1]$, unlike the fixed-angle $v(t)$ generator above), which is then reflected across $n_0$ and $n_2$ to obtain two adjacent spike poles. `Spike Sharpness` tilts each spike pole from its flat core position toward the reflected (fully extruded) position, and the two resulting half-spaces are intersected (`max`) to form the spike shape. A third `blockerPole` — the same arc sampled one phase-step ahead — chops the spike tips off, with `Blocker Size` controlling how much survives:
$$SDF_{\text{spike}}(p) = \max\big(\max(d_{\text{spike1}}, d_{\text{spike2}}),\ d_{\text{blocker}}\big)$$
This is the same domain-folded CSG technique as the old `icosa-v3` source (evaluating only a handful of dot products instead of brute-forcing 60 planes), now running as one branch of `mapSDF` alongside the duality-morph family above.

### 6. Family Crossfade & Facet-Aware Coloring
`Spike Mode` linearly crossfades the two families' SDF values, edge-seam distances, and a per-family "which half-space is dominant" index (`mix(dSmooth, dSpikeFinal, mode)`, etc.), producing a continuous blend between the smooth duality morph and the spiky CSG rather than a hard switch between two shaders. The dominant-half-space index feeds a dedicated `Color Mode = 0` ("Facet Family") coloring path that assigns each active facet family its own stable hue, on top of the inherited Chamber Sectors / Depth Gradient / Normal Spectrum modes and a new animated Fresnel-driven Iridescent mode.

### 7. Multi-Layer Crystal Raymarching
- **Under-Relaxation**: Ray steps use a conservative $0.5\times$ scaling factor on miss (`t += dist * 0.5`), tuned to stay stable on the acute needle ridges the spike family can produce.
- **Front-to-Back Transparency**: Steps through transparent outer faces (`Opacity` 0.6–0.8) to accumulate color and reveal inner self-intersecting facets without depth sorting.

---

## Heavy Fractal Raymarchers (`mandelbox`, `kifs`, `mandelbulb`, `pseudo_kleinian`, `apollonian`)

Five stock ISF generators ([`library/sources/<id>/<id>.fs`](file:///home/gj/projects/liquid-lsd/library/sources), identical copy in `src/main/resources/default_sources/`) share one structure with `icosa_h3` and `gyroid_hyperspace`: a distance-estimated raymarch, `LSD_COST` 3, `is3D` with their own `Zoom`/`RotateX/Y/Z`, orbit-trap palettes selected by `ColorMode`, a tetrahedral-difference normal, a small AO tap set and a signed `FlightSpeed`. There is no include mechanism, so each file inlines its helpers. They target an NVIDIA RTX A2000 as the floor and are desktop only: GLSL 3.30 core, no `web/sync_manifest.json` entry. Per-source derivations and known issues are in `.planning/heavy-sources-notes/<id>.md`.

Every one exposes a normalised `Quality` input that remaps to live loop counts under constant caps (`for (i = 0; i < MAX; i++) { if (i >= n) break; }`), so Quality trades fidelity for speed during a set.

| Source | Algorithm | Constant caps | Default Quality: steps / iterations / AO taps |
|---|---|---|---|
| `mandelbox` | Box fold + sphere fold + scale, running-derivative DE (`z <- s * sphereFold(boxFold(z)) + c`, `DE = norm(z) / dr`) | march 128, fold 12, AO 4 | 0.5: 88 / 9 / 2 (range 48-128 / 6-12 / 0-4) |
| `kifs` | Fold into a fundamental domain, scale about the IFS fixed vertex, per-iteration rotation, base-shape SDF / s^n; four constructions via `SurfaceType` | march 112, fold 12, AO 4 | 0.5: 80 / 9 / 2 (range 48-112 / 5-12 / 0-4) |
| `mandelbulb` | Triplex power map `z^n + c` in spherical coordinates, DE `0.5 log(r) r / dr`; analytic bounding sphere skips misses | march 80, iter 10, AO 4 | 0.5: 64 / 8 / 3 (range 48-80 / 6-10 / 2-4) |
| `pseudo_kleinian` | Periodic triangle-wave slab fold, sphere inversion scale capped at `kmax`, offset, DE `(norm(p) - core) / dr` | march 120, iter 12, AO 4 | 0.33: 72 / 8 / 2 (range 48-120 / 6-12 / 1-4) |
| `apollonian` | Cell wrap, optional y twist, capped sphere inversion, same DE form | march 120, iter 12, AO 4 | 0.08: 54 / 6 / 1 (range 48-120 / 6-12 / 1-4) |

`escape_time` (2D, below) is a sixth heavy generator in the same registry but is not a raymarcher.

Worst case per pixel is steps x iterations estimator passes (for example 88 x 9 = 792 for the Mandelbox default, 1536 at Quality 1); rays that miss or hit early cost far less. No shader uses textures, float buffers or extra passes.

**Measured cost** (RTX A2000 Laptop GPU via PRIME offload, `GL_RENDERER` confirmed, 1920x1080, hidden GL 3.3 context, offscreen RGBA8, glFinish-timed average of several frames; indicative only, GPU clocks not locked, single deck, no compositing):

| Source | Default | Quality 1 | Quality 0 | Notes |
|---|---|---|---|---|
| `mandelbox` | 7.7 ms | 8.4 ms | 5.0 ms | closest-camera, Zoom 1, Quality 1 corners 14 to 42 ms |
| `kifs` | 4.8 ms | 5.7 ms | 2.1 ms | corners not searched |
| `mandelbulb` | 5.2 ms | 5.7 ms | 4.5 ms | corners not searched |
| `pseudo_kleinian` | about 11.2 ms | 14.8 ms | 8.1 ms | everything-max corner 27.7 ms |
| `apollonian` | 8.7 ms | 27.3 ms | 7.8 ms | the earlier default 0.25 cost 15.5 ms; lowered to 0.08 for two-deck headroom |

**`escape_time`** (`library/sources/escape_time/escape_time.fs`): 2D escape-time iteration `z <- z^p + k` (Julia, Burning Ship Julia with `abs` before the power, Mandelbrot with `z0 = 0`) or relaxed Newton on `z^3 - 1`. Iteration cap `Detail` 48 to 400 under a constant `MAX_IT`; escape radius 256 for smooth colouring `n + 1 - log(log|z|)/log(p)`; Newton stops within 1e-5 of a root. `Detail` above 0.75 adds 2x2 supersampling. It supplies its own `Zoom` (0.3x to 3000x), `CenterX/Y` and `Rotation`; fp32 limits useful magnification to about 1e3 to 1e4. Measured on the RTX A2000 Laptop GPU at 1080p: Julia 0.4 ms, Newton 0.9 ms, Mandelbrot 1.5 ms at the default Detail, Julia at Detail 1 (with antialiasing) 1.2 ms; interior-heavy deep views cost far more because every pixel runs the full cap.

Defaults sit inside a 60 fps budget on one deck; two heavy decks plus a crossfade is the case to watch. Zooming in until every pixel hits, or an extreme parameter set, is what pushes a source toward 30 fps. The stock audit (`StockGpuCostAuditTest`) asserts all five carry `LSD_COST` 3; it does not measure frame time.

**Volume and simulation generators** (`nebula_volume`, `reaction_diffusion`, `fluid_ink`, `lenia`). `nebula_volume` is a raymarched emission/absorption volume (`LSD_COST` 3, `is3D`, own camera): domain-warped ridged value-noise fbm with a 0 to 4 tap light march, interleaved-gradient-noise jitter and empty-space leaping, caps 72 view steps, 4 light taps and 5 octaves (default about 28 steps, 2 taps, 2 octaves). The other three are the first stock generators to use `PASSES`: persistent `FLOAT` ping-pong grids of fixed size, so cost does not depend on output resolution. Each sim sub-step is its own pass with its own persistent target (`rdA..rdD`, `lnA..lnD`, `prs1..prs4`), because two passes sharing one target would share one slot and `resizePassFBOs` would allocate it twice. A persistent target reads the most recently written buffer, so chained targets see the same frame's output. Buffers are zeroed on allocation and on resize, so each sim re-seeds itself when its state is empty and never relies on `FRAMEINDEX`. `reaction_diffusion` is 512 x 512 with 4 sub-steps per frame (Gray-Scott, 9-point Laplacian, `Du*dt` capped at 1.2), `fluid_ink` is 384 x 384 with an RK2 semi-Lagrangian velocity pass, 4 Jacobi pressure passes (warm-started across frames) and a dye pass, and `lenia` is 256 x 256 with 4 steps per frame and a 27 x 27 tap window at most. Steps are per frame, not `TIMEDELTA`-scaled, so the simulations run faster at higher frame rates. The three sims are declared `LSD_COST` 2 and show no badge. Measured on the RTX A2000 Laptop GPU at 1080p, default settings: `reaction_diffusion` about 0.5 ms, `fluid_ink` about 0.7 ms, `lenia` about 2 ms, `nebula_volume` about 17 ms (Quality 1, Detail 4, Lighting 1 about 68 ms). Known limitation: `lenia` does not reproduce the published gliding creatures; it makes bubbles, rings and stripes.

**Light generators** (`quasicrystal`, `thin_film`, `tunnel_scenes`, `video_feedback`, `hyperbolic_tiling`). These target integrated graphics and carry no `LSD_COST` tag: the absence of the browser triangle is the signal. All are 2D, so the deck View zoom and rotate apply. Four are single pass with no texture reads and a constant-bound loop (`quasicrystal` 8 gratings, `thin_film` 4 octaves, `tunnel_scenes` 3 layers, `hyperbolic_tiling` 16 folds) with a Quality input that lowers the cap. `video_feedback` is the only multipass one: a persistent 512 x 512 RGBA8 state pass plus a bilinear display pass, re-seeding itself when the buffer is empty. Measured on an Intel UHD (TGL GT1) at 1080p, one deck, defaults: about 3 to 4 ms for the single-pass sources (`thin_film` about 6 ms, `tunnel_scenes` wormhole about 5 ms) and 2 to 4 ms for `video_feedback`. All five are also web sources (`web/sync_manifest.json`), transpiled to GLSL ES 3.00 by `syncWeb`. Time-driven phases are wrapped (`mod(TIME*speed, period)`) so they stay precise in long sessions.

**More light generators** (`cyclic_ca`, `metaballs`, `wave_ripples`, `harmonograph`, `aurora_ribbons`, `isometric_voxels`, `halftone_matrix`, `kali_set`, `girih_stars`, `glitch_blocks`, `voronoi_cells`). Same rules as above: no `LSD_COST`, web sources, a Quality input. Only `cyclic_ca` is multipass: four chained persistent 256 x 256 RGBA8 sub-step passes plus a display pass, reseeding when the grid is empty (never `FRAMEINDEX`), with the generation rate derived from `TIMEDELTA` and capped at 4 per frame. Measured at 1080p on the Intel UHD (TGL GT1) when the GPU was otherwise idle, roughly 2x the figures in the first batch because the machine was running slower: `cyclic_ca` 4 to 5 ms, `voronoi_cells` 7 to 9, `glitch_blocks` 6 to 9, `kali_set` 10, `girih_stars` 9 to 12, `isometric_voxels` 10 to 14, `wave_ripples` 10 to 16, `aurora_ribbons` 14 to 20, `halftone_matrix` 7 to 17; `metaballs` and `harmonograph` were 19 to 27 and 22 to 26 before optimisation. `harmonograph` now evaluates its 65 curve vertices once into a 65 x 2 float strip (pass 0; row 1 holds a bounding box per chunk of 8 segments) and the display pass skips chunks outside the glow radius (about 7 ms average). `metaballs` uses a monotonic dome height with a flat-top attenuation for its shading and a default Quality of 0.3 (single sample, about 11 ms); Quality of 0.5 or more turns on 2 x 2 antialiasing, which quadruples the field evaluations.

**Stock registration touchpoints** for a new stock source: `VisualSourceRegistry.DEFAULT_SOURCE_IDS`, `GeneratorDefaults.CURATED_KNOBS` + `GeneratorDefaultsTest.stockInputNames`, `SourceDocRegistry` (description plus `"<id>/<Param>"` tooltips), the `StockGpuCostAuditTest` heavy allowlist, `Source3DModeTest`, the licence and provenance rows, and the user-guide pages. Both `.fs` trees must be identical (`diff -rq library/sources src/main/resources/default_sources`).

---

## Video Recording & Deterministic Offline Studio

Liquid LSD includes both a zero-lag live capture pipeline and a sample-accurate offline rendering studio in the `llm.slop.liquidlsd.export` package.

### 1. Zero-Copy DMA Readback (`PboReadbackPipeline`)
- **Ping-Pong PBO Ring Buffer**: Uses two `GL_PIXEL_PACK_BUFFER` objects. While PBO $A$ is mapped into CPU memory via `glMapBufferRange(GL_READ_ONLY)`, PBO $B$ executes an asynchronous GPU DMA transfer via `glReadPixels(..., 0)`.
- **Direct Memory Copy**: Frame transfers use direct C-pointer block copies (`MemoryUtil.memCopy`) to eliminate CPU overhead on Thread 0. Vertical flipping is delegated to the FFmpeg filter graph (`-vf vflip`).

### 2. Live Session Recording (`RealtimeRecorder`)
- **Background Frame Queue**: Bounded `ArrayBlockingQueue<ByteBuffer>(10)` prevents frame capture from stalling the main OpenGL render loop. If disk I/O stalls, frames are cleanly dropped with a live HUD counter and percentage indicator.
- **Lock-Free Zero-Allocation Audio Tapping**: Live audio blocks from `AudioEngine.processAudio` are captured into a pre-allocated pool of `AudioBlock` instances via a lock-free, wait-free Single-Producer Single-Consumer (`SpscQueue`) ring buffer without heap allocations or mutex acquisitions on the real-time audio thread. Audio is written to a temporary PCM WAV file on a background worker thread and losslessly remuxed with FFmpeg (`-c:v copy -c:a aac -b:a 320k`) upon stopping.
- **Live REC Tally Badge**: Pulsing red record badge overlaid directly on the master preview monitor with elapsed timer (`REC MM:SS`).

### 3. Deterministic Time Virtualization (`TimeSource`)
- **Central Time Authority**: All shaders (`uTime`), `CVRegistry` evaluators, `DynamicSpiral`, and `Mixer` query `TimeSource.getTimeSec()` and `TimeSource.getTimeNanos()`.
- **Offline Simulation Clock**: During `OfflineRenderStudio` passes, `TimeSource.setSimulatedTime(subFrameTimeSec, subFrameDt)` is set before stepping DSP analysis and deck rendering, guaranteeing sample-accurate synchronization between audio frequencies and visual animations regardless of rendering speed.

### 4. Offline Render Studio (`OfflineRenderStudio`)
- **Frame-Accurate Stepping**: Iterates frame-by-frame on Thread 0 while yielding to GLFW events.
- **Multi-Pass Motion Blur (`AccumulationBuffer`)**: Supports up to 8x temporal super-sampling, blending sub-frame passes to generate cinematic motion blur.
- **Automatic Encoder Prioritization & Fallback**: Automatically probes and prioritizes hardware encoders (`h264_nvenc`, `h264_qsv`) with transparent fallback to software encoders (`libx264`, `libx265`, `prores_ks`).

---

## Desktop-to-Web Shader & Asset Synchronization

To ensure continuous parity between desktop visual sources and the browser-based WebGL2 visualizer (`web/`), an automated synchronization subsystem tracks file changes and translates shader dialects.

### 1. Dialect Conversion
- **GLSL 3.30 Core $\rightarrow$ WebGL2 (GLSL ES 3.00)**:
  - Desktop shaders in `src/main/resources/shaders/` and `library/sources/*/shader.frag` use `#version 330 core`.
  - The synchronization tool transpiles `#version 330 core` into `#version 300 es\nprecision highp float;` for `web/shaders/`.
- **Sole Source of Truth**: Shaders and visual sources should always be authored in desktop directories; running `./scripts/sync_web.py --apply` or `./gradlew syncWeb` mechanically updates the web client.

### 2. Algorithmic Drift Tracking
- For procedural geometry and math modules (`Evaluators.kt` $\rightarrow$ `web/evaluator.js`), `web/sync_manifest.json` tracks SHA-256 hashes.
- Whenever a desktop Kotlin algorithm is modified, running `./scripts/sync_web.py --check` or `./gradlew checkWebSync` alerts the developer to review and update the JavaScript equivalent. Once verified, `./scripts/sync_web.py --mark-synced <target>` records the updated hash.

---

## Flexible ISF Directory Architecture, Role Auto-Detection & Asset Resolution

Liquid LSD scans arbitrary user-specified directories for Interactive Shader Format (ISF) assets without forcing files into rigid `Generators/`, `Filters/`, or `Transitions/` folders.

### 1. Role Auto-Detection via JSON `INPUTS`
Shaders are classified dynamically by parsing their ISF JSON header and inspecting image inputs:
- **0 Image Inputs**: Auto-detected as **Generator** (`ISFAssetType.GENERATOR`), loaded into `VisualSourceRegistry`.
- **1 Image Input**: Auto-detected as **Filter** (`ISFAssetType.FILTER`), loaded into `ISFFilterRegistry`.
- **2+ Image Inputs** (or 1 image input with explicit `progress` transition parameter): Auto-detected as **Transition** (`ISFAssetType.TRANSITION`), loaded into `ISFTransitionRegistry`.

### 2. Folder Hierarchy Preservation
- Relative subfolder structures (e.g., `PackName/Subfolder/shader.fs`) are captured in `ISFAsset.folderPath` and mirrored into `categories` tags.
- The unified browser (`BrowserPane`) shows these folders as tree entries under each section and searches the folder names and custom tags along with the shader name.

### 3. Respecting Relative Assets (`IMPORTED`)
- Shaders remain in their original directories during execution.
- Shaders declaring static lookup tables, noise textures, or auxiliary assets in `IMPORTED` (using either JSON Object or JSON Array syntax) have their paths resolved relative to the shader's parent folder (`baseDir`).
- `ISFParser.buildGLSLFragmentShader` automatically injects `uniform sampler2D <name>;` declarations for all declared imported textures.
- `ISFTextureLoader` decodes image files into OpenGL 2D textures on Thread 0 via STBImage (`stbi_load`). During execution, pre-resolved texture units are bound with zero allocations per frame on the render thread.



### 4. Discrete (stepped) inputs
A parameter can declare a step set (`ModulatableParameter.steps` / `labels`); `evaluate()` snaps its result to the nearest step while `baseValue` stays continuous, so knobs, LFOs, audio, macros, MIDI and OSC all land on a valid value.
- `long` / `int` inputs: steps come from `VALUES` (or the whole-number `MIN..MAX` span), labels from `LABELS` when there is one per step. `bool` has 2 steps.
- `float` inputs that are really discrete take the optional Liquid LSD key `"STEP"` (for example `"MIN": 2, "MAX": 24, "STEP": 1`). The shader still receives a float uniform; other ISF hosts ignore the key. `(MAX-MIN)/STEP` must be a whole number, otherwise `STEP` is ignored and a warning is logged.
- Integer uniforms are rounded, not truncated, before they reach the shader.

### 5. GPU cost tag (`LSD_COST`)
`ISFHeader.LSD_COST` (optional Liquid LSD key, 1-3) is the author's declaration of how heavy a shader is; `ISFHeader.gpuCost` clamps it to 0-3 (0 = undeclared). The stock browser catalogs (`BrowseCatalogs`) copy it into `AssetItem.gpuCost` for stock generators and filters, and `PresetListPanel.drawCostBadge` draws a warning triangle for cost 3 (`GPU_COST_HEAVY`), with `GPU_COST_TIP` in the tooltip. It is static metadata, not a measurement. Bundled sources live in `library/sources/` (the build copies them over `default_sources`), so tag both copies. `StockGpuCostAuditTest` checks range and the heavy set.
