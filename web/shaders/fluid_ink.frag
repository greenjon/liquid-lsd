#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Stable-fluids ink: orbiting emitters stir and dye a persistent incompressible flow with vorticity curls, rendered as glowing ink.",
    "CREDIT": "Liquid LSD",
    "LSD_COST": 2,
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Organic",
        "Simulation"
    ],
    "INPUTS": [
        { "NAME": "Emitters", "LABEL": "Emitters", "TYPE": "float", "DEFAULT": 0.75, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Force", "LABEL": "Stir Force", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Swirl", "LABEL": "Swirl", "TYPE": "float", "DEFAULT": 0.7, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Turbulence", "LABEL": "Turbulence", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Viscosity", "LABEL": "Viscosity", "TYPE": "float", "DEFAULT": 0.25, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "DyeFade", "LABEL": "Dye Fade", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Projection", "LABEL": "Projection", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SimSpeed", "LABEL": "Sim Speed", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.85, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ],
    "PASSES": [
        { "TARGET": "vel",  "PERSISTENT": true, "FLOAT": true, "WIDTH": "384", "HEIGHT": "384" },
        { "TARGET": "prs1", "PERSISTENT": true, "FLOAT": true, "WIDTH": "384", "HEIGHT": "384" },
        { "TARGET": "prs2", "PERSISTENT": true, "FLOAT": true, "WIDTH": "384", "HEIGHT": "384" },
        { "TARGET": "prs3", "PERSISTENT": true, "FLOAT": true, "WIDTH": "384", "HEIGHT": "384" },
        { "TARGET": "prs4", "PERSISTENT": true, "FLOAT": true, "WIDTH": "384", "HEIGHT": "384" },
        { "TARGET": "dye",  "PERSISTENT": true, "FLOAT": true, "WIDTH": "384", "HEIGHT": "384" },
        { }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define Force_ (Force*1.0)
#define Swirl_ (Swirl*0.35)
#define Turbulence_ (Turbulence*0.12)
#define Viscosity_ (Viscosity*0.02)
#define DyeFade_ (0.004+DyeFade*0.03)
#define SimDt_ (SimSpeed*2.0)
#define Brightness_ (Brightness*2.0)

// Stable fluids (Stam, "Stable Fluids", SIGGRAPH 1999) on a fixed 384x384 periodic grid.
// Units: velocity is in grid cells per sim step, so every stencil has h = 1.
// Per frame:  vel  : semi-Lagrangian self-advection of the pressure-projected velocity, plus emitter forces,
//                    vorticity confinement, curl-noise turbulence and viscous damping.
//             prs1..4 : four Jacobi iterations of  lap(p) = div(u*)  (warm-started from last frame's p, so the
//                    solve converges over successive frames; "Projection" scales how much of grad p is removed).
//             dye  : advection of RGB dye by the projected velocity, plus emitter injection.
// Steps are per frame, not TIMEDELTA-scaled: the sim speed follows the deck frame rate. The grid is square and
// stretched over the output, so on 16:9 the emitters read as slightly wide ellipses. The domain wraps (torus).

const float PI = 3.14159265358979323846;
const float N = 384.0;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float gnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = dot(hash22(i) - 0.5, f);
    float b = dot(hash22(i + vec2(1.0, 0.0)) - 0.5, f - vec2(1.0, 0.0));
    float c = dot(hash22(i + vec2(0.0, 1.0)) - 0.5, f - vec2(0.0, 1.0));
    float d = dot(hash22(i + vec2(1.0, 1.0)) - 0.5, f - vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Emitter i: position on a Lissajous orbit, and its velocity (cells per step) by finite difference.
vec2 emitterPos(int i, float t) {
    float fi = float(i);
    return vec2(0.5) + vec2(0.34 * sin(t * (0.23 + 0.07 * fi) + fi * 1.9),
                             0.34 * cos(t * (0.31 + 0.05 * fi) + fi * 2.7));
}

vec3 emitterColor(int i, vec2 vel) {
    int cMode = clamp(int(floor(ColorMode + 0.5)), 0, 3);
    float fi = float(i);
    float h;
    if (cMode == 0)      h = HueOffset + fi * 0.27;
    else if (cMode == 1) h = HueOffset + atan(vel.y, vel.x) / (2.0 * PI);
    else if (cMode == 2) h = HueOffset + mod(fi, 2.0) * 0.5 + 0.08;
    else                 h = HueOffset + 0.62;
    float sat = (cMode == 3) ? Saturation * 0.4 : Saturation;
    return hsv2rgb(vec3(fract(h), sat, 1.0));
}

// Pressure-projected velocity at uv (cells/step): u = u* - Projection * grad p.
vec2 projVel(vec2 uv) {
    vec2 px = vec2(1.0 / N);
    vec2 u = texture(vel, uv).xy;
    float pl = texture(prs4, uv - vec2(px.x, 0.0)).x;
    float pr = texture(prs4, uv + vec2(px.x, 0.0)).x;
    float pd = texture(prs4, uv - vec2(0.0, px.y)).x;
    float pu = texture(prs4, uv + vec2(0.0, px.y)).x;
    return u - Projection * 0.5 * vec2(pr - pl, pu - pd);
}

float curlAt(vec2 uv) {
    vec2 px = vec2(1.0 / N);
    float vr = texture(vel, uv + vec2(px.x, 0.0)).y;
    float vl = texture(vel, uv - vec2(px.x, 0.0)).y;
    float ur = texture(vel, uv + vec2(0.0, px.y)).x;
    float ul = texture(vel, uv - vec2(0.0, px.y)).x;
    return 0.5 * ((vr - vl) - (ur - ul));
}

vec4 jacobi(sampler2D prevP) {
    vec2 px = 1.0 / RENDERSIZE;
    vec2 uv = gl_FragCoord.xy * px;
    float pl = texture(prevP, uv - vec2(px.x, 0.0)).x;
    float pr = texture(prevP, uv + vec2(px.x, 0.0)).x;
    float pd = texture(prevP, uv - vec2(0.0, px.y)).x;
    float pu = texture(prevP, uv + vec2(0.0, px.y)).x;
    float div = 0.5 * (texture(vel, uv + vec2(px.x, 0.0)).x - texture(vel, uv - vec2(px.x, 0.0)).x
                     + texture(vel, uv + vec2(0.0, px.y)).y - texture(vel, uv - vec2(0.0, px.y)).y);
    float p = 0.25 * (pl + pr + pd + pu - div);
    if (!(p == p)) p = 0.0;
    return vec4(clamp(p, -200.0, 200.0), 0.0, 0.0, 1.0);
}

// Black is transparent: alpha follows the brightest channel (the renderer stores colour x alpha, so the
// stored pixel is the colour itself and a deck's BG layer shows through wherever this source is dark).
vec4 blackToAlpha(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float a = max(c.r, max(c.g, c.b));
    return vec4(c / max(a, 1e-4), a);
}

void main() {
    vec2 px = vec2(1.0 / N);
    vec2 uv = gl_FragCoord.xy / RENDERSIZE;
    float t = TIME;
    int nEm = clamp(1 + int(floor(Emitters * 3.999)), 1, 4);   // 0..1 knob -> 1..4 emitters

    if (PASSINDEX == 0) {
        // --- velocity: advect (RK2), forces, vorticity confinement, damping ---
        vec2 u0 = projVel(uv);
        vec2 mid = uv - 0.5 * SimDt_ * u0 * px;
        vec2 back = uv - SimDt_ * projVel(mid) * px;
        vec2 u = projVel(back);

        // vorticity confinement: push along N x grad|w| to re-inject swirl lost to numerical dissipation
        float wl = abs(curlAt(uv - vec2(px.x, 0.0)));
        float wr = abs(curlAt(uv + vec2(px.x, 0.0)));
        float wd = abs(curlAt(uv - vec2(0.0, px.y)));
        float wu = abs(curlAt(uv + vec2(0.0, px.y)));
        vec2 eta = 0.5 * vec2(wr - wl, wu - wd);
        eta /= max(length(eta), 1e-4);
        u += SimDt_ * Swirl_ * curlAt(uv) * vec2(eta.y, -eta.x);

        // emitters: directional impulse along the orbit plus a tangential swirl
        for (int i = 0; i < 4; i++) {
            if (i >= nEm) break;
            vec2 p0 = emitterPos(i, t);
            vec2 ev = (emitterPos(i, t + 0.1) - p0) * 10.0;           // unit-ish direction of travel
            vec2 d = uv - p0;
            d -= floor(d + 0.5);
            float splat = exp(-dot(d, d) / 0.0016);
            vec2 tang = vec2(-d.y, d.x) / max(length(d), 0.01);
            u += SimDt_ * Force_ * splat * (ev * 2.0 + tang * 0.6) * 1.2;
        }

        // curl-noise turbulence keeps the flow from settling into a still pool
        float e = 0.01;
        vec2 np = uv * 3.0 + vec2(t * 0.05, -t * 0.04);
        vec2 curl = vec2(gnoise(np + vec2(0.0, e)) - gnoise(np - vec2(0.0, e)),
                        -(gnoise(np + vec2(e, 0.0)) - gnoise(np - vec2(e, 0.0)))) / (2.0 * e);
        u += SimDt_ * Turbulence_ * curl;

        u *= 1.0 - Viscosity_ * min(SimDt_, 1.0);
        float sp = length(u);
        if (sp > 12.0) u *= 12.0 / sp;
        if (!(sp == sp)) u = vec2(0.0);
        gl_FragColor = vec4(u, 0.0, 1.0);
        return;
    }
    if (PASSINDEX == 1) { gl_FragColor = jacobi(prs4); return; }
    if (PASSINDEX == 2) { gl_FragColor = jacobi(prs1); return; }
    if (PASSINDEX == 3) { gl_FragColor = jacobi(prs2); return; }
    if (PASSINDEX == 4) { gl_FragColor = jacobi(prs3); return; }

    if (PASSINDEX == 5) {
        // --- dye: advect by the projected velocity, fade, inject ---
        vec2 u = projVel(uv);
        vec3 c = texture(dye, uv - SimDt_ * u * px).rgb;
        c *= 1.0 - DyeFade_ * min(SimDt_, 1.0);
        for (int i = 0; i < 4; i++) {
            if (i >= nEm) break;
            vec2 p0 = emitterPos(i, t);
            vec2 d = uv - p0;
            d -= floor(d + 0.5);
            float splat = exp(-dot(d, d) / 0.00025);
            vec2 ev = (emitterPos(i, t + 0.1) - p0) * 10.0;
            c += emitterColor(i, ev) * splat * 0.07 * min(SimDt_, 1.0);
        }
        c = clamp(c, 0.0, 6.0);
        if (!(c.r == c.r && c.g == c.g && c.b == c.b)) c = vec3(0.0);
        gl_FragColor = vec4(c, 1.0);
        return;
    }

    // --- display: tone-mapped glowing ink over a dark ground, lit by dye density gradient ---
    vec2 suv = isf_FragNormCoord;
    vec3 c = texture(dye, suv).rgb;
    float lum = dot(c, vec3(0.3, 0.5, 0.2));
    float hx = dot(texture(dye, suv + vec2(px.x, 0.0)).rgb - texture(dye, suv - vec2(px.x, 0.0)).rgb, vec3(0.33));
    float hy = dot(texture(dye, suv + vec2(0.0, px.y)).rgb - texture(dye, suv - vec2(0.0, px.y)).rgb, vec3(0.33));
    float rim = clamp(-(hx * 0.6 + hy * 0.8) * 2.0, 0.0, 1.0);
    vec3 ink = 1.0 - exp(-c * Brightness_ * 1.2);
    vec3 col = vec3(0.012, 0.010, 0.025) + ink + rim * ink * 0.6 + vec3(lum * lum) * 0.15 * Brightness_;
    gl_FragColor = blackToAlpha(col);
}
