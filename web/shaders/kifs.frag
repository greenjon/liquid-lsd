#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Raymarched kaleidoscopic IFS fractal: iterated abs/plane folds, per-iteration rotation, scale and offset. Menger, Sierpinski, octahedral and mixed constructions, with orbit-trap colouring and an endless self-similar zoom.",
    "CREDIT": "Liquid LSD",
    "LSD_COST": 3,
    "ISFVSN": "2.0",
    "is3D": true,
    "CATEGORIES": [
        "Generator",
        "3D",
        "Fractal"
    ],
    "LSD_PHASES": {"MorphPhase": ["MorphSpeed"], "FlightPhase": ["FlightSpeed"]},
    "INPUTS": [
        { "NAME": "SurfaceType", "LABEL": "Fractal Type", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "Scale", "LABEL": "Scale", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Offset", "LABEL": "Offset", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "TwistX", "LABEL": "Twist X", "TYPE": "float", "DEFAULT": 0.1, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "TwistY", "LABEL": "Twist Y", "TYPE": "float", "DEFAULT": 0.15, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "TwistZ", "LABEL": "Twist Z", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "MorphSpeed", "LABEL": "Morph Speed", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "FlightSpeed", "LABEL": "Flight Speed", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 4.0, "STEP": 1 },
        { "NAME": "ColorSpread", "LABEL": "Color Spread", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.58, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "RotateX", "LABEL": "Rotate X", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateY", "LABEL": "Rotate Y", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateZ", "LABEL": "Rotate Z", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
// Scale and Offset are multipliers on the nominal value of the chosen construction (0.5 = nominal).
#define ScaleMul_ pow(1.5, 2.0*Scale-1.0)
#define OffsetMul_ pow(1.5, 2.0*Offset-1.0)
#define TwistX_ (TwistX*0.6)
#define TwistY_ (TwistY*0.6)
#define TwistZ_ (TwistZ*0.6)
#define Glow_ (Glow*1.5)
#define Brightness_ (Brightness*2.0)
#define Zoom_ (Zoom<0.5?pow(0.1,1.0-2.0*Zoom):pow(5.0,2.0*Zoom-1.0))

const float PI = 3.14159265358979323846;

// Hard loop caps. The Quality input picks the live counts below these.
const int MAX_STEPS = 112;
const int MAX_FOLDS = 12;

mat3 rotX(float a) { float c = cos(a), s = sin(a); return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c); }
mat3 rotY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c); }
mat3 rotZ(float a) { float c = cos(a), s = sin(a); return mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0); }

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Per-frame fractal state (set once in main).
mat3 gRot;       // rotation applied before every fold
float gScale;    // scale per iteration
float gOff;      // offset multiplier
int gType;       // construction: 0 Menger, 1 Sierpinski, 2 octahedral, 3 mixed
int gIter;       // live fold iterations
// Orbit traps written by map(): x = min |p|^2, y = min axis-plane distance, z = iteration of the min |p|^2.
vec3 gTrap;

// Sort |p| into the fundamental domain x >= y >= z >= 0 (the 6 axis-permutation planes plus the 3 axis planes).
vec3 sortFold(vec3 p) {
    p = abs(p);
    if (p.x < p.y) p.xy = p.yx;
    if (p.x < p.z) p.xz = p.zx;
    if (p.y < p.z) p.yz = p.zy;
    return p;
}

// Reflect across the planes x+y=0, x+z=0, y+z=0 (tetrahedral symmetry group).
vec3 tetraFold(vec3 p) {
    if (p.x + p.y < 0.0) p.xy = -p.yx;
    if (p.x + p.z < 0.0) p.xz = -p.zx;
    if (p.y + p.z < 0.0) p.yz = -p.zy;
    return p;
}

// Distance estimate of the iterated structure. The base shape is measured in the final frame and divided by the
// accumulated scale s^n, the standard bound for a similarity IFS.
float map(vec3 p) {
    float sc = 1.0;
    float s = gScale;
    float k = gOff;
    gTrap = vec3(1e6, 1e6, 0.0);
    p = clamp(p, vec3(-1e3), vec3(1e3));
    for (int i = 0; i < MAX_FOLDS; i++) {
        if (i >= gIter) break;
        if (i > 0) p = gRot * p;  // first level stays axis-aligned so Twist/Morph reshape the inside without turning the object
        int t = gType;
        if (t == 3) t = ((i - 2 * (i / 2)) == 0) ? 0 : 1;
        float zlim = 0.0;
        vec3 c;
        if (t == 0) {
            // Menger: fold into x >= y >= z, scale about the cube corner, then wrap z so the edge cubes (1,1,0) stay.
            p = sortFold(p);
            c = vec3(k * (s - 1.0));
            p = p * s - c;
            zlim = -0.5 * c.z;
            if (p.z < zlim) p.z += c.z;
        } else if (t == 1) {
            // Sierpinski tetrahedron: tetrahedral plane folds, scale about the vertex (k,k,k).
            p = tetraFold(p);
            c = vec3(k * (s - 1.0));
            p = p * s - c;
        } else {
            // Octahedral: sorted abs fold, scale about the vertex (k,0,0).
            p = sortFold(p);
            c = vec3(k * (s - 1.0), 0.0, 0.0);
            p = p * s - c;
        }
        sc *= s;
        float r2 = dot(p, p);
        if (r2 < gTrap.x) { gTrap.x = r2; gTrap.z = float(i + 1); }
        gTrap.y = min(gTrap.y, min(abs(p.x), min(abs(p.y), abs(p.z))));
    }
    float d;
    int bt = gType;
    if (bt == 0 || bt == 3) {
        // Box of half-size k.
        vec3 q = abs(p) - vec3(k);
        d = length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
    } else if (bt == 1) {
        // Tetrahedron with vertices (+-k, +-k, +-k), even sign parity: max of the four face-plane distances.
        float m = max(max(dot(p, vec3(-1.0, -1.0, -1.0)), dot(p, vec3(-1.0, 1.0, 1.0))),
                      max(dot(p, vec3(1.0, -1.0, 1.0)), dot(p, vec3(1.0, 1.0, -1.0))));
        d = (m - k) * 0.57735027;
    } else {
        // Octahedron |x|+|y|+|z| <= k.
        d = (abs(p.x) + abs(p.y) + abs(p.z) - k) * 0.57735027;
    }
    return d / sc;
}

vec3 calcNormal(vec3 p, float h) {
    // Tetrahedral central difference: 4 samples.
    const vec2 e = vec2(1.0, -1.0);
    return normalize(e.xyy * map(p + e.xyy * h) + e.yyx * map(p + e.yyx * h) +
                     e.yxy * map(p + e.yxy * h) + e.xxx * map(p + e.xxx * h));
}

// Cheap ambient occlusion: at most 4 taps along the normal. Tap count comes from Quality.
float softAO(vec3 p, vec3 n, int taps, float scale) {
    float occ = 0.0;
    float w = 1.0;
    for (int i = 1; i <= 4; i++) {
        if (i > taps) break;
        float h = scale * float(i) * float(i) * 0.5;
        float d = map(p + n * h);
        occ += w * max(h - d, 0.0) / h;
        w *= 0.6;
    }
    return clamp(1.0 - occ * 0.8, 0.0, 1.0);
}

// Colour ramp. mode: 0 and 3 spectrum, 1 complementary two-tone, 2 banded, 4 gold.
vec3 palette(float t, float hueOffset, float sat, int mode) {
    t = clamp(t, 0.0, 1.0);
    if (mode == 1) {
        return hsv2rgb(vec3(fract(hueOffset + 0.5 * smoothstep(0.0, 1.0, t)), sat, 1.0));
    } else if (mode == 2) {
        return hsv2rgb(vec3(fract(hueOffset + floor(t * 6.0) / 6.0 * 0.8), sat, 0.55 + 0.45 * fract(t * 6.0)));
    } else if (mode == 4) {
        return mix(vec3(0.55, 0.32, 0.08), vec3(1.0, 0.9, 0.55), t) * (0.5 + 0.5 * sat);
    }
    return hsv2rgb(vec3(fract(hueOffset + t * 0.7), sat, 1.0));
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

    // Quality -> live loop counts (all under the hard caps) and AO taps.
    float q = clamp(Quality, 0.0, 1.0);
    gIter = clamp(int(floor(5.0 + q * 7.0 + 0.5)), 5, MAX_FOLDS);
    int steps = clamp(int(floor(48.0 + q * 64.0)), 48, MAX_STEPS);
    int aoTaps = q < 0.2 ? 0 : (q < 0.6 ? 2 : 4);

    gType = clamp(int(floor(SurfaceType + 0.5)), 0, 3);
    float nominal = (gType == 0) ? 3.0 : 2.0;
    gScale = max(nominal * ScaleMul_, 1.2);
    gOff = clamp(OffsetMul_, 0.3, 2.0);

    // Per-level rotation (skipped on the first level, see map()): static twist plus a slow time-driven morph.
    float m = MorphPhase * 0.3;
    gRot = rotZ(TwistZ_ + m * 0.43) * rotY(TwistY_ + m * 0.7) * rotX(TwistX_ + m);

    // Camera starts outside the fractal looking straight at a face (a cube face for Menger/mixed, a triangular face for the
    // tetrahedron and octahedron). Flight dollies toward fp, a vertex of that face, by powers of the scale, so the path stays
    // outside the shape.
    vec3 fp;
    vec3 face;     // unit direction from the centre to the camera
    float d0;
    if (gType == 2) { fp = vec3(gOff, 0.0, 0.0); face = normalize(vec3(1.0)); d0 = 2.4; }
    else if (gType == 1) { fp = gOff * vec3(1.0, -1.0, -1.0); face = -normalize(vec3(1.0)); d0 = 4.0; }
    else { fp = vec3(gOff); face = vec3(0.0, 0.0, 1.0); d0 = 2.9; }
    vec3 ro0 = face * d0 * max(gOff, 1.0);
    float u = FlightPhase * 0.5;
    float flightK = pow(gScale, -fract(u));
    // The view direction is fixed (it looks at the origin from the nominal start), so the dolly toward fp is self-similar.
    vec3 fwd = -face;
    vec3 rgt = normalize(cross(fwd, vec3(0.0, 1.0, 0.0)));
    vec3 upv = cross(rgt, fwd);
    float fov = 1.1 / max(Zoom_, 0.05);
    vec3 rd = normalize(rgt * uv.x * fov + upv * uv.y * fov + fwd);
    vec3 ro = fp + (ro0 - fp) * flightK;

    // User rotation of the whole camera rig about the origin (keeps the dolly self-similar).
    mat3 rot = rotZ(RotateZ) * rotY(RotateY) * rotX(RotateX);
    ro = rot * ro;
    rd = rot * rd;

    // Bounding sphere around the origin: rays that miss it skip the march.
    float rb = 1.75 * max(gOff, 1.0) + 0.1;
    float bq = dot(ro, rd);
    float bc = dot(ro, ro) - rb * rb;
    float disc = bq * bq - bc;
    float t = 0.0;
    float tMax = 0.0;
    bool hit = false;
    float pixAngle = fov / RENDERSIZE.y;
    float d = 1.0;
    float stepsUsed = 0.0;
    if (disc > 0.0) {
        float sq = sqrt(disc);
        t = max(-bq - sq, 0.0);
        tMax = -bq + sq;
        for (int i = 0; i < MAX_STEPS; i++) {
            if (i >= steps) break;
            d = map(ro + rd * t);
            float eps = max(t * pixAngle * 0.5, 1e-5);
            stepsUsed = float(i);
            if (d < eps) { hit = true; break; }
            t += d * 0.8;
            if (t > tMax) break;
        }
        if (!hit && t <= tMax && d < t * pixAngle * 4.0) hit = true;
    }

    vec3 bg = hsv2rgb(vec3(fract(HueOffset + 0.55), 0.6, 0.03 + 0.07 * (0.5 - uv.y)));
    vec3 col = bg;
    vec3 glowCol = hsv2rgb(vec3(fract(HueOffset + 0.12), max(Saturation * 0.8, 0.0), 1.0));

    if (hit) {
        vec3 p = ro + rd * t;
        float nh = max(t * pixAngle, 1e-5);
        vec3 n = calcNormal(p, nh);
        map(p); // refresh the orbit traps at the hit point
        vec3 trap = gTrap;
        float aoScale = max(nh * 6.0, 0.004);
        float ao = (aoTaps > 0) ? softAO(p, n, aoTaps, aoScale) : 1.0;
        float stepAO = 1.0 - clamp(stepsUsed / float(steps), 0.0, 1.0);
        ao *= 0.5 + 0.5 * stepAO;

        int cMode = clamp(int(floor(ColorMode + 0.5)), 0, 4);
        float tv;
        if (cMode == 0) tv = clamp(sqrt(max(trap.x, 0.0)) * 0.35, 0.0, 1.0);
        else if (cMode == 1) tv = clamp(trap.y * 2.0, 0.0, 1.0);
        else if (cMode == 2) tv = clamp(trap.z / float(max(gIter, 1)), 0.0, 1.0);
        else if (cMode == 3) tv = clamp(dot(n, vec3(0.4, 0.5, 0.3)) + 0.5, 0.0, 1.0);
        else tv = clamp(sqrt(max(trap.x, 0.0)) * 0.35, 0.0, 1.0);
        // Folding makes every copy of a detail read the same, so shift the hue by things the folds do not erase: which
        // fold level the orbit came closest at, where the point sits in the world, and which way the surface faces.
        float spreadShift = 0.0;
        if (cMode != 4) {
            spreadShift = ColorSpread * (0.5 * trap.z / float(max(gIter, 1))
                                         + 0.35 * dot(p, vec3(0.45, 0.7, 0.35))
                                         + 0.2 * dot(n, vec3(0.6, -0.3, 0.5)));
        }
        vec3 base = palette(tv, HueOffset + spreadShift, Saturation, cMode) * Brightness_;

        // Camera-relative lights (built on the view basis, so every start face is lit the same way)
        vec3 ldir = normalize(rot * (rgt * 0.6 + upv * 0.8 - fwd * 0.4));
        vec3 ldir2 = normalize(rot * (-rgt * 0.7 - upv * 0.2 + fwd * 0.5));
        float diff = max(dot(n, ldir), 0.0);
        float fill = max(dot(n, ldir2), 0.0);
        float fres = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 3.0);
        vec3 hv = normalize(ldir - rd);
        float spec = pow(max(dot(n, hv), 0.0), 32.0);

        col = base * (0.18 + 0.85 * diff + 0.25 * fill) * ao;
        col += vec3(spec * 0.25 * ao);
        col += glowCol * fres * 0.35 * Brightness_ * ao;

        float fog = smoothstep(0.0, 14.0, t);
        col = mix(col, bg, fog * 0.6);
    }

    // Step-count glow: rays that graze the structure pick up a soft halo.
    float grazing = clamp(stepsUsed / float(steps), 0.0, 1.0);
    col += glowCol * pow(grazing, 3.0) * 0.5 * Glow_ * (hit ? 0.6 : 1.0);

    // NaN / Inf guard.
    if (!(col.r == col.r) || !(col.g == col.g) || !(col.b == col.b)) col = bg;
    col = clamp(col, vec3(0.0), vec3(8.0));
    gl_FragColor = blackToAlpha(col);
}
