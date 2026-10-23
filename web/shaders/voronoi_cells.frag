#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Animated Worley/Voronoi cells: nine hashed feature points per pixel (3x3 neighbourhood), each orbiting on a small path, with the two nearest distances F1 and F2 shaded as flat cell colours, cracked-glass edge glow, distance rings or a faceted gem. Euclidean, Manhattan or Chebyshev metric.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Geometric"
    ],
    "LSD_PHASES": {"SpeedPhase": ["Speed"]},
    "INPUTS": [
        { "NAME": "ColorMethod", "LABEL": "Style", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "SurfaceType", "LABEL": "Metric", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 2.0, "STEP": 1 },
        { "NAME": "PaletteMode", "LABEL": "Palette", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "CellScale", "LABEL": "Cell Scale", "TYPE": "float", "DEFAULT": 0.35, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Jitter", "LABEL": "Jitter", "TYPE": "float", "DEFAULT": 0.8, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Speed", "LABEL": "Speed", "TYPE": "float", "DEFAULT": 0.3, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "EdgeWidth", "LABEL": "Edge Width", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "EdgeGlow", "LABEL": "Edge Glow", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.75, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Detail", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 2.0, "MIN": 1.0, "MAX": 3.0, "STEP": 1 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define CellScale_ (2.0+CellScale*14.0)
#define EdgeWidth_ (0.01+EdgeWidth*0.19)
#define EdgeGlow_ (EdgeGlow*2.0)
#define Saturation_ (Saturation*1.6)
#define Brightness_ (Brightness*2.0)

const float TAU = 6.28318531;

// Integer hash of a lattice cell (two multiply-xorshift rounds); identical on every GPU.
uint hashCell(ivec2 c) {
    uint h = uint(c.x) * 1597334677u ^ uint(c.y) * 3812015801u;
    h ^= h >> 15;
    h *= 2246822519u;
    h ^= h >> 13;
    h *= 3266489917u;
    h ^= h >> 16;
    return h;
}

float u01(uint h, int shift) {
    return float((h >> uint(shift)) & 255u) * (1.0 / 255.0);
}

float metric(vec2 d, int m) {
    if (m == 1) return abs(d.x) + abs(d.y);
    if (m == 2) return max(abs(d.x), abs(d.y));
    return length(d);
}

vec3 hsv2rgb(vec3 c) {
    vec3 k = clamp(abs(fract(c.x + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    return c.z * mix(vec3(1.0), k, c.y);
}

vec3 palette(float t, int mode) {
    if (mode == 0) return hsv2rgb(vec3(t, 0.8, 0.95));
    vec3 d = mode == 1 ? vec3(0.00, 0.10, 0.20) : (mode == 2 ? vec3(0.55, 0.65, 0.75) : vec3(0.60, 0.62, 0.64));
    float f = mode == 3 ? 0.35 : 1.0;
    return 0.5 + 0.5 * cos(TAU * (vec3(f) * t + d));
}

vec3 hueRotate(vec3 c, float a) {
    // Rotation about the grey axis (1,1,1)/sqrt(3).
    float ca = cos(a), sa = sin(a);
    vec3 k = vec3(0.57735027);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}

// Style shading for one sample. p is in cell units.
vec3 shade(vec2 p, float tt, int style, int met, int pal, float jit, float ew, float aa, float gl) {
    ivec2 ic = ivec2(floor(p));
    float f1 = 1e4;
    float f2 = 1e4;
    uint id1 = 0u;
    vec2 d1 = vec2(0.0);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            ivec2 c = ic + ivec2(i, j);
            uint h = hashCell(c);
            // Each feature point orbits its cell centre; integer cycle counts make the phase wrap seamless.
            vec2 ph = vec2(u01(h, 0), u01(h, 8));
            vec2 k = vec2(1.0 + floor(u01(h, 16) * 2.99), 1.0 + floor(u01(h, 24) * 2.99));
            if ((h & 0x1000000u) != 0u) k.x = -k.x;
            if ((h & 0x2000000u) != 0u) k.y = -k.y;
            vec2 o = 0.5 + 0.5 * jit * sin(TAU * (ph + k * tt));
            vec2 d = vec2(float(i), float(j)) + o - (p - vec2(ic));
            float m = metric(d, met);
            if (m < f1) {
                f2 = f1;
                f1 = m;
                id1 = h;
                d1 = d;
            } else if (m < f2) {
                f2 = m;
            }
        }
    }
    float edge = max(f2 - f1, 0.0);
    float idv = u01(id1 >> 3u, 0) + 0.0039 * u01(id1, 24);
    vec3 cell = palette(idv, pal);
    // Crack mask: 1 on the cell border, 0 inside (F2-F1 -> 0 on the border).
    float crack = 1.0 - smoothstep(ew - aa, ew + aa, edge);
    float glow = exp(-edge / (ew * 3.0 + 0.02));
    vec3 col;
    if (style == 0) {
        col = mix(cell * (0.75 + 0.25 * u01(id1, 4)), cell * 0.12, crack) + cell * glow * 0.25 * gl;
    } else if (style == 1) {
        col = cell * 0.10 * (1.0 - f1 * 0.5);
        col += vec3(1.0, 0.95, 0.85) * crack * 0.9 + cell * glow * 0.7 * gl + vec3(1.0, 0.9, 0.7) * glow * glow * 0.25 * gl;
    } else if (style == 2) {
        float r = abs(fract(f1 * 3.0) - 0.5) * 2.0;
        float ring = 1.0 - smoothstep(0.12 - aa * 1.5, 0.12 + aa * 1.5, r);
        col = cell * (0.25 + 0.5 * (1.0 - clamp(f1, 0.0, 1.0))) + cell * ring * 0.9 + vec3(crack) * 0.35 + cell * glow * 0.4 * gl;
    } else {
        // Facet: a cone around the site gives a normal; light from the upper left.
        // d1 points pixel -> site, so the outward direction is -d1.
        vec3 n = normalize(vec3(-d1 * 1.2, 0.6));
        vec3 L = normalize(vec3(-0.5, 0.6, 0.65));
        float lam = clamp(dot(n, L), 0.0, 1.0);
        vec3 H = normalize(L + vec3(0.0, 0.0, 1.0));
        float spec = pow(max(dot(n, H), 0.0), 24.0);
        col = cell * (0.25 + 0.95 * lam) + vec3(spec * 0.5);
        col = mix(col, vec3(1.0, 0.97, 0.9) * (0.5 + 0.5 * glow), crack * 0.8) + cell * glow * 0.3 * gl;
    }
    return col;
}

void main() {
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 uv = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0);
    int style = clamp(int(floor(ColorMethod + 0.5)), 0, 3);
    int met = clamp(int(floor(SurfaceType + 0.5)), 0, 2);
    int pal = clamp(int(floor(PaletteMode + 0.5)), 0, 3);
    int q = clamp(int(floor(Detail + 0.5)), 1, 3);

    float tt = mod(SpeedPhase * 0.4, 1.0);
    float sc = CellScale_;
    vec2 p = uv * sc + vec2(37.0, 53.0);

    // Edge-width in F2-F1 units is scale independent; antialias width is one pixel in cell units.
    float px = sc / RENDERSIZE.y;
    float aa = (q == 1 ? 0.25 : 1.0) * px;
    float jit = Jitter;
    float ew = EdgeWidth_;

    vec3 col;
    if (q == 3) {
        col = vec3(0.0);
        for (int s = 0; s < 4; s++) {
            vec2 o = (vec2(float(s & 1), float(s >> 1)) - 0.5) * 0.5 * px;
            col += shade(p + o, tt, style, met, pal, jit, ew, aa, EdgeGlow_);
        }
        col *= 0.25;
    } else {
        col = shade(p, tt, style, met, pal, jit, ew, aa, EdgeGlow_);
    }

    col = hueRotate(col, HueOffset * TAU);
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, Saturation_);
    col *= Brightness_;
    col = clamp(col, 0.0, 1.0);
    gl_FragColor = vec4(col, 1.0);
}
