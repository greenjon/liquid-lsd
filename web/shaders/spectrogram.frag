#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Scrolling spectrogram of the live audio: each moment's spectrum is written into a 512-column ring buffer, so time runs along one axis and frequency (linear to logarithmic) along the other. Each pixel sums the power of every FFT bin its band covers and a Tilt lifts the highs, as on a studio analyser, so the whole frequency axis lights up, not just the bass. Scroll, rise, ring-outward or tunnel-inward layouts, optional mirrored frequency axis, four palettes and an age fade.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Audio"
    ],
    "INPUTS": [
        { "NAME": "fftImage", "TYPE": "audioFFT" },
        { "NAME": "Layout", "LABEL": "Layout", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1, "LABELS": ["Scroll", "Rise", "Ring", "Tunnel"] },
        { "NAME": "Mirror", "LABEL": "Mirror", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0, "STEP": 1, "LABELS": ["Off", "On"] },
        { "NAME": "PaletteMode", "LABEL": "Palette", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1, "LABELS": ["Fire", "Ice", "Rainbow", "Mono"] },
        { "NAME": "ScrollSpeed", "LABEL": "Scroll Speed", "TYPE": "float", "DEFAULT": 0.35, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "LogScale", "LABEL": "Log Scale", "TYPE": "float", "DEFAULT": 0.8, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Range", "LABEL": "Range", "TYPE": "float", "DEFAULT": 0.75, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Tilt", "LABEL": "Tilt", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Floor", "LABEL": "Floor", "TYPE": "float", "DEFAULT": 0.45, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Gain", "LABEL": "Gain", "TYPE": "float", "DEFAULT": 0.25, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Fade", "LABEL": "Age Fade", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ],
    "PASSES": [
        { "TARGET": "headState", "PERSISTENT": true, "WIDTH": "1", "HEIGHT": "1" },
        { "TARGET": "history", "PERSISTENT": true, "WIDTH": "512", "HEIGHT": "512" },
        { }
    ]
}
*/

// Inputs are normalized to 0..1; these remap them to the shader's working units.
#define ScrollSpeed_ (8.0+ScrollSpeed*232.0)   // columns per second (512 columns on screen)
#define Tilt_ (Tilt*6.0)                       // dB per octave added above 1 kHz (and taken off below)
#define Floor_ (Floor*0.8)                     // band level (0..1, -80..0 dB) shown as black
#define Gain_ (0.25+Gain*3.75)
#define Saturation_ (Saturation*2.0)
#define Brightness_ (Brightness*2.0)

// The history is a ring of N columns (time) x 512 rows (linear FFT bins). headState holds the write head as a
// running column position, advanced by ScrollSpeed x TIMEDELTA, so the scroll speed is per second, not per
// frame, and a speed change never jumps the picture. Columns the head passed this frame get the current
// spectrum; while the head stays inside one column that column keeps the peak, so a slow scroll still shows
// every transient. The head is stored in an 8-bit-safe form (column / 32 and column % 32 as byte values, the
// fraction in b), so it survives a host that renders the pass to RGBA8; last frame's head is not stored but
// recomputed from this frame's step, which every pass sees identically.

const float N = 512.0;
const float TAU = 6.28318531;
const float BANDS = 64.0;   // frequency-axis resolution: each pixel reads a band 1/64 of the axis wide
const int TAPS = 8;         // samples across that band

// Spectrum row of the current frame at a linear-bin coordinate (0..1).
float spectrumAt(float y) {
    return texture(fftImage, vec2(y, 0.5)).r;
}

// Columns the head moves this frame: below N, so the ring never laps itself.
float headStep() {
    return min(ScrollSpeed_ * clamp(TIMEDELTA, 0.0, 0.1), N - 1.0);
}

float readHead() {
    vec3 h = texture(headState, vec2(0.5)).rgb;
    return floor(h.r * 255.0 + 0.5) * 32.0 + floor(h.g * 255.0 + 0.5) + clamp(h.b, 0.0, 0.996);
}

vec4 headPass() {
    float now = mod(readHead() + headStep(), N);
    float col = floor(now);
    return vec4(floor(col / 32.0) / 255.0, mod(col, 32.0) / 255.0, now - col, 1.0);
}

vec4 historyPass() {
    float now = readHead();
    float colNow = floor(now);
    float colOld = floor(mod(now - headStep(), N));
    float c = floor(gl_FragCoord.x);
    float y = gl_FragCoord.y / 512.0;
    float fresh = spectrumAt(y);
    float kept = texelFetch(history, ivec2(gl_FragCoord.xy), 0).r;

    float count = mod(colNow - colOld, N);   // columns entered this frame
    float k = mod(c - colOld, N);            // this column, counted from last frame's
    float v = kept;
    if (count == 0.0) {
        if (k == 0.0) v = max(kept, fresh);  // still inside the same column: peak hold
    } else if (k > 0.0 && k <= count) {
        v = fresh;                           // newly entered (or skipped over) columns
    }
    return vec4(v, v, v, 1.0);
}

// Screen frequency coordinate (0 = low, 1 = top of Range) -> linear FFT-bin coordinate (0..1 of Nyquist). Log Scale
// blends the frequency itself (geometrically), so 0.8 is nearly a log axis rather than a linear one with a log tail.
float binCoord(float f) {
    float top = mix(0.12, 1.0, Range);                      // highest bin shown, fraction of Nyquist
    float lo = 0.65 / 512.0;                                 // bottom of the axis, about 30 Hz at 48 kHz
    float lin = max(f * top, lo);
    float logc = lo * pow(top / lo, f);
    return exp(mix(log(lin), log(logc), LogScale));
}

// Level (0..1 = -80..0 dB) at age a (0 = now, 1 = oldest on screen) of the band around screen frequency f. The band's
// bins are summed as power, the way a fractional-octave analyser reads: on a log axis a pixel near 10 kHz spans
// dozens of bins and gets all of their energy, not one bin's. Divided by the Hann window's noise bandwidth (1.5 bins)
// so a pure tone still reads its own level. Then Tilt adds dB per octave from 1 kHz (the bin axis is taken as
// 24 kHz, i.e. 48 kHz audio), which flattens music's natural fall-off. Smooth between history columns.
float bandLevel(float a, float f) {
    float pos = readHead() - a * (N - 2.0);
    float c0 = floor(pos);
    float t = pos - c0;
    float x0 = (mod(c0, N) + 0.5) / N;
    float x1 = (mod(c0 + 1.0, N) + 0.5) / N;
    float yLo = binCoord(clamp(f - 0.5 / BANDS, 0.0, 1.0));
    float yHi = binCoord(clamp(f + 0.5 / BANDS, 0.0, 1.0));
    float p = 0.0;
    for (int i = 0; i < TAPS; i++) {
        float y = mix(yLo, yHi, (float(i) + 0.5) / float(TAPS));
        float v = mix(texture(history, vec2(x0, y)).r, texture(history, vec2(x1, y)).r, t);
        p += exp2((v - 1.0) * 26.5754248);                   // 10^(8(v-1)): level back to power
    }
    p *= max((yHi - yLo) * 512.0 / 1.5, 1.0) / float(TAPS);
    float db = 3.01029996 * log2(max(p, 1e-12)) + Tilt_ * log2(binCoord(f) * 24.0);
    return clamp(db / 80.0 + 1.0, 0.0, 1.0);
}

vec3 hsv2rgb(vec3 c) {
    vec3 k = clamp(abs(fract(c.x + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    return c.z * mix(vec3(1.0), k, c.y);
}

vec3 colorize(float v, float f, int pal) {
    if (pal == 0) {                                          // fire: black, red, orange, yellow, white
        return clamp(vec3(v * 3.0, v * 3.0 - 1.0, v * 3.0 - 2.0), 0.0, 1.0);
    } else if (pal == 1) {                                   // ice: deep blue, cyan, white
        return clamp(vec3(v * 3.0 - 2.0, v * 2.2 - 0.6, v * 1.6), 0.0, 1.0);
    } else if (pal == 2) {                                   // rainbow by frequency, level = brightness
        return hsv2rgb(vec3(0.75 * (1.0 - f), 0.85, v));
    }
    return vec3(v);                                          // mono (Hue Offset tints it)
}

vec3 hueRotate(vec3 c, float a) {
    float ca = cos(a), sa = sin(a);
    vec3 k = vec3(0.57735027);
    return c * ca + cross(k, c) * sa + k * dot(k, c) * (1.0 - ca);
}

// Black is transparent: alpha follows the brightest channel (the renderer stores colour x alpha, so the
// stored pixel is the colour itself and a deck's BG layer shows through wherever this source is dark).
vec4 blackToAlpha(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float a = max(c.r, max(c.g, c.b));
    return vec4(c / max(a, 1e-4), a);
}

void main() {
    if (PASSINDEX == 0) { gl_FragColor = headPass(); return; }
    if (PASSINDEX == 1) { gl_FragColor = historyPass(); return; }

    vec2 uv = isf_FragNormCoord;
    int lay = clamp(int(floor(Layout + 0.5)), 0, 3);
    int pal = clamp(int(floor(PaletteMode + 0.5)), 0, 3);

    float age, f;
    if (lay == 0) {                       // scroll: newest at the right, flowing left
        age = 1.0 - uv.x; f = uv.y;
    } else if (lay == 1) {                // rise: newest at the bottom, flowing up
        age = uv.y; f = uv.x;
    } else {                                 // ring: newest at the centre flowing out; tunnel: newest at the rim
        float aspect = RENDERSIZE.x / RENDERSIZE.y;
        vec2 p = (uv - 0.5) * vec2(aspect, 1.0) * 2.0;
        float r = length(p) / length(vec2(aspect, 1.0));
        age = lay == 2 ? r : 1.0 - r;
        f = abs(atan(p.x, p.y)) / (TAU * 0.5);   // 0 at the top, 1 at the bottom, mirrored left/right
    }
    if (Mirror > 0.5) f = abs(f * 2.0 - 1.0);   // low frequencies in the middle
    age = clamp(age, 0.0, 1.0);

    float raw = bandLevel(age, clamp(f, 0.0, 1.0));
    float v = clamp((raw - Floor_) / max(1.0 - Floor_, 1e-3), 0.0, 1.0) * Gain_;
    v *= mix(1.0, 1.0 - age, Fade);
    v = clamp(v, 0.0, 1.0);

    vec3 col = colorize(v, f, pal);
    if (pal == 3) col *= hsv2rgb(vec3(HueOffset, 0.7, 1.0)) * 1.3;
    else col = hueRotate(col, HueOffset * TAU);
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, Saturation_) * Brightness_;
    gl_FragColor = blackToAlpha(col);
}
