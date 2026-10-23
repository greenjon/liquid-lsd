#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Gray-Scott reaction-diffusion on a persistent fixed-size grid: coral, spots, mitosis and worm regimes that grow, split and drift, rendered with relief lighting.",
    "CREDIT": "Liquid LSD",
    "LSD_COST": 2,
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Organic",
        "Simulation"
    ],
    "INPUTS": [
        { "NAME": "Regime", "LABEL": "Regime", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Feed", "LABEL": "Feed Trim", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Kill", "LABEL": "Kill Trim", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Diffusion", "LABEL": "Diffusion", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SimSpeed", "LABEL": "Sim Speed", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Reseed", "LABEL": "Reseed", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Relief", "LABEL": "Relief", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.8, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ],
    "PASSES": [
        { "TARGET": "rdA", "PERSISTENT": true, "FLOAT": true, "WIDTH": "512", "HEIGHT": "512" },
        { "TARGET": "rdB", "PERSISTENT": true, "FLOAT": true, "WIDTH": "512", "HEIGHT": "512" },
        { "TARGET": "rdC", "PERSISTENT": true, "FLOAT": true, "WIDTH": "512", "HEIGHT": "512" },
        { "TARGET": "rdD", "PERSISTENT": true, "FLOAT": true, "WIDTH": "512", "HEIGHT": "512" },
        { }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define Diffusion_ (0.5+Diffusion*1.0)
#define Relief_ (Relief*4.0)
#define Brightness_ (Brightness*2.0)
#define Zoom_ (Zoom<0.5?pow(0.1,1.0-2.0*Zoom):pow(5.0,2.0*Zoom-1.0))

// Gray-Scott:  dU/dt = Du*lap(U) - U*V^2 + F*(1-U)
//              dV/dt = Dv*lap(V) + U*V^2 - (F+k)*V
// State: R = U, G = V, B = reseed latch, A = 1 once seeded (all-zero means "empty", e.g. after a resize).
// Grid is a fixed 512x512 torus, so the pattern does not depend on the render preset.
// Four chained sim passes (rdA <- rdD, rdB <- rdA, rdC <- rdB, rdD <- rdC) = 4 sub-steps per frame.
// Distinct targets are used because two passes sharing one persistent target leak an FBO on resize.
// Steps are per frame (not TIMEDELTA-scaled), so the sim runs at the deck frame rate.

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

// Seed: U=1 everywhere, V blobs scattered by noise; a different pattern each reseed latch value.
vec4 seedState(vec2 uv, float salt) {
    float n = vnoise(uv * 14.0 + salt * 17.3);
    float blob = smoothstep(0.62, 0.70, n);
    float v = blob * 0.5;
    return vec4(1.0 - 0.5 * blob, v, 0.0, 1.0);
}

// Regime sweeps a path through the (F,k) plane: maze -> worms -> coral (default) -> chaos/spots -> solitons.
vec2 regimeFK(float r) {
    float s = clamp(r, 0.0, 1.0) * 4.0;
    vec2 a, b;
    if (s < 1.0)      { a = vec2(0.0300, 0.0570); b = vec2(0.0390, 0.0580); }
    else if (s < 2.0) { a = vec2(0.0390, 0.0580); b = vec2(0.0545, 0.0620); }
    else if (s < 3.0) { a = vec2(0.0545, 0.0620); b = vec2(0.0620, 0.0609); }
    else              { a = vec2(0.0620, 0.0609); b = vec2(0.0220, 0.0590); }
    return mix(a, b, smoothstep(0.0, 1.0, s - min(floor(s), 3.0)));
}

// Patterns live in a band around the curve k = sqrt(F)/2 - F (measured with a CPU port of this sim): about 0.003
// above it V dies out for good (black screen), about 0.0075 below it V floods the whole grid (flat, no pattern).
float killBand(float f) { return 0.5 * sqrt(f) - f; }

vec4 simStep(sampler2D prev, bool detectExtinction) {
    vec2 px = 1.0 / RENDERSIZE;
    vec2 uv = gl_FragCoord.xy * px;
    vec4 c = texture(prev, uv);

    // Reseed latch: crossing the 0.5 threshold re-seeds once, with a new salt each time.
    float armed = step(0.5, Reseed);
    // Extinction: if V has decayed to zero everywhere it can never come back, so the picture stays black.
    // A coarse 24x24 lattice (about 21 cells apart, finer than a spot) detects it; the first pass reseeds
    // and the other three pass the fresh seed along.
    float alive = 1.0;
    if (detectExtinction) {
        alive = 0.0;
        for (int j = 0; j < 24; j++)
            for (int i = 0; i < 24; i++)
                alive = max(alive, texture(prev, (vec2(float(i), float(j)) + 0.5) * (1.0 / 24.0)).g);
    }
    if (c.a < 0.5 || alive < 0.002 || (armed > 0.5 && c.b < 0.5)) {
        float salt = float(FRAMEINDEX) * 0.013 + TIME;
        vec4 s = seedState(uv, salt);
        s.b = armed;
        return s;
    }

    // Periodic-wrap 9-point Laplacian (weights 0.05 corner, 0.2 edge, -1 centre).
    vec4 l = texture(prev, fract(uv + vec2(-px.x, 0.0))) * 0.2
           + texture(prev, fract(uv + vec2( px.x, 0.0))) * 0.2
           + texture(prev, fract(uv + vec2(0.0, -px.y))) * 0.2
           + texture(prev, fract(uv + vec2(0.0,  px.y))) * 0.2
           + texture(prev, fract(uv + vec2(-px.x, -px.y))) * 0.05
           + texture(prev, fract(uv + vec2( px.x, -px.y))) * 0.05
           + texture(prev, fract(uv + vec2(-px.x,  px.y))) * 0.05
           + texture(prev, fract(uv + vec2( px.x,  px.y))) * 0.05
           - c;

    vec2 fk = regimeFK(Regime) + vec2(Feed * 0.010, Kill * 0.006);
    fk.x = clamp(fk.x, 0.014, 0.075);
    fk.y = clamp(fk.y, killBand(fk.x) - 0.0065, killBand(fk.x) + 0.003);
    float u = c.r, v = c.g;
    float uvv = u * v * v;
    float dt = min(0.5 + SimSpeed * 0.5, 1.2 / Diffusion_);   // Du*dt*1.6 < 2 keeps the explicit step stable
    float nu = u + dt * (Diffusion_ * l.r - uvv + fk.x * (1.0 - u));
    float nv = v + dt * (0.5 * Diffusion_ * l.g + uvv - (fk.x + fk.y) * v);
    nu = clamp(nu, 0.0, 1.0);
    nv = clamp(nv, 0.0, 1.0);
    if (!(nu == nu)) nu = 1.0;
    if (!(nv == nv)) nv = 0.0;
    return vec4(nu, nv, armed, 1.0);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    if (PASSINDEX == 0) { gl_FragColor = simStep(rdD, true); return; }
    if (PASSINDEX == 1) { gl_FragColor = simStep(rdA, false); return; }
    if (PASSINDEX == 2) { gl_FragColor = simStep(rdB, false); return; }
    if (PASSINDEX == 3) { gl_FragColor = simStep(rdC, false); return; }

    // Display pass: sample the latest state (rdD was written last), tiled, with relief lighting.
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 p = (isf_FragNormCoord - 0.5) * vec2(1.0, 1.0 / aspect) / Zoom_ + 0.5;   // grid spans the width; crops vertically
    vec2 uv = fract(p);
    vec2 e = vec2(1.0 / 512.0, 0.0);
    float v = texture(rdD, uv).g;
    float hx = texture(rdD, fract(uv + e.xy)).g - texture(rdD, fract(uv - e.xy)).g;
    float hy = texture(rdD, fract(uv + e.yx)).g - texture(rdD, fract(uv - e.yx)).g;
    vec3 n = normalize(vec3(-hx * Relief_ * 8.0, -hy * Relief_ * 8.0, 1.0));
    float diff = max(dot(n, normalize(vec3(0.4, 0.6, 0.7))), 0.0);
    float spec = pow(max(dot(reflect(vec3(-0.4, -0.6, -0.7), n), vec3(0.0, 0.0, 1.0)), 0.0), 24.0);
    float t = clamp(v * 2.5, 0.0, 1.0);

    int cMode = clamp(int(floor(ColorMode + 0.5)), 0, 3);
    vec3 col;
    if (cMode == 0) {
        col = hsv2rgb(vec3(fract(HueOffset + 0.55 - 0.5 * t), Saturation, Brightness_ * (0.15 + 0.85 * t)));
    } else if (cMode == 1) {
        col = mix(vec3(0.02, 0.0, 0.08), hsv2rgb(vec3(fract(HueOffset + 0.08), Saturation, Brightness_)), t);
    } else if (cMode == 2) {
        col = hsv2rgb(vec3(fract(HueOffset + n.x * 0.6 + n.y * 0.4 + t * 0.3), Saturation, Brightness_ * (0.2 + 0.8 * t)));
    } else {
        col = vec3(Brightness_ * (0.05 + 0.95 * t));
    }
    col = col * (0.35 + 0.65 * diff) + vec3(spec * 0.5 * min(Relief_, 1.0)) * t;
    gl_FragColor = vec4(col, 1.0);
}
