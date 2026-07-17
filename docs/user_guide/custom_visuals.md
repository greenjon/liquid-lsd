# Custom Visual Sources

Liquid LSD is designed to be highly extensible. While it comes with several built-in procedural visual generators (like Mandalas, Mandelbulbs, and Gyroids), you are not limited to them. You can easily add, share, and customize your own visual sources!

## Adding a Custom Visual Source

If you've downloaded a custom visual source from someone else, installing it is incredibly simple:

1. Navigate to the `presets/sources/` directory inside your Liquid LSD folder.
2. Create a new folder (e.g. `presets/sources/my_cool_shader/`).
3. Drop the provided `meta.json` and `.frag` (shader) files into this folder.
4. Launch Liquid LSD.

The app will automatically detect your new visual source, compile it, and seamlessly integrate it into the UI. You'll find it available in the **Source** selector under the Deck parameters, complete with all its custom sliders and modulation options!

---

## Creating Your Own Custom Visuals (Advanced)

If you're comfortable writing GLSL fragment shaders (similar to Shadertoy), you can create your own custom video sources from scratch. 

A custom visual source requires two files in a subfolder within `presets/sources/`:
1. `meta.json` (defines the UI and parameters)
2. `[shader_name].frag` (the GLSL code)

### The `meta.json` File

This file acts as a manifest for your shader. It tells the Liquid LSD UI what sliders to draw and what minimum/maximum values to use.

Here is an example `meta.json` for a simple pulsing circle:

```json
{
  "id": "pulsing_circle",
  "name": "Pulsing Circle",
  "parameters": [
    {
      "name": "Radius",
      "default": 0.5,
      "min": 0.1,
      "max": 2.0
    },
    {
      "name": "Glow Intensity",
      "default": 0.8,
      "min": 0.0,
      "max": 5.0
    }
  ]
}
```

- **id**: A unique string identifier for your shader. It should match the filename of your `.frag` file (e.g., `pulsing_circle.frag`).
- **name**: The human-readable name that will appear in the Liquid LSD UI.
- **parameters**: An array of parameter definitions. For each parameter, Liquid LSD will automatically generate a slider in the UI, complete with CV modulation capabilities.

> [!TIP]
> **View Grouping Rules**
> If you want specific parameters to appear inside the standardized **View** UI subgroup rather than your shader's general subgroup, you must name them exactly as follows:
> `Zoom`, `Rotate X`, `Rotate Y`, `Rotate Z`, `Cam Rotate X`, `Cam Rotate Y`, `Cam Rotate Z`, `Scale`, `Scale X`, `Scale Y`, `Scale Z`.
> Liquid LSD will automatically detect these parameter names and group them logically for the user.

### The Shader File (`.frag`)

Your fragment shader should be written in standard GLSL (version `330 core`). 

Liquid LSD will automatically inject several built-in uniforms into your shader that you can use out of the box:
- `uniform float uTime;` (Time in seconds since launch)
- `uniform vec2 uResolution;` (The width and height of the rendering target)
- `uniform float uAlpha;` (The global master gain/alpha for the deck)

Additionally, **every parameter you define in `meta.json` is automatically injected as a uniform float.** Liquid LSD simply takes the parameter's `"name"`, removes the spaces, and prefixes it with `u`. 

For example, the parameters defined in the JSON above will be available in your shader as:
```glsl
uniform float uRadius;
uniform float uGlowIntensity;
```

#### Example Shader
Here is how you might write the `pulsing_circle.frag` to use the parameters defined in the JSON:

```glsl
#version 330 core

out vec4 FragColor;

// Built-in Liquid LSD Uniforms
uniform float uTime;
uniform vec2 uResolution;
uniform float uAlpha;

// Your Custom Uniforms (from meta.json)
uniform float uRadius;
uniform float uGlowIntensity;

void main() {
    // Normalize pixel coordinates (from -1 to 1)
    vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution.xy) / uResolution.y;

    // Calculate distance from center
    float dist = length(uv);

    // Apply the custom radius parameter (pulsing with time)
    float currentRadius = uRadius + sin(uTime * 2.0) * 0.1;

    // Create a smooth circle
    float circle = smoothstep(currentRadius, currentRadius - 0.02, dist);

    // Apply the glow intensity
    vec3 color = vec3(0.2, 0.5, 1.0) * circle * uGlowIntensity;

    // Output to screen (always multiply final alpha by uAlpha!)
    FragColor = vec4(color, uAlpha * circle);
}
```

### Tips for Creators
1. **Always apply `uAlpha`**: Make sure your final `FragColor.a` (or `FragColor.rgb`) is multiplied by `uAlpha`. If you don't, the Deck's master gain slider will not affect your shader.
2. **Use CVs to test boundaries**: Your parameters will be connected to the CV modulation matrix. Test your shader with extreme values (via Random or LFO modulators) to ensure it doesn't crash or produce black screens when parameters hit their absolute minimums or maximums. 
3. **No Recompilation Required**: You don't need to rebuild Liquid LSD when writing shaders. Just save your `.frag` and `meta.json` files and restart the app to see the changes!
