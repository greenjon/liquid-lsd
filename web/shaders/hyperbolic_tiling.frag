#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Poincare-disk {P,Q} tiling built by iterated mirror folds of the (2,P,Q) Coxeter triangle. Escher-style tile colouring, hyperbolic edge glow, a drifting Mobius view and slow rotation.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Geometric",
        "Tiling"
    ],
    "LSD_PHASES": {"SpeedPhase": ["Speed"], "SpinPhase": ["Spin"]},
    "INPUTS": [
        { "NAME": "P", "LABEL": "Tile Sides P", "TYPE": "float", "DEFAULT": 5.0, "MIN": 3.0, "MAX": 10.0, "STEP": 1 },
        { "NAME": "Q", "LABEL": "Tiles Per Vertex Q", "TYPE": "float", "DEFAULT": 4.0, "MIN": 3.0, "MAX": 10.0, "STEP": 1 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.8, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Speed", "LABEL": "Speed", "TYPE": "float", "DEFAULT": 0.3, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Drift", "LABEL": "Drift", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Spin", "LABEL": "Spin", "TYPE": "float", "DEFAULT": 0.25, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "EdgeWidth", "LABEL": "Edge Width", "TYPE": "float", "DEFAULT": 0.35, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Subdivision", "LABEL": "Subdivision Lines", "TYPE": "float", "DEFAULT": 0.25, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "Outside", "LABEL": "Outside Disk", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 2.0, "STEP": 1 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.58, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.75, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define Quality_ (6.0+Quality*10.0)
#define EdgeWidth_ (0.004+EdgeWidth*0.09)
#define Glow_ (Glow*2.0)
#define Drift_ (Drift*0.75)
#define Brightness_ (Brightness*2.0)

const float PI = 3.14159265358979323846;
const float TWO_PI = 6.28318530717958647692;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec2 cmul(vec2 a, vec2 b) { return vec2(a.x * b.x - a.y * b.y, a.x * b.y + a.y * b.x); }
vec2 cdiv(vec2 a, vec2 b) { return cmul(a, vec2(b.x, -b.y)) / max(dot(b, b), 1e-6); }

// Black is transparent: alpha follows the brightest channel (the renderer stores colour x alpha, so the
// stored pixel is the colour itself and a deck's BG layer shows through wherever this source is dark).
vec4 blackToAlpha(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float a = max(c.r, max(c.g, c.b));
    return vec4(c / max(a, 1e-4), a);
}

void main() {
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 zs = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0) * 2.08;
    float pixel = 2.08 / RENDERSIZE.y;

    // Valid hyperbolic pair: (p-2)(q-2) > 4. Raise q to the smallest valid value if needed.
    int pi = clamp(int(floor(P + 0.5)), 3, 10);
    int qi = clamp(int(floor(Q + 0.5)), 3, 10);
    qi = max(qi, 4 / (pi - 2) + 3);
    float pf = float(pi);
    float qf = float(qi);

    // Fundamental triangle: angles PI/2, PI/p, PI/q. Mirrors: the x axis, a diameter at angle PI/p,
    // and a circle orthogonal to the unit circle with centre on that diameter.
    float a = PI / pf;
    float b = PI / qf;
    float cb = cos(b);
    float sa = sin(a);
    float d2 = cb * cb / max(cb * cb - sa * sa, 1e-4);
    float dc = sqrt(d2);
    vec2 cen = dc * vec2(cos(a), sin(a));
    float R2 = max(d2 - 1.0, 1e-4);
    float Rr = sqrt(R2);
    vec2 n2 = vec2(sa, -cos(a));

    // View: rotation, then Mobius translation along a slow Lissajous path.
    float sp = SpeedPhase * 0.5;
    float ang = SpinPhase * 0.5;
    vec2 t = Drift_ * vec2(cos(sp * 0.7), sin(sp * 1.1));
    float r2s = dot(zs, zs);

    // Hyperbolic size of one pixel (isometry invariant).
    float aaH = pixel * 2.0 / max(1.0 - r2s, 0.02);

    int outside = int(floor(Outside + 0.5));
    bool inDisk = r2s < 1.0;
    vec2 w = zs;
    if (!inDisk) {
        if (outside == 1) {
            w = zs / r2s;
            aaH = pixel * 2.0 / max(1.0 - 1.0 / r2s, 0.02);
        }
    }
    w = cmul(w, vec2(cos(ang), sin(ang)));
    vec2 z = cdiv(w + t, vec2(1.0, 0.0) + cmul(vec2(t.x, -t.y), w));

    // Iterated mirror folds into the fundamental triangle.
    int iters = int(Quality_);
    float depth = 0.0;
    float par = 0.0;
    float rotCount = 0.0;
    bool settled = false;
    for (int i = 0; i < 16; i++) {
        if (i >= iters) break;
        bool moved = false;
        if (z.y < 0.0) { z.y = -z.y; par += 1.0; moved = true; }
        float dd = dot(z, n2);
        if (dd < 0.0) { z -= 2.0 * dd * n2; par += 1.0; rotCount += 1.0; moved = true; }
        vec2 v = z - cen;
        float r2 = dot(v, v);
        if (r2 < R2) {
            z = cen + v * (R2 / max(r2, 1e-6));
            par += 1.0; depth += 1.0; moved = true;
        }
        if (!moved) { settled = true; break; }
    }
    // The last iteration may leave the point settled without us having seen a clean pass.
    if (!settled) {
        vec2 v = z - cen;
        settled = z.y >= 0.0 && dot(z, n2) >= 0.0 && dot(v, v) >= R2;
    }

    // Hyperbolic distances (as sinh d) from the folded point to the three mirrors.
    float zz = clamp(dot(z, z), 0.0, 0.9999);
    float conf = 1.0 - zz;
    vec2 v = z - cen;
    float eTile = abs(dot(v, v) - R2) / (Rr * conf);     // tile edge (circle mirror)
    float eAx = 2.0 * abs(z.y) / conf;                     // x axis mirror (tile centre to vertex)
    float eDi = 2.0 * abs(dot(z, n2)) / conf;              // diameter mirror (tile centre to edge midpoint)
    float eSub = min(eAx, eDi);
    float cdist = 2.0 * length(z) / conf;                  // ~sinh of distance from tile centre

    // Colouring.
    int cmode = clamp(int(floor(ColorMode + 0.5)), 0, 3);
    float tri = mod(par, 2.0);
    float hue = HueOffset;
    float sat = Saturation;
    float val = Brightness_;
    if (cmode == 0) {
        hue += 0.07 * mod(depth, 7.0) + 0.5 * mod(depth, 2.0) * 0.25;
        val *= (0.55 + 0.25 * tri) * (1.15 - 0.45 * smoothstep(0.0, 1.5, cdist));
    } else if (cmode == 1) {
        hue += 0.11 * depth;
        val *= (0.85 - 0.35 * smoothstep(0.0, 1.5, cdist)) * (1.0 - 0.05 * tri);
    } else if (cmode == 2) {
        hue += 0.5 * mod(depth, 2.0);
        val *= 0.45 + 0.4 * tri;
    } else {
        sat *= 0.15;
        val *= (0.9 - 0.08 * depth) * (0.7 + 0.3 * tri) * (1.1 - 0.4 * smoothstep(0.0, 1.5, cdist));
    }
    vec3 tileCol = hsv2rgb(vec3(fract(hue), sat, clamp(val, 0.0, 2.0)));

    // Edge line + glow, widths in hyperbolic units, anti-aliased by one pixel.
    float w0 = EdgeWidth_;
    float line = 1.0 - smoothstep(w0 - aaH, w0 + aaH, eTile);
    float subLine = (1.0 - smoothstep(w0 * 0.4 - aaH, w0 * 0.4 + aaH, eSub)) * Subdivision;
    float glow = Glow_ * exp(-eTile * 7.0) * 0.5;
    vec3 edgeCol = hsv2rgb(vec3(fract(HueOffset + 0.08), Saturation * 0.35, Brightness_ * 1.2));
    vec3 glowCol = hsv2rgb(vec3(fract(HueOffset + 0.5), Saturation, 1.0));

    vec3 col = tileCol;
    col = mix(col, tileCol * 0.35, subLine);
    col = mix(col, edgeCol, line);
    col += glowCol * glow * Brightness_ * 0.5;

    // Sub-pixel tiles near the rim, or points the fold budget could not settle, melt into a mean colour.
    vec3 meanCol = glowCol * (0.12 + 0.2 * Glow_) * Brightness_ + tileCol * 0.15;
    float fade = smoothstep(0.35, 1.6, aaH);
    if (!settled) fade = 1.0;
    col = mix(col, meanCol, fade);

    // Outside the disk.
    if (!inDisk) {
        if (outside == 0) {
            col = meanCol * 0.6 * exp(-(sqrt(r2s) - 1.0) * 6.0);
        } else if (outside == 2) {
            col = hsv2rgb(vec3(fract(HueOffset + 0.5), Saturation * 0.6, Brightness_ * 0.25));
        }
    }

    // Boundary rim glow.
    float rim = exp(-abs(sqrt(r2s) - 1.0) * 28.0);
    col += glowCol * rim * (0.15 + 0.6 * Glow_) * Brightness_ * 0.5;

    // Soft shoulder above 0.8 so Brightness near max stays shaded instead of clipping to white.
    col = max(col, 0.0);
    col = mix(col, 0.8 + 0.2 * (1.0 - exp(-(col - 0.8) * 5.0)), step(0.8, col));
    gl_FragColor = blackToAlpha(col);
}
