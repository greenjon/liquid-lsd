#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2      uResolution;
uniform float     uTime;
uniform float     uPowerOn;
uniform float     uWarmupProgress;
uniform float     uBarrelStrength;
uniform float     uScanlineStrength;
uniform float     uShadowMaskStrength;
uniform float     uVignetteStrength;
uniform float     uChromaticAberration;

// --- Helpers ---

// Fast pseudo-random (animates with uTime for snow)
float rand(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233 + uTime * 0.1))) * 43758.5453);
}

// Barrel distortion — bends UV toward screen edges like curved glass
vec2 barrel(vec2 uv) {
    vec2 cc = uv - 0.5;
    float dist = dot(cc, cc);
    return uv + cc * dist * uBarrelStrength * 2.5;
}

// Vignette factor — darkens corners
float vignette(vec2 uv) {
    vec2 vc = uv - 0.5;
    return clamp(1.0 - dot(vc, vc) * uVignetteStrength * 3.5, 0.0, 1.0);
}

void main() {

    // -------------------------------------------------------
    // STATE: POWER OFF — animated static snow
    // -------------------------------------------------------
    if (uPowerOn < 0.01 && uWarmupProgress < 0.01) {
        float n1 = rand(vTexCoord);
        float n2 = rand(vTexCoord + vec2(0.1, 0.3));
        // Mix fine and coarse grain for texture variety
        float snow = n1 * 0.7 + n2 * 0.3;
        // Subtle horizontal scan lines in the static
        float scan = 0.85 + 0.15 * sin(vTexCoord.y * uResolution.y * 3.14159);
        fragColor = vec4(vec3(snow * scan), 1.0);
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
    // STATE: FULLY ON — CRT post-processing
    // -------------------------------------------------------

    // 1. Barrel distortion
    vec2 uv = barrel(vTexCoord);

    // 2. Out-of-bounds → black (curved screen shows bezel behind)
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // 3. Chromatic aberration — RGB channels slightly split toward edges
    vec2 caVec = (uv - 0.5) * uChromaticAberration;
    float r = texture(uTexture, uv + caVec).r;
    float g = texture(uTexture, uv).g;
    float b = texture(uTexture, uv - caVec).b;
    vec3 color = vec3(r, g, b);

    // 4. Scanlines — horizontal dark lines at pixel frequency
    //    Use uv.y * resolution for screen-space frequency
    float scanPhase = uv.y * uResolution.y;
    float scanline = 0.5 + 0.5 * sin(scanPhase * 3.14159);
    scanline = pow(scanline, 0.7);   // slightly sharpen the lines
    color *= mix(1.0, scanline, uScanlineStrength);

    // 5. RGB shadow mask — phosphor dot triad pattern
    //    3-pixel repeating pattern: R | G | B
    float maskPx = mod(uv.x * uResolution.x, 3.0);
    vec3 mask;
    mask.r = smoothstep(0.0, 0.5, maskPx) * (1.0 - smoothstep(0.5, 1.5, maskPx));
    mask.g = smoothstep(1.0, 1.5, maskPx) * (1.0 - smoothstep(1.5, 2.5, maskPx));
    mask.b = smoothstep(2.0, 2.5, maskPx) * (1.0 - smoothstep(2.5, 3.5, maskPx));
    // Bias toward white (full) so the mask is subtle rather than dark
    vec3 phosphorMask = vec3(0.65) + mask * 0.35;
    color *= mix(vec3(1.0), phosphorMask, uShadowMaskStrength);

    // 6. Vignette
    color *= vignette(uv);

    // 7. Subtle phosphor glow / ambient — tiny additive green tint at low levels
    //    Gives the impression of CRT phosphor persistence at dark areas
    color += vec3(0.0, 0.008, 0.004) * (1.0 - dot(color, vec3(0.299, 0.587, 0.114)));

    fragColor = vec4(color, 1.0);
}
