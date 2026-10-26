#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Raymarched distance-estimated Mandelbox: box fold, sphere fold and scale, with animatable scale (positive and negative), orbit-trap colouring, ambient occlusion and a camera that glides in and out of the structure.",
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
        { "NAME": "Scale", "LABEL": "Scale", "TYPE": "float", "DEFAULT": 0.25, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "FoldLimit", "LABEL": "Fold Limit", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "MinRadius", "LABEL": "Min Radius", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "MaxRadius", "LABEL": "Max Radius", "TYPE": "float", "DEFAULT": 0.33333, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "FlightSpeed", "LABEL": "Flight Speed", "TYPE": "float", "DEFAULT": 0.25, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 4.0, "STEP": 1 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.75, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "RotateX", "LABEL": "Rotate X", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateY", "LABEL": "Rotate Y", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateZ", "LABEL": "Rotate Z", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
// Scale: 0 -> +/-1.5 jump is deliberate (|scale| < 1.5 collapses the set); the sign picks the positive or negative family.
#define Scale_ (Scale < 0.0 ? -(1.5 + (-Scale) * 2.0) : (1.5 + Scale * 2.0))
#define FoldLimit_ (0.5 + FoldLimit)
#define MinRadius_ (0.1 + MinRadius * 0.8)
#define MaxRadius_ (0.5 + MaxRadius * 1.5)
#define Glow_ (Glow * 2.0)
#define Brightness_ (Brightness * 2.0)
#define Zoom_ (Zoom<0.5?pow(0.1,1.0-2.0*Zoom):pow(5.0,2.0*Zoom-1.0))

// Hard loop caps (the Quality input picks the live counts below these).
#define MAX_MARCH 128
#define MAX_FOLD 12
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

// Hue from a scalar in 0..1 (mode picks how the scalar is stretched around the wheel).
vec3 palette(float t, float hueOffset, float sat, int mode) {
    float spread = (mode == 2) ? 1.0 : ((mode == 4) ? 0.9 : 0.7);
    float h = fract(hueOffset + clamp(t, 0.0, 1.0) * spread);
    return hsv2rgb(vec3(h, sat, 1.0));
}

// Working values shared by the distance estimator (set once in main).
float gScale;
float gFold;
float gMinR2;
float gMaxR2;
int gIter;

// Distance estimate of the Mandelbox at p.
// Iteration: z <- s * sphereFold(boxFold(z)) + p, with the running derivative norm
// dr <- |s| * k * dr + 1, where k is the sphere fold's (conformal) scale factor. DE = |z| / dr.
// trap = (min |z|, min distance to a coordinate plane, fraction of sphere folds that fired).
float de(vec3 p, out vec3 trap) {
    vec3 z = p;
    float dr = 1.0;
    float minLen = 1.0e5;
    float minPlane = 1.0e5;
    float folds = 0.0;
    float aS = abs(gScale);
    for (int i = 0; i < MAX_FOLD; i++) {
        if (i >= gIter) break;
        // Box fold: reflect each coordinate back into [-limit, limit]
        z = clamp(z, -gFold, gFold) * 2.0 - z;
        // Sphere fold: invert inside the max radius, linearly magnify inside the min radius
        float r2 = dot(z, z);
        float k = 1.0;
        if (r2 < gMinR2) {
            k = gMaxR2 / gMinR2;
            folds += 1.0;
        } else if (r2 < gMaxR2) {
            k = gMaxR2 / r2;
            folds += 1.0;
        }
        z *= k;
        dr = dr * aS * k + 1.0;
        z = z * gScale + p;
        float l = length(z);
        minLen = min(minLen, l);
        vec3 az = abs(z);
        minPlane = min(minPlane, min(az.x, min(az.y, az.z)));
        if (l > 40.0) break;
    }
    trap = vec3(minLen, minPlane, folds / float(max(gIter, 1)));
    return length(z) / max(dr, 1.0e-4);
}

float de(vec3 p) {
    vec3 t;
    return de(p, t);
}

// Tetrahedral central difference (4 evaluations)
vec3 calcNormal(vec3 p, float h) {
    const vec3 k0 = vec3(1.0, -1.0, -1.0);
    const vec3 k1 = vec3(-1.0, -1.0, 1.0);
    const vec3 k2 = vec3(-1.0, 1.0, -1.0);
    const vec3 k3 = vec3(1.0, 1.0, 1.0);
    vec3 n = k0 * de(p + k0 * h) + k1 * de(p + k1 * h) + k2 * de(p + k2 * h) + k3 * de(p + k3 * h);
    float l = length(n);
    return l > 1.0e-8 ? n / l : vec3(0.0, 1.0, 0.0);
}

// Soft ambient occlusion from up to MAX_AO distance comparisons along the normal
float softAO(vec3 p, vec3 n, int taps) {
    if (taps <= 0) return 1.0;
    float occ = 0.0;
    float w = 1.0;
    for (int i = 0; i < MAX_AO; i++) {
        if (i >= taps) break;
        float h = 0.03 + 0.09 * float(i);
        float d = de(p + n * h);
        occ += max(h - d, 0.0) * w;
        w *= 0.65;
    }
    return clamp(1.0 - 2.2 * occ, 0.0, 1.0);
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

    // Quality: march steps 48..128, fold iterations 6..12, AO taps 0..4
    float q = clamp(Quality, 0.0, 1.0);
    int marchSteps = int(mix(48.0, 128.0, q) + 0.5);
    gIter = int(mix(6.0, 12.0, q) + 0.5);
    int aoTaps = int(floor(q * 4.99));

    gScale = Scale_;
    gFold = FoldLimit_;
    float minR = MinRadius_;
    float maxR = max(MaxRadius_, minR + 0.05);
    gMinR2 = minR * minR;
    gMaxR2 = maxR * maxR;

    // Camera: looks at the origin from a distance that glides in and out but stays outside the set
    float fov = 1.2 / max(Zoom_, 0.05);
    float phase = FlightPhase * 0.4;
    // Approximate half-extent of the set, so the framing follows Scale and Fold Limit.
    // Measured by ray-marching the DE: the positive family shrinks as Scale grows, while the
    // negative family stays about the same size (~2 * sqrt(3) * Fold) at every Scale, so its framing must not follow |s|.
    float bound = (gScale > 0.0) ? gFold * (gScale + 1.0) / max(gScale - 1.0, 0.5) * 2.3 : gFold * 2.3;
    float camDist = bound * (2.1 + 0.7 * cos(phase));
    vec3 rd = normalize(vec3(uv * fov, 1.0));

    // Zero rotation looks straight down a coordinate axis, i.e. at the middle of a face of the box,
    // rolled 45 degrees about the view axis so that face reads as a square rather than a diamond
    mat3 rot = rotZ(RotateZ) * rotY(RotateY) * rotX(RotateX) * rotZ(0.78539816);
    rd = rot * rd;
    // Keep the camera in open space: the path never goes nearer than 1.4 x the estimated extent,
    // and as a safeguard the distance estimate at the camera pushes it outward (a few steps, continuous in camDist).
    vec3 camDir = rot * vec3(0.0, 0.0, -1.0);
    float clearance = 0.1 * bound;
    for (int i = 0; i < 6; i++) {
        float dc = de(camDir * camDist);
        if (dc >= clearance) break;
        camDist += clearance - dc + 0.02 * bound;
    }
    vec3 ro = camDir * camDist;

    float tMax = camDist + 2.5 * bound + 1.0;
    float t = 0.0;
    float hit = -1.0;
    int used = 0;
    for (int i = 0; i < MAX_MARCH; i++) {
        if (i >= marchSteps) break;
        used = i;
        vec3 p = ro + rd * t;
        float d = de(p);
        float eps = max(2.0e-4, t * fov / RENDERSIZE.y * 0.6);
        if (d < eps) {
            hit = t;
            break;
        }
        t += max(d * 0.8, eps * 0.5);
        if (t > tMax) break;
        // Out of steps while still near the surface (a grazing ray): shade it as a hit rather than leave a hole
        if (i == marchSteps - 1) hit = t;
    }
    float stepRatio = float(used) / float(max(marchSteps - 1, 1));

    int cMode = clamp(int(floor(ColorMode + 0.5)), 0, 4);
    vec3 bgTop = hsv2rgb(vec3(fract(HueOffset + 0.62), 0.6 * Saturation, 0.10 * Brightness_ + 0.02));
    vec3 bgBot = hsv2rgb(vec3(fract(HueOffset + 0.55), 0.5 * Saturation, 0.03));
    vec3 col = mix(bgBot, bgTop, clamp(uv.y + 0.5, 0.0, 1.0));

    if (hit >= 0.0) {
        vec3 p = ro + rd * hit;
        float eps = max(2.0e-4, hit * fov / RENDERSIZE.y * 0.6);
        vec3 n = calcNormal(p, max(eps, 0.0015));
        vec3 trap;
        de(p, trap);

        vec3 L = rot * normalize(vec3(0.5, 0.8, -0.4));   // camera-relative: the lit side always faces the viewer
        float diff = max(dot(n, L), 0.0);
        float back = 0.5 + 0.5 * dot(n, -L);
        float fres = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 3.0);
        float ao = softAO(p, n, aoTaps);
        // Cheap occlusion fallback from the march step count (more steps = tighter crevice)
        float crev = 1.0 - 0.35 * stepRatio;

        float tv;
        if (cMode == 0) {
            tv = 1.0 - clamp(trap.x * 0.35, 0.0, 1.0);
        } else if (cMode == 1) {
            tv = clamp(trap.y * 0.8, 0.0, 1.0);
        } else if (cMode == 2) {
            tv = trap.z;
        } else if (cMode == 3) {
            tv = dot(n, vec3(0.3333)) * 0.5 + 0.5;
        } else {
            tv = clamp(hit / tMax, 0.0, 1.0);
        }
        vec3 base = palette(tv, HueOffset, Saturation, cMode);
        // Value varies slightly with the other trap so the surface keeps depth
        float shade = 0.55 + 0.45 * clamp(trap.y * 1.2, 0.0, 1.0);

        vec3 lit = base * Brightness_ * shade * (0.18 + 0.85 * diff + 0.25 * back) * ao * crev;
        lit += base * fres * 0.5 * Brightness_;
        col = lit;

        float fog = smoothstep(camDist * 0.4, camDist + 2.0 * bound, hit);
        col = mix(col, bgTop, fog * 0.8);
    }

    // Glow from near-miss march steps: halos the silhouette and the crevices
    vec3 glowCol = hsv2rgb(vec3(fract(HueOffset + 0.12), Saturation, 1.0));
    col += glowCol * pow(stepRatio, 3.0) * Glow_ * 0.35;

    col = max(col, vec3(0.0));
    col = col / (1.0 + col * 0.35);
    if (any(isnan(col)) || any(isinf(col))) col = vec3(0.0);
    gl_FragColor = blackToAlpha(col);
}
