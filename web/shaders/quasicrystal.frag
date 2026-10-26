#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Quasicrystal interference: the sum of 5 to 8 rotated cosine gratings with drifting phases gives Penrose-like quasi-periodic patterns. Smooth, banded, two-tone or neon-line styles, cosine palettes, spin and signed drift.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Geometric"
    ],
    "LSD_PHASES": {"SpinPhase": ["SpinSpeed"], "DriftPhase": ["DriftSpeed"]},
    "INPUTS": [
        { "NAME": "Symmetries", "LABEL": "Gratings", "TYPE": "float", "DEFAULT": 7.0, "MIN": 5.0, "MAX": 8.0, "STEP": 1 },
        { "NAME": "ColorMethod", "LABEL": "Style", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "PaletteMode", "LABEL": "Palette", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "Frequency", "LABEL": "Frequency", "TYPE": "float", "DEFAULT": 0.35, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Bands", "LABEL": "Bands", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Sharpness", "LABEL": "Sharpness", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Rotation", "LABEL": "Rotation", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "SpinSpeed", "LABEL": "Spin Speed", "TYPE": "float", "DEFAULT": 0.1, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "DriftSpeed", "LABEL": "Drift Speed", "TYPE": "float", "DEFAULT": 0.25, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.85, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define Frequency_ (1.5+Frequency*10.5)
#define Bands_ (0.5+Bands*3.5)
#define Rotation_ (Rotation*3.14159265)
#define Brightness_ (Brightness*2.0)

const float TAU = 6.28318530717958647692;
const float PI = 3.14159265358979323846;

// Cosine palette a + b cos(2 pi (c t + d)); four families, hue offset shifts the phase.
vec3 palette(float t, float hue, int mode) {
    vec3 a = vec3(0.5);
    vec3 b = vec3(0.5);
    vec3 c = vec3(1.0);
    vec3 d = vec3(0.0, 0.33, 0.67);
    if (mode == 1) {            // ember
        c = vec3(1.0, 0.8, 0.6);
        d = vec3(0.0, 0.12, 0.25);
    } else if (mode == 2) {     // deep sea
        a = vec3(0.4, 0.5, 0.55);
        c = vec3(0.9, 1.0, 0.7);
        d = vec3(0.55, 0.35, 0.2);
    } else if (mode == 3) {     // neon
        c = vec3(1.0, 1.0, 1.0);
        d = vec3(0.85, 0.2, 0.5);
    }
    return a + b * cos(TAU * (c * t + d + hue));
}

// Normalised grating sum (unit standard deviation) at a point.
float gratings(vec2 p, int n, float k, float spin, float drift) {
    float sum = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= n) break;
        float fi = float(i);
        float ang = PI * fi / float(n) + spin;
        // each grating drifts at its own rate; wrapped so the phase stays precise
        float w = 0.7 + 0.6 * fract(0.618034 * (fi + 1.0));
        float ph = mod(drift * w, TAU);
        sum += cos(k * dot(p, vec2(cos(ang), sin(ang))) + ph);
    }
    return sum * inversesqrt(0.5 * float(n));
}

vec3 shade(float q, float fw, float sharp, int style, int pmode, float hue) {
    float x = q * Bands_ * 0.5;
    float fx = max(fw * Bands_ * 0.5, 1e-4);
    float soft = mix(2.5, 0.6, sharp);
    if (style == 0) {
        // smooth palette sweep
        return palette(x * 0.5, hue, pmode);
    } else if (style == 1) {
        // posterised bands separated by thin dark contour lines
        float id = floor(x);
        float f = fract(x);
        float e = min(f, 1.0 - f);
        float lw = fx * soft;
        float line = smoothstep(0.0, lw * 1.5 + 0.02 * (1.0 - sharp), e);
        vec3 c = palette(id * 0.13 + 0.5 * x * 0.0, hue, pmode);
        return c * mix(0.15, 1.0, line);
    } else if (style == 2) {
        // two-tone threshold blocks (tile-like)
        float m = smoothstep(-fx * PI * soft, fx * PI * soft, cos(PI * x));
        return mix(palette(0.15, hue, pmode), palette(0.6, hue, pmode), m);
    }
    // neon contour lines on a dark ground
    float d = abs(sin(PI * x));
    float g = pow(clamp(1.0 - d, 0.0, 1.0), mix(6.0, 48.0, sharp));
    vec3 c = palette(x * 0.25, hue, pmode);
    return c * (0.05 + 1.5 * g) + vec3(g * g * 0.25);
}

// Black is transparent: alpha follows the brightest channel (the renderer stores colour x alpha, so the
// stored pixel is the colour itself and a deck's BG layer shows through wherever this source is dark).
vec4 blackToAlpha(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float a = max(c.r, max(c.g, c.b));
    return vec4(c / max(a, 1e-4), a);
}

void main() {
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 pix = 1.0 / RENDERSIZE;
    int n = clamp(int(floor(Symmetries + 0.5)), 5, 8);
    int style = clamp(int(floor(ColorMethod + 0.5)), 0, 3);
    int pmode = clamp(int(floor(PaletteMode + 0.5)), 0, 3);
    float k = TAU * Frequency_;
    float spin = mod(Rotation_ + SpinPhase * 0.6, TAU);
    float drift = DriftPhase * 2.0;
    float sharp = clamp(Sharpness, 0.0, 1.0);

    // contour widths from the pixel footprint of the centre sample
    vec2 c0 = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0);
    float qc = gratings(c0, n, k, spin, drift);
    float fw = max(fwidth(qc), 1e-4);

    // Quality: 1 sample below 0.5, 2x2 supersampling from 0.5 up
    int aa = Quality >= 0.5 ? 2 : 1;
    vec3 acc = vec3(0.0);
    for (int j = 0; j < 2; j++) {
        for (int i = 0; i < 2; i++) {
            if (i >= aa || j >= aa) continue;
            vec2 o = aa == 2 ? (vec2(float(i), float(j)) - 0.5) * 0.5 : vec2(0.0);
            vec2 p = (isf_FragNormCoord + o * pix - 0.5) * vec2(aspect, 1.0);
            float q = gratings(p, n, k, spin, drift);
            acc += shade(q, fw, sharp, style, pmode, HueOffset);
        }
    }
    vec3 col = acc / float(aa * aa);

    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(luma), col, Saturation) * Brightness_;
    gl_FragColor = blackToAlpha(col);
}
