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

---

## $H_3$ Coxeter Symmetry Folding IFS Engine (`icosa_dodeca`)

The `icosa_dodeca` visual source implements a pure **$H_3$ Coxeter Reflection Group Iterated Function System (IFS)** raymarcher in GLSL ([`shader.frag`](file:///home/gj/projects/liquid-lsd/library/sources/icosa_dodeca/shader.frag)):

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

### 5. Multi-Layer Crystal Raymarching
- **Under-Relaxation**: Ray steps use a $0.65\times$ scaling factor (`t += dist * 0.65`) for numerical stability across sharp mirror boundaries.
- **Front-to-Back Transparency**: Steps through transparent outer faces (`uOpacity` 0.6–0.8) to accumulate color and reveal inner self-intersecting facets without depth sorting.

