#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform vec2  uResolution;
uniform float uTime;
uniform float uAlpha;

// Feedback history from previous frame
uniform sampler2D src;

// Parameters (set by DynamicSpiral.setupUniforms)
uniform float uMaxPoints;   // integer-snapped by subclass
uniform float uScale;
uniform float uDamping;
uniform float uWaveFreq;
uniform float uWaveAmp;
uniform float uShear;
uniform float uSpeed;
uniform float uDotSize;
uniform float uGlow;
uniform float uHueOffset;
uniform float uHueSweep;
uniform float uTrailDecay;

// IQ cosine palette
vec3 palette(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
    return a + b * cos(6.28318530 * (c * t + d));
}

void main() {
    // Normalize to [-1, 1] with correct aspect ratio
    vec2 uv = vTexCoord * 2.0 - 1.0;
    float aspect = uResolution.x / uResolution.y;
    uv.x *= aspect;
    uv /= max(uScale, 0.05);

    vec3 newLight = vec3(0.0);
    float time = uTime * uSpeed;

    // A(n) = n^1.5 / (n + D) is strictly increasing for all n,D > 0.
    // Solve for nMax such that A(nMax) == viewRadius so that uMaxPoints points
    // are evenly distributed across the visible spiral on screen.
    float glowReach = uScale * 0.2;            // radius at which exp glow ≈ 0
    float viewRadius = aspect / uScale + glowReach;

    float nMax = viewRadius * viewRadius + sqrt(viewRadius * uDamping * 2.0);
    for (int k = 0; k < 4; k++) {
        float f = (nMax * sqrt(nMax)) / (nMax + uDamping) - viewRadius;
        float df = (sqrt(nMax) * (0.5 * nMax + 1.5 * uDamping)) / ((nMax + uDamping) * (nMax + uDamping));
        nMax -= f / max(df, 0.001);
    }
    nMax = max(nMax, 1.0);

    int maxN = int(clamp(uMaxPoints, 1.0, 2000.0));
    float dn = nMax / float(maxN);

    for (int i = 1; i <= maxN; i++) {
        float n = float(i) * dn;

        // Radial amplitude: A(n) = n^1.5 / (n + damping).
        // Written as n*sqrt(n) to avoid the slow generic pow() path.
        float A = (n * sqrt(n)) / (n + uDamping);

        // Early exit if numerical precision overshoots viewRadius
        if (A > viewRadius) break;

        // Angular velocity: omega(n) = amp*cos(freq*n) + shear*n
        float omega = uWaveAmp * cos(uWaveFreq * n) + uShear * n;

        // Current angle
        float theta = omega * time;

        // Cartesian position
        vec2 pos = vec2(A * cos(theta), A * sin(theta));

        // Distance from pixel to this point
        float d = length(uv - pos);

        // Solid dot core
        float dotMask = smoothstep(uDotSize, uDotSize * 0.5, d);

        // Exponential glow falloff (scale-adjusted so zoom feels consistent).
        // Only compute exp() when the point is close enough to matter.
        float glowArg = d * (50.0 / max(uScale, 0.01));
        float glowFactor = (glowArg < 10.0) ? exp(-glowArg) * uGlow : 0.0;

        // Skip if this point contributes nothing to this pixel
        float contribution = dotMask + glowFactor;
        if (contribution <= 0.0) continue;

        // Color: IQ palette driven by index n.
        // uHueSweep controls cycles-per-point; uHueOffset rotates the whole palette.
        float t = n * uHueSweep - time * 0.1;
        vec3 col = palette(
            t,
            vec3(0.5, 0.5, 0.5),
            vec3(0.5, 0.5, 0.5),
            vec3(1.0, 1.0, 1.0),
            vec3(uHueOffset,
                 fract(uHueOffset + 0.33333),
                 fract(uHueOffset + 0.66667))
        );

        // Additive accumulation
        newLight += col * contribution;
    }

    // Reinhard tone-map to prevent blown-out whites
    newLight = newLight / (1.0 + newLight);

    // Decay the previous frame and add new light on top
    vec3 prevFrame = texture(src, vTexCoord).rgb;
    vec3 finalColor = prevFrame * uTrailDecay + newLight;

    fragColor = vec4(finalColor, uAlpha);
}
