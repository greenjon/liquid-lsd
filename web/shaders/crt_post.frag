#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2      uResolution;
uniform float     uTime;
uniform float     uPowerOn;
uniform float     uWarmupProgress;
uniform float     uShutdownProgress;

// --- Helpers ---

// Fast pseudo-random (animates with uTime for snow)
float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233 + uTime * 0.1))) * 43758.5453);
}

void main() {

    // -------------------------------------------------------
    // STATE: POWERING DOWN — CRT Electron Beam Collapse
    // 1. Collapse vertically into bright horizontal line across middle (0.0 -> 0.42)
    // 2. Shrink horizontally into glowing pinpoint central dot (0.42 -> 0.75)
    // 3. Central dot phosphor decay & fade out to black (0.75 -> 1.0)
    // -------------------------------------------------------
    if (uShutdownProgress > 0.001 && uShutdownProgress < 1.0) {
        vec2 uv = vTexCoord;
        float s = uShutdownProgress;

        if (s <= 0.42) {
            // Phase 1: Vertical collapse into intense bright center line
            float p1 = s / 0.42;
            float vScale = max(0.0035, pow(1.0 - p1, 2.5) * 0.5);
            float distY = abs(uv.y - 0.5);

            if (distY < vScale) {
                // Inside squashed raster beam
                vec2 squashedUV = vec2(uv.x, 0.5 + (uv.y - 0.5) * (0.5 / max(vScale, 0.001)));
                vec3 col = texture(uTexture, squashedUV).rgb;
                float boost = 1.0 + p1 * 3.5;
                float core = exp(-distY * (350.0 / max(vScale * 2.0, 0.01))) * p1;
                col = col * boost + vec3(core * 1.5, core * 1.7, core * 1.6);
                fragColor = vec4(col, 1.0);
            } else {
                // Phosphor bloom glow outside compressed raster
                float glow = exp(-(distY - vScale) * 120.0) * p1 * 0.9;
                vec3 col = vec3(glow * 0.92, glow, glow * 0.95);
                fragColor = vec4(col, 1.0);
            }
            return;
        } else if (s <= 0.75) {
            // Phase 2: Horizontal collapse into dot
            float p2 = (s - 0.42) / 0.33;
            float hScale = max(0.005, pow(1.0 - p2, 2.2) * 0.5);
            float distX = abs(uv.x - 0.5);
            float distY = abs(uv.y - 0.5);

            float lineCore = exp(-distY * 500.0);
            float lineGlow = exp(-distY * 100.0) * 0.4;
            float edgeTaper = smoothstep(hScale + 0.01, max(0.0, hScale - 0.02), distX);
            float flareX = exp(-max(0.0, distX - hScale) * 150.0);

            float intensity = 2.5 + (1.0 - hScale / 0.5) * 2.5;
            float beam = (lineCore + lineGlow) * edgeTaper * intensity + lineCore * flareX * 0.7;

            vec3 col = vec3(beam * 0.95, beam * 1.0, beam * 0.92);
            fragColor = vec4(col, 1.0);
            return;
        } else {
            // Phase 3: Central dot phosphor decay & fade out
            float p3 = (s - 0.75) / 0.25;
            vec2 delta = (uv - 0.5) * vec2(uResolution.x / uResolution.y, 1.0);
            float r = length(delta);

            float dotCore = exp(-r * r * 45000.0);
            float dotHalo = exp(-r * 80.0) * 0.6;
            float decay = pow(1.0 - p3, 2.2);
            float brightness = (dotCore + dotHalo) * decay * 4.0;

            vec3 col = vec3(brightness * 0.95, brightness * 1.0, brightness * 0.90);
            fragColor = vec4(col, 1.0);
            return;
        }
    }

    // -------------------------------------------------------
    // STATE: FULLY OFF
    // -------------------------------------------------------
    if (uPowerOn < 0.01 || uShutdownProgress >= 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // -------------------------------------------------------
    // STATE: WARMING UP — white line expanding from center
    // uWarmupProgress: 0.0 (just turned on) → 1.0 (fully warm)
    // -------------------------------------------------------
    if (uWarmupProgress < 1.0) {
        float p = uWarmupProgress;

        // Phase 1 (0..0.75): expanding white line
        // Phase 2 (0.75..1.0): phosphor flash and settle
        float lineHalf;
        float brightness;

        if (p < 0.75) {
            // Normalized 0..1 within phase 1
            float p1 = p / 0.75;
            lineHalf = p1 * 0.5;             // grows from 0 to half-screen
            brightness = 0.9 + p1 * 0.1;    // brightens as it expands
        } else {
            // Phase 2: full raster flash + decay
            float p2 = (p - 0.75) / 0.25;   // 0..1 within phase 2
            lineHalf = 0.5;                  // full screen
            brightness = 1.3 - p2 * 0.4;    // brief overexposure then settles to ~0.9
        }

        float distFromCenter = abs(vTexCoord.y - 0.5);

        if (distFromCenter < lineHalf) {
            // Inside expanding raster: radial glow falloff from center
            float falloff = 1.0 - (distFromCenter / max(lineHalf, 0.001)) * 0.3;
            float col = brightness * falloff;
            // Slight green phosphor tint during warmup
            fragColor = vec4(col * 0.95, col, col * 0.90, 1.0);
        } else {
            // Outside raster: residual noise that fades as line expands
            float noiseAmt = (1.0 - p) * 0.4;
            float noise = rand(vTexCoord) * noiseAmt;
            fragColor = vec4(vec3(noise), 1.0);
        }
        return;
    }

    // -------------------------------------------------------
    // STATE: FULLY ON — Clean Direct Presentation
    // -------------------------------------------------------
    fragColor = texture(uTexture, vTexCoord);
}
