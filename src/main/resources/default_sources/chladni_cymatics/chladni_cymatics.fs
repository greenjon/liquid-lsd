/*
{
    "DESCRIPTION": "Physical 2D acoustic plate resonance simulation (Chladni cymatics) with harmonic modal frequencies, boundary geometry folding, particle accumulation physics, and fluid antinode inversion.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Physics",
        "Organic"
    ],
    "INPUTS": [
        { "NAME": "FrequencyM", "LABEL": "Frequency M", "TYPE": "float", "DEFAULT": 3.0, "MIN": 1.0, "MAX": 16.0 },
        { "NAME": "FrequencyN", "LABEL": "Frequency N", "TYPE": "float", "DEFAULT": 5.0, "MIN": 1.0, "MAX": 16.0 },
        { "NAME": "FrequencyL", "LABEL": "Frequency L", "TYPE": "float", "DEFAULT": 2.0, "MIN": 0.0, "MAX": 12.0 },
        { "NAME": "PlateShape", "LABEL": "Plate Shape", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "NodeSharpness", "LABEL": "Node Sharpness", "TYPE": "float", "DEFAULT": 2.5, "MIN": 0.5, "MAX": 8.0 },
        { "NAME": "SandAccumulation", "LABEL": "Sand Accumulation", "TYPE": "float", "DEFAULT": 0.7, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "VibrationSpeed", "LABEL": "Vibration Speed", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 2.0 },
        { "NAME": "InvertMode", "LABEL": "Invert (Fluid Antinodes)", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 1.2, "MIN": 0.0, "MAX": 3.0 },
        { "NAME": "PaletteMode", "LABEL": "Palette Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Scale", "LABEL": "Scale", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.2, "MAX": 4.0 }
    ]
}
*/

const float PI = 3.14159265358979323846;

// Pseudo-random noise for sand grain texture
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 p = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0) / max(Scale, 0.05);

    // Vibration oscillation time phase
    float osc = sin(TIME * VibrationSpeed * 6.28318) * 0.15;

    // 1. Square plate standing wave calculation
    // w_sq = a * sin(n pi x) * sin(m pi y) - b * sin(m pi x) * sin(n pi y) + c * cos(l pi x) * cos(l pi y)
    float n = FrequencyN + osc;
    float m = FrequencyM - osc;
    float l = FrequencyL;

    float wSq = sin(n * PI * p.x) * sin(m * PI * p.y)
              - sin(m * PI * p.x) * sin(n * PI * p.y)
              + 0.5 * cos(l * PI * p.x) * cos(l * PI * p.y);

    // 2. Circular plate standing wave calculation (Bessel-like radial nodes)
    float r = length(p) * 2.0;
    float theta = atan(p.y, p.x);
    float wCirc = sin(r * n * PI) * cos(m * theta)
                + 0.4 * cos(r * l * PI);

    // Blend plate geometry based on PlateShape
    float w = mix(wSq, wCirc, PlateShape);

    // Boundary damping: plate borders have damping falloff
    float boundDist = mix(max(abs(p.x), abs(p.y)) * 2.0, length(p) * 2.0, PlateShape);
    float borderMask = smoothstep(1.8, 1.5, boundDist);

    // Distance to nodal line (|w| == 0)
    float nodeDist = abs(w);

    // Particle sand accumulation physics
    // Particles get thrown off antinodes and settle into nodes
    float sandDensity = exp(-pow(nodeDist * NodeSharpness, 2.0));
    
    // Add micro-granular sand noise
    float grain = hash(p * 250.0);
    sandDensity *= (0.6 + 0.8 * grain * SandAccumulation);

    // Antinode kinetic energy (fluid mode)
    float antinodeEnergy = abs(w) * borderMask;

    // Combine based on InvertMode
    float visualSignal = mix(sandDensity, antinodeEnergy, InvertMode);

    // Acoustic glow envelope
    float acousticGlow = exp(-nodeDist * 2.0) * (Glow * 0.3) * borderMask;

    // Color mapping
    int cMode = clamp(int(floor(PaletteMode + 0.5)), 0, 3);
    vec3 col = vec3(0.0);

    if (cMode == 0) {
        // Obsidian Plate & Shimmering Gold Sand
        vec3 plateCol = vec3(0.03, 0.03, 0.05);
        vec3 sandCol = vec3(1.0, 0.82, 0.25) * (0.8 + 0.4 * grain);
        col = mix(plateCol, sandCol, visualSignal);
        col += vec3(1.0, 0.6, 0.1) * acousticGlow;
    } else if (cMode == 1) {
        // Electric Cymatic Blue & Ultraviolet
        vec3 darkViolet = vec3(0.04, 0.01, 0.08);
        vec3 neonCyan = vec3(0.0, 0.85, 1.0);
        col = mix(darkViolet, neonCyan, visualSignal);
        col += vec3(0.3, 0.1, 1.0) * acousticGlow;
    } else if (cMode == 2) {
        // Prismatic Harmonic Spectrum
        float hue = fract(HueOffset + w * 0.3 + boundDist * 0.2);
        vec3 rainbow = hsv2rgb(vec3(hue, 0.85, 1.0));
        col = rainbow * visualSignal + rainbow * acousticGlow;
    } else {
        // Bioluminescent Emerald
        vec3 darkTeal = vec3(0.01, 0.04, 0.03);
        vec3 emerald = vec3(0.1, 1.0, 0.45);
        col = mix(darkTeal, emerald, visualSignal);
        col += vec3(0.0, 0.8, 0.3) * acousticGlow;
    }

    col *= borderMask;

    gl_FragColor = vec4(col, borderMask);
}
