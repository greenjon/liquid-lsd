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
     [FX Slot 1] (deck.fxSlots[0] -> deck.fxFBOs[0] — any ISF filter: Invert, Posterize,
                  Luma Key, Feedback Loop, 3D Elevation, Bloom, Glitch, Mirror, Trails, etc.)
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
- The UI shader browser (`ShaderPickerPopup`) provides a dual-mode interface:
  - **Collapsible Folder Tree Mode** (`Icons.FOLDER`): Groups shaders by their subfolder structure in expandable tree nodes.
  - **Flat List Mode** (`Icons.LAYOUT_FULL`): Fast 3-column table showing folder tags in the Categories column.
  - Category pill filter row incorporates top-level folder names and custom tags.

### 3. Respecting Relative Assets (`IMPORTED`)
- Shaders remain in their original directories during execution.
- Shaders declaring static lookup tables, noise textures, or auxiliary assets in `IMPORTED` (using either JSON Object or JSON Array syntax) have their paths resolved relative to the shader's parent folder (`baseDir`).
- `ISFParser.buildGLSLFragmentShader` automatically injects `uniform sampler2D <name>;` declarations for all declared imported textures.
- `ISFTextureLoader` decodes image files into OpenGL 2D textures on Thread 0 via STBImage (`stbi_load`). During execution, pre-resolved texture units are bound with zero allocations per frame on the render thread.


