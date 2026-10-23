#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Aurora borealis: 3 to 6 layered curtains, each a displaced vertical sheet with a bright lower edge, exponential upward glow, drifting ray structure and a green-to-violet gradient, over a faint starfield with optional mountain silhouette or water reflection.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Organic"
    ],
    "LSD_PHASES": {"WavePhase": ["WaveSpeed"]},
    "INPUTS": [
        { "NAME": "Ribbons", "LABEL": "Ribbons", "TYPE": "float", "DEFAULT": 4.0, "MIN": 3.0, "MAX": 6.0, "STEP": 1 },
        { "NAME": "CurtainHeight", "LABEL": "Curtain Height", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "WaveAmp", "LABEL": "Wave Amplitude", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "WaveSpeed", "LABEL": "Wave Speed", "TYPE": "float", "DEFAULT": 0.3, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Shimmer", "LABEL": "Shimmer", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SkyDarkness", "LABEL": "Sky Darkness", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "PaletteMode", "LABEL": "Palette", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "SurfaceType", "LABEL": "Horizon", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 2.0, "STEP": 1 },
        { "NAME": "Detail", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 2.0, "MIN": 1.0, "MAX": 4.0, "STEP": 1 },
        { "NAME": "HueShift", "LABEL": "Hue Shift", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.65, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define CurtainHeight_ (0.07+CurtainHeight*0.30)
#define WaveAmp_ (0.01+WaveAmp*0.16)
#define Shimmer_ (Shimmer)
#define SkyDarkness_ (SkyDarkness)
#define Saturation_ (Saturation*1.6)
#define Brightness_ (0.2+Brightness*2.0)

const float TAU_ = 6.28318531;

// Value noise on a 256x256 periodic lattice, so a time axis wrapped at 256 is seamless.
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
    float a = hash2(c);
    float b = hash2(c + ivec2(1, 0));
    float d = hash2(c + ivec2(0, 1));
    float e = hash2(c + ivec2(1, 1));
    return mix(mix(a, b, u.x), mix(d, e, u.x), u.y);
}

vec3 hueRotate(vec3 c, float a) {
    float ca = cos(a), sa = sin(a);
    vec3 k = vec3(0.57735027);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}

// Three-stop gradient (low edge, middle, top of the curtain) per palette.
vec3 gradient(int pm, float t) {
    vec3 lo, mi, hi;
    if (pm == 0) {        // classic: green to violet
        lo = vec3(0.10, 1.00, 0.35); mi = vec3(0.10, 0.75, 0.70); hi = vec3(0.60, 0.20, 1.00);
    } else if (pm == 1) { // ice: cyan to deep blue
        lo = vec3(0.15, 1.00, 0.90); mi = vec3(0.15, 0.55, 1.00); hi = vec3(0.35, 0.20, 0.95);
    } else if (pm == 2) { // solar storm: green base, crimson crown
        lo = vec3(0.35, 1.00, 0.25); mi = vec3(0.90, 0.35, 0.35); hi = vec3(1.00, 0.08, 0.20);
    } else {              // ember: gold to magenta
        lo = vec3(1.00, 0.80, 0.20); mi = vec3(1.00, 0.35, 0.35); hi = vec3(0.75, 0.15, 0.90);
    }
    return t < 0.5 ? mix(lo, mi, t * 2.0) : mix(mi, hi, t * 2.0 - 1.0);
}

// Sum of the curtains at sky position (x, s); s is 0 at the horizon, 1 at the zenith.
vec3 auroraSum(float x, float s, int nRib, int oct, int pm) {
    float tn = mod(WavePhase * 1.5, 256.0);
    float cyc = WavePhase * 0.2;
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 6; i++) {
        if (i >= nRib) break;
        float fi = float(i);
        float dir = (i & 1) == 0 ? 1.0 : -1.0;
        float xi = x * (1.0 + 0.12 * fi) + 3.1 * fi;

        // Base line: two phase-locked sines plus noise octaves; phases wrap via fract.
        float p1 = TAU_ * fract(cyc * dir * (0.7 + 0.15 * fi));
        float p2 = TAU_ * fract(cyc * -dir * (1.3 + 0.1 * fi));
        float a1 = xi * 2.1 + p1;
        float a2 = xi * 4.7 + p2 + fi;
        float w = sin(a1) + 0.5 * sin(a2);
        float slope = 2.1 * cos(a1) + 2.35 * cos(a2);
        float H = CurtainHeight_ * (1.0 - 0.07 * fi);
        // Cull: the sines bound the base line to within 0.6 of the noise term, and the glow is
        // negligible beyond ~7 scale heights above it or just below it.
        float ybMin = 0.10 + 0.075 * fi + WaveAmp_ * (w - 2.0);
        if (s < ybMin - 0.12 || s > 0.10 + 0.075 * fi + WaveAmp_ * (w + 2.0) + 7.0 * H) continue;
        float amp = 0.5;
        for (int o = 0; o < 4; o++) {
            if (o >= oct) break;
            float fo = exp2(float(o));
            w += amp * 2.0 * (vnoise(vec2(xi * 1.3 * fo + 17.0 * fi, (dir > 0.0 ? tn : 256.0 - tn) + 31.0 * float(o))) - 0.5);
            amp *= 0.5;
        }
        float yb = 0.10 + 0.075 * fi + WaveAmp_ * w;

        float d = s - yb;
        float dd = max(d, 0.0);
        // Exponential glow upward, sharp falloff below the bright lower edge.
        float I = exp(-dd / H) * exp(min(d, 0.0) * 32.0);
        I *= 1.0 + 1.6 * exp(-abs(d) * 28.0);
        // Curtain seen edge-on where the base line is steep: brighter folds.
        I *= 1.0 + 0.12 * min(abs(slope) * WaveAmp_ * 20.0, 4.0);

        // Vertical ray structure, stronger higher up, drifting and flickering in time.
        float r = vnoise(vec2(xi * 38.0 + 9.0 * fi, mod(tn * 2.0 + 13.0 * fi, 256.0)));
        float rays = mix(1.0, 0.2 + 1.6 * r * r + 0.5 * r, Shimmer_ * smoothstep(0.0, 0.12, dd + 0.02));
        I *= rays;

        vec3 c = gradient(pm, clamp(dd / (3.0 * H) + 0.08 * fi, 0.0, 1.0));
        acc += c * I * 0.5;
    }
    return acc;
}

// Complete view of the sky (stars + aurora + optional far shore) at screen position (x, y).
vec3 skyScene(float x, float y, float yh, int surf, int nRib, int oct, int pm, vec3 skyLow, vec3 skyHigh) {
    float s = clamp((y - yh) / (1.0 - yh), 0.0, 1.0);
    vec3 col = mix(skyLow, skyHigh, sqrt(s));

    vec3 au = auroraSum(x, s, nRib, oct, pm);
    au = hueRotate(au, HueShift * TAU_);
    float lum = dot(au, vec3(0.299, 0.587, 0.114));
    au = mix(vec3(lum), au, Saturation_) * Brightness_;

    // Stars: one jittered point per cell, twinkling, dimmed under bright aurora.
    vec2 cell = vec2(x, y) * 150.0;
    vec2 ci = floor(cell);
    float h = hash2(ivec2(ci));
    if (h > 0.97) {
        float h2 = hash2(ivec2(ci) + ivec2(91, 37));
        float h3 = hash2(ivec2(ci) + ivec2(17, 203));
        vec2 jit = vec2(h2, h3) * 0.6 + 0.2;
        float dist = length(fract(cell) - jit);
        float tw = 0.65 + 0.35 * sin(TAU_ * fract(TIME * (0.1 + 0.2 * h2) + h3));
        float star = smoothstep(0.22, 0.0, dist) * tw * (0.3 + 0.7 * (h - 0.97) / 0.03);
        col += vec3(0.9, 0.95, 1.0) * star * (1.0 - clamp(lum * 3.0, 0.0, 1.0));
    }

    // Tone-map the additive aurora so overlaps saturate smoothly instead of clipping.
    col += 1.0 - exp(-au);
    return col;
}

void main() {
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    float x = (isf_FragNormCoord.x - 0.5) * aspect;
    float y = isf_FragNormCoord.y;
    int surf = clamp(int(floor(SurfaceType + 0.5)), 0, 2);
    int nRib = clamp(int(floor(Ribbons + 0.5)), 3, 6);
    int oct = clamp(int(floor(Detail + 0.5)), 1, 4);
    int pm = clamp(int(floor(PaletteMode + 0.5)), 0, 3);

    float yh = surf == 0 ? 0.0 : (surf == 1 ? 0.2 : 0.38);
    vec3 skyHigh = mix(vec3(0.020, 0.040, 0.110), vec3(0.0, 0.0, 0.004), SkyDarkness_);
    vec3 skyLow = mix(vec3(0.050, 0.110, 0.170), vec3(0.003, 0.010, 0.020), SkyDarkness_);

    // Shore silhouette height: mountains are tall, the water-side treeline is low.
    float rx = x;
    bool water = (surf == 2) && (y < yh);
    float sy = y;
    if (water) {
        // Mirror about the horizon with a gentle ripple displacement.
        float rip = sin(y * 90.0 + TAU_ * fract(TIME * 0.15)) * 0.004 + (vnoise(vec2(x * 30.0, y * 120.0)) - 0.5) * 0.006;
        rx = x + rip;
        sy = 2.0 * yh - y;
    }
    float ridgeAmp = surf == 1 ? 0.10 : 0.035;
    float ridge = yh;
    if (surf != 0) {
        float rr = 0.5 * sin(rx * 4.3 + 1.3) + 0.3 * sin(rx * 9.7 + 4.0) + 0.9 * vnoise(vec2(rx * 7.0 + 40.0, 3.0)) - 0.2;
        ridge = yh + ridgeAmp * max(rr, 0.0) + (surf == 1 ? 0.0 : 0.004);
    }

    vec3 col = skyScene(rx, sy, yh, surf, nRib, oct, pm, skyLow, skyHigh);

    if (surf != 0 && sy < ridge) {
        // Land: near-black with a trace of sky light.
        float edge = smoothstep(0.0, 0.01, ridge - sy);
        vec3 land = vec3(0.002, 0.004, 0.006) + col * 0.04;
        col = mix(col, land, edge);
    }
    if (water) {
        float depth = (yh - y) / yh;
        vec3 deep = skyHigh * 0.5 + vec3(0.0, 0.003, 0.006);
        col = mix(col * 0.55, deep, clamp(depth * 0.6, 0.0, 0.7));
    }

    col = clamp(col, 0.0, 1.0);
    gl_FragColor = vec4(col, 1.0);
}
