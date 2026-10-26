#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Kali-family inversion-fold fractal in the plane: iterate z -> |z|/(z.z) - c with an optional per-step rotation, colour by orbit traps (point, line, circle, combined) and accumulated orbit change. The constant c circles on a small animated path.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Fractal"
    ],
    "LSD_PHASES": {"SpeedPhase": ["Speed"]},
    "INPUTS": [
        { "NAME": "OrbitRadius", "LABEL": "Orbit Radius", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Speed", "LABEL": "Orbit Speed", "TYPE": "float", "DEFAULT": 0.3, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Iterations", "LABEL": "Iterations", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Twist", "LABEL": "Twist Per Step", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "SurfaceType", "LABEL": "Trap Type", "TYPE": "float", "DEFAULT": 3.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "TrapScale", "LABEL": "Trap Scale", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Drift", "LABEL": "Centre Drift", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "PaletteMode", "LABEL": "Palette Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.8, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define OrbitRadius_ (OrbitRadius*0.25)
#define Iterations_ (12.0+Iterations*12.0)
#define Twist_ (Twist*0.35)
#define TrapScale_ (0.15+TrapScale*1.35)
#define Zoom_ (0.6+Zoom*2.4)
#define Drift_ (Drift*0.5)
#define Brightness_ (Brightness*2.0)

const float PI = 3.14159265358979323846;
const float TAU = 6.28318530717958647692;
// 100 * TAU: time wrap; phase rates below are multiples of 0.01 rad/s at default Speed.
const float TIME_WRAP = 628.3185307;

// Cosine palette a + b cos(2 pi (c t + d)); four families, hue offset shifts the phase.
vec3 palette(float t, float hue, int mode) {
    vec3 a = vec3(0.5);
    vec3 b = vec3(0.5);
    vec3 c = vec3(1.0);
    vec3 d = vec3(0.0, 0.33, 0.67);
    if (mode == 1) { c = vec3(1.0, 1.0, 0.5); d = vec3(0.8, 0.9, 0.3); }
    else if (mode == 2) { c = vec3(2.0, 1.0, 0.0); d = vec3(0.5, 0.2, 0.25); }
    else if (mode == 3) { a = vec3(0.55, 0.45, 0.4); b = vec3(0.45, 0.4, 0.5); c = vec3(0.7, 0.9, 1.0); d = vec3(0.1, 0.25, 0.55); }
    return a + b * cos(TAU * (c * t + d + hue));
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
    vec2 uv = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0) * 2.0;

    float tt = mod(TIME, TIME_WRAP);
    float ph = SpeedPhase;
    // Constant c: a base point on the fractal-rich region, circled by a small ellipse.
    vec2 c = vec2(0.82, 0.55) + OrbitRadius_ * vec2(cos(ph), sin(ph * 1.3 + 0.7));
    vec2 cen = Drift_ * vec2(sin(tt * 0.07), cos(tt * 0.05));
    vec2 z = uv / Zoom_ + cen;

    int iters = int(Iterations_ * (0.5 + 0.5 * Quality));
    iters = clamp(iters, 6, 24);
    float rotA = Twist_;
    mat2 R = mat2(cos(rotA), sin(rotA), -sin(rotA), cos(rotA));

    int tmode = clamp(int(floor(SurfaceType + 0.5)), 0, 3);
    float sc = TrapScale_;
    vec2 pt = vec2(0.3, 0.2) * sc;
    float lineAng = 0.5 + 0.2 * sin(tt * 0.1);
    vec2 ln = vec2(cos(lineAng), sin(lineAng));
    float lineOff = 0.25 * sc;
    float circR = 0.6 * sc;

    float dPt = 1e3;
    float dLn = 1e3;
    float dCr = 1e3;
    float sumChg = 0.0;
    float lastIt = 0.0;
    float prevLen = length(z);

    for (int i = 0; i < 24; i++) {
        if (i >= iters) break;
        float r2 = max(dot(z, z), 1e-8);
        z = abs(z) / r2 - c;
        z = R * z;
        // Keep the orbit finite: a point very near the origin maps to a huge value.
        z = clamp(z, vec2(-1e3), vec2(1e3));
        float len = length(z);
        sumChg += abs(len - prevLen) / (1.0 + len + prevLen);
        prevLen = len;
        float dp = length(z - pt);
        float dl = abs(dot(z, ln) - lineOff);
        float dc = abs(len - circR);
        // Remember the iteration of the closest approach to the active trap (point or combined).
        if (dp < dPt) { dPt = dp; lastIt = float(i); }
        dLn = min(dLn, dl);
        dCr = min(dCr, dc);
    }

    float iterN = float(iters);
    float tP = 1.0 - exp(-dPt * 2.0);
    float tL = 1.0 - exp(-dLn * 3.0);
    float tC = 1.0 - exp(-dCr * 3.0);
    float chg = sumChg / iterN;
    float tIt = lastIt / iterN;

    // Active trap value in 0..1 (0 = orbit touched the trap).
    float trap;
    if (tmode == 0) trap = tP;
    else if (tmode == 1) trap = tL;
    else if (tmode == 2) trap = tC;
    else trap = min(min(tP, tL), tC);

    int cmode = clamp(int(floor(ColorMode + 0.5)), 0, 3);
    int pmode = clamp(int(floor(PaletteMode + 0.5)), 0, 3);
    float hue = HueOffset;
    vec3 col;
    if (cmode == 0) {
        // Palette lookup by trap distance, glowing where the orbit grazes the trap.
        col = palette(trap * 0.8 + tIt * 0.3, hue, pmode);
        col *= 0.2 + 1.2 * exp(-trap * 5.0);
    } else if (cmode == 1) {
        // Accumulated orbit change: flowing filigree texture.
        float k = clamp(chg * 3.0, 0.0, 1.0);
        col = palette(k * 1.2, hue, pmode) * (0.25 + 0.9 * k);
        col += palette(trap, hue + 0.5, pmode) * 0.35 * exp(-trap * 6.0);
    } else if (cmode == 2) {
        // The three traps drive separate layers.
        vec3 l1 = palette(0.0 + tP, hue, pmode) * exp(-tP * 4.0);
        vec3 l2 = palette(0.33 + tL, hue, pmode) * exp(-tL * 4.0);
        vec3 l3 = palette(0.66 + tC, hue, pmode) * exp(-tC * 4.0);
        col = l1 + l2 + l3;
        col *= 0.2 + chg * 2.0;
    } else {
        // Mono ink: trap lines on a tinted ground.
        float line = exp(-trap * 9.0);
        float k = 0.15 + 0.85 * clamp(chg * 2.5, 0.0, 1.0);
        col = palette(0.3, hue, pmode) * k * 0.35 + palette(0.6, hue, pmode) * line * 1.1;
    }

    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, Saturation * 1.4);
    col *= Brightness_;
    col = max(col, 0.0);
    // Soft shoulder so high Brightness does not clip hard.
    col = mix(col, 0.8 + 0.2 * (1.0 - exp(-(col - 0.8) * 5.0)), step(0.8, col));
    if (!(col.r == col.r) || !(col.g == col.g) || !(col.b == col.b)) col = vec3(0.0);
    gl_FragColor = blackToAlpha(col);
}
