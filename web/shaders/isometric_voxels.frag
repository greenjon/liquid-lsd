#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Q*bert-style isometric cube field: a grid of cube columns whose heights follow a ripple, noise or diagonal-sweep wave, drawn with top, left and right face shading. Occlusion is solved analytically per height layer (no raymarching).",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Geometric"
    ],
    "LSD_PHASES": {"SpeedPhase": ["Speed"]},
    "INPUTS": [
        { "NAME": "SurfaceType", "LABEL": "Wave", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 2.0, "STEP": 1 },
        { "NAME": "CellSize", "LABEL": "Cell Size", "TYPE": "float", "DEFAULT": 0.45, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "MaxHeight", "LABEL": "Max Height", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Speed", "LABEL": "Wave Speed", "TYPE": "float", "DEFAULT": 0.3, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Frequency", "LABEL": "Wave Scale", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Gap", "LABEL": "Edge Width", "TYPE": "float", "DEFAULT": 0.25, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Contrast", "LABEL": "Face Contrast", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "PaletteMode", "LABEL": "Palette", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.85, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define CubesAcross_ (30.0 - CellSize * 24.0)      // cube widths per screen height: 30 .. 6
#define WaveFreq_ (0.35 + Frequency * 1.3)
#define EdgeW_ (Gap * 0.12)
#define Contrast_ (Contrast * 0.85)
#define Brightness_ (Brightness * 2.0)

const float TAU = 6.28318530718;
const float ISO_A = 0.86602540378;   // cos(30 deg): half-width of a unit cube's hexagon
const int MAX_LAYERS = 8;

// Isometric projection (z up): u = (x - y) * ISO_A, v = (x + y) / 2 + z.
// A pixel (u, v) is the image of the ray x = v + s - Z, y = v - s - Z with s = u / (2 ISO_A),
// parametrised by its height Z; larger Z is nearer the camera.

float hash21(vec2 id) {
    uvec2 q = uvec2(ivec2(id));
    uint h = q.x * 1597334677u ^ q.y * 3812015801u;
    h = (h ^ (h >> 15)) * 2246822519u;
    h ^= h >> 13;
    h *= 3266489917u;
    h ^= h >> 16;
    return float(h & 0xFFFFFFu) / 16777216.0;
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float gPhase;   // wrapped wave phase [0, TAU)
float gDrift;   // wrapped drift distance for the noise wave
int gWave;
int gHn;

// Wave value in 0..1 for cell (i, j).
float waveField(vec2 c) {
    float f;
    if (gWave == 0) {
        float r = length(c + vec2(0.5));
        f = 0.5 + 0.5 * sin(r * WaveFreq_ * 0.9 - gPhase);
    } else if (gWave == 1) {
        f = vnoise(c * (WaveFreq_ * 0.3) + vec2(gDrift, gDrift * 0.57));
        f = clamp((f - 0.5) * 1.5 + 0.5, 0.0, 1.0);
    } else {
        float d = (c.x + c.y) * WaveFreq_ * 0.18 - gPhase / TAU;
        float tri = 1.0 - abs(2.0 * fract(d) - 1.0);
        f = tri * tri * (3.0 - 2.0 * tri);
    }
    return f;
}

// Integer column height 0..gHn.
int heightOf(vec2 c) {
    return int(floor(waveField(c) * float(gHn) + 0.5));
}

vec3 palette(float t) {
    int m = clamp(int(floor(PaletteMode + 0.5)), 0, 3);
    t += HueOffset;
    vec3 a = vec3(0.5), b = vec3(0.5), cc = vec3(1.0), d;
    if (m == 0)      { d = vec3(0.00, 0.33, 0.67); }
    else if (m == 1) { a = vec3(0.5, 0.35, 0.25); b = vec3(0.5, 0.4, 0.3); cc = vec3(0.8, 0.7, 0.6); d = vec3(0.0, 0.1, 0.2); }
    else if (m == 2) { a = vec3(0.2, 0.45, 0.6); b = vec3(0.2, 0.35, 0.4); cc = vec3(0.9, 0.8, 0.7); d = vec3(0.55, 0.45, 0.35); }
    else             { cc = vec3(1.0, 1.0, 1.0); d = vec3(0.8, 0.0, 0.5); b = vec3(0.55); }
    return clamp(a + b * cos(TAU * (cc * t + d)), 0.0, 1.0);
}

vec3 grade(vec3 c) {
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = max(mix(vec3(l), c, Saturation * 1.3), 0.0);
    return c * Brightness_;
}

// One shading sample at pixel position fc (pixels, may be fractional).
vec3 shadeAt(vec2 fc) {
    vec2 res = RENDERSIZE;
    float N = CubesAcross_;
    vec2 p = (fc - 0.5 * res) / res.y * N;
    float pxw = N / res.y;                       // pixel size in cube units
    float v = p.y - 0.25 * float(gHn) ;     // keep the mid-height of the field near the screen centre
    float s = p.x / (2.0 * ISO_A);
    float ew = EdgeW_;
    float cst = Contrast_;

    for (int k = MAX_LAYERS - 1; k >= 0; k--) {
        if (k >= gHn) continue;
        float fk = float(k);
        float X1 = v + s - fk - 1.0;
        float Y1 = v - s - fk - 1.0;
        vec2 c1 = vec2(floor(X1), floor(Y1));
        float fx = X1 - c1.x;
        float fy = Y1 - c1.y;

        int face = -1;                 // 0 top, 1 left (-x normal), 2 right (-y normal)
        vec2 cell = c1;
        vec2 loc = vec2(0.0);          // local face coordinates in 0..1
        int h1 = heightOf(c1);
        if (h1 > k) {
            face = 0; loc = vec2(fx, fy);
        } else {
            bool xFirst = fx > fy;
            vec2 c2 = c1 + (xFirst ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
            int h2 = heightOf(c2);
            if (h2 > k) {
                cell = c2;
                if (xFirst) { face = 1; loc = vec2(fy + 1.0 - fx, fx); }
                else        { face = 2; loc = vec2(fx + 1.0 - fy, fy); }
            } else {
                vec2 c3 = c1 + vec2(1.0);
                if (heightOf(c3) > k) {
                    cell = c3;
                    if (xFirst) { face = 2; loc = vec2(fx - fy, fy); }
                    else        { face = 1; loc = vec2(fy - fx, fx); }
                }
            }
        }
        if (face < 0) continue;

        int hc = heightOf(cell);
        float tc = float(hc) / float(gHn);
        float tl = (fk + 0.5) / float(gHn);
        float tcol = (face == 0 ? tc : mix(tc, tl, 0.5)) * 0.75 + 0.12 * hash21(cell + vec2(7.0, 3.0));
        vec3 col = palette(tcol);
        float shade = face == 0 ? 1.0 : (face == 1 ? 1.0 - 0.35 * cst : 1.0 - 0.7 * cst);
        if (face != 0) shade *= mix(0.78, 1.0, loc.y);   // darker toward the base of side faces
        col *= shade;
        float d = min(min(loc.x, 1.0 - loc.x), min(loc.y, 1.0 - loc.y));
        float edge = 1.0 - smoothstep(ew - pxw, ew + pxw, d);
        edge *= step(0.0005, ew);
        col = mix(col, col * 0.05, edge);
        return col;
    }

    // Ground plane Z = 0: a dim tinted checker, visible where columns are empty.
    vec2 g = vec2(v + s, v - s);
    float chk = mod(floor(g.x) + floor(g.y), 2.0);
    return palette(0.0) * (0.05 + 0.03 * chk);
}

void main() {
    gWave = clamp(int(floor(SurfaceType + 0.5)), 0, 2);
    gHn = 1 + int(floor(MaxHeight * 7.0 + 0.5));
    gHn = clamp(gHn, 1, MAX_LAYERS);
    gPhase = mod(SpeedPhase * 3.0, TAU);
    gDrift = mod(SpeedPhase * 0.5, 1024.0);

    vec2 fc = isf_FragNormCoord * RENDERSIZE;   // deck Zoom / Rotate Z act through isf_FragNormCoord
    vec3 acc;
    if (Quality < 0.34) {
        acc = shadeAt(fc);
    } else if (Quality < 0.67) {
        acc = 0.5 * (shadeAt(fc + vec2(-0.25, -0.125)) + shadeAt(fc + vec2(0.25, 0.125)));
    } else {
        acc = 0.25 * (shadeAt(fc + vec2(-0.25, -0.25)) + shadeAt(fc + vec2(0.25, -0.25))
                    + shadeAt(fc + vec2(-0.25, 0.25)) + shadeAt(fc + vec2(0.25, 0.25)));
    }
    acc = grade(acc);
    if (any(isnan(acc))) acc = vec3(0.0);
    gl_FragColor = vec4(clamp(acc, 0.0, 1.0), 1.0);
}
