#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Raymarched Mandelbulb with an animatable power, Julia-mode blend, orbit-trap colouring, ambient occlusion and a turntable camera.",
    "CREDIT": "Liquid LSD",
    "LSD_COST": 3,
    "ISFVSN": "2.0",
    "is3D": true,
    "CATEGORIES": [
        "Generator",
        "3D",
        "Fractal"
    ],
    "LSD_PHASES": {"FlightPhase": ["FlightSpeed"]},
    "INPUTS": [
        { "NAME": "Power", "LABEL": "Power", "TYPE": "float", "DEFAULT": 0.42857, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "PowerSwing", "LABEL": "Power Swing", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "JuliaMix", "LABEL": "Julia Mix", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Bailout", "LABEL": "Bailout", "TYPE": "float", "DEFAULT": 0.33333, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "FlightSpeed", "LABEL": "Flight Speed", "TYPE": "float", "DEFAULT": 0.25, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 4.0, "STEP": 1 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.8, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "RotateX", "LABEL": "Rotate X", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateY", "LABEL": "Rotate Y", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateZ", "LABEL": "Rotate Z", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define Power_ (2.0+Power*14.0)
#define Bailout_ (2.0+Bailout*6.0)
#define Glow_ (Glow*2.0)
#define Brightness_ (Brightness*2.0)
#define Zoom_ (Zoom<0.5?pow(0.1,1.0-2.0*Zoom):pow(5.0,2.0*Zoom-1.0))

const float PI = 3.14159265358979323846;
const int MAX_STEPS = 80;   // hard caps (loop bounds must be constant); Quality picks the live count
const int MAX_ITERS = 10;
const int MAX_AO = 4;

mat3 rotX(float a) { float c = cos(a), s = sin(a); return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c); }
mat3 rotY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c); }
mat3 rotZ(float a) { float c = cos(a), s = sin(a); return mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0); }

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Palette over t in roughly 0..1. Mode 4 is a monochrome gold ramp; the others walk the hue wheel.
vec3 palette(float t, float hueOffset, float sat, int mode) {
    if (mode >= 4) {
        return mix(vec3(0.35, 0.18, 0.05), vec3(1.0, 0.85, 0.4), clamp(t, 0.0, 1.0));
    }
    return hsv2rgb(vec3(fract(hueOffset + t), sat, 1.0));
}

// Mandelbulb distance estimate. z -> z^n + c with the triplex (spherical-coordinate) power:
//   r = |z|, theta = acos(z.z / r), phi = atan(z.y, z.x)
//   z^n = r^n (sin(n theta) cos(n phi), sin(n theta) sin(n phi), cos(n theta))
// The running derivative is dr_{k+1} = n r_k^(n-1) dr_k + 1 and the estimate is 0.5 log(r) r / dr.
// trap: x = min |z|, y = min |z.y|, z = min |z.x|, w = smooth escape iteration.
float de(vec3 p, float n, int iters, float bail, float jm, vec3 jc, out vec4 trap) {
    vec3 c = mix(p, jc, jm);
    vec3 z = p;
    float dr = 1.0;
    float r = length(z);
    trap = vec4(1e5, 1e5, 1e5, float(iters));
    for (int i = 0; i < MAX_ITERS; i++) {
        if (i >= iters) break;
        r = length(z);
        trap.x = min(trap.x, r);
        trap.y = min(trap.y, abs(z.y));
        trap.z = min(trap.z, abs(z.x));
        if (r > bail) {
            trap.w = float(i) + 1.0 - log(log(r) / log(bail)) / log(max(n, 1.5));
            break;
        }
        r = max(r, 1e-6);
        float lr = log(r);
        float zr = exp(n * lr);                      // r^n
        dr = min(n * (zr / r) * dr + 1.0, 1e30);      // n r^(n-1) dr + 1
        float th = acos(clamp(z.z / r, -1.0, 1.0)) * n;
        float ph = atan(z.y, z.x + 1e-9) * n;
        float sth = sin(th);
        z = zr * vec3(sth * cos(ph), sth * sin(ph), cos(th)) + c;
    }
    r = max(length(z), 1e-6);
    return 0.5 * log(r) * r / max(dr, 1e-6);
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
    vec2 uv = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0);

    // Quality -> live loop counts (defaults: 64 steps, 8 iterations, 3 AO taps)
    float q = clamp(Quality, 0.0, 1.0);
    int steps = int(48.0 + 32.0 * q + 0.5);
    int iters = int(6.0 + 4.0 * q + 0.5);
    int aoTaps = int(2.0 + 2.0 * q);

    float n = clamp(Power_ + PowerSwing * 4.0 * sin(TIME * 0.35), 2.0, 16.0);
    float bail = Bailout_;
    float jm = JuliaMix;
    float ja = TIME * 0.12;
    vec3 jc = vec3(0.45, -0.35, 0.4) + 0.12 * vec3(sin(ja), cos(ja * 1.3), sin(ja * 0.7));

    // Camera: tilt off the pole, orbit about the pole (FlightSpeed), then user rotation.
    float spin = FlightPhase * 0.7;
    float camDist = 3.4 - 0.5 * (0.5 + 0.5 * sin(FlightPhase * 0.4));
    mat3 rot = rotZ(RotateZ) * rotY(RotateY) * rotX(RotateX) * rotZ(spin) * rotX(-1.15);
    vec3 ro = rot * vec3(0.0, 0.0, -camDist);
    vec3 rd = rot * normalize(vec3(uv, 1.2 * Zoom_));

    // Analytic bounding sphere: pixels that miss it never iterate.
    float bound = 1.25 + 0.75 / (0.5 * n);
    float b = dot(ro, rd);
    float cq = dot(ro, ro) - bound * bound;
    float disc = b * b - cq;

    vec3 bgTop = vec3(0.03, 0.02, 0.06);
    vec3 bgBot = vec3(0.01, 0.01, 0.02);
    vec3 col = mix(bgBot, bgTop, clamp(isf_FragNormCoord.y, 0.0, 1.0));
    vec3 glowCol = hsv2rgb(vec3(fract(HueOffset + 0.55), Saturation, 1.0));

    vec4 trap = vec4(0.0);
    float glow = 0.0;
    float hitT = -1.0;
    int usedSteps = 0;
    float vig = 1.0 - 0.5 * dot(uv, uv);

    if (disc > 0.0) {
        float sq = sqrt(disc);
        float t = max(-b - sq, 0.0);
        float tFar = -b + sq;
        float minD = 1e5;
        for (int i = 0; i < MAX_STEPS; i++) {
            if (i >= steps) break;
            vec3 p = ro + rd * t;
            float d = de(p, n, iters, bail, jm, jc, trap);
            minD = min(minD, d);
            usedSteps = i;
            float eps = 0.00035 * t;
            if (d < eps) { hitT = t; break; }
            t += max(d * 0.8, eps * 0.5);
            if (t > tFar) break;
        }
        glow = exp(-minD * 9.0);

        if (hitT > 0.0) {
            vec3 p = ro + rd * hitT;
            float ne = max(0.00035 * hitT, 0.0004) * 1.5;
            vec4 tmp;
            vec2 e = vec2(ne, 0.0);
            vec3 nor = normalize(vec3(
                de(p + e.xyy, n, iters, bail, jm, jc, tmp) - de(p - e.xyy, n, iters, bail, jm, jc, tmp),
                de(p + e.yxy, n, iters, bail, jm, jc, tmp) - de(p - e.yxy, n, iters, bail, jm, jc, tmp),
                de(p + e.yyx, n, iters, bail, jm, jc, tmp) - de(p - e.yyx, n, iters, bail, jm, jc, tmp)) + 1e-9);
            // trap at the hit point itself
            de(p, n, iters, bail, jm, jc, trap);

            // soft ambient occlusion, at most 4 taps
            float occ = 0.0;
            float w = 1.0;
            for (int k = 1; k <= MAX_AO; k++) {
                if (k > aoTaps) break;
                float h = 0.015 + 0.05 * float(k);
                float dd = de(p + nor * h, n, iters, bail, jm, jc, tmp);
                occ += (h - max(dd, 0.0)) * w;
                w *= 0.6;
            }
            float ao = clamp(1.0 - occ * 2.5, 0.0, 1.0);
            // cheap cavity term from the march effort
            ao *= 1.0 - 0.35 * float(usedSteps) / float(max(steps, 1));

            int cMode = clamp(int(floor(ColorMode + 0.5)), 0, 4);
            float tc;
            if (cMode == 0) {
                tc = trap.x * 0.8;                       // distance-to-origin trap
            } else if (cMode == 1) {
                tc = trap.y * 1.2 + trap.z * 0.5;        // plane traps
            } else if (cMode == 2) {
                tc = trap.w * 0.1;                       // smooth escape count
            } else if (cMode == 3) {
                tc = dot(nor, vec3(0.35, 0.35, 0.35));   // normal spectrum
            } else {
                tc = trap.x * 0.8;                       // gold ramp on the origin trap
            }
            vec3 base = palette(tc, HueOffset, Saturation, cMode) * Brightness_;

            // Camera-relative light: it orbits with the view, so the lit side always faces the viewer
            vec3 ldir = rot * normalize(vec3(0.55, 0.8, -0.35));
            float diff = max(dot(nor, ldir), 0.0);
            float fres = pow(1.0 - max(dot(nor, -rd), 0.0), 3.0);
            float spec = pow(max(dot(reflect(rd, nor), ldir), 0.0), 24.0);
            col = base * (0.18 + 0.82 * diff) * ao
                + vec3(fres * 0.45) * glowCol * Brightness_ * ao
                + vec3(spec * 0.25 * ao);
            float fog = smoothstep(1.5, 5.0, hitT);
            col = mix(col, bgTop, fog * 0.6);
        }
    }

    // glow around the silhouette, from the closest approach of the march
    col += glowCol * glow * Glow_ * 0.12 * (hitT > 0.0 ? 0.5 : 1.0);
    col *= vig;
    col = clamp(col, 0.0, 4.0);
    if (!(col.x == col.x) || !(col.y == col.y) || !(col.z == col.z)) col = vec3(0.0);

    gl_FragColor = blackToAlpha(col);
}
