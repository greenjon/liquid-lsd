#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Cyclic cellular automaton (Belousov-Zhabotinsky look): a 256x256 toroidal grid of cyclic states where a cell advances when enough neighbours are one state ahead, self-organising from noise into rotating spirals and waves. Moore, von Neumann or extended neighbourhood, adjustable speed, reseed button, cosine palettes.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Simulation"
    ],
    "INPUTS": [
        { "NAME": "States", "LABEL": "States", "TYPE": "float", "DEFAULT": 0.1, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Threshold", "LABEL": "Threshold", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Neighborhood", "LABEL": "Neighbourhood", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 2.0, "STEP": 1 },
        { "NAME": "Speed", "LABEL": "Speed", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Density", "LABEL": "Seed Density", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Reseed", "LABEL": "Reseed", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "PaletteMode", "LABEL": "Palette", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 }
    ],
    "PASSES": [
        { "TARGET": "simA", "PERSISTENT": true, "WIDTH": "256", "HEIGHT": "256" },
        { "TARGET": "simB", "PERSISTENT": true, "WIDTH": "256", "HEIGHT": "256" },
        { "TARGET": "simC", "PERSISTENT": true, "WIDTH": "256", "HEIGHT": "256" },
        { "TARGET": "simD", "PERSISTENT": true, "WIDTH": "256", "HEIGHT": "256" },
        { }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define States_ (3.0+States*21.0)              // number of cyclic states, 3..24
#define Speed_ (2.0+Speed*118.0)               // automaton generations per second (4 sub-steps/frame max)
#define Density_ (0.02+Density*0.98)           // fraction of cells that get a random state at (re)seed
#define Saturation_ (Saturation*2.0)
#define Brightness_ (Brightness*2.0)

// Grid layout (RGBA8, 256x256, wraps toroidally): R = state byte (0..23), G = the state count the cell was
// written with, B = 128 x the last seen Reseed level (for rising-edge detection) + the threshold it was written
// with, A = 1 once written (alpha must stay 1: the pass may be blended). A = 0 means "empty" (never written, or
// cleared by a resize), and that cell is seeded from a hash. Nothing depends on FRAMEINDEX.
//
// A rule change under a running pattern (a randomize, a knob): a new state count rescales each cell's state by
// phase (s/n kept), so spirals keep their shape with more or fewer bands; a new threshold or neighbourhood
// reseeds, because the old pattern is usually not alive under the new rule (it froze or synchronised into one
// flat colour).
//
// Sub-stepping: four chained persistent passes simA..simD. Pass k reads the previous pass (simA<-simD,
// simB<-simA, ...) and advances one generation if k < stepsDue, else copies. Generations are due when
// floor(TIME*rate) increases within this frame (stateless, frame-rate independent up to 4 steps/frame).
const float GRID = 256.0;
const int GN = 256;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec4 fetchPrev(ivec2 c) {
    c = ivec2((c.x + GN) % GN, (c.y + GN) % GN);
    if (PASSINDEX == 0) return texelFetch(simD, c, 0);
    if (PASSINDEX == 1) return texelFetch(simA, c, 0);
    if (PASSINDEX == 2) return texelFetch(simB, c, 0);
    return texelFetch(simC, c, 0);
}

int stateOf(vec4 v, int n) {
    int s = int(floor(v.r * 255.0 + 0.5));
    return s % n;
}

int thresholdFor(int hood) {
    int total = (hood == 1) ? 4 : ((hood == 2) ? 24 : 8);
    // Threshold fraction maps to 1..0.6*neighbours (default 0.4: Moore 2, von Neumann 1, extended 6).
    return clamp(int(floor(Threshold * float(total) * 0.6 + 0.5)), 1, total);
}

vec4 written(int s, int n, float rs, int thr) {
    return vec4(float(s) / 255.0, float(n) / 255.0, (rs * 128.0 + float(thr)) / 255.0, 1.0);
}

vec4 simPass() {
    ivec2 c = ivec2(floor(gl_FragCoord.xy));
    int n = int(floor(States_ + 0.5));
    int hood = int(floor(Neighborhood + 0.5));
    int thr = thresholdFor(hood);
    vec4 me = fetchPrev(c);
    float rs = step(0.5, Reseed);
    bool empty = me.a < 0.5;
    int b = int(floor(me.b * 255.0 + 0.5));
    bool edge = Reseed > 0.5 && b < 128;
    // The stored threshold, which also changes with the neighbourhood (each has its own total); 0 = written before
    // thresholds were stored.
    int oldThr = b % 128;
    bool ruleChanged = !empty && oldThr != 0 && oldThr != thr;

    if (empty || edge || ruleChanged) {
        vec2 salt = vec2(fract(TIME * 0.0137) * 97.0, fract(TIME * 0.0071) * 53.0);
        vec2 p = gl_FragCoord.xy + salt;
        float pick = hash12(p);
        float val = hash12(p * 1.37 + 17.0);
        int s = 0;
        if (pick < Density_) s = int(floor(val * float(n)));
        s = clamp(s, 0, n - 1);
        return written(s, n, rs, thr);
    }

    int s = stateOf(me, n);
    int oldN = int(floor(me.g * 255.0 + 0.5));
    if (oldN >= 3 && oldN != n) {
        // Same phase, new count: every cell is remapped the same way, so a cell one state ahead of its neighbour
        // mostly stays one ahead and the waves keep travelling. Neighbours are remapped the same way below.
        s = int(floor(float(int(floor(me.r * 255.0 + 0.5)) % oldN) * float(n) / float(oldN) + 0.5)) % n;
    }
    float dt = clamp(TIMEDELTA, 0.0, 0.1);
    float t0 = max(TIME - dt, 0.0);
    int due = int(floor(TIME * Speed_) - floor(t0 * Speed_));
    if (dt <= 0.0) due = 0;
    if (PASSINDEX < due && oldN == n) {
        int nxt = (s + 1) % n;
        int count = 0;
        int total = 8;
        if (hood == 1) {
            total = 4;
            count += (stateOf(fetchPrev(c + ivec2(1, 0)), n) == nxt) ? 1 : 0;
            count += (stateOf(fetchPrev(c + ivec2(-1, 0)), n) == nxt) ? 1 : 0;
            count += (stateOf(fetchPrev(c + ivec2(0, 1)), n) == nxt) ? 1 : 0;
            count += (stateOf(fetchPrev(c + ivec2(0, -1)), n) == nxt) ? 1 : 0;
        } else {
            int r = (hood == 2) ? 2 : 1;
            total = (hood == 2) ? 24 : 8;
            for (int j = -2; j <= 2; j++) {
                for (int i = -2; i <= 2; i++) {
                    if (abs(i) <= r && abs(j) <= r && (i != 0 || j != 0)) {
                        count += (stateOf(fetchPrev(c + ivec2(i, j)), n) == nxt) ? 1 : 0;
                    }
                }
            }
        }
        if (count >= thr) s = nxt;
    }
    return written(s, n, rs, thr);
}

// Periodic cosine palettes (integer frequency, so state n-1 -> 0 has no seam).
vec3 stateColor(int s, int n) {
    float t = float(s) / float(n);
    vec3 a = vec3(0.5), b = vec3(0.5), d = vec3(0.0, 0.33, 0.67);
    int mode = int(floor(PaletteMode + 0.5));
    if (mode == 1) {            // ember
        a = vec3(0.5, 0.3, 0.2); b = vec3(0.5, 0.4, 0.3); d = vec3(0.0, 0.1, 0.2);
    } else if (mode == 2) {     // deep sea
        a = vec3(0.35, 0.5, 0.55); b = vec3(0.35, 0.4, 0.4); d = vec3(0.55, 0.4, 0.3);
    } else if (mode == 3) {     // neon
        d = vec3(0.85, 0.2, 0.5);
    }
    // cos(2pi t): value 1 at s = 0 so the "rest" state is a bright band edge; ramp is smooth in t
    return clamp(a + b * cos(6.28318530718 * (t + d + HueOffset)), 0.0, 1.0);
}

vec3 cellColor(ivec2 c, int n) {
    c = ivec2((c.x + GN) % GN, (c.y + GN) % GN);
    return stateColor(stateOf(texelFetch(simD, c, 0), n), n);
}

// Black is transparent: alpha follows the brightest channel (the renderer stores colour x alpha, so the
// stored pixel is the colour itself and a deck's BG layer shows through wherever this source is dark).
vec4 blackToAlpha(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float a = max(c.r, max(c.g, c.b));
    return vec4(c / max(a, 1e-4), a);
}

vec4 displayPass() {
    int n = int(floor(States_ + 0.5));
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 scale = aspect > 1.0 ? vec2(1.0, 1.0 / aspect) : vec2(aspect, 1.0);
    vec2 uv = (isf_FragNormCoord - 0.5) * scale + 0.5;       // square grid covers the frame
    vec2 g = uv * GRID;
    float q = clamp(Quality, 0.0, 1.0);
    vec3 col;
    if (q < 0.34) {
        col = cellColor(ivec2(floor(g)), n);                  // crisp cells
    } else {
        vec2 gp = g - 0.5;
        ivec2 i0 = ivec2(floor(gp));
        vec2 f = fract(gp);
        if (q > 0.67) f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);   // smootherstep: rounder fronts
        vec3 c00 = cellColor(i0, n);
        vec3 c10 = cellColor(i0 + ivec2(1, 0), n);
        vec3 c01 = cellColor(i0 + ivec2(0, 1), n);
        vec3 c11 = cellColor(i0 + ivec2(1, 1), n);
        col = mix(mix(c00, c10, f.x), mix(c01, c11, f.x), f.y);
    }
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, Saturation_) * Brightness_;
    col = clamp(col, 0.0, 1.0);
    if (!(col.r == col.r && col.g == col.g && col.b == col.b)) col = vec3(0.0);
    return blackToAlpha(col);
}

void main() {
    if (PASSINDEX < 4) gl_FragColor = simPass();
    else gl_FragColor = displayPass();
}
