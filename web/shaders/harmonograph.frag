#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Oscilloscope and harmonograph curves: Lissajous figures from two damped sinusoids per axis, drawn as a glowing phosphor line that winds many times into a dense, spiralling rosette. Preset frequency ratios, detune, signed phase drift, damping spiral, line width, glow and trail length.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Geometric"
    ],
    "LSD_PHASES": {"DetunePhase": ["Detune"], "DriftPhase": ["DriftSpeed"]},
    "INPUTS": [
        { "NAME": "RatioPreset", "LABEL": "Ratio X:Y", "TYPE": "float", "DEFAULT": 2.0, "MIN": 0.0, "MAX": 7.0, "STEP": 1 },
        { "NAME": "PaletteMode", "LABEL": "Palette", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "Detune", "LABEL": "Detune", "TYPE": "float", "DEFAULT": 0.25, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "DriftSpeed", "LABEL": "Phase Drift", "TYPE": "float", "DEFAULT": 0.3, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Damping", "LABEL": "Damping", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "LineWidth", "LABEL": "Line Width", "TYPE": "float", "DEFAULT": 0.2, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Trail", "LABEL": "Trail Length", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Rotation", "LABEL": "Rotation", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ],
    "PASSES": [
        { "TARGET": "curveLUT", "WIDTH": "129", "HEIGHT": "2", "FLOAT": true },
        { }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define Detune_ (Detune*0.08)
#define Damping_ (Damping*6.0)
#define LineWidth_ (0.0015+LineWidth*0.012)
#define Glow_ (Glow*2.0)
#define Trail_ (2.0+16.0*Trail*Trail)   // trail length in periods of the fastest axis
#define Rotation_ (Rotation*3.14159265)
#define Brightness_ (Brightness*2.0)

const float TAU = 6.28318530717958647692;
const int MAXSEG = 128;
const int CHUNK = 8;

// Frequency ratio presets X:Y.
vec2 ratioOf(int k) {
    if (k == 0) return vec2(1.0, 1.0);
    if (k == 1) return vec2(1.0, 2.0);
    if (k == 2) return vec2(2.0, 3.0);
    if (k == 3) return vec2(3.0, 4.0);
    if (k == 4) return vec2(3.0, 5.0);
    if (k == 5) return vec2(4.0, 5.0);
    if (k == 6) return vec2(5.0, 6.0);
    return vec2(3.0, 7.0);
}

// Rotate a colour about the grey axis (Rodrigues), h in turns.
vec3 hueRotate(vec3 c, float h) {
    float a = h * TAU;
    float ca = cos(a);
    float sa = sin(a);
    vec3 k = vec3(0.57735027);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}

// Trail colour by age (0 = oldest, 1 = head).
vec3 trailColor(float age, int mode) {
    if (mode == 1) {            // rainbow along the trail
        return 0.5 + 0.5 * cos(TAU * (age * 0.8 + vec3(0.0, 0.33, 0.67)));
    } else if (mode == 2) {     // ember
        return mix(vec3(0.5, 0.06, 0.02), vec3(1.0, 0.7, 0.25), age);
    } else if (mode == 3) {     // ice
        return mix(vec3(0.1, 0.15, 0.9), vec3(0.55, 0.95, 1.0), age);
    }
    return mix(vec3(0.02, 0.45, 0.12), vec3(0.35, 1.0, 0.45), age);  // P1 phosphor
}

// Curve point for trail position u (0 = oldest, 1 = head). Two damped sinusoids per axis;
// phases are wrapped per term so large TIME stays precise.
vec2 curve(float u, vec2 f, float span, float damp, vec4 hp, vec4 ph) {
    float t = span * (u - 1.0);              // time relative to the head, <= 0
    float env = exp(damp * (u - 1.0));       // older parts have decayed further
    float x = 0.7 * sin(f.x * t + hp.x + ph.x) + 0.3 * sin(0.5 * f.x * t + hp.z + ph.z);
    float y = 0.7 * sin(f.y * t + hp.y + ph.y) + 0.3 * sin(0.5 * f.y * t + hp.w + ph.w);
    return env * vec2(x, y);
}

void main() {
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    int pmode = clamp(int(floor(PaletteMode + 0.5)), 0, 3);
    int n = int(floor(mix(48.0, 128.0, clamp(Quality, 0.0, 1.0)) + 0.5));
    n = clamp(n, 48, MAXSEG);

    // frequencies normalised so the fastest axis runs at 1 rad per unit time
    vec2 r = ratioOf(clamp(int(floor(RatioPreset + 0.5)), 0, 7));
    vec2 f = r / max(r.x, r.y);
    float fy0 = f.y;
    f.y *= 1.0 + Detune_;
    float span = TAU * Trail_ / max(f.x, f.y);

    // head phases (wrapped per term) and signed phase drift
    // f.y * head with head = 0.3 TIME, integrated so Detune changes do not jump the phase (Detune_ = Detune x 0.08)
    float head = TIME * 0.3;
    float headY = fy0 * 0.3 * (TIME + 0.08 * DetunePhase);
    float drift = DriftPhase * 1.5;
    vec4 hp = vec4(mod(f.x * head, TAU), mod(headY, TAU),
                   mod(0.5 * f.x * head, TAU), mod(0.5 * headY, TAU));
    vec4 ph = vec4(0.0, 1.5708 + mod(drift, TAU), 1.3 + mod(0.7 * drift, TAU), 0.4 - mod(0.45 * drift, TAU));

    const float SCALE = 0.44;

    // Pass 0: the curve vertices are the same for every pixel, so evaluate them once into a
    // 129 x 1 float strip instead of once per pixel per segment.
    if (PASSINDEX == 0) {
        int vi = int(gl_FragCoord.x);
        if (gl_FragCoord.y < 1.0) {
            vec2 v = SCALE * curve(float(vi) / float(n), f, span, Damping_, hp, ph);
            gl_FragColor = vec4(v, 0.0, 1.0);
        } else {
            // row 1: bounding box (min xy, max xy) of the 9 vertices of chunk vi (8 segments)
            vec2 lo = vec2(1e4);
            vec2 hi = vec2(-1e4);
            for (int j = 0; j <= CHUNK; j++) {
                int k = vi * CHUNK + j;
                if (k > n) break;
                vec2 v = SCALE * curve(float(k) / float(n), f, span, Damping_, hp, ph);
                lo = min(lo, v);
                hi = max(hi, v);
            }
            gl_FragColor = vec4(lo, hi);
        }
        return;
    }

    // pixel in curve space (height = 1, curve scaled to fit)
    vec2 p = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0);
    float cr = cos(Rotation_);
    float sr = sin(Rotation_);
    p = vec2(cr * p.x + sr * p.y, -sr * p.x + cr * p.y);

    float w = LineWidth_;
    float g = w * (1.5 + 2.5 * Glow_);       // glow radius
    float invN = 1.0 / float(n);
    float rCut2 = 1.0 / (36.0 * g * g);
    float refLen = SCALE * span * 0.5 * invN;           // typical segment length
    float core = 0.0;
    float coreAge = 0.0;
    float glowSum = 0.0;
    float glowAge = 0.0;

    for (int ci = 0; ci < MAXSEG / CHUNK; ci++) {
        if (ci * CHUNK >= n) break;
        // Skip a whole chunk of 8 segments when the pixel is outside its glow radius.
        vec4 bb = texelFetch(curveLUT, ivec2(ci, 1), 0);
        vec2 dq = max(max(bb.xy - p, p - bb.zw), vec2(0.0));
        if (dot(dq, dq) * rCut2 >= 1.0) continue;
        vec2 a = texelFetch(curveLUT, ivec2(ci * CHUNK, 0), 0).xy;
        for (int k = 0; k < CHUNK; k++) {
            int i = ci * CHUNK + k;
            if (i >= n) break;
            vec2 b = texelFetch(curveLUT, ivec2(i + 1, 0), 0).xy;
            vec2 ab = b - a;
            vec2 ap = p - a;
            float l2 = dot(ab, ab);
            float h = clamp(dot(ap, ab) / max(l2, 1e-10), 0.0, 1.0);
            vec2 dv = ap - ab * h;
            float d2 = dot(dv, dv);
            float cut = 1.0 - d2 * rCut2;                      // glow reaches 6 radii, then is cut
            if (cut <= 0.0) { a = b; continue; }
            float len = sqrt(l2);
            float uu = (float(i) + h) * invN;
            float fade = 0.06 + 0.94 * uu * uu;                  // trail-fade age gradient
            // phosphor dwell: slow parts of the beam (turning points) burn brighter
            float dwell = clamp(sqrt(refLen) * inversesqrt(len + 1e-4), 1.0, 2.5);
            float c = exp(-d2 / (w * w)) * fade * dwell;
            if (c > core) { core = c; coreAge = uu; }
            // glow: length-weighted so segment joints are not double counted
            float gk = g * g / (d2 + g * g) * len * 4.0 * fade * cut;
            glowSum += gk;
            glowAge += gk * uu;
            a = b;
        }
    }
    glowAge = glowAge / max(glowSum, 1e-5);

    vec3 col = trailColor(coreAge, pmode) * core * 1.6;
    col += trailColor(glowAge, pmode) * glowSum * 0.09 * Glow_;
    col += vec3(core * core * core) * 0.5;               // white-hot core
    col = hueRotate(col, HueOffset);
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(luma), col, Saturation) * Brightness_;
    col = 1.0 - exp(-max(col, vec3(0.0)) * 1.4);         // soft tonemap
    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
