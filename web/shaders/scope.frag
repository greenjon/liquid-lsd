#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Audio oscilloscope in a feedback loop: the live waveform (trigger-aligned) or the spectrum is drawn as a glowing line or ring into a persistent frame that zooms, turns, warps and fades each frame, so the trace smears into tunnels, spirals and flowing trails.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Audio",
        "Feedback"
    ],
    "LSD_PHASES": {"HuePhase": ["HueSpeed"]},
    "INPUTS": [
        { "NAME": "waveImage", "TYPE": "audio" },
        { "NAME": "fftImage", "TYPE": "audioFFT" },
        { "NAME": "Mode", "LABEL": "Trace", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1, "LABELS": ["Wave Line", "Wave Ring", "Spectrum Line", "Spectrum Ring"] },
        { "NAME": "Amplitude", "LABEL": "Amplitude", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Smoothing", "LABEL": "Smoothing", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Thickness", "LABEL": "Thickness", "TYPE": "float", "DEFAULT": 0.2, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "RingSize", "LABEL": "Ring Size", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 0.3, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Rotation", "LABEL": "Rotation", "TYPE": "float", "DEFAULT": 0.15, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Warp", "LABEL": "Warp", "TYPE": "float", "DEFAULT": 0.2, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Decay", "LABEL": "Decay", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueSpeed", "LABEL": "Hue Speed", "TYPE": "float", "DEFAULT": 0.2, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ],
    "PASSES": [
        { "TARGET": "trigger", "WIDTH": "1", "HEIGHT": "1" },
        { "TARGET": "trails", "PERSISTENT": true, "WIDTH": "1024", "HEIGHT": "1024" },
        { }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
// Feedback rates are per SECOND and scaled by TIMEDELTA, so the look does not depend on the frame rate.
#define Amplitude_ (0.05+Amplitude*0.9)      // trace height, buffer units (buffer spans -1..1)
#define Smoothing_ (Smoothing*6.0)           // waveform low-pass: tap spacing in samples (5 taps)
#define Thickness_ (0.002+Thickness*0.02)    // line half-width, buffer units
#define RingSize_ (0.1+RingSize*0.6)         // ring radius, buffer units
#define Zoom_ (Zoom*1.2)                     // log-zoom rate, 1/s (positive = trails flow outward)
#define Rotation_ (Rotation*2.0)             // rad/s
#define Warp_ (Warp*0.25)                    // sine warp displacement, buffer units per second
#define Decay_ (0.3+Decay*5.0)               // exponential fade rate, 1/s
#define Saturation_ (Saturation*2.0)
#define Brightness_ (Brightness*2.0)

const float PI = 3.14159265358979;
const float TAU = 6.28318530717959;
const float T_WRAP = 628.3185307;   // the warp's time frequencies are multiples of 0.01 rad/s, so mod(TIME, T_WRAP) is seamless
const float RES = 1024.0;
const float WINDOW = 256.0;         // waveform samples across the trace (of the 512 in the texture)

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hsv2rgb(vec3 c) {
    vec3 k = clamp(abs(fract(c.x + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    return c.z * mix(vec3(1.0), k, c.y);
}

mat2 rot2(float a) { float c = cos(a), s = sin(a); return mat2(c, s, -s, c); }

// Pass 0: the first rising zero crossing in the first half of the waveform, so a steady tone stands still
// instead of sliding. Stored as sample index / 255 (exact in an 8-bit target); 0 when there is none (noise, silence).
vec4 triggerPass() {
    float idx = 0.0;
    float prev = texelFetch(waveImage, ivec2(0, 0), 0).r;
    for (int i = 1; i < 256; i++) {
        float cur = texelFetch(waveImage, ivec2(i, 0), 0).r;
        if (prev < 0.5 && cur >= 0.5) { idx = float(i); break; }
        prev = cur;
    }
    return vec4(idx / 255.0, 0.0, 0.0, 1.0);
}

// Trace value at u (0..1 along the trace), signed, in buffer units.
float traceAt(float u, int mode, float trig) {
    if (mode <= 1) {
        float s = trig + clamp(u, 0.0, 1.0) * (WINDOW - 1.0);
        // 5-tap binomial low-pass: high frequencies would otherwise fill the window with a solid band
        float w = 0.0;
        for (int k = -2; k <= 2; k++) {
            float wt = k == 0 ? 6.0 : (abs(k) == 1 ? 4.0 : 1.0);
            w += wt * texture(waveImage, vec2((s + float(k) * Smoothing_ + 0.5) / 512.0, 0.5)).r;
        }
        return (w / 16.0 - 0.5) * 2.0 * Amplitude_;
    }
    // spectrum on a log axis from ~70 Hz to ~17 kHz
    float bin = 1.5 * pow(360.0 / 1.5, clamp(u, 0.0, 1.0));
    return texture(fftImage, vec2(bin / 512.0, 0.5)).r * Amplitude_;
}

// Line coverage at p (buffer units) for the trace, with antialias width aa.
float traceMask(vec2 p, int mode, float trig, float aa) {
    float d;
    float e = 1.0 / 512.0;
    if (mode == 0 || mode == 2) {
        // horizontal trace across the buffer; spectrum is mirrored above and below the centre line
        float u = p.x * 0.5 + 0.5;
        float h = traceAt(u, mode, trig);
        float slope = (traceAt(u + e, mode, trig) - traceAt(u - e, mode, trig)) / (4.0 * e);
        float y = mode == 2 ? abs(p.y) : p.y;
        d = abs(y - h) / sqrt(1.0 + slope * slope);
        if (abs(p.x) > 1.0) d = 1e3;
    } else {
        // ring: the trace runs once round, mirrored left/right so it closes without a seam
        float r = length(p);
        float a = atan(p.x, p.y);              // 0 at the top
        float u = abs(a) / PI;
        float h = traceAt(u, mode, trig);
        float rad = RingSize_ + (mode == 3 ? h : h * 0.5);
        float dr = (traceAt(u + e, mode, trig) - traceAt(u - e, mode, trig)) / (2.0 * e) / PI;
        if (mode == 1) dr *= 0.5;
        // slope correction uses the ring's radius, not the pixel's: near the centre r -> 0 would shrink every
        // distance and draw spokes
        float rr = max(rad, 0.05);
        d = abs(r - rad) / sqrt(1.0 + dr * dr / (rr * rr));
    }
    return 1.0 - smoothstep(Thickness_ - aa, Thickness_ + aa, d);
}

vec3 traceColor(vec2 p) {
    float hue = HueOffset + HuePhase * 0.1 + 0.08 * length(p);
    return hsv2rgb(vec3(fract(hue), 0.8, 1.0));
}

vec3 tapPrev(vec2 uv) {
    vec2 inside = step(vec2(0.0), uv) * step(uv, vec2(1.0));
    return texture(trails, uv).rgb * (inside.x * inside.y);
}

vec4 trailsPass(int mode, float trig) {
    vec2 frag = gl_FragCoord.xy;
    vec2 p = frag / RES * 2.0 - 1.0;
    float dt = clamp(TIMEDELTA, 0.0, 0.1);
    float tw = mod(TIME, T_WRAP);

    // Inverse warp: where did this pixel's content come from last frame?
    vec2 q = rot2(Rotation_ * dt) * (p * exp(-Zoom_ * dt));
    q += Warp_ * dt * vec2(sin(q.y * 5.0 + tw * 0.7), sin(q.x * 5.0 - tw * 0.9));
    vec3 col = tapPrev(q * 0.5 + 0.5) * exp(-Decay_ * dt);

    float m = traceMask(p, mode, trig, 1.5 / RES);
    // screen blend, at reduced strength: the trace lands on the same pixels every frame and would burn to white
    col = 1.0 - (1.0 - col) * (1.0 - traceColor(p) * m * 0.45);
    col = clamp(col, 0.0, 1.0);
    if (!(col.r == col.r && col.g == col.g && col.b == col.b)) col = vec3(0.0);

    // Stochastic rounding so a slow fade does not stall on 8-bit steps.
    float n = hash12(frag + vec2(float(FRAMEINDEX % 997) * 1.7, tw * 13.0)) - 0.5;
    return vec4(clamp(col + vec3(n * 0.9 / 255.0), 0.0, 1.0), 1.0);
}

// Black is transparent: alpha follows the brightest channel (the renderer stores colour x alpha, so the
// stored pixel is the colour itself and a deck's BG layer shows through wherever this source is dark).
vec4 blackToAlpha(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float a = max(c.r, max(c.g, c.b));
    return vec4(c / max(a, 1e-4), a);
}

void main() {
    int mode = clamp(int(floor(Mode + 0.5)), 0, 3);
    if (PASSINDEX == 0) { gl_FragColor = triggerPass(); return; }
    float trig = mode <= 1 ? floor(texture(trigger, vec2(0.5)).r * 255.0 + 0.5) : 0.0;
    if (PASSINDEX == 1) { gl_FragColor = trailsPass(mode, trig); return; }

    // Display: the square buffer covers the frame (crops the long axis); the current trace is drawn again at
    // full resolution on top so the newest line stays sharp while the trails are upsampled.
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 scale = aspect > 1.0 ? vec2(1.0, 1.0 / aspect) : vec2(aspect, 1.0);
    vec2 buv = (isf_FragNormCoord - 0.5) * scale + 0.5;
    vec2 p = buv * 2.0 - 1.0;
    vec3 c = texture(trails, buv).rgb;
    float aa = 2.0 * scale.x / RENDERSIZE.x * 1.5;
    float m = traceMask(p, mode, trig, aa);
    c = 1.0 - (1.0 - c) * (1.0 - traceColor(p) * m);
    float lum = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(lum), c, Saturation_) * Brightness_;
    gl_FragColor = blackToAlpha(c);
}
