#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Procedural halftone / LED matrix: a moving scalar pattern (plasma, radial pulse, diagonal bands or low-octave noise) is sampled per cell of a rotated grid and drawn as round halftone dots, glowing square LED pixels, line screens, or three-screen CMYK-style colour halftone.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Geometric"
    ],
    "LSD_PHASES": {"PatternPhase": ["PatternSpeed"]},
    "INPUTS": [
        { "NAME": "SurfaceType", "LABEL": "Pattern", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "ColorMethod", "LABEL": "Dot Style", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "PaletteMode", "LABEL": "Palette", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "Density", "LABEL": "Grid Density", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Sharpness", "LABEL": "Dot Sharpness", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "PatternSpeed", "LABEL": "Pattern Speed", "TYPE": "float", "DEFAULT": 0.3, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "PatternScale", "LABEL": "Pattern Scale", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "GridRotation", "LABEL": "Grid Rotation", "TYPE": "float", "DEFAULT": 0.1, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Contrast", "LABEL": "Contrast", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueShift", "LABEL": "Hue Shift", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.7, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Detail", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 2.0, "MIN": 1.0, "MAX": 3.0, "STEP": 1 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define Density_ (8.0+Density*72.0)              // cells per screen height
#define PatternScale_ (1.0+PatternScale*5.0)     // pattern feature frequency
#define GridRotation_ (GridRotation*1.5707963)   // 0..90 degrees (square grid is 90-periodic)
#define Contrast_ (0.5+Contrast*2.5)
#define Saturation_ (Saturation*1.6)
#define Brightness_ (Brightness*2.0)

const float TAU = 6.28318531;

float hash2(ivec2 c) {
    uint x = uint(c.x & 255);
    uint y = uint(c.y & 255);
    uint h = x * 1597334677u ^ y * 3812015801u;
    h ^= h >> 15;
    h *= 2246822519u;
    h ^= h >> 13;
    return float(h >> 8) * (1.0 / 16777216.0);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * (3.0 - 2.0 * f);
    ivec2 c = ivec2(i);
    return mix(mix(hash2(c), hash2(c + ivec2(1, 0)), u.x),
               mix(hash2(c + ivec2(0, 1)), hash2(c + ivec2(1, 1)), u.x), u.y);
}

// Driving scalar field in 0..1. Time enters only through sin/cos of a phase t in [0, 2 pi)
// with integer multiples, so wrapping t is seamless.
float pattern(vec2 uv, float t, int kind) {
    vec2 p = uv * PatternScale_ * 2.0;
    float f;
    if (kind == 0) {
        float v = sin(p.x + t)
                + sin(p.y * 1.3 + 2.0 * t)
                + sin((p.x + p.y) * 0.7 - t)
                + sin(length(p + 1.5 * vec2(sin(t), cos(t))) * 1.2 - t);
        f = 0.5 + 0.125 * v;
    } else if (kind == 1) {
        float r = length(p);
        f = 0.5 + 0.5 * sin(r * 1.6 - 2.0 * t);
        f = f * (0.6 + 0.4 * sin(atan(p.y, p.x + 1e-5) * 3.0 + t) * exp(-r * 0.08)) + 0.15 * f;
    } else if (kind == 2) {
        vec2 d = vec2(0.8253, 0.5646);
        float u = dot(p, d);
        float w = dot(p, vec2(-d.y, d.x));
        f = 0.5 + 0.5 * sin(u * 1.5 + t);
        f *= 0.7 + 0.3 * sin(w * 0.8 + 2.0 * t);
    } else {
        vec2 o1 = 1.5 * vec2(cos(t), sin(t));
        vec2 o2 = 1.2 * vec2(cos(2.0 * t + 1.0), sin(2.0 * t + 1.0));
        f = 0.65 * vnoise(p * 0.6 + o1 + vec2(40.0, 25.0))
          + 0.35 * vnoise(p * 1.3 + o2 + vec2(7.0, 91.0));
        f = (f - 0.5) * 1.6 + 0.5;
    }
    return clamp(0.5 + (clamp(f, 0.0, 1.0) - 0.5) * Contrast_, 0.0, 1.0);
}

vec3 hueRotate(vec3 c, float a) {
    float ca = cos(a), sa = sin(a);
    vec3 k = vec3(0.57735027);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}

vec3 palette(float f, int mode) {
    vec3 c;
    if (mode == 0) {
        c = vec3(0.35, 0.65, 1.0) * (0.55 + 0.45 * f);
    } else if (mode == 1) {
        c = 0.5 + 0.5 * cos(TAU * (f * 0.9 + vec3(0.0, 0.33, 0.67)));
    } else if (mode == 2) {
        c = clamp(vec3(f * 1.6, f * f * 1.5, f * f * f * f * 1.2), 0.0, 1.0);
    } else {
        c = mix(vec3(1.0, 0.1, 0.6), vec3(0.1, 0.9, 1.0), f);
    }
    c = hueRotate(c, HueShift * TAU);
    float lum = dot(c, vec3(0.299, 0.587, 0.114));
    return clamp(mix(vec3(lum), c, Saturation_), 0.0, 1.0);
}

vec2 rot(vec2 v, float a) {
    float c = cos(a), s = sin(a);
    return vec2(c * v.x - s * v.y, s * v.x + c * v.y);
}

// One dot screen. Returns coverage (0..1) of a round dot of area proportional to amt.
// g = grid coordinate, edge half-width w in cell units.
float roundDot(vec2 l, float amt, float w) {
    float h = 0.7071 * sqrt(clamp(amt, 0.0, 1.0));
    float cov = clamp((h - length(l)) / (2.0 * w) + 0.5, 0.0, 1.0);
    return cov * smoothstep(0.0, w, h);
}

vec3 shade(vec2 uv, float t, float pw, float soft, int sub) {
    int kind = clamp(int(floor(SurfaceType + 0.5)), 0, 3);
    int style = clamp(int(floor(ColorMethod + 0.5)), 0, 3);
    int pal = clamp(int(floor(PaletteMode + 0.5)), 0, 3);
    float w = pw * (sub > 1 ? 0.4 : 0.75) + soft + 1e-4;

    if (style == 3) {
        // Subtractive inks on paper: cyan 15, magenta 75, yellow 0 degrees from the base rotation.
        vec3 paper = vec3(0.96, 0.95, 0.92);
        vec3 inkC = vec3(0.0, 0.62, 0.85);
        vec3 inkM = vec3(0.9, 0.0, 0.5);
        vec3 inkY = vec3(1.0, 0.9, 0.0);
        vec3 col = paper;
        for (int i = 0; i < 3; i++) {
            float ang = GridRotation_ + (i == 0 ? 0.2617994 : (i == 1 ? 1.3089969 : 0.0));
            vec2 g = rot(uv, -ang) * Density_;
            vec2 cell = floor(g);
            vec2 l = g - cell - 0.5;
            vec2 cuv = rot((cell + 0.5) / Density_, ang);
            vec3 rgb = palette(pattern(cuv, t, kind), pal);
            float amt = 1.0 - (i == 0 ? rgb.r : (i == 1 ? rgb.g : rgb.b));
            float cov = roundDot(l, amt, w);
            col *= mix(vec3(1.0), i == 0 ? inkC : (i == 1 ? inkM : inkY), cov);
        }
        return col;
    }

    vec2 g = rot(uv, -GridRotation_) * Density_;
    vec2 cell = floor(g);
    vec2 l = g - cell - 0.5;
    vec3 bg = vec3(0.02, 0.02, 0.03);

    if (style == 2) {
        // Line screen: thickness follows the field, which varies continuously along the line.
        vec2 puv = rot(vec2(g.x, cell.y + 0.5) / Density_, GridRotation_);
        float f = pattern(puv, t, kind);
        float h = 0.5 * f * 1.05;
        float cov = clamp((h - abs(l.y)) / (2.0 * w) + 0.5, 0.0, 1.0) * smoothstep(0.0, w, h);
        return mix(bg, palette(f, pal), cov);
    }

    vec2 cuv = rot((cell + 0.5) / Density_, GridRotation_);
    float f = pattern(cuv, t, kind);
    vec3 pc = palette(f, pal);
    if (style == 0) {
        float cov = roundDot(l, f, w);
        return mix(bg, pc, cov);
    }
    // Square LED with a soft glow that is clipped to reach zero at the cell edge.
    float hb = 0.34;
    vec2 q = abs(l) - hb;
    float outside = length(max(q, 0.0));
    float inside = max(q.x, q.y);
    float sd = outside + min(inside, 0.0);
    float body = clamp(0.5 - sd / (2.0 * w), 0.0, 1.0);
    float edge0 = exp(-0.16 * 22.0);
    float glow = max(exp(-outside * 22.0) - edge0, 0.0) / (1.0 - edge0);
    float lit = 0.15 + 0.85 * f;
    float gAmt = 0.15 + 0.6 * (1.0 - Sharpness);
    vec3 col = bg + pc * (body * lit + glow * f * gAmt);
    return col;
}

void main() {
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 uv = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0);
    int n = clamp(int(floor(Detail + 0.5)), 1, 3);
    float t = mod(PatternPhase * 1.5, TAU);
    // Pixel footprint in cell units, and the extra edge softness from Dot Sharpness.
    float pw = Density_ / RENDERSIZE.y;
    float soft = (1.0 - Sharpness) * 0.22;

    vec3 sum = vec3(0.0);
    for (int j = 0; j < 3; j++) {
        if (j >= n) break;
        for (int i = 0; i < 3; i++) {
            if (i >= n) break;
            vec2 off = (vec2(float(i), float(j)) + 0.5) / float(n) - 0.5;
            sum += shade(uv + off / RENDERSIZE.y, t, pw, soft, n);
        }
    }
    vec3 col = sum / float(n * n) * Brightness_;
    col = clamp(col, 0.0, 1.0);
    gl_FragColor = vec4(col, 1.0);
}
