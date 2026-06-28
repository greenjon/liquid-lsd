# Core Concepts

Spirals Desktop is structured around a few primary visual generation and mixing systems. Understanding these concepts will help you build complex, audio-reactive live performances.

## Mandala Synthesis Engine

The core generative visual source in Spirals is the **Mandala**. It uses mathematical formulas to draw complex, symmetrical geometric shapes.

### Lobes & Geometry
- **Lobe Count (Petals)**: Dictates the rotational symmetry of the mandala (how many arms or repeating patterns are generated).
- **Ratios & Libraries**: The system includes a curated library of about 300 classic mandala ratio presets that determine how overlapping lines intersect and form intricate patterns.

### Color Cycles & Palettes
- The mandala's colors dynamically cycle through color space based on parametric rates.
- Color cycles can be modulated by external audio CVs to change palette characteristics on musical accents.

### 3D Symmetrical Projections
- **3D Modes**: Extrudes the 2D mandala into 3D space using one of three symmetrical methods (or disables it in 2D mode):
  1. **Spherical Mapping**: Normalizes the 2D curve and projects it onto the surface of a 3D sphere using longitude ($\phi$) and latitude ($\theta$) angles. This creates intricate closed cages.
  2. **Polyhedral Reflections**: Replicates the spherical-mapped 3D curve across polyhedral reflection groups (Cubic/Octahedral with 8 instances, or Tetrahedral with 4 instances) to produce highly symmetric crystal lattices.
  3. **Coordinate Permutation**: Duplicates and renders the 2D loop onto the three primary coordinate planes simultaneously (XY, YZ, ZX planes) to create a mechanical, gyroscopic structure.
- **3D Controls**: Exposes parameters like `Sphere Wrap X/Y` (controlling longitude/latitude wrapping range), `Mirror Group` (Cubic vs. Tetrahedral mirroring), and `Permute XY/YZ/ZX` (individual plane scales).
- **Yaw, Pitch & Roll**: Supports 3D rotation of the mandala in space. Like all parameters, these can be modulated by CV sources (e.g. Bass CV modulating Yaw/Pitch).
- **Perspective Projection**: Slide between a flat Orthographic view (`3D Persp` = 0) and an immersive, deep Perspective view (`3D Persp` = 1).

### Feedback Loop (Ping-Pong FBOs)
- Each deck uses a dual Framebuffer Object (FBO) feedback loop.
- The output of the current frame is slightly scaled, rotated, blurred, or shifted in hue, and then blended back into the background of the next frame.
- This creates long-exposure trails, fluid organic movement, and standard video-feedback zoom effects.

---

## Dual-Deck Mixer

Spirals follows a traditional DJ/VJ layout, featuring two independent decks that feed into a central mixer.

### Deck A & Deck B
- Each deck acts as an independent generator running its own Visual Source (currently a Mandala).
- Decks possess individual settings for feedback parameters (Decay, Gain, Zoom, Rotate, Hue Shift, Blur, Chroma Offset, and Feedback Mode).

### Blending Modes
- The central Mixer blends the outputs of Deck A and Deck B using select blending equations: Additive blend (`ADD`), Screen blend (`SCREEN`), Multiply blend (`MULT`), Maximum/Lighten (`MAX`), or standard crossfade (`XFADE`).

### Crossfader Controls
- The crossfader (`crossfade`) slider controls the interpolation weight between Deck A and Deck B.
- Like all parameters, the crossfader can be modulated by a CV source (for example, audio-derived `bass` or an `LFO`) to alternate decks automatically in sync with the beat.
