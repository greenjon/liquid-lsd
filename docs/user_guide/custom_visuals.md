# Custom Visual Sources & Shaders

Liquid LSD is designed for extensibility. Beyond its built-in procedural visual generators (Mandalas, Gyroids, Chladni, Dynamic Spiral, Attractor Feedback), you can easily build, install, and share custom dynamic GLSL visual sources.

---

## Installing a Custom Visual Source

1. Open the `library/sources/` directory inside your Liquid LSD installation folder.
2. Create a new subfolder (e.g. `library/sources/my_cool_shader/`).
3. Place your `meta.json` manifest and `shader.frag` file into this folder.
4. Launch Liquid LSD.

The application automatically scans `library/sources/` on startup, compiles new shaders against the shared vertex pipeline, generates UI sliders for parameters, and adds the source to the Deck Source selector.

---

## Creating Custom Visual Sources

A custom visual source subfolder requires two files:
1. `meta.json` (defines UI controls, parameters, and descriptions)
2. `[shader_name].frag` (standard GLSL 330 core fragment shader)

### The `meta.json` Manifest Format

The manifest file defines the shader metadata, UI parameters, and optional documentation strings:

```json
{
  "id": "pulsing_circle",
  "name": "Pulsing Circle",
  "description": "Generates a glowing, pulse-modulated vector circle.",
  "parameters": [
    {
      "name": "Radius",
      "default": 0.5,
      "min": 0.1,
      "max": 2.0,
      "description": "Base radius of the generated circle."
    },
    {
      "name": "Glow Intensity",
      "default": 0.8,
      "min": 0.0,
      "max": 5.0,
      "description": "Multiplicative brightness and falloff glow depth."
    }
  ]
}
```

#### Manifest Fields
- **`id`**: Unique string identifier; must match the `.frag` filename (e.g. `pulsing_circle.frag`).
- **`name`**: Human-readable source title shown in UI dropdowns.
- **`description`** *(Optional)*: Engine description surfaced in UI tooltips and hover popups.
- **`parameters`**: Array of modulatable parameter descriptors:
  - `name`: Parameter title.
  - `default`, `min`, `max`: Parameter range bounds.
  - `description` *(Optional)*: Parameter documentation string displayed in the Preset Grid hover tooltip.

> [!TIP]
> **Automatic View Subgrouping**  
> If you name specific parameters using standard spatial names (`Zoom`, `Rotate X`, `Rotate Y`, `Rotate Z`, `Cam Rotate X`, `Cam Rotate Y`, `Cam Rotate Z`, `Scale`, `Scale X`, `Scale Y`, `Scale Z`), Liquid LSD will automatically group them inside the standardized **View** UI collapsible subgroup.

---

## The Fragment Shader (`.frag`)

Shaders are written in GLSL version `330 core`.

### Built-in Injected Uniforms
Liquid LSD automatically injects core rendering uniforms:
- `uniform float uTime;` — Time in seconds since application launch.
- `uniform vec2 uResolution;` — Target rendering viewport width and height in pixels.
- `uniform float uAlpha;` — Deck master gain / opacity setting (`0.0` to `1.0`).

### Parameter Uniform Mapping
Every parameter defined in `meta.json` is automatically injected as a `uniform float`. Parameter names are converted by removing spaces and prefixing with `u` (e.g., `"Glow Intensity"` $\rightarrow$ `uniform float uGlowIntensity;`).

### Shader Example (`pulsing_circle.frag`)

```glsl
#version 330 core

out vec4 FragColor;

// Built-in Injected Uniforms
uniform float uTime;
uniform vec2 uResolution;
uniform float uAlpha;

// Parameter Uniforms (from meta.json)
uniform float uRadius;
uniform float uGlowIntensity;

void main() {
    // Normalize UV coordinates (-1 to 1)
    vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution.xy) / uResolution.y;

    // Distance from center
    float dist = length(uv);

    // Apply radius parameter with time pulse
    float currentRadius = uRadius + sin(uTime * 2.0) * 0.1;
    float circle = smoothstep(currentRadius, currentRadius - 0.02, dist);

    // Apply glow intensity
    vec3 color = vec3(0.2, 0.5, 1.0) * circle * uGlowIntensity;

    // Output final color (always multiply by uAlpha for Deck gain control!)
    FragColor = vec4(color, uAlpha * circle);
}
```

---

## Creator Best Practices

1. **Always Multiply by `uAlpha`**: Ensure `FragColor` final alpha or RGB output is scaled by `uAlpha`.
2. **Handle Extreme Parameter Bounds**: Test parameters against extreme min/max values (via Random or LFO modulators) to verify shader stability.
3. **Hot Shader Reloading**: Modify `.frag` or `meta.json` files and restart the app to see immediate updates.
