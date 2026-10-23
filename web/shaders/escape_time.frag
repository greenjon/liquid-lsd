#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "High-iteration 2D escape-time fractals: Julia, Burning Ship Julia, relaxed Newton (cubic roots) and Mandelbrot, with smooth colouring, orbit traps, animated constant and a fractional power.",
    "CREDIT": "Liquid LSD",
    "LSD_COST": 3,
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Fractal"
    ],
    "LSD_PHASES": {"MorphPhase": ["MorphSpeed"]},
    "INPUTS": [
        { "NAME": "FractalType", "LABEL": "Fractal Type", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "JuliaRadius", "LABEL": "Julia Radius", "TYPE": "float", "DEFAULT": 0.65, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "JuliaAngle", "LABEL": "Julia Angle", "TYPE": "float", "DEFAULT": 0.1, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Power", "LABEL": "Power", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "MorphSpeed", "LABEL": "Morph Speed", "TYPE": "float", "DEFAULT": 0.15, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Detail", "LABEL": "Detail", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "CenterX", "LABEL": "Center X", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "CenterY", "LABEL": "Center Y", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Rotation", "LABEL": "Rotation", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "ColorCycle", "LABEL": "Color Cycle", "TYPE": "float", "DEFAULT": 0.35, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "TrapGlow", "LABEL": "Trap Glow", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.85, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define JuliaRadius_ (0.2+JuliaRadius*0.9)
#define JuliaAngle_ (JuliaAngle*3.14159265)
#define Power_ (2.0+Power*3.0)
#define Zoom_ (Zoom<0.5?pow(0.3,1.0-2.0*Zoom):pow(3000.0,2.0*Zoom-1.0))
#define Rotation_ (Rotation*3.14159265)
#define ColorCycle_ (ColorCycle*0.6)
#define TrapGlow_ (TrapGlow*3.0)
#define Brightness_ (Brightness*2.0)

const float PI = 3.14159265358979323846;
const int MAX_IT = 400;
const float BAILOUT = 256.0;
const float NEWTON_TOL = 1.0e-5;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec2 cmul(vec2 a, vec2 b) { return vec2(a.x * b.x - a.y * b.y, a.x * b.y + a.y * b.x); }
vec2 cdiv(vec2 a, vec2 b) { return vec2(a.x * b.x + a.y * b.y, a.y * b.x - a.x * b.y) / max(dot(b, b), 1.0e-20); }

// z^p for real p >= 2 (principal branch); the integer fast path covers the default.
vec2 cpow(vec2 z, float p) {
    if (p < 2.001) return vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y);
    float r = length(z);
    if (r < 1.0e-12) return vec2(0.0);
    float a = atan(z.y, z.x) * p;
    return pow(r, p) * vec2(cos(a), sin(a));
}

// Returns (smooth iteration count, ring trap, cross trap, kind). kind: -1 = never escaped,
// 0..2 = Newton root index, 3 = escaped.
vec4 iterate(vec2 p, int type, int maxIt, vec2 c, float pw, out vec2 zOut) {
    vec2 z;
    vec2 k = p;
    float trapRing = 1.0e5;
    float trapCross = 1.0e5;

    if (type == 2) {
        // Relaxed Newton on z^3 - 1. The relaxation factor drifts with c; the roots stay fixed points.
        vec2 relax = vec2(1.0, 0.0) + 0.2 * c;
        z = p;
        for (int i = 0; i < MAX_IT; i++) {
            if (i >= maxIt) break;
            vec2 z2 = cmul(z, z);
            vec2 f = cmul(z2, z) - vec2(1.0, 0.0);
            vec2 df = 3.0 * z2;
            z -= cmul(relax, cdiv(f, df));
            trapRing = min(trapRing, abs(length(z) - 1.0));
            trapCross = min(trapCross, min(abs(z.x), abs(z.y)));
            vec2 d0 = z - vec2(1.0, 0.0);
            vec2 d1 = z - vec2(-0.5, 0.8660254);
            vec2 d2 = z - vec2(-0.5, -0.8660254);
            float e0 = length(d0), e1 = length(d1), e2 = length(d2);
            float err = min(e0, min(e1, e2));
            if (err < NEWTON_TOL) {
                float root = (err == e0) ? 0.0 : ((err == e1) ? 1.0 : 2.0);
                float nu = float(i) + 1.0 - log2(max(log(err + 1.0e-30) / log(NEWTON_TOL), 1.0));
                zOut = z;
                return vec4(nu, trapRing, trapCross, root);
            }
        }
        zOut = z;
        return vec4(float(maxIt), trapRing, trapCross, -1.0);
    }

    if (type == 3) { z = vec2(0.0); }
    else { z = p; k = c; }

    for (int i = 0; i < MAX_IT; i++) {
        if (i >= maxIt) break;
        if (type == 1) z = abs(z);
        z = cpow(z, pw) + k;
        trapRing = min(trapRing, abs(length(z) - 1.0));
        trapCross = min(trapCross, min(abs(z.x), abs(z.y)));
        float r2 = dot(z, z);
        if (r2 > BAILOUT * BAILOUT) {
            float nu = float(i) + 1.0 - log(max(0.5 * log(r2), 1.0e-6)) / log(max(pw, 2.0));
            zOut = z;
            return vec4(nu, trapRing, trapCross, 3.0);
        }
    }
    zOut = z;
    return vec4(float(maxIt), trapRing, trapCross, -1.0);
}

vec3 shade(vec4 s, vec2 zEnd, int maxIt) {
    float nu = s.x;
    float glow = exp(-min(s.y, s.z * 2.0) * 8.0) * TrapGlow_;
    int mode = int(ColorMode + 0.5);
    if (s.w < -0.5) {
        // Interior: dark, lit only by the orbit trap.
        float v = (mode == 1 ? glow * 0.6 : glow * 0.15) * Brightness_;
        return hsv2rgb(vec3(fract(HueOffset + 0.6), Saturation, clamp(v, 0.0, 1.0)));
    }
    float hue = HueOffset;
    float val = 1.0;
    float sat = Saturation;
    float t = nu * ColorCycle_;
    if (s.w < 2.5) { hue += s.w / 3.0; t *= 0.2; } // Newton: one base hue per root
    if (mode == 0) {
        hue += t;
        val = 0.45 + 0.55 * (0.5 + 0.5 * cos(nu * 0.35));
    } else if (mode == 1) {
        hue += 0.5 * s.y + t * 0.3;
        val = clamp(0.25 + glow, 0.0, 1.2);
    } else if (mode == 2) {
        hue += s.w > 2.5 ? atan(zEnd.y, zEnd.x) / (2.0 * PI) + t * 0.25 : t;
        val = 1.0 - exp(-nu * 0.08);
    } else {
        sat *= 0.15;
        val = 1.0 - exp(-nu * 0.05);
    }
    val += glow * 0.35;
    val *= Brightness_;
    if (s.w < 2.5) val *= clamp(1.0 - nu / float(maxIt), 0.0, 1.0) * 0.8 + 0.2;
    return hsv2rgb(vec3(fract(hue), clamp(sat, 0.0, 1.0), clamp(val, 0.0, 1.5)));
}

void main() {
    int type = int(FractalType + 0.5);
    int maxIt = int(mix(48.0, float(MAX_IT), Detail));
    int aa = Detail > 0.75 ? 2 : 1;

    float ang = JuliaAngle_ + MorphPhase;
    vec2 c = JuliaRadius_ * vec2(cos(ang), sin(ang));
    float pw = Power_;

    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    float cr = cos(Rotation_), sr = sin(Rotation_);
    float halfH = 1.5 / Zoom_;
    vec2 center = vec2(CenterX, CenterY) * 1.5 / Zoom_;
    if (type == 3) center += vec2(-0.5, 0.0);

    vec3 col = vec3(0.0);
    for (int sy = 0; sy < 2; sy++) {
        for (int sx = 0; sx < 2; sx++) {
            if (sx >= aa || sy >= aa) continue;
            vec2 off = aa == 1 ? vec2(0.0) : (vec2(float(sx), float(sy)) - 0.5) * 0.5;
            vec2 uv = (isf_FragNormCoord + off / RENDERSIZE.xy - 0.5) * vec2(aspect, 1.0) * 2.0 * halfH;
            uv = vec2(cr * uv.x - sr * uv.y, sr * uv.x + cr * uv.y) + center;
            vec2 zEnd;
            vec4 s = iterate(uv, type, maxIt, c, pw, zEnd);
            col += shade(s, zEnd, maxIt);
        }
    }
    col /= float(aa * aa);
    gl_FragColor = vec4(col, 1.0);
}
