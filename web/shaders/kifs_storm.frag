#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "The original Liquid LSD KIFS, restored: every fold level rotates freely before a six-way kaleidoscope fold, so nested cubes tumble against each other and fill the frame. Morphing base shape, neon orbit-trap glow and an optional endless lattice. Tumble spins the fold angles.",
    "CREDIT": "Liquid LSD",
    "LSD_COST": 3,
    "ISFVSN": "2.0",
    "is3D": true,
    "CATEGORIES": [
        "Generator",
        "3D",
        "Fractal"
    ],
    "LSD_PHASES": {"TumblePhase": ["TumbleSpeed"], "FlightPhase": ["FlySpeed"]},
    "INPUTS": [
        { "NAME": "Iterations", "LABEL": "Iterations", "TYPE": "float", "DEFAULT": 5.0, "MIN": 1.0, "MAX": 8.0, "STEP": 1 },
        { "NAME": "Scale", "LABEL": "Scale", "TYPE": "float", "DEFAULT": 0.3333, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "FoldX", "LABEL": "Fold X", "TYPE": "float", "DEFAULT": 0.25, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "FoldY", "LABEL": "Fold Y", "TYPE": "float", "DEFAULT": 0.25, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "FoldZ", "LABEL": "Fold Z", "TYPE": "float", "DEFAULT": 0.25, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "FoldAngleX", "LABEL": "Fold Angle X", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "FoldAngleY", "LABEL": "Fold Angle Y", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "FoldAngleZ", "LABEL": "Fold Angle Z", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "TumbleSpeed", "LABEL": "Tumble Speed", "TYPE": "float", "DEFAULT": 0.15, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "ShapeMorph", "LABEL": "Shape Morph", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Smoothness", "LABEL": "Smoothness", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "RepeatSpacing", "LABEL": "Repeat Spacing", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Repeat3D", "LABEL": "Repeat 3D", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0, "STEP": 1 },
        { "NAME": "FlySpeed", "LABEL": "Fly Speed", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "ColorShift", "LABEL": "Color Shift", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "TrapMode", "LABEL": "Trap Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 4.0, "STEP": 1 },
        { "NAME": "TrapGlow", "LABEL": "Trap Glow", "TYPE": "float", "DEFAULT": 0.15, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "NormalColoring", "LABEL": "Normal Coloring", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "NormalFrequency", "LABEL": "Normal Frequency", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "RotateX", "LABEL": "Rotate X", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateY", "LABEL": "Rotate Y", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateZ", "LABEL": "Rotate Z", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 }
    ]
}
*/

// Port of the first KIFS source (removed in fc1ad3f when it was too heavy for integrated graphics). The look comes from
// four things the newer KIFS deliberately avoids: every level, the first included, rotates by an unconstrained angle
// BEFORE folding; the fold is a six-way kaleidoscope rather than a true polyhedral group; the per-axis offsets and low
// scales let the copies overlap and spread over the whole frame (no bounding volume); and the colour is additive neon
// (hyperbolic orbit-trap glow plus linear step glow, no occlusion or fog).

// Inputs are normalized to 0..1 / -1..1; these remap them to the original shader's working units.
#define Scale_ (1.0 + 3.0 * Scale)
#define FoldOff_ (2.0 * vec3(FoldX, FoldY, FoldZ))
#define Zoom_ (Zoom < 0.5 ? pow(0.1, 1.0 - 2.0 * Zoom) : pow(5.0, 2.0 * Zoom - 1.0))
#define RepeatSpacing_ (10.0 * RepeatSpacing)
#define NormalFrequency_ (1.0 + 9.0 * NormalFrequency)
#define TrapGlow_ (2.0 * TrapGlow)

const float TAU = 6.28318530718;
const int MAX_RAY_STEPS = 80;
const float MIN_DIST = 0.001;
const float MAX_DIST = 10.0;

mat3 gFoldRot;   // per-level rotation, set once in main
vec3 gFoldOff;   // per-axis offset subtracted after each fold
float gFlight;   // camera flight along z

// Smooth minimum and maximum (Inigo Quilez).
float smin(float a, float b, float k) {
    if (k <= 0.0001) return min(a, b);
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float smax(float a, float b, float k) {
    if (k <= 0.0001) return max(a, b);
    float h = clamp(0.5 - 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) + k * h * (1.0 - h);
}

mat3 rotateX(float a) { float c = cos(a), s = sin(a); return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c); }
mat3 rotateY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c); }
mat3 rotateZ(float a) { float c = cos(a), s = sin(a); return mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0); }

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdSphere(vec3 p, float s) { return length(p) - s; }

float sdTetrahedron(vec3 p, float r) {
    vec3 q = abs(p);
    float d = max(q.x + q.y, max(q.y + q.z, q.z + q.x));
    return (d - r) / sqrt(3.0);
}

float sdDodecahedron(vec3 p, float r) {
    float phi = 1.6180339887;
    vec3 n = normalize(vec3(phi, 1.0, 0.0));
    p = abs(p);
    float d = dot(p, n) - r;
    d = max(d, dot(p.yxz, n) - r);
    d = max(d, dot(p.zyx, n) - r);
    d = max(d, max(p.x, max(p.y, p.z)) * (1.0 / sqrt(3.0)) - r);
    return d;
}

float sdTruncatedIcosahedron(vec3 p, float r) {
    float phi = 1.6180339887;
    vec3 n1 = normalize(vec3(0.0, 1.0, phi));
    vec3 n2 = normalize(vec3(1.0, phi, 0.0));
    vec3 n3 = normalize(vec3(phi, 0.0, 1.0));
    p = abs(p);
    float d = dot(p, n1);
    d = max(d, dot(p, n2));
    d = max(d, dot(p, n3));
    d = max(d, dot(p, vec3(1.0 / sqrt(3.0))) - r * 1.1547);
    return d - r;
}

// Mirror x and y, mirror across the 60-degree line in xy (six-way kaleidoscope), mirror z. Smoothness rounds the creases.
vec3 hexFold(vec3 z, float k) {
    z.x = k > 0.0001 ? smax(z.x, -z.x, k) : abs(z.x);
    z.y = k > 0.0001 ? smax(z.y, -z.y, k) : abs(z.y);
    vec2 nHex = vec2(0.86602540378, -0.5);
    float t = dot(z.xy, nHex);
    z.xy -= 2.0 * smin(0.0, t, k) * nHex;
    z.z = k > 0.0001 ? smax(z.z, -z.z, k) : abs(z.z);
    return z;
}

float map(vec3 p, out float trap) {
    vec3 z = p;
    z.z -= gFlight;
    if (RepeatSpacing_ > 0.0) {
        // The original wrote the y period as mix(1e10, spacing, Repeat3D): at 0 that quantised y to steps of about
        // 1000 (stripes), at 1 it cancelled to a period of 0 (NaN, a blank frame). Repeat y only when asked instead.
        // Across the view the copies sit half a cell off the axis, so the camera flies down a corridor between them
        // instead of starting inside one.
        float rs = max(RepeatSpacing_, 0.5);
        z.x = mod(z.x, rs) - rs * 0.5;
        z.z = mod(z.z + rs * 0.5, rs) - rs * 0.5;
        if (Repeat3D >= 0.5) z.y = mod(z.y, rs) - rs * 0.5;
    }

    float dr = 1.0;
    trap = 1e10;
    int maxIt = int(Iterations + 0.5);
    int trapMode = int(TrapMode + 0.5);
    for (int i = 0; i < 8; i++) {
        if (i >= maxIt) break;
        z = gFoldRot * z;              // rotate before folding, on every level
        z = hexFold(z, Smoothness);
        z -= gFoldOff;
        z *= Scale_;
        dr *= Scale_;

        float distVal;
        if (trapMode == 0) distVal = dot(z, z);                                   // origin
        else if (trapMode == 1) distVal = min(abs(z.x), min(abs(z.y), abs(z.z)));  // nearest fold plane
        else if (trapMode == 2) distVal = abs(z.x);
        else if (trapMode == 3) distVal = abs(z.y);
        else distVal = abs(z.z);
        trap = min(trap, distVal);
    }

    // Shape Morph walks cube -> sphere -> tetrahedron -> dodecahedron -> truncated icosahedron.
    float m = clamp(ShapeMorph, 0.0, 1.0) * 4.0;
    float d;
    if (m < 1.0) d = mix(sdBox(z, vec3(1.0)), sdSphere(z, 1.0), m);
    else if (m < 2.0) d = mix(sdSphere(z, 1.0), sdTetrahedron(z, 1.0), m - 1.0);
    else if (m < 3.0) d = mix(sdTetrahedron(z, 1.0), sdDodecahedron(z, 1.0), m - 2.0);
    else d = mix(sdDodecahedron(z, 1.0), sdTruncatedIcosahedron(z, 1.0), clamp(m - 3.0, 0.0, 1.0));
    return d / dr;
}

vec3 getNormal(vec3 p) {
    float trap;
    vec2 eps = vec2(0.001, 0.0);
    return normalize(vec3(
        map(p + eps.xyy, trap) - map(p - eps.xyy, trap),
        map(p + eps.yxy, trap) - map(p - eps.yxy, trap),
        map(p + eps.yyx, trap) - map(p - eps.yyx, trap)
    ));
}

// Cosine palette (Inigo Quilez) with the original constants: red cycles twice, green once, blue holds.
vec3 palette(float t) {
    return vec3(0.5) + vec3(0.5) * cos(TAU * (vec3(2.0, 1.0, 0.0) * t + vec3(0.5, 0.20, 0.25)));
}

vec3 lerp3(vec3 a, vec3 b, float t) { return a + (b - a) * t; }

// Black is transparent: alpha follows the brightest channel, so a deck's BG layer shows wherever this source is dark.
vec4 blackToAlpha(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float a = max(c.r, max(c.g, c.b));
    return vec4(c / max(a, 1e-4), a);
}

void main() {
    vec2 uv = isf_FragNormCoord * 2.0 - 1.0;
    uv.x *= RENDERSIZE.x / RENDERSIZE.y;

    // Each base shape gets the fold angles that suit its symmetry, so Fold Angle 0 already means a twisted lattice;
    // Fold Angle and Tumble add on top. Tumble turns the three axes at unrelated rates, so the storm never repeats.
    float m = clamp(ShapeMorph, 0.0, 1.0) * 4.0;
    vec3 cubeA = vec3(0.0, 0.7854, 0.0);
    vec3 tetraA = vec3(0.0, 1.231, 2.0944);
    vec3 dodecA = vec3(2.0344, 0.0, 2.4119);
    vec3 sphereA = lerp3(cubeA, tetraA, 0.5);
    vec3 baseA;
    if (m < 1.0) baseA = lerp3(cubeA, sphereA, m);
    else if (m < 2.0) baseA = lerp3(sphereA, tetraA, m - 1.0);
    else if (m < 3.0) baseA = lerp3(tetraA, dodecA, m - 2.0);
    else baseA = dodecA;
    vec3 tumble = vec3(mod(TumblePhase * 0.5, TAU), mod(TumblePhase * 0.5 * 0.618034, TAU), mod(TumblePhase * 0.5 * 0.381966, TAU));
    vec3 a = baseA + vec3(FoldAngleX, FoldAngleY, FoldAngleZ) + tumble;
    gFoldRot = rotateX(a.x) * rotateY(a.y) * rotateZ(a.z);
    gFoldOff = FoldOff_;
    gFlight = (RepeatSpacing_ > 0.0) ? mod(FlightPhase * 5.0, max(RepeatSpacing_, 0.5)) : FlightPhase * 5.0;

    vec3 ro = vec3(0.0, 0.0, -3.0 / Zoom_);
    vec3 rd = normalize(vec3(uv, 1.2));
    mat3 camRot = rotateZ(RotateZ) * rotateY(RotateY) * rotateX(RotateX);
    ro = camRot * ro;
    rd = camRot * rd;

    float t = 0.0;
    float trap = 0.0;
    int stepsTaken = 0;
    bool hit = false;
    for (int i = 0; i < MAX_RAY_STEPS; i++) {
        float d = map(ro + rd * t, trap);
        if (d < MIN_DIST) { hit = true; break; }
        t += d;
        if (t > MAX_DIST) break;
        stepsTaken = i;
    }

    vec3 color = vec3(0.0);
    if (hit) {
        vec3 p = ro + rd * t;
        vec3 normal = getNormal(p);
        vec3 lightDir = normalize(vec3(1.0, 1.0, -1.0));
        float diff = max(dot(normal, lightDir), 0.0);
        float rim = pow(1.0 + dot(normal, rd), 4.0);

        float colOrbit = fract(log(trap + 1.0) * 0.5 + ColorShift);
        float colNormal = fract(dot(normal, vec3(0.5, 0.3, 0.8)) * NormalFrequency_ + mod(TIME * 0.2, 1.0) + ColorShift);
        float colVal = mix(colOrbit, colNormal, NormalColoring);

        color = palette(colVal) * (diff * 0.8 + 0.2) + vec3(0.8, 0.9, 1.0) * rim * 0.4;
        // Neon in the creases: grows without bound as the orbit nears its trap.
        if (TrapGlow_ > 0.0) color += palette(colVal + 0.3) * (TrapGlow_ / (0.01 + trap));
    }

    // Step-count glow, linear and added to surfaces too.
    if (Glow > 0.0) {
        float glowFactor = float(stepsTaken) / float(MAX_RAY_STEPS);
        color += palette(fract(ColorShift)) * glowFactor * Glow * 1.5;
    }

    if (!(color.r == color.r) || !(color.g == color.g) || !(color.b == color.b)) color = vec3(0.0);
    gl_FragColor = blackToAlpha(color);
}
