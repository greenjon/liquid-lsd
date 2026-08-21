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

## Dynamic Render Resolution & Viewport Scaling

Liquid LSD supports arbitrary user-defined render resolutions and aspect ratios (e.g. 1080p, 720p, 540p, 4K, 4:3 UXGA 1600x1200, 1:1 Square 800x800, or Custom) configured via `UITheme`:

- **Dynamic Pipeline Resizing**: `Mixer.resize(width, height)` and `Deck.resize(width, height)` reallocate `cleanFBO`, `fb1`, `fb2`, and `masterFBO` on the main OpenGL thread without interrupting playback or dropping preset states.
- **Shader Aspect Awareness**: Generative fragment shaders evaluate `float aspect = uResolution.x / uResolution.y;` from `targetFBO` dimensions, rendering undistorted geometry across any aspect ratio.
- **Display Scaling with `ViewportHelper`**: [`ViewportHelper.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/rendering/ViewportHelper.kt) computes letterbox, pillarbox, and fill coordinates for secondary monitor outputs and background video blits:
  - `FIT`: Preserves exact content aspect ratio with letterboxing or pillarboxing.
  - `FILL`: Centers and crops edges to completely fill the target screen.
  - `STRETCH`: Stretches content to fill the target viewport.
- **Aspect-Adaptive UI Previews**: `MixerMonitorLayoutCalculator` dynamically adjusts Deck A, Deck B, Deck C, and Master preview monitor heights to match the active render aspect ratio.

---

## Source Documentation Registry (`SourceDocRegistry.kt`)

[`SourceDocRegistry.kt`](file:///home/gj/projects/liquid-lsd/src/main/kotlin/llm/slop/liquidlsd/rendering/SourceDocRegistry.kt) is an immutable singleton repository storing documentation for visual sources and parameters:

- **Source Descriptions**: `sourceDescriptions: Map<String, String>` keyed by `sourceId`. Covers all built-in engines (`mandala`, `dynamic_spiral`, `gyroid`, `chladni`, `attractor_feedback`, `icosa_dodeca`).
- **Parameter Descriptions**: `paramDescriptions: Map<String, String>` keyed by `"<sourceId>/<paramName>"`, `"feedback/<paramName>"`, or `"mixer/<paramName>"`.
- **UI Lookup API**: Surfaced by `PresetGridRenderer` and `DeckControlPanel` to draw rich tooltips.

---

## Pluggable Dynamic Visual Sources (`VisualSourceRegistry.kt`)

Beyond hardcoded generators, the engine loads dynamic shaders from `library/sources/`:

- **`VisualSourceRegistry`**: Scans subfolders on startup, parses `meta.json`, compiles `shader.frag` against standard vertex shaders (`blit.vert`), and builds `DynamicVisualSource` templates.
- **Shader Ownership**: The master template in `VisualSourceRegistry` owns the OpenGL shader program (`ownsShader = true`). Deck clones share shader program handles safely (`ownsShader = false`) to eliminate duplicate compilation overhead.
- **Error Fallbacks**: If custom GLSL fails compilation, a fallback checkerboard shader is bound so the application avoids crashing.
