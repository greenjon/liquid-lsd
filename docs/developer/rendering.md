# OpenGL Rendering Pipeline & Visual Sources

This document details the OpenGL graphics rendering pipeline, Framebuffer Object (FBO) ping-pong architecture, shader management, `SourceDocRegistry`, and visual source math in Liquid LSD.

---

## Framebuffer Object (FBO) Ping-Pong Loop

To generate feedback effects (decay, zoom, rotation, hue shift, blur, chromatic aberration), each Deck maintains a dual FBO ping-pong loop:

```
[VisualSource (Mandala / GLSL Shader)]
                 │
                 ▼
            [cleanFBO]  (Renders raw source geometry/pixels)
                 │
                 ▼
        [feedback.frag] ◄── [Previous Frame Feedback Texture]
                 │
                 ▼
       [Write feedbackFBO]  (Applies decay, zoom, rotate, blur, chroma)
                 │
                 ▼
        (Swap Read/Write FBOs)
                 │
                 ▼
    [Mixer.kt / mixer.frag] ──► [masterFBO] ──► Screen
```

### Execution Steps
1. **Source Render**: The active `VisualSource` renders clean geometry or raymarched shader pixels to `cleanFBO`.
2. **Feedback Quad Pass**: Binds the write `feedbackFBO` and renders a fullscreen quad running `src/main/resources/shaders/feedback.frag`. Passes the previous frame's feedback texture, `cleanFBO` texture, and evaluated feedback parameters (**Decay**, **Gain**, **Zoom**, **Rotate**, **Hue Shift**, **Blur**, **Chroma Offset**).
3. **Buffer Swap**: Swaps the read and write feedback FBO references.
4. **Mixer Compositing**: `Mixer.kt` binds `masterFBO` and executes `mixer.frag` to blend Deck A and Deck B output textures according to the active blending mode and crossfader position.

---

## Source Documentation Registry (`SourceDocRegistry.kt`)

[`SourceDocRegistry.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/rendering/SourceDocRegistry.kt) is an immutable singleton repository storing documentation for visual sources and parameters:

- **Source Descriptions**: `sourceDescriptions: Map<String, String>` keyed by `sourceId`. Covers all 10 built-in engines (`mandala`, `kifs`, `dynamic_spiral`, `gyroid`, `mandelbulb`, `mandelbox`, `chladni`, `clifford_torus`, `pseudo_kleinian`, `attractor_feedback`).
- **Parameter Descriptions**: `paramDescriptions: Map<String, String>` covering ~120 parameters, keyed by `"<sourceId>/<paramName>"`, `"feedback/<paramName>"`, or `"mixer/<paramName>"`.
- **UI Lookup API**: Surfaced by `PresetGridRenderer` and `DeckControlPanel` to draw rich tooltips.

---

## Pluggable Dynamic Visual Sources (`VisualSourceRegistry.kt`)

Beyond hardcoded generators, the engine loads dynamic shaders from `library/sources/`:

- **`VisualSourceRegistry`**: Scans subfolders on startup, parses `meta.json`, compiles `[name].frag` against standard vertex shaders, and builds `DynamicVisualSource` templates.
- **Shader Ownership**: The master template in `VisualSourceRegistry` owns the OpenGL shader program (`ownsShader = true`). Deck clones share shader program handles safely (`ownsShader = false`) to eliminate duplicate compilation overhead.
- **Error Fallbacks**: If custom GLSL fails compilation, a fallback checkerboard shader is bound so the application avoids crashing.

---

## KIFS Fractal Folding Engine (`Kifs.kt`)

[`Kifs.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/rendering/Kifs.kt) extends `DynamicVisualSource` to calculate 3D polyhedral fold angles on the CPU before uniform binding:

- **Shape Morphing Interpolation**: The `Shape Morph` parameter (`0.0` to `4.0`) interpolates 3D fold angles across 4 polyhedral symmetry modes:
  1. *Cube Symmetry* ($morph < 1.0$): Interpolates between Cube angles $(0, 0.7854, 0)$ and Sphere transition angles $(0, 1.0082, 1.0472)$.
  2. *Tetrahedron Symmetry* ($morph < 2.0$): Interpolates between Sphere angles and Tetrahedron angles $(0, 1.231, 2.0944)$.
  3. *Dodecahedron / Icosahedron Symmetry* ($morph < 3.0$): Interpolates between Tetrahedron angles and Icosahedron angles $(2.0344, 0, 2.4119)$.
  4. *Soccer Ball Symmetry* ($morph \ge 3.0$): Uses Icosahedral symmetry angles with extended folding iterations.
- **Uniform Binding**: Calculates `uFoldAngleX`, `uFoldAngleY`, `uFoldAngleZ` by adding manual user fold angle offsets to calculated polyhedral base angles.
