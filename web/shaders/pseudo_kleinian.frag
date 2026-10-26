#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Raymarched Kleinian-style fractal: a periodic mirror fold of space (a slab wrap) followed by repeated sphere inversion, with orbit-trap colouring and a fly-through camera.",
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
        { "NAME": "SlabSize", "LABEL": "Slab Size", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "InversionRadius", "LABEL": "Inversion Radius", "TYPE": "float", "DEFAULT": 0.64, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "InversionStrength", "LABEL": "Inversion Strength", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "OffsetX", "LABEL": "Offset X", "TYPE": "float", "DEFAULT": 0.2, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "OffsetY", "LABEL": "Offset Y", "TYPE": "float", "DEFAULT": 0.4, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "OffsetZ", "LABEL": "Offset Z", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "CoreSize", "LABEL": "Core Size", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "FlightSpeed", "LABEL": "Flight Speed", "TYPE": "float", "DEFAULT": 0.25, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.7, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "RotateX", "LABEL": "Rotate X", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateY", "LABEL": "Rotate Y", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateZ", "LABEL": "Rotate Z", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.33, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define SlabSize_ (0.6+SlabSize*0.8)
#define InversionRadius_ (0.6+InversionRadius*0.9)
#define InversionStrength_ (2.0+InversionStrength*10.0)
#define Offset_ (vec3(OffsetX, OffsetY, OffsetZ)*0.5)
#define CoreSize_ (0.3+CoreSize*1.7)
#define Brightness_ (Brightness*2.0)
#define Zoom_ (Zoom<0.5?pow(0.1,1.0-2.0*Zoom):pow(5.0,2.0*Zoom-1.0))

// Quality remaps to the loop caps: 6..12 fold+inversion iterations, 48..120 march steps, 1..4 AO taps.
#define MAX_ITER 12
#define MAX_STEPS 120
#define MAX_AO 4

const float PI = 3.14159265358979323846;

mat3 rotX(float a) { float c = cos(a), s = sin(a); return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c); }
mat3 rotY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c); }
mat3 rotZ(float a) { float c = cos(a), s = sin(a); return mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0); }

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Working values shared by the distance estimator and the colouring (set per pixel in main).
int gIter = 8;
float gSlab = 1.0;
float gR2 = 1.4;
float gKMax = 8.0;
vec3 gOffset = vec3(0.1, 0.2, 0.0);
float gCore = 1.0;
// Orbit traps written by the last map() call: x = closest approach to the origin,
// y = closest approach to the slab walls, z = how many inversions fired.
vec3 gTrap = vec3(0.0);

// Mirror fold into the slab [-b, b], repeated with period 4b (a triangle wave per axis).
// Inside the slab it is the identity; beyond a wall it reflects, so the wrap is an isometry.
vec3 slabWrap(vec3 p, float b) {
    return abs(mod(p - b, 4.0 * b) - 2.0 * b) - b;
}

// Distance estimate. Each iteration folds the position into the slab (an isometry) and inverts it
// in the sphere of radius sqrt(gR2) when it lies inside (a conformal map). An inversion scales
// lengths locally by k = R^2 / |p|^2, so the running product dr of those factors converts the
// distance to a core sphere in the folded space back into a distance in the original space.
float map(vec3 p) {
    float dr = 1.0;
    float trapR = 1e5;
    float trapW = 1e5;
    float inv = 0.0;
    for (int i = 0; i < MAX_ITER; i++) {
        if (i >= gIter) break;
        p = slabWrap(p, gSlab);
        float r2 = dot(p, p);
        float k = clamp(gR2 / max(r2, 1e-4), 1.0, gKMax);
        p *= k;
        dr *= k;
        inv += step(1.0001, k);
        p += gOffset;
        trapR = min(trapR, dot(p, p));
        trapW = min(trapW, min(gSlab - abs(p.x), min(gSlab - abs(p.y), gSlab - abs(p.z))));
    }
    gTrap = vec3(sqrt(trapR), trapW, inv);
    return (length(p) - gCore) / dr;
}

vec3 calcNormal(vec3 p, float e) {
    vec2 h = vec2(e, 0.0);
    return normalize(vec3(
        map(p + h.xyy) - map(p - h.xyy),
        map(p + h.yxy) - map(p - h.yxy),
        map(p + h.yyx) - map(p - h.yyx)
    ) + 1e-9);
}

// Ambient occlusion from a few taps along the normal.
float softAO(vec3 p, vec3 n, int taps, float scale) {
    float occ = 0.0;
    float w = 1.0;
    for (int i = 1; i <= MAX_AO; i++) {
        if (i > taps) break;
        float h = scale * float(i);
        occ += w * max(h - map(p + n * h), 0.0);
        w *= 0.6;
    }
    return clamp(1.0 - occ / (scale * 2.0), 0.0, 1.0);
}

// Orbit-trap palette: t in roughly 0..1 selects the hue; mode picks which trap feeds it (done by the caller).
vec3 palette(float t, float hueOffset, float sat, int mode) {
    if (mode == 3) {
        // Monochrome gold ramp
        return mix(vec3(0.35, 0.18, 0.05), vec3(1.0, 0.85, 0.45), clamp(t, 0.0, 1.0));
    }
    return hsv2rgb(vec3(fract(hueOffset + t), sat, 1.0));
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

    float q = clamp(Quality, 0.0, 1.0);
    gIter = 6 + int(floor(q * 6.0 + 0.5));
    int steps = 48 + int(floor(q * 72.0 + 0.5));
    int aoTaps = 1 + int(floor(q * 3.0 + 0.5));

    gSlab = SlabSize_;
    float radius = InversionRadius_;
    gR2 = radius * radius;
    gKMax = InversionStrength_;
    gOffset = Offset_;
    gCore = CoreSize_;

    // Camera flies down a channel of the periodic structure. The world repeats every 4 slab widths
    // along each axis, so the travel distance is wrapped to keep float precision at long run times.
    float period = 4.0 * gSlab;
    float travel = mod(FlightPhase * 1.5, period);
    vec3 ro = vec3(0.9 * gSlab, 0.9 * gSlab, travel);
    float fov = 1.2 / max(Zoom_, 0.05);
    vec3 rd = normalize(vec3(uv * fov, 1.0));
    rd = rotZ(RotateZ) * rotY(RotateY) * rotX(RotateX) * rd;

    float pixelAngle = fov / RENDERSIZE.y;
    float t = 0.0;
    bool hit = false;
    float stepsUsed = 0.0;
    float glow = 0.0;
    for (int i = 0; i < MAX_STEPS; i++) {
        if (i >= steps) break;
        float d = map(ro + rd * t);
        float eps = max(0.5 * pixelAngle * t, 1e-4);
        glow += exp(-max(d, 0.0) * 6.0 / gSlab) * 0.012;
        if (d < eps) { hit = true; break; }
        t += max(d * 0.8, 1e-4);
        stepsUsed += 1.0;
        if (t > 40.0) break;
    }

    vec3 bg = mix(vec3(0.015, 0.01, 0.03), vec3(0.04, 0.02, 0.06), uv.y + 0.5);
    vec3 col = bg;
    int cMode = clamp(int(floor(ColorMode + 0.5)), 0, 3);

    if (hit) {
        vec3 p = ro + rd * t;
        float eps = max(0.5 * pixelAngle * t, 1e-4);
        vec3 n = calcNormal(p, max(2.0 * eps, 1e-4));
        map(p); // refresh orbit traps at the hit point
        vec3 trap = gTrap;
        float ao = softAO(p, n, aoTaps, 0.04 * gSlab);
        float diff = max(dot(n, normalize(vec3(0.5, 0.8, -0.3))), 0.0);
        float fres = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);

        float tv;
        if (cMode == 0) tv = trap.x * 0.35;                  // distance trap: rings around the inversion centres
        else if (cMode == 1) tv = trap.z * 0.08;             // inversion depth: how deep the orbit went
        else if (cMode == 2) tv = dot(n, vec3(0.3)) + 0.5;   // normal spectrum
        else tv = 1.0 - clamp(trap.y * 0.8, 0.0, 1.0);       // wall trap, gold ramp
        vec3 surf = palette(tv, HueOffset, Saturation, cMode) * Brightness_;

        col = surf * (0.25 + 0.75 * diff) * (0.25 + 0.75 * ao) + vec3(fres * 0.25 * Brightness_) * ao;
        float fog = 1.0 - exp(-t / (10.0 * gSlab));
        col = mix(col, bg, clamp(fog, 0.0, 1.0));
    }

    // Soft glow accumulated from near misses, tinted by the hue offset
    vec3 glowCol = hsv2rgb(vec3(fract(HueOffset + 0.55), Saturation, 1.0));
    col += glowCol * glow * 0.4 * Brightness_;

    col = clamp(col, 0.0, 4.0);
    if (!(col.r == col.r) || !(col.g == col.g) || !(col.b == col.b)) col = bg;
    gl_FragColor = blackToAlpha(col);
}
