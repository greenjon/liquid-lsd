#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Dynamic Spiral",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Organic",
        "Liquid"
    ],
    "INPUTS": [
        { "NAME": "MaxPoints", "LABEL": "Max Points", "TYPE": "float", "DEFAULT": 500.0, "MIN": 100.0, "MAX": 2000.0, "STEP": 50 },
        { "NAME": "Scale", "LABEL": "Scale", "TYPE": "float", "DEFAULT": 0.47368, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Damping", "LABEL": "Damping", "TYPE": "float", "DEFAULT": 0.1984, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "WaveFreq", "LABEL": "Wave Freq", "TYPE": "float", "DEFAULT": 0.19192, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "WaveAmp", "LABEL": "Wave Amp", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Shear", "LABEL": "Shear", "TYPE": "float", "DEFAULT": 0.2, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Speed", "LABEL": "Speed", "TYPE": "float", "DEFAULT": 0.5, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "DotSize", "LABEL": "Dot Size", "TYPE": "float", "DEFAULT": 0.18367, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueSweep", "LABEL": "Hue Sweep", "TYPE": "float", "DEFAULT": 0.09091, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "TrailDecay", "LABEL": "Trail Decay", "TYPE": "float", "DEFAULT": 0.86735, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define Scale_ (0.05+Scale*0.95)
#define Damping_ (1.0+Damping*499.0)
#define WaveFreq_ (0.01+WaveFreq*0.99)
#define WaveAmp_ (WaveAmp*0.02)
#define Shear_ (Shear*0.5)
#define DotSize_ (0.001+DotSize*0.049)
#define Glow_ (Glow*5.0)
#define HueSweep_ (0.001+HueSweep*0.099)
#define TrailDecay_ (TrailDecay*0.98)


// IQ cosine palette
vec3 palette(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
    return a + b * cos(6.28318530 * (c * t + d));
}

// Solves A(n) = n^1.5 / (n + damping) == targetR for n using Newton-Raphson
float solveN(float targetR, float damping) {
    if (targetR <= 0.0) return 0.0;
    float n = targetR * targetR + sqrt(targetR * damping * 2.0);
    for (int k = 0; k < 4; k++) {
        float f = (n * sqrt(n)) / (n + damping) - targetR;
        float df = (sqrt(n) * (0.5 * n + 1.5 * damping)) / ((n + damping) * (n + damping));
        n -= f / max(df, 0.001);
    }
    return max(n, 0.0);
}

void main() {
    // Normalize to [-1, 1] with correct aspect ratio
    vec2 uv = isf_FragNormCoord * 2.0 - 1.0;
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    uv.x *= aspect;
    uv /= max(Scale_, 0.05);

    vec3 newLight = vec3(0.0);

    float integratedTime = TIME * Speed;
    float integratedShear = integratedTime * Shear_;

    // A(n) = n^1.5 / (n + D) is strictly increasing for all n,D > 0.
    // Solve for nMax such that A(nMax) == viewRadius so that MaxPoints points
    // are evenly distributed across the visible spiral on screen.
    float glowReach = Scale_ * 0.2;            // radius at which exp glow ≈ 0
    float viewRadius = aspect / Scale_ + glowReach;
    float nMax = solveN(viewRadius, Damping_);

    int maxN = int(clamp(MaxPoints, 1.0, 2000.0));
    float dn = nMax / float(maxN);

    // Per-fragment radial range culling:
    // Only iterate over points whose radial distance A(n) is close enough
    // to this fragment's distance r = length(uv) to contribute visible light.
    float pixelRadius = length(uv);
    float pointReach = DotSize_ + glowReach;
    float rMin = max(pixelRadius - pointReach, 0.0);
    float rMax = pixelRadius + pointReach;

    float nMin = solveN(rMin, Damping_);
    float nMaxRange = solveN(rMax, Damping_);

    int iStart = clamp(int(floor(nMin / dn)) - 1, 1, maxN);
    int iEnd   = clamp(int(ceil(nMaxRange / dn)) + 1, 1, maxN);

    for (int i = iStart; i <= iEnd; i++) {
        float n = float(i) * dn;

        // Radial amplitude: A(n) = n^1.5 / (n + damping).
        // Written as n*sqrt(n) to avoid the slow generic pow() path.
        float A = (n * sqrt(n)) / (n + Damping_);

        // Early exit if numerical precision overshoots viewRadius
        if (A > viewRadius) break;

        // Current angle using integrated time & integrated shear
        float omegaWave = WaveAmp_ * cos(WaveFreq_ * n);
        float theta = omegaWave * integratedTime + n * integratedShear;

        // Cartesian position
        vec2 pos = vec2(A * cos(theta), A * sin(theta));

        // Distance from pixel to this point
        float d = length(uv - pos);

        // Solid dot core
        float dotMask = smoothstep(DotSize_, DotSize_ * 0.5, d);

        // Exponential glow falloff (scale-adjusted so zoom feels consistent).
        float glowArg = d * (50.0 / max(Scale_, 0.01));
        float glowFactor = (glowArg < 10.0) ? exp(-glowArg) * Glow_ : 0.0;

        // Skip if this point contributes nothing to this pixel
        float contribution = dotMask + glowFactor;
        if (contribution <= 0.0) continue;

        // Color: IQ palette driven by index n.
        // HueSweep_ controls cycles-per-point; HueOffset rotates the whole palette.
        float t = n * HueSweep_ - integratedTime * 0.1;
        vec3 col = palette(
            t,
            vec3(0.5, 0.5, 0.5),
            vec3(0.5, 0.5, 0.5),
            vec3(1.0, 1.0, 1.0),
            vec3(HueOffset,
                 fract(HueOffset + 0.33333),
                 fract(HueOffset + 0.66667))
        );

        // Additive accumulation
        newLight += col * contribution;
    }

    // Reinhard tone-map to prevent blown-out whites
    newLight = newLight / (1.0 + newLight);

    gl_FragColor = vec4(newLight, uAlpha);
}
