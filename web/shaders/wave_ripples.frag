#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Interference of 2 to 8 travelling circular waves: each source radiates cos(k r - w t) with distance falloff, and sources orbit on Lissajous paths or rings. Styles: shaded height field, banded contours, neon crests, thin-film ramp.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Geometric"
    ],
    "LSD_PHASES": {"WavePhase": ["WaveSpeed"], "OrbitPhase": ["OrbitSpeed"]},
    "INPUTS": [
        { "NAME": "Symmetries", "LABEL": "Sources", "TYPE": "float", "DEFAULT": 4.0, "MIN": 2.0, "MAX": 8.0, "STEP": 1 },
        { "NAME": "Wavelength", "LABEL": "Wavelength", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "WaveSpeed", "LABEL": "Wave Speed", "TYPE": "float", "DEFAULT": 0.3, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "OrbitSpeed", "LABEL": "Orbit Speed", "TYPE": "float", "DEFAULT": 0.25, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Falloff", "LABEL": "Falloff", "TYPE": "float", "DEFAULT": 0.35, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SurfaceType", "LABEL": "Layout", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 2.0, "STEP": 1 },
        { "NAME": "ColorMethod", "LABEL": "Style", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "PaletteMode", "LABEL": "Palette Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "Contrast", "LABEL": "Contrast", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Detail", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 2.0, "MIN": 1.0, "MAX": 4.0, "STEP": 1 },
        { "NAME": "HueShift", "LABEL": "Hue Shift", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.75, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define Wavelength_ (0.5 * pow(0.08, Wavelength))          // spatial wavelength, screen-height units
#define Falloff_ (Falloff * 6.0)
#define Contrast_ (0.4 + Contrast * 1.6)
#define Saturation_ (Saturation * 1.6)
#define Brightness_ (Brightness * 2.0)

const float TAU = 6.28318531;
const int MAXSRC = 8;

vec3 hueRotate(vec3 c, float a) {
    float ca = cos(a), sa = sin(a);
    vec3 k = vec3(0.57735027);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}

// Cosine palettes a + b cos(2 pi (c t + d)).
vec3 palette(float t, int mode) {
    if (mode == 0) return 0.5 + 0.5 * cos(TAU * (t + vec3(0.0, 0.33, 0.67)));
    if (mode == 1) return 0.5 + 0.5 * cos(TAU * (vec3(0.8, 0.9, 1.0) * t + vec3(0.0, 0.1, 0.2) - 0.1)); // ember
    if (mode == 2) return 0.5 + 0.5 * cos(TAU * (vec3(0.7, 0.8, 0.9) * t + vec3(0.5, 0.6, 0.7)));        // ice
    return 0.5 + 0.5 * cos(TAU * (vec3(1.0, 1.0, 1.0) * t + vec3(0.0, 0.05, 0.1)));                       // near-mono
}

vec2 srcPos[MAXSRC];

void buildSources(int n, int lay, float th) {
    for (int i = 0; i < MAXSRC; i++) {
        float fi = float(i);
        vec2 p;
        if (lay == 0) {
            // Lissajous: integer frequencies keep the loop closed over one 2 pi of th.
            float fa = 1.0 + mod(fi, 3.0);
            float fb = 2.0 + mod(fi, 2.0) + floor(fi / 4.0);
            p = vec2(0.62 * sin(fa * th + fi * 1.3), 0.34 * sin(fb * th + fi * 2.1 + 1.0));
        } else if (lay == 1) {
            float a = TAU * fi / float(n) + th;
            p = 0.34 * vec2(cos(a), sin(a));
        } else {
            // Two counter-rotating rings, alternate sources.
            bool inner = mod(fi, 2.0) < 0.5;
            float a = TAU * floor(fi * 0.5) / max(floor(float(n) * 0.5), 1.0) + (inner ? th : -th);
            float rad = inner ? 0.16 : 0.40;
            p = rad * vec2(cos(a), sin(a));
        }
        srcPos[i] = p;
    }
}

// Height field h and its gradient g at uv.
float field(vec2 uv, int n, float k, float wphase, float fall, out vec2 g) {
    float h = 0.0;
    g = vec2(0.0);
    for (int i = 0; i < MAXSRC; i++) {
        if (i >= n) break;
        vec2 d = uv - srcPos[i];
        float r = max(length(d), 1e-4);
        float w = 1.0 / (1.0 + fall * r);
        float th = k * r - wphase + float(i) * 0.7;
        float c = cos(th), s = sin(th);
        h += c * w;
        float dr = -k * s * w - c * fall * w * w;   // d/dr of cos(k r - ..) * w
        g += dr * d / r;
    }
    return h;
}

vec3 shade(vec2 uv, int n, float k, float wphase, float fall, int style, int pal, float contrast) {
    vec2 g;
    float h = field(uv, n, k, wphase, fall, g);
    float inv = 1.0 / sqrt(0.5 * float(n));
    float hn = clamp(h * inv * 0.7, -1.0, 1.0);
    g *= inv * 0.7;
    float lambda = TAU / k;
    float pix = 1.0 / RENDERSIZE.y;

    if (style == 0) {
        // Shaded height field with a light and a specular lobe.
        vec3 nrm = normalize(vec3(-g * lambda * 0.16 * contrast, 1.0));
        vec3 L = normalize(vec3(-0.45, 0.5, 0.75));
        float diff = max(dot(nrm, L), 0.0);
        float spec = pow(max(dot(nrm, normalize(L + vec3(0.0, 0.0, 1.0))), 0.0), 60.0);
        vec3 base = palette(0.5 + 0.35 * hn, pal);
        return base * (0.25 + 0.85 * diff) + vec3(spec * 0.6);
    } else if (style == 1) {
        // Banded contours: palette-filled bands with dark isolines, edge width from the gradient.
        float bands = 3.0 + 3.0 * contrast;
        float v = hn * bands;
        float gl = max(length(g) * bands * pix, 1e-5);
        float dist = abs(fract(v) - 0.5);                 // 0.5 at the isoline
        float line = 1.0 - smoothstep(0.0, 1.5 * gl, 0.5 - dist);
        vec3 fillc = palette(floor(v) / (2.0 * bands) + 0.5, pal);
        return mix(fillc, vec3(0.02), line * 0.9);
    } else if (style == 2) {
        // Neon crests: glow on the constructive maxima, white core.
        float a = 0.5 + 0.5 * hn;
        float glow = pow(a, 3.0 + 6.0 * contrast);
        float core = pow(a, 14.0 + 24.0 * contrast);
        vec3 c = palette(0.15 + 0.5 * a + 0.1 * length(uv), pal);
        return c * glow * 1.3 + vec3(core) * 0.7;
    }
    // Thin-film ramp: height as film thickness, per-channel two-beam interference.
    float d = 300.0 + hn * 260.0 * contrast;
    vec3 lam = vec3(610.0, 540.0, 460.0);
    vec3 refl = 0.5 - 0.5 * cos(TAU * 2.0 * 1.45 * d / lam);
    vec3 nrm = normalize(vec3(-g * lambda * 0.08, 1.0));
    float sh = 0.75 + 0.5 * dot(nrm, normalize(vec3(-0.4, 0.5, 0.75)));
    return refl * sh;
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
    int n = clamp(int(floor(Symmetries + 0.5)), 2, MAXSRC);
    int lay = clamp(int(floor(SurfaceType + 0.5)), 0, 2);
    int style = clamp(int(floor(ColorMethod + 0.5)), 0, 3);
    int pal = clamp(int(floor(PaletteMode + 0.5)), 0, 3);
    int aa = clamp(int(floor(Detail + 0.5)), 1, 4);

    float k = TAU / Wavelength_;
    float wphase = TAU * fract(WavePhase * 1.5);       // fract keeps the phase small
    float orbit = TAU * fract(OrbitPhase * 0.3);       // integer path frequencies: closed loop
    buildSources(n, lay, orbit);

    vec3 acc = vec3(0.0);
    float cnt = 0.0;
    for (int j = 0; j < 4; j++) {
        if (j >= aa) break;
        for (int i = 0; i < 4; i++) {
            if (i >= aa) break;
            vec2 off = (vec2(float(i), float(j)) + 0.5) / float(aa) - 0.5;
            vec2 fc = isf_FragNormCoord * RENDERSIZE + off;   // deck Zoom / Rotate Z act through isf_FragNormCoord
            vec2 uv = (fc / RENDERSIZE - 0.5) * vec2(aspect, 1.0);
            acc += shade(uv, n, k, wphase, Falloff_, style, pal, Contrast_);
            cnt += 1.0;
        }
    }
    vec3 col = acc / cnt;

    col = hueRotate(col, HueShift * TAU);
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, Saturation_);
    col *= Brightness_;
    col = clamp(col, 0.0, 1.0);
    if (!(col.r == col.r) || !(col.g == col.g) || !(col.b == col.b)) col = vec3(0.0);
    gl_FragColor = blackToAlpha(col);
}
