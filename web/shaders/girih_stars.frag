#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Islamic girih star-and-polygon strapwork on a periodic planar lattice (square, hexagonal or 4.8.8 tiling). The plane is folded into the fundamental triangle of the tiling's wallpaper group and Hankin-style lines leave each edge midpoint at an adjustable contact angle, forming 4, 6 or 8-point stars with optional over-under interlace.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Geometric",
        "Tiling"
    ],
    "LSD_PHASES": {"SpeedPhase": ["Speed"]},
    "INPUTS": [
        { "NAME": "Symmetries", "LABEL": "Star Points", "TYPE": "float", "DEFAULT": 8.0, "MIN": 4.0, "MAX": 8.0, "STEP": 2 },
        { "NAME": "SurfaceType", "LABEL": "Style", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 2.0, "STEP": 1 },
        { "NAME": "PaletteMode", "LABEL": "Palette", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "TileScale", "LABEL": "Tile Scale", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Angle", "LABEL": "Contact Angle", "TYPE": "float", "DEFAULT": 0.55, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Sweep", "LABEL": "Angle Sweep", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Speed", "LABEL": "Morph Speed", "TYPE": "float", "DEFAULT": 0.3, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "LineWidth", "LABEL": "Line Width", "TYPE": "float", "DEFAULT": 0.35, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Interlace", "LABEL": "Interlace", "TYPE": "float", "DEFAULT": 0.8, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 0.35, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define TileScale_ (2.0+TileScale*7.0)          // cells per screen height
#define Angle_ (0.14+Angle*1.29)                 // contact angle, radians (8 to 82 degrees)
#define Sweep_ (Sweep*0.45)                      // half amplitude of the animated angle, radians
#define LineWidth_ (0.012+LineWidth*0.07)        // ribbon half width, tile units (cell = 1)
#define Glow_ (Glow*2.0)
#define Saturation_ (Saturation*2.0)
#define Brightness_ (Brightness*2.0)

const float PI = 3.14159265358979323846;
const float TAU = 6.28318530717958647692;
const float HALF = 0.5;                          // apothem of the base polygon (tile units)
const float RT2 = 0.70710678118654752;

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
        d = vec3(0.85, 0.2, 0.5);
    }
    return a + b * cos(TAU * (c * t + d + hue));
}

float dseg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-8), 0.0, 1.0);
    return length(pa - ba * h);
}

// Distance along a ray (origin o inside the fundamental triangle, direction d) to the first
// triangle side it leaves through: the x axis, the mirror at angle phi, or the wall x = HALF.
// All three sides are mirrors of the whole pattern, so the ray meets its mirror image there.
float rayHit(vec2 o, vec2 d, vec2 nin) {
    float t = 2.0;
    float dn = d.y;
    if (dn < -1e-5) t = min(t, max(o.y, 0.0) / -dn);
    dn = dot(nin, d);
    if (dn < -1e-5) t = min(t, max(dot(nin, o), 0.0) / -dn);
    dn = -d.x;
    if (dn < -1e-5) t = min(t, max(HALF - o.x, 0.0) / -dn);
    return t;
}

float cover(float d, float w, float aa) {
    return 1.0 - smoothstep(w - aa, w + aa, d);
}

vec3 sampleGirih(vec2 p, int lat, float theta, float hw, float ow, float aa,
                 int style, float interlace, float glowAmt, int pmode, float hue, float sat) {
    // ---- 1. fold into the fundamental triangle 0 <= y <= x tan(phi), x <= HALF ----
    float par = 0.0;     // number of mirror reflections used (mod 2 = handedness)
    vec2 q;
    float phi;
    if (lat == 1) {
        // p6m: hexagon centres on a triangular lattice with spacing 1 (apothem 1/2).
        vec2 h = vec2(1.0, 1.7320508075688772);
        vec2 a1 = mod(p, h) - 0.5 * h;
        vec2 a2 = mod(p - 0.5 * h, h) - 0.5 * h;
        q = (dot(a1, a1) < dot(a2, a2)) ? a1 : a2;
        float r = length(q);
        float ang = atan(q.y, q.x + 1e-9);
        ang -= floor(ang / (PI / 3.0)) * (PI / 3.0);      // rotations by 60 degrees
        if (ang > PI / 6.0) { ang = PI / 3.0 - ang; par += 1.0; }   // mirror at 30 degrees
        q = r * vec2(cos(ang), sin(ang));
        phi = PI / 6.0;
    } else {
        // p4m: square cells of side 1 centred on the integer lattice.
        q = fract(p + 0.5) - 0.5;
        if (q.x < 0.0) { q.x = -q.x; par += 1.0; }
        if (q.y < 0.0) { q.y = -q.y; par += 1.0; }
        if (q.y > q.x) { q = q.yx; par += 1.0; }
        phi = PI / 4.0;
    }
    bool odd = mod(par, 2.0) > 0.5;
    bool oct = (lat == 2);

    // ---- 2. Hankin rays from the edge midpoints ----
    float st = sin(theta);
    float ct = cos(theta);
    vec2 nin = vec2(sin(phi), -cos(phi));    // inward normal of the mirror at angle phi
    vec2 M1 = vec2(HALF, 0.0);               // midpoint of the edge x = HALF
    vec2 d1 = vec2(-st, ct);
    float t1 = rayHit(M1, d1, nin);
    vec2 dm = vec2(-st, -ct);                // the crossing strand at M1 (mirror image of d1)

    vec2 M2 = vec2(0.0);
    vec2 d2 = vec2(0.0);
    vec2 d3 = vec2(0.0);
    float t2 = 0.0;
    float t3 = 0.0;
    if (oct) {
        // 4.8.8: octagons at the lattice points, squares at the cell corners. The octagon/square
        // edge midpoint lies on the diagonal mirror; one ray enters the octagon, one the square.
        M2 = vec2(HALF * RT2);
        vec2 e = vec2(RT2, -RT2);
        vec2 nI = vec2(-RT2, -RT2);
        d2 = e * ct + nI * st;
        d3 = e * ct - nI * st;
        t2 = rayHit(M2, d2, nin);
        t3 = rayHit(M2, d3, nin);
        // The octagon rays from the two adjacent edges meet on the vertex bisector: stop both there.
        vec2 r = M2 - M1;
        float det = d2.x * d1.y - d1.x * d2.y;
        if (abs(det) > 1e-5) {
            float ta = (d2.x * r.y - r.x * d2.y) / det;
            float tb = (d1.x * r.y - d1.y * r.x) / det;
            if (ta > 0.0 && tb > 0.0) { t1 = min(t1, ta); t2 = min(t2, tb); }
        }
    }

    // ---- 3. strand distances ----
    float alpha = min(2.0 * theta, PI - 2.0 * theta);
    float R = clamp(1.3 * (hw + ow + 2.0 * aa) / max(sin(0.5 * alpha), 0.2), 0.02, 0.22);
    float dA1 = dseg(q, M1 - d1 * R, M1 + d1 * t1);
    float dB1 = dseg(q, M1 - dm * R, M1 + dm * R);
    float dA2 = 1e3;
    float dA3 = 1e3;
    if (oct) {
        dA2 = dseg(q, M2 - d2 * R, M2 + d2 * t2);
        dA3 = dseg(q, M2 - d3 * R, M2 + d3 * t3);
    }
    float dmin = min(min(dA1, dB1), min(dA2, dA3));

    // ---- 4. colour ----
    float rn = clamp(length(q) / 0.7, 0.0, 1.0);
    float lum = 0.0;
    vec3 bg;
    if (style == 2) {
        // coloured regions between the strands
        float s1 = (dot(q - M1, vec2(ct, st)) > 0.0) ? 1.0 : 0.0;
        float id = s1;
        if (oct) {
            float s2 = (dot(q - M2, vec2(d2.y, -d2.x)) > 0.0) ? 1.0 : 0.0;
            float s3 = (dot(q - M2, vec2(d3.y, -d3.x)) > 0.0) ? 1.0 : 0.0;
            id = (q.x + q.y > 2.0 * HALF * RT2) ? 4.0 + s3 : s1 + 2.0 * s2;
        }
        bg = palette(0.17 * id + 0.25 * rn + 0.5, hue, pmode) * 0.45;
    } else {
        bg = palette(0.6 + 0.3 * rn, hue, pmode) * 0.08;
    }
    float tcol = 0.45 * rn;
    vec3 fillBase = palette(tcol, hue, pmode);
    vec3 outl = fillBase * 0.1;

    float owid = hw + ow;
    // flat base: union of all strand ribbons
    float oc = max(max(cover(dA1, owid, aa), cover(dB1, owid, aa)), max(cover(dA2, owid, aa), cover(dA3, owid, aa)));
    float fc = max(max(cover(dA1, hw, aa), cover(dB1, hw, aa)), max(cover(dA2, hw, aa), cover(dA3, hw, aa)));
    float sh = clamp(dmin / max(hw, 1e-4), 0.0, 1.0);
    vec3 fillCol = fillBase * (1.15 - 0.45 * sh * sh);
    vec3 col = bg;
    col = mix(col, outl, oc);
    col = mix(col, fillCol, fc);

    // ---- 5. over-under weave around each crossing ----
    if (interlace > 0.001 && style != 1) {
        // M1: strand A1 is over on even parity, the crossing strand otherwise
        float m1 = (1.0 - smoothstep(0.8 * R, R, length(q - M1))) * interlace;
        if (m1 > 0.0) {
            float dO = odd ? dB1 : dA1;
            float dU = odd ? dA1 : dB1;
            vec3 w = bg;
            float shadow = 0.7 * (1.0 - smoothstep(owid, owid + 3.0 * ow + 2.0 * aa, dO));
            float shU = clamp(dU / max(hw, 1e-4), 0.0, 1.0);
            float shO = clamp(dO / max(hw, 1e-4), 0.0, 1.0);
            w = mix(w, outl, cover(dU, owid, aa));
            w = mix(w, fillBase * (1.15 - 0.45 * shU * shU) * (1.0 - shadow), cover(dU, hw, aa));
            w = mix(w, outl, cover(dO, owid, aa));
            w = mix(w, fillBase * (1.15 - 0.45 * shO * shO), cover(dO, hw, aa));
            col = mix(col, w, m1);
        }
        if (oct) {
            // M2: the octagon-side strand A2 is over on odd parity, the square-side strand A3 on even
            float m2 = (1.0 - smoothstep(0.8 * R, R, length(q - M2))) * interlace;
            if (m2 > 0.0) {
                float dO = odd ? dA2 : dA3;
                float dU = odd ? dA3 : dA2;
                vec3 w = bg;
                float shadow = 0.7 * (1.0 - smoothstep(owid, owid + 3.0 * ow + 2.0 * aa, dO));
                float shU = clamp(dU / max(hw, 1e-4), 0.0, 1.0);
                float shO = clamp(dO / max(hw, 1e-4), 0.0, 1.0);
                w = mix(w, outl, cover(dU, owid, aa));
                w = mix(w, fillBase * (1.15 - 0.45 * shU * shU) * (1.0 - shadow), cover(dU, hw, aa));
                w = mix(w, outl, cover(dO, owid, aa));
                w = mix(w, fillBase * (1.15 - 0.45 * shO * shO), cover(dO, hw, aa));
                col = mix(col, w, m2);
            }
        }
    }

    // ---- 6. glow ----
    vec3 glowCol = palette(0.2 + 0.5 * rn, hue + 0.5, pmode);
    col += glowCol * glowAmt * 0.5 * exp(-max(dmin - hw, 0.0) * 14.0);

    // saturation about luma
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(l), col, sat);
    return col;
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
    float cells = TileScale_;
    vec2 base = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0) * cells;
    float px = cells / RENDERSIZE.y;
    float aa = 0.7 * px;

    float nsym = floor(Symmetries + 0.5);
    int lat = (nsym >= 7.0) ? 2 : ((nsym >= 5.0) ? 1 : 0);
    int style = clamp(int(floor(SurfaceType + 0.5)), 0, 2);
    int pmode = clamp(int(floor(PaletteMode + 0.5)), 0, 3);

    float phase = SpeedPhase;
    float theta = clamp(Angle_ + Sweep_ * sin(phase), 0.07, 1.50);

    float hw = LineWidth_;
    float ow = 0.3 * hw + 0.002;
    if (style == 1) { hw = 0.3 * hw + 0.004; ow = 0.25 * hw; }

    // Quality: 1, 2 (diagonal pair) or 4 (2x2) samples per pixel.
    int ns = (Quality < 0.34) ? 1 : ((Quality < 0.67) ? 2 : 4);
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        if (i >= ns) break;
        vec2 o = vec2(0.0);
        if (ns == 2) o = (i == 0) ? vec2(-0.25, -0.25) : vec2(0.25, 0.25);
        else if (ns == 4) o = vec2((i == 0 || i == 3) ? -0.25 : 0.25, (i < 2) ? -0.25 : 0.25);
        acc += sampleGirih(base + o * px, lat, theta, hw, ow, aa, style, Interlace, Glow_, pmode, HueOffset, Saturation_);
    }
    vec3 col = max(acc / float(ns), 0.0) * Brightness_;
    // Soft shoulder above 0.8 so Brightness near max stays shaded instead of clipping to white.
    col = mix(col, 0.8 + 0.2 * (1.0 - exp(-(col - 0.8) * 5.0)), step(0.8, col));
    gl_FragColor = blackToAlpha(col);
}
