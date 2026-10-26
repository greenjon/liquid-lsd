#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Procedural glitch / datamosh look with no image input: a base pattern (gradient bars, test-card colour bars, diagonal stripes, noise field) corrupted by hashed block displacement, scanline offsets, horizontal tearing bands, RGB channel split, posterise crush and full-block colour dropouts. Glitches step at a chosen rate.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Glitch"
    ],
    "LSD_PHASES": {"RatePhase": ["Rate"]},
    "INPUTS": [
        { "NAME": "SurfaceType", "LABEL": "Base Pattern", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "PaletteMode", "LABEL": "Palette", "TYPE": "float", "DEFAULT": 3.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "Intensity", "LABEL": "Glitch Intensity", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "BlockSize", "LABEL": "Block Size", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Rate", "LABEL": "Glitch Rate", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Tear", "LABEL": "Tear Amount", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Split", "LABEL": "Channel Split", "TYPE": "float", "DEFAULT": 0.35, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Crush", "LABEL": "Crush", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.7, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define BlockSize_ (0.02 + BlockSize * 0.30)      // block height, fraction of frame height
#define Tear_ (Tear * 0.45)                       // max band shift, fraction of frame width
#define Split_ (Split * 0.05)                     // max channel offset, fraction of frame width
#define Saturation_ (Saturation * 2.0)
#define Brightness_ (Brightness * 2.0)

const float TAU = 6.28318530718;

// Integer hash (lowbias32-style avalanche), identical on all GPUs. Result in [0,1).
uint mix32(uint h) {
    h ^= h >> 16; h *= 2246822519u;
    h ^= h >> 13; h *= 3266489917u;
    h ^= h >> 16;
    return h;
}
float hash3(uint a, uint b, uint c) {
    uint h = mix32(a * 747796405u + 2891336453u);
    h = mix32(h ^ (b * 1597334677u + 374761393u));
    h = mix32(h ^ (c * 3812015801u + 668265263u));
    return float(h & 0xFFFFFFu) / 16777216.0;
}
// Cell ids may be negative (displaced sample coords); fold them into a fixed period first.
uint cellId(float f) { return uint(int(mod(f, 4096.0))); }

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float valueNoise(vec2 p, int oct) {
    float sum = 0.0, amp = 0.5, norm = 0.0;
    for (int o = 0; o < 3; o++) {
        if (o >= oct) break;
        vec2 i = floor(p), f = fract(p);
        f = f * f * (3.0 - 2.0 * f);
        uint ix = cellId(i.x), iy = cellId(i.y);
        float a = hash3(ix, iy, 11u + uint(o)), b = hash3(ix + 1u, iy, 11u + uint(o));
        float c = hash3(ix, iy + 1u, 11u + uint(o)), d = hash3(ix + 1u, iy + 1u, 11u + uint(o));
        sum += amp * mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        norm += amp;
        amp *= 0.5;
        p = p * 2.03 + 17.0;
    }
    return sum / norm;
}

// Base pattern: a colour index t in [0,1). u,v are frame coordinates (v up), scroll is a wrapped phase in [0,1).
float basePattern(vec2 uv, float aspect, float scroll, int kind, int oct) {
    float u = uv.x, v = uv.y;
    if (kind == 0) {
        // Eight vertical bars, each a vertical gradient offset by its index; scrolls one bar per period.
        float x = u * 8.0 + scroll;
        return fract((floor(x) + v) / 8.0);
    } else if (kind == 1) {
        // Test card: seven colour bars, a reversed castellation strip, and a ramp of the same bars.
        float x7 = u * 7.0 + scroll;
        float bar = mod(floor(x7), 7.0);
        float tTop = (bar + 0.5) / 8.0;
        float tMid = (6.0 - bar + 0.5) / 8.0;
        float tBot = (floor(fract(u + scroll / 7.0) * 8.0) + 0.5) / 8.0;
        if (v > 0.33) return tTop;
        if (v > 0.25) return tMid;
        return tBot;
    } else if (kind == 2) {
        float s = (u * aspect + v) * 6.0 + scroll * 6.0;
        return fract(floor(s) / 7.0);
    }
    // Noise field, quantised into 8 flat levels so blocks read as data.
    vec2 q = vec2(u * aspect, v) * 5.0 + vec2(scroll * 8.0, 0.0);
    float n = valueNoise(q, oct);
    return floor(clamp(n, 0.0, 0.999) * 8.0) / 8.0 + 0.0625;
}

vec3 palette(float t, int mode) {
    if (mode == 0) return hsv2rgb(vec3(fract(HueOffset + t * 0.85), 0.9, 1.0));
    if (mode == 1) return 0.5 + 0.5 * cos(TAU * (t + HueOffset + vec3(0.0, 0.33, 0.67)));
    if (mode == 2) return mix(vec3(0.02), hsv2rgb(vec3(HueOffset, 0.7, 1.0)), t);
    // Test-card primaries: index i -> white yellow cyan green magenta red blue black.
    float i = mod(floor(clamp(t, 0.0, 0.999) * 8.0) + floor(HueOffset * 8.0 + 0.5), 8.0);
    return vec3(mod(i, 4.0) < 2.0 ? 1.0 : 0.0, i < 4.0 ? 1.0 : 0.0, mod(i, 2.0) < 1.0 ? 1.0 : 0.0) * 0.9;
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
    vec2 uv = isf_FragNormCoord;
    int kind = clamp(int(floor(SurfaceType + 0.5)), 0, 3);
    int mode = clamp(int(floor(PaletteMode + 0.5)), 0, 3);
    int layers = 1 + int(clamp(Quality, 0.0, 1.0) * 2.999);
    int oct = 1 + int(clamp(Quality, 0.0, 1.0) * 2.999);

    // Quantised time: the whole glitch state is a pure function of this integer.
    uint step0 = uint(floor(mod(0.5 * TIME + 23.5 * RatePhase, 86400.0)));   // integral of Rate_
    float scroll = fract(TIME * 0.0625);

    // Per-step global state: occasionally a calm frame, otherwise a burst gain 0.5..1.5.
    float hg = hash3(step0, 1u, 901u);
    float calm = hg < 0.18 * (1.0 - Intensity) ? 0.0 : 1.0;
    float gain = calm * (0.5 + hash3(step0, 2u, 902u));
    float I = clamp(Intensity * gain, 0.0, 1.5);

    vec2 p = uv;          // sample position, displaced below
    float dropout = 0.0;  // 0 none, else 1 + selector
    float dropSel = 0.0;
    float splitBoost = 0.0;

    // Block displacement layers: rows of random-width blocks, each jumping with some probability.
    for (int k = 0; k < 3; k++) {
        if (k >= layers) break;
        float fk = float(k);
        uint uk = uint(k);
        uint stp = step0 / (uk + 1u);                 // coarser layers hold longer
        float bh = BlockSize_ * (k == 0 ? 1.0 : (k == 1 ? 0.5 : 0.25));
        float bw = bh * 2.6 / aspect;                  // wide blocks, in frame-width units
        float row = floor(uv.y / bh);
        uint ur = cellId(row);
        float rowOff = hash3(ur, uk, 31u) * bw;
        float colf = floor((uv.x + rowOff) / bw);
        uint uc = cellId(colf);
        float h = hash3(ur * 8191u + uc, stp, 41u + uk);
        float prob = I * I * (k == 0 ? 0.30 : 0.22);
        if (h < prob) {
            float hx = hash3(ur * 8191u + uc, stp, 51u + uk);
            float hy = hash3(ur * 8191u + uc, stp, 61u + uk);
            p.x += (hx * 2.0 - 1.0) * (0.12 + 0.35 * I);
            if (hy < 0.4) p.y += (hy * 2.5 - 0.5) * 0.15 * I;
            splitBoost += 1.0;
        }
        // Dropouts live on the coarsest active layer only (k == 1 when available): solid-colour blocks.
        if (k == (layers > 1 ? 1 : 0)) {
            float hd = hash3(ur * 8191u + uc, stp, 71u);
            if (hd < I * I * 0.12) { dropout = 1.0; dropSel = hash3(ur * 8191u + uc, stp, 72u); }
        }
    }

    // Horizontal tearing bands: bands of random height, shifted sideways with a slight skew.
    {
        float bandsA = 6.0 + floor(hash3(step0, 3u, 903u) * 10.0);
        float bi = floor(uv.y * bandsA);
        float hb = hash3(cellId(bi), step0, 81u);
        if (hb < (0.02 + 0.30 * Tear) * (0.2 + I)) {
            float sh = hash3(cellId(bi), step0, 82u) * 2.0 - 1.0;
            float skew = (fract(uv.y * bandsA) - 0.5) * (hash3(cellId(bi), step0, 83u) - 0.5) * 0.1;
            p.x += (sh * Tear_ + skew * Tear) * (0.3 + 0.7 * calm);
        }
    }

    // Scanline offsets: individual lines nudged sideways.
    {
        float line = floor(uv.y * 270.0);
        float hl = hash3(cellId(line), step0, 91u);
        if (hl < 0.25 * I * I) p.x += (hash3(cellId(line), step0, 92u) - 0.5) * 0.04 * I;
    }

    // RGB channel split: three samples of the pattern with opposed horizontal offsets.
    float sp = Split_ * (1.0 + 0.6 * min(splitBoost, 3.0)) * (0.5 + 0.5 * I);
    float tR = basePattern(vec2(p.x - sp, p.y), aspect, scroll, kind, oct);
    float tG = basePattern(p, aspect, scroll, kind, oct);
    float tB = basePattern(vec2(p.x + sp, p.y), aspect, scroll, kind, oct);
    vec3 col = vec3(palette(tR, mode).r, palette(tG, mode).g, palette(tB, mode).b);

    // Dropout: solid block of black, white, or a saturated colour from the palette.
    if (dropout > 0.5) {
        vec3 dc = dropSel < 0.25 ? vec3(0.0) : (dropSel < 0.45 ? vec3(0.95)
                  : hsv2rgb(vec3(fract(dropSel * 3.0 + HueOffset), 1.0, 1.0)));
        col = dc;
    }

    // Subtle line structure, then colour grade.
    col *= 1.0 - 0.08 * Intensity * mod(floor(uv.y * 270.0), 2.0);
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(luma), col, Saturation_);
    col *= Brightness_;

    // Posterise crush: 32 levels down to 2.
    if (Crush > 0.001) {
        float lv = floor(mix(32.0, 2.0, Crush) + 0.5) - 1.0;
        col = floor(clamp(col, 0.0, 1.0) * lv + 0.5) / lv;
    }
    col = clamp(col, 0.0, 1.0);
    gl_FragColor = blackToAlpha(col);
}
