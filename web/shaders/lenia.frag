#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Lenia: a continuous cellular automaton on a persistent grid. A smooth ring kernel and a bell-shaped growth rule let soft, living blobs glide, split and merge.",
    "CREDIT": "Liquid LSD",
    "LSD_COST": 2,
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Organic",
        "Simulation"
    ],
    "INPUTS": [
        { "NAME": "GrowthCenter", "LABEL": "Growth Center", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "GrowthWidth", "LABEL": "Growth Width", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Radius", "LABEL": "Kernel Radius", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SimSpeed", "LABEL": "Sim Speed", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SeedDensity", "LABEL": "Seed Density", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Reseed", "LABEL": "Reseed", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Relief", "LABEL": "Relief", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.8, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ],
    "PASSES": [
        { "TARGET": "lnA", "PERSISTENT": true, "FLOAT": true, "WIDTH": "256", "HEIGHT": "256" },
        { "TARGET": "lnB", "PERSISTENT": true, "FLOAT": true, "WIDTH": "256", "HEIGHT": "256" },
        { "TARGET": "lnC", "PERSISTENT": true, "FLOAT": true, "WIDTH": "256", "HEIGHT": "256" },
        { "TARGET": "lnD", "PERSISTENT": true, "FLOAT": true, "WIDTH": "256", "HEIGHT": "256" },
        { }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define GrowthCenter_ (0.08+GrowthCenter*0.14)
#define GrowthWidth_ (0.008+GrowthWidth*0.020)
#define Radius_ (5.0+Radius*8.0)
#define Relief_ (Relief*4.0)
#define Brightness_ (Brightness*2.0)
#define Zoom_ (Zoom<0.5?pow(0.1,1.0-2.0*Zoom):pow(5.0,2.0*Zoom-1.0))

// Lenia (Chan, 2019, "Lenia: Biology of Artificial Life"), single channel, single ring kernel, on a fixed
// 256x256 torus:
//   U(x)  = sum_n K(|n|/R) A(x+n) / sum_n K(|n|/R)         K(r) = exp(4 - 1/(r(1-r)))  for 0<r<1  (smooth bump)
//   G(U)  = 2 exp(-(U-mu)^2 / (2 sigma^2)) - 1
//   A'    = clamp(A + dt G(U), 0, 1)
// Four chained passes (lnA <- lnD, lnB <- lnA, lnC <- lnB, lnD <- lnC) = 4 steps per frame, dt = 0.05..0.1 each.
// Cost: up to a 27x27 tap window per pixel per pass (R <= 13), ~2900 taps/pixel/frame on a 65k-pixel grid.
// An empty grid (extinction at high Growth Center) is detected and reseeded automatically.
// State layout: R = A, G = reseed latch, B unused, A = 1 once seeded (all-zero means "empty", e.g. after a resize).
// Steps are per frame, not TIMEDELTA-scaled.

const int RMAX = 13;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

// Seed: patches of smooth random texture on a quiet background; Lenia organises them into creatures.
float seedA(vec2 cell, float salt) {
    float blobMask = smoothstep(0.78 - SeedDensity * 0.22, 0.86 - SeedDensity * 0.22, vnoise(cell * 0.045 + salt * 13.7));
    float tex = 0.5 + 0.5 * vnoise(cell * 0.35 + salt * 5.1) * vnoise(cell * 0.11 + salt);
    return clamp(blobMask * tex, 0.0, 1.0);
}

vec4 simStep(sampler2D prev) {
    vec2 px = 1.0 / RENDERSIZE;
    vec2 uv = gl_FragCoord.xy * px;
    vec4 c = texture(prev, uv);

    float armed = step(0.5, Reseed);
    // Extinction: with the growth peak above anything the field reaches, every cell decays to exactly 0 and
    // U = 0 can never grow again, so the picture stays black even after Growth Center comes back down.
    // A coarse 16x16 lattice (16 cells apart, finer than any creature) detects an empty grid; every pixel
    // sees the same lattice, so the whole grid reseeds together.
    float alive = 0.0;
    for (int j = 0; j < 16; j++)
        for (int i = 0; i < 16; i++)
            alive = max(alive, texture(prev, (vec2(float(i), float(j)) * 16.0 + 8.0) * px).r);
    if (c.a < 0.5 || alive < 0.002 || (armed > 0.5 && c.g < 0.5)) {
        float salt = fract(float(FRAMEINDEX) * 0.0137 + TIME * 0.31);
        return vec4(seedA(gl_FragCoord.xy, salt), armed, 0.0, 1.0);
    }

    float R = Radius_;
    float sum = 0.0, wsum = 0.0;
    for (int j = -RMAX; j <= RMAX; j++) {
        for (int i = -RMAX; i <= RMAX; i++) {
            float r = length(vec2(float(i), float(j))) / R;
            if (r <= 0.0 || r >= 1.0) continue;
            float w = exp(4.0 - 1.0 / (r * (1.0 - r)));
            sum += w * texture(prev, fract(uv + vec2(float(i), float(j)) * px)).r;
            wsum += w;
        }
    }
    float U = sum / max(wsum, 1e-6);
    float g = 2.0 * exp(-((U - GrowthCenter_) * (U - GrowthCenter_)) / (2.0 * GrowthWidth_ * GrowthWidth_)) - 1.0;
    float dt = 0.03 + SimSpeed * 0.07;
    float a = clamp(c.r + dt * g, 0.0, 1.0);
    if (!(a == a)) a = 0.0;
    return vec4(a, armed, 0.0, 1.0);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Black is transparent: alpha follows the brightest channel (the renderer stores colour x alpha, so the
// stored pixel is the colour itself and a deck's BG layer shows through wherever this source is dark).
vec4 blackToAlpha(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float a = max(c.r, max(c.g, c.b));
    return vec4(c / max(a, 1e-4), a);
}

void main() {
    if (PASSINDEX == 0) { gl_FragColor = simStep(lnD); return; }
    if (PASSINDEX == 1) { gl_FragColor = simStep(lnA); return; }
    if (PASSINDEX == 2) { gl_FragColor = simStep(lnB); return; }
    if (PASSINDEX == 3) { gl_FragColor = simStep(lnC); return; }

    // Display: lnD was written last. The grid spans the output width and crops vertically; Zoom < 1 tiles it.
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 p = (isf_FragNormCoord - 0.5) * vec2(1.0, 1.0 / aspect) / Zoom_ + 0.5;
    vec2 uv = fract(p);
    vec2 e = vec2(1.0 / 256.0, 0.0);
    float a = texture(lnD, uv).r;
    float hx = texture(lnD, fract(uv + e.xy)).r - texture(lnD, fract(uv - e.xy)).r;
    float hy = texture(lnD, fract(uv + e.yx)).r - texture(lnD, fract(uv - e.yx)).r;
    vec3 n = normalize(vec3(-hx * Relief_ * 6.0, -hy * Relief_ * 6.0, 1.0));
    float diff = max(dot(n, normalize(vec3(0.4, 0.6, 0.7))), 0.0);
    float spec = pow(max(dot(reflect(vec3(-0.4, -0.6, -0.7), n), vec3(0.0, 0.0, 1.0)), 0.0), 20.0);

    int cMode = clamp(int(floor(ColorMode + 0.5)), 0, 3);
    vec3 col;
    if (cMode == 0) {
        col = hsv2rgb(vec3(fract(HueOffset + 0.62 - 0.55 * a), Saturation, Brightness_ * (0.12 + 0.88 * a)));
    } else if (cMode == 1) {
        col = mix(vec3(0.01, 0.0, 0.05), hsv2rgb(vec3(fract(HueOffset + 0.05), Saturation, Brightness_)), a);
    } else if (cMode == 2) {
        col = hsv2rgb(vec3(fract(HueOffset + n.x * 0.6 + n.y * 0.4 + a * 0.3), Saturation, Brightness_ * (0.15 + 0.85 * a)));
    } else {
        col = vec3(Brightness_ * (0.04 + 0.96 * a));
    }
    col = col * (0.35 + 0.65 * diff) + vec3(spec * 0.5 * min(Relief_, 1.0)) * a;
    gl_FragColor = blackToAlpha(col);
}
