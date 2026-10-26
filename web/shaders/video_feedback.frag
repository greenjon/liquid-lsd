#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Classic video feedback as a generator: a small persistent frame is re-sampled with zoom, rotation and drift, decayed, hue-shifted and re-fed with a few moving seed shapes, giving endless tunnels, spirals and trails.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Feedback",
        "Simulation"
    ],
    "INPUTS": [
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 0.45, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Rotation", "LABEL": "Rotation", "TYPE": "float", "DEFAULT": 0.35, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "HueShift", "LABEL": "Hue Shift", "TYPE": "float", "DEFAULT": 0.2, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Decay", "LABEL": "Decay", "TYPE": "float", "DEFAULT": 0.15, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SeedShape", "LABEL": "Seed Shape", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "SeedSize", "LABEL": "Seed Size", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Drift", "LABEL": "Drift", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ],
    "PASSES": [
        { "TARGET": "vfbState", "PERSISTENT": true, "WIDTH": "512", "HEIGHT": "512" },
        { }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
// All feedback rates are per SECOND and scaled by TIMEDELTA, so the look is frame-rate independent.
#define Zoom_ (Zoom*1.5)                 // log-zoom rate, 1/s (positive = content flows outward)
#define Rotation_ (Rotation*1.5)         // rad/s
#define HueShift_ (HueShift*3.0)         // rad/s of rotation about the grey axis
#define Decay_ (0.4+Decay*4.0)           // exponential fade rate, 1/s
#define SeedSize_ (0.08+SeedSize*0.4)    // shape radius in buffer units (buffer spans -1..1)
#define Drift_ (Drift*0.2)               // sampling offset speed, buffer-uv per second
#define Saturation_ (Saturation*2.0)
#define Brightness_ (Brightness*2.0)

// State buffer: RGB = previous frame (8-bit), A = 1 once written. All-zero (A = 0) means "empty",
// e.g. first frame or after a resize cleared it; that pixel is re-seeded to black and the seed shapes
// repaint it. Nothing depends on FRAMEINDEX.

const float PI = 3.14159265358979323846;
const float TWO_PI = 6.28318530717958647692;
const float T_WRAP = 628.3185307;   // every time frequency below is a multiple of 0.01 rad/s, so mod(TIME, T_WRAP) is seamless
const float RES = 512.0;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Rotate a colour about the grey axis (Rodrigues), i.e. a cheap hue shift that keeps luminance roughly constant.
vec3 hueRotate(vec3 c, float a) {
    float ca = cos(a), sa = sin(a);
    vec3 k = vec3(0.57735027);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}

mat2 rot2(float a) { float c = cos(a), s = sin(a); return mat2(c, s, -s, c); }

// One feedback tap: zero outside the buffer so the edges do not smear.
vec3 tapPrev(vec2 uv) {
    vec2 inside = step(vec2(0.0), uv) * step(uv, vec2(1.0));
    return texture(vfbState, uv).rgb * (inside.x * inside.y);
}

// Seed shape i (0..2) at point p (buffer units). Returns coverage 0..1.
float seedMask(vec2 p, int i, int shape, float tw, float r) {
    float fi = float(i);
    // Lissajous orbit for the centre
    vec2 c = vec2(sin(tw * (0.37 + 0.16 * fi) + fi * 2.1), cos(tw * (0.53 + 0.11 * fi) + fi * 1.3)) * (0.35 + 0.1 * fi);
    float w = 0.014;                   // line half-width
    float aa = 1.5 / RES;
    vec2 q = p - c;
    float d;
    if (shape == 0) {                  // ring
        d = abs(length(q) - r);
    } else if (shape == 1) {           // polygon outline, 3..5 sides, slowly turning
        float n = 3.0 + fi;
        float a = atan(q.y, q.x + 1e-6) + tw * (0.4 + 0.2 * fi);
        float sector = TWO_PI / n;
        float m = mod(a, sector) - 0.5 * sector;
        d = abs(length(q) * cos(m) - r * cos(0.5 * sector));
    } else if (shape == 2) {           // spinning line segment
        float a = tw * (0.5 + 0.23 * fi) + fi * 1.9;
        vec2 dir = vec2(cos(a), sin(a));
        float t = clamp(dot(q, dir), -r * 1.5, r * 1.5);
        d = length(q - dir * t);
    } else {                           // filled soft blob
        d = max(length(q) - r * 0.5, 0.0);
        w = 0.03;
    }
    return 1.0 - smoothstep(w - aa, w + aa, d);
}

vec4 feedbackPass() {
    vec2 frag = gl_FragCoord.xy;
    vec2 uv = frag / RES;
    vec2 p = uv * 2.0 - 1.0;
    float dt = clamp(TIMEDELTA, 0.0, 0.1);
    float tw = mod(TIME, T_WRAP);

    // Empty state (A == 0): start from black; the shapes below paint the first frame.
    bool empty = texture(vfbState, uv).a < 0.5;

    vec3 col = vec3(0.0);
    if (!empty) {
        // Inverse warp: where did this pixel's content come from last frame?
        vec2 drift = Drift_ * vec2(cos(tw * 0.13), sin(tw * 0.17)) * dt;
        vec2 q = rot2(Rotation_ * dt) * (p * exp(-Zoom_ * dt)) + drift * 2.0;
        vec2 suv = q * 0.5 + 0.5;

        int taps = 1 + int(floor(clamp(Quality, 0.0, 1.0) * 3.999));
        vec3 acc;
        if (taps == 1) {
            acc = tapPrev(suv);
        } else {
            float rad = 0.8 / RES;
            acc = vec3(0.0);
            for (int k = 0; k < 4; k++) {
                if (k < taps) {
                    float a = (float(k) / float(taps)) * TWO_PI + 0.7;
                    acc += tapPrev(suv + rad * vec2(cos(a), sin(a)));
                }
            }
            acc /= float(taps);
        }
        col = hueRotate(acc, HueShift_ * dt);
        col *= exp(-Decay_ * dt);
        col = clamp(col, 0.0, 1.0);
    }

    // Re-inject moving seed shapes (screen blend: never exceeds 1).
    int shape = int(floor(clamp(SeedShape, 0.0, 3.0) + 0.5));
    float r = SeedSize_;
    for (int i = 0; i < 3; i++) {
        float m = seedMask(p, i, shape, tw, r);
        float hue = fract(float(i) / 3.0 + tw * (30.0 / T_WRAP));
        vec3 sc = hsv2rgb(vec3(hue, 0.85, 1.0));
        col = 1.0 - (1.0 - col) * (1.0 - sc * m);
    }
    col = clamp(col, 0.0, 1.0);
    if (!(col.r == col.r && col.g == col.g && col.b == col.b)) col = vec3(0.0);

    // Stochastic rounding so slow decay / hue drift does not stall on 8-bit steps.
    float n = hash12(frag + vec2(float(FRAMEINDEX % 997) * 1.7, TIME * 13.0)) - 0.5;
    col = clamp(col + vec3(n * 0.9 / 255.0), 0.0, 1.0);
    return vec4(col, 1.0);
}

// Black is transparent: alpha follows the brightest channel (the renderer stores colour x alpha, so the
// stored pixel is the colour itself and a deck's BG layer shows through wherever this source is dark).
vec4 blackToAlpha(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float a = max(c.r, max(c.g, c.b));
    return vec4(c / max(a, 1e-4), a);
}

void main() {
    if (PASSINDEX == 0) { gl_FragColor = feedbackPass(); return; }

    // Display pass: the square buffer covers the frame (crops the long axis), bilinear upsample.
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 scale = aspect > 1.0 ? vec2(1.0, 1.0 / aspect) : vec2(aspect, 1.0);
    vec2 uv = (isf_FragNormCoord - 0.5) * scale + 0.5;
    vec3 c = texture(vfbState, uv).rgb;
    float lum = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(lum), c, Saturation_) * Brightness_;
    gl_FragColor = blackToAlpha(c);
}
