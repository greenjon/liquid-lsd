#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "2D metaballs: 4 to 12 balls on Lissajous orbits summing an inverse-square field, rendered as shaded blobs, iso-line contours, neon outlines or a gooey two-tone. Ball size and variance, signed orbit speed, orbit spread, merge threshold and edge softness.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Geometric"
    ],
    "LSD_PHASES": {"OrbitPhase": ["OrbitSpeed"]},
    "INPUTS": [
        { "NAME": "MaxPoints", "LABEL": "Balls", "TYPE": "float", "DEFAULT": 7.0, "MIN": 4.0, "MAX": 12.0, "STEP": 1 },
        { "NAME": "ColorMethod", "LABEL": "Style", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "PaletteMode", "LABEL": "Palette", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "BallSize", "LABEL": "Ball Size", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SizeVariance", "LABEL": "Size Variance", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "OrbitSpeed", "LABEL": "Orbit Speed", "TYPE": "float", "DEFAULT": 0.4, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "OrbitSpread", "LABEL": "Orbit Spread", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Merge", "LABEL": "Merge", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Softness", "LABEL": "Softness", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.85, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define BallSize_ (0.03+BallSize*0.12)
#define OrbitSpread_ (0.2+OrbitSpread*0.3)
#define Merge_ (1.6-Merge*1.3)
#define Softness_ (Softness*0.03)
#define Brightness_ (Brightness*2.0)

const float TAU = 6.28318530717958647692;
const int MAXB = 12;

uint hash1(uint x) {
    x ^= x >> 16; x *= 0x7feb352du;
    x ^= x >> 15; x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}
// Deterministic per-ball random in [0,1).
float rnd(int i, int salt) {
    return float(hash1(uint(i * 8 + salt) + 0x9e3779b9u) & 0xffffffu) / 16777216.0;
}

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
        d = vec3(0.85, 0.2, 0.5);
    }
    return a + b * cos(TAU * (c * t + d + hue));
}

// Field f = sum r_i^2 / (|p-c_i|^2 + eps): equals 1 on a lone ball's rim (|p-c| = r).
// Returns f in .x, grad f in .yz, and the f-weighted palette coordinate in .w.
vec4 field(vec2 p, int n, vec2 cen[MAXB], float rad[MAXB]) {
    float f = 0.0;
    vec2 g = vec2(0.0);
    float tw = 0.0;
    float sw = 1e-9;
    for (int i = 0; i < MAXB; i++) {
        if (i >= n) break;
        vec2 d = p - cen[i];
        float r2 = rad[i] * rad[i];
        float inv = 1.0 / (dot(d, d) + 0.02 * r2);
        float fi = r2 * inv;
        f += fi;
        g -= 2.0 * fi * inv * d;
        tw += fi * fi * (float(i) + 0.5) / float(n);
        sw += fi * fi;
    }
    // weighting by f_i^2 makes the dominant ball own the colour
    return vec4(f, g, tw / sw);
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
    float px = pix.y;
    int n = clamp(int(floor(MaxPoints + 0.5)), 4, MAXB);
    int style = clamp(int(floor(ColorMethod + 0.5)), 0, 3);
    int pmode = clamp(int(floor(PaletteMode + 0.5)), 0, 3);
    float thr = Merge_;
    float soft = Softness_;

    // Orbits: x = A sin(kx*s + ph), y = B sin(ky*s + ph2) with half-integer rates, so the
    // pattern repeats exactly every 4 pi of s and the wrap below is seamless.
    float s = mod(OrbitPhase * 1.2, 2.0 * TAU);
    vec2 cen[MAXB];
    float rad[MAXB];
    float rsum = 0.0;
    for (int i = 0; i < MAXB; i++) {
        if (i >= n) break;
        float kx = 0.5 * (1.0 + floor(rnd(i, 0) * 5.0));
        float ky = 0.5 * (1.0 + floor(rnd(i, 1) * 5.0));
        if (abs(kx - ky) < 0.01) ky += 0.5;
        float ph = TAU * rnd(i, 2);
        float ph2 = TAU * rnd(i, 3);
        float amp = OrbitSpread_ * (0.55 + 0.45 * rnd(i, 4));
        cen[i] = vec2(amp * aspect * sin(kx * s + ph), amp * 1.5 * sin(ky * s + ph2));
        float v = SizeVariance * (rnd(i, 5) - 0.5) * 1.7;
        rad[i] = BallSize_ * (1.0 + v);
        rsum += rad[i];
    }
    float ravg = rsum / float(n);

    int aa = Quality >= 0.5 ? 2 : 1;
    vec3 acc = vec3(0.0);
    for (int j = 0; j < 2; j++) {
        for (int i = 0; i < 2; i++) {
            if (i >= aa || j >= aa) continue;
            vec2 o = aa == 2 ? (vec2(float(i), float(j)) - 0.5) * 0.5 : vec2(0.0);
            vec2 p = (isf_FragNormCoord + o * pix - 0.5) * vec2(aspect, 1.0);
            vec4 F = field(p, n, cen, rad);
            float f = max(F.x, 1e-6);
            vec2 g = F.yz;

            float t = clamp(F.w, 0.0, 1.0);

            // signed distance estimate to the iso-curve f = thr, in screen-height units (+ inside)
            float lf = log(f);
            float gl = length(g) / f + 1e-4;
            float de = (lf - log(thr)) / gl;
            float w = px * 1.0 + soft;
            float inside = smoothstep(-w, w, de);

            vec3 bg = palette(0.55, HueOffset, pmode) * 0.05;
            vec3 col;
            if (style == 0) {
                // shaded blobs: height ~ sqrt(f - thr), normal from its gradient (rim tilts away)
                // dome height h(u) = sqrt(u / (1 + 0.35 u)): vertical at the rim, monotonic and flat
                // on top, so the pole of the field (a sharp peak at each ball centre) shows no cone
                float u = max(f - thr, 0.0);
                float su = sqrt(u) + 0.05;
                float k1 = 1.0 + 0.35 * u;
                float dh = 0.5 / (su * k1 * sqrt(k1));
                vec2 sl = g * dh * ravg * 1.1 / (1.0 + 0.03 * (f / thr) * (f / thr));   // flat top, no visible edge
                vec3 nrm = normalize(vec3(-sl, 1.0));
                vec3 L = normalize(vec3(-0.45, 0.6, 0.65));
                float dif = max(dot(nrm, L), 0.0);
                vec3 H = normalize(L + vec3(0.0, 0.0, 1.0));
                float spec = pow(max(dot(nrm, H), 0.0), 40.0);
                float fres = pow(1.0 - nrm.z, 2.0);
                vec3 base = palette(t, HueOffset, pmode);
                vec3 lit = base * (0.25 + 0.85 * dif) + vec3(spec * 0.6) + base * fres * 0.4;
                vec3 halo = palette(t, HueOffset, pmode) * 0.18 * clamp(f / thr, 0.0, 1.0) * clamp(f / thr, 0.0, 1.0);
                col = mix(bg + halo, lit, inside);
            } else if (style == 1) {
                // iso-line bands in log-field space; palette steps per band, dark contour lines
                float k = lf * 1.6 - log(thr) * 1.6;
                float bandId = floor(k);
                float fr = k - bandId;
                float e = min(fr, 1.0 - fr) / (gl * 1.6);
                float line = smoothstep(0.0, px * 1.5 + soft, e);
                vec3 c = palette(t * 0.3 + bandId * 0.09, HueOffset, pmode);
                float lum = mix(0.35, 1.0, clamp(k * 0.25 + 0.5, 0.0, 1.0));
                vec3 shaded = c * lum * mix(0.12, 1.0, line);
                col = mix(bg, shaded, smoothstep(-w, w, de + 0.06));
                col = mix(col, shaded, step(0.0, k));
            } else if (style == 2) {
                // neon outline on dark ground: core line plus exponential glow around the iso-curve
                float ad = abs(de);
                float core = 1.0 - smoothstep(px * 0.8, px * 2.2 + soft, ad);
                float glow = exp(-ad / (0.006 + soft * 1.2 + ravg * 0.06));
                vec3 c = palette(t, HueOffset, pmode);
                col = bg + c * (core * 1.4 + glow * 0.7) + vec3(core * core * 0.35);
                col += c * 0.06 * inside;
            } else {
                // gooey two-tone: flat fill, lighter inner core from the field, thin highlight rim
                vec3 cA = palette(0.1 + t * 0.15, HueOffset, pmode);
                vec3 cB = palette(0.55 + t * 0.1, HueOffset, pmode);
                float core = smoothstep(thr, thr * 6.0, f);
                vec3 blob = mix(cA, cB, core * 0.8);
                float rim = 1.0 - smoothstep(0.0, px * 3.0 + soft, abs(de));
                blob += rim * 0.25;
                col = mix(bg * 2.0 + vec3(0.02), blob, inside);
            }
            acc += col;
        }
    }
    vec3 col = acc / float(aa * aa);

    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(luma), col, Saturation) * Brightness_;
    gl_FragColor = blackToAlpha(col);
}
