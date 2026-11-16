#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Chladni cymatics: sand settling on the nodal lines of a vibrating square plate, round plate or water dish, snapping between real resonant modes. Pitch Follow lets the loudest held note pick the mode, as the driving tone does on a real plate.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Physics",
        "Organic"
    ],
    "LSD_PHASES": {"VibrationPhase": ["VibrationSpeed"]},
    "INPUTS": [
        { "NAME": "fftImage", "TYPE": "audioFFT" },
        { "NAME": "PlateShape", "LABEL": "Plate", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 2.0, "STEP": 1 },
        { "NAME": "FrequencyM", "LABEL": "Mode M", "TYPE": "float", "DEFAULT": 3.0, "MIN": 1.0, "MAX": 16.0 },
        { "NAME": "FrequencyN", "LABEL": "Mode N", "TYPE": "float", "DEFAULT": 5.0, "MIN": 1.0, "MAX": 16.0 },
        { "NAME": "PitchFollow", "LABEL": "Pitch Follow", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "PitchRange", "LABEL": "Pitch Range", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "ModeMix", "LABEL": "Mode Mix", "TYPE": "float", "DEFAULT": -0.5, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "FrequencyL", "LABEL": "Symmetry", "TYPE": "float", "DEFAULT": 6.0, "MIN": 3.0, "MAX": 12.0, "STEP": 1 },
        { "NAME": "ModeSnap", "LABEL": "Mode Snap", "TYPE": "float", "DEFAULT": 0.8, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "NodeSharpness", "LABEL": "Line Width", "TYPE": "float", "DEFAULT": 0.2, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SandAccumulation", "LABEL": "Settled", "TYPE": "float", "DEFAULT": 0.85, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "VibrationSpeed", "LABEL": "Vibration", "TYPE": "float", "DEFAULT": 0.15, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "InvertMode", "LABEL": "Invert (Antinodes)", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "PaletteMode", "LABEL": "Palette Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Scale", "LABEL": "Scale", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ],
    "PASSES": [
        { "TARGET": "specState", "PERSISTENT": true, "WIDTH": "80", "HEIGHT": "1" },
        { "TARGET": "pitchState", "PERSISTENT": true, "WIDTH": "1", "HEIGHT": "1" },
        { }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define NodeSharpness_ (0.002+NodeSharpness*0.026)
#define ModeMix_ (ModeMix*1.5707963)
#define PitchRange_ (0.5+PitchRange)   // modes per octave as a power of 2: 0.5 = x1.41 (real plate), 1 = x2, 1.5 = x2.83
#define Glow_ (Glow*3.0)
#define Scale_ (Scale<0.5?pow(0.2,1.0-2.0*Scale):pow(4.0,2.0*Scale-1.0))

// How it works. A plate only rings at whole-number modes, and sand collects on the lines that do not
// move (the nodal lines, w = 0). Each plate type gives w and its gradient; the sand is drawn from the
// distance to the nearest nodal line, |w| / |grad w|, so lines keep an even width and the rest of the
// plate stays bare. Between two whole modes the plate crossfades and the sand is thrown up and
// resettles: Mode Snap sets how much of each step that takes.

const float PI = 3.14159265358979323846;
const float PLATE_R = 0.48;   // plate half-size at Scale 1x, in screen heights

// Pitch Follow. On a real plate the driving tone picks the mode: Chladni found a mode's frequency grows with
// the square of its size, so the mode numbers here scale with sqrt(pitch / 440 Hz). The pitch is the loudest
// *held* tone between 180 Hz and 3.5 kHz (below that the 47 Hz FFT bins can't tell bass notes apart; the
// bass already drives the plate through the audio modulators). Two small persistent passes find it once
// per frame: specState smooths each bin over 0.6 s so held notes beat drum hits, and pitchState keeps the
// current peak until another beats it by 0.08 (6 dB), then glides to it in 0.08 s. Tuned offline on five
// tracks: 15-30 changes a minute, each held 2-3 s. Values are stored 16-bit across two bytes, so the passes
// survive an 8-bit render target.
const float BIN_HZ = 46.875;      // 512 bins over 24 kHz (48 kHz audio)
const int KLO = 4;                // 187 Hz
const int KHI = 75;               // 3.5 kHz
const float HOLD_TAU = 0.4;       // seconds a bin's level is smoothed over
const float GLIDE_TAU = 0.08;     // seconds to glide to a new peak
const float MARGIN = 0.05;        // a new peak must beat the current one by this, 4 dB (0..1 = -80..0 dB)
const float TILT = 3.0 / 80.0;    // +3 dB per octave, so a melody isn't outshouted by its own low partials
const float MIN_LEVEL = 0.3;      // quieter than -56 dB: no pitch, hold the last one

vec2 enc16(float v) {
    v = floor(clamp(v, 0.0, 1.0) * 65535.0 + 0.5);
    float hi = floor(v / 256.0);
    return vec2(hi, v - hi * 256.0) / 255.0;
}
float dec16(vec2 c) {
    return (floor(c.x * 255.0 + 0.5) * 256.0 + floor(c.y * 255.0 + 0.5)) / 65535.0;
}
float binLevel(int k) {
    return dec16(texelFetch(specState, ivec2(k, 0), 0).rg);
}
float binScore(int k) {
    return binLevel(k) + TILT * log2(float(k) * BIN_HZ / 440.0);
}

vec4 specPass() {
    float k = floor(gl_FragCoord.x);
    float fresh = texture(fftImage, vec2((k + 0.5) / 512.0, 0.5)).r;
    float kept = dec16(texelFetch(specState, ivec2(gl_FragCoord.xy), 0).rg);
    float a = 1.0 - exp(-clamp(TIMEDELTA, 0.0, 0.1) / HOLD_TAU);
    return vec4(enc16(kept + (fresh - kept) * a), 0.0, 1.0);
}

// r = current peak bin / 255, gb = smoothed log2(pitch / 440 Hz) mapped from -2..3, a = 1 once a pitch was heard
vec4 pitchPass() {
    vec4 st = texelFetch(pitchState, ivec2(0, 0), 0);
    bool heard = st.a > 0.5;
    int cur = heard ? int(floor(st.r * 255.0 + 0.5)) : -1;
    float L = heard ? dec16(st.gb) * 5.0 - 2.0 : 0.0;
    int best = KLO;
    float bestScore = -1e9;
    for (int k = KLO; k <= KHI; k++) {
        float sc = binScore(k);
        if (sc > bestScore) { bestScore = sc; best = k; }
    }
    if (binLevel(best) > MIN_LEVEL && (cur < 0 || bestScore > binScore(cur) + MARGIN)) cur = best;
    if (cur < 0) return vec4(0.0);
    // Between bins: a parabola through the peak and its neighbours
    float a = binLevel(cur - 1), b = binLevel(cur), c = binLevel(cur + 1);
    float den = a - 2.0 * b + c;
    float d = abs(den) > 1e-5 ? clamp(0.5 * (a - c) / den, -0.5, 0.5) : 0.0;
    float target = log2((float(cur) + d) * BIN_HZ / 440.0);
    if (!heard) L = target;
    L += (target - L) * (1.0 - exp(-clamp(TIMEDELTA, 0.0, 0.1) / GLIDE_TAU));
    return vec4(float(cur) / 255.0, enc16((L + 2.0) / 5.0), 1.0);
}

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Square plate with free edges, mode (a, b): cos(a pi x) cos(b pi y) over 0..1. Returns (w, dw/dq).
vec3 squareMode(vec2 q, float a, float b) {
    vec2 x = (q + 1.0) * 0.5;
    float ca = cos(a * PI * x.x), sa = sin(a * PI * x.x);
    float cb = cos(b * PI * x.y), sb = sin(b * PI * x.y);
    return vec3(ca * cb, -0.5 * a * PI * sa * cb, -0.5 * b * PI * ca * sb);
}

// Bessel J_m(x) and J_m'(x), WKB form: right node positions at a fraction of the cost of the series.
vec2 besselJ(float m, float x) {
    x = max(x, 1e-3);
    float s = x * x - m * m;
    float c = pow(max(m, 1.0), 4.0 / 3.0);
    float A = 0.79788456 * pow(abs(s) + c, -0.25);
    float dA = -0.5 * sign(s) * x * A / (abs(s) + c);
    if (s > 0.0) {
        float q = sqrt(s);
        float ph = q - m * acos(m / x) - 0.25 * PI;
        return vec2(A * cos(ph), dA * cos(ph) - A * sin(ph) * q / x);
    }
    float q = sqrt(-s);
    float E = 0.70710678 * exp(-(m * log((m + q) / x) - q));
    return vec2(A * E, (dA + A * q / x) * E);
}

// Wavenumber that puts n nodal rings inside the plate with a free (moving) rim: WKB phase = n pi.
float ringK(float m, float n) {
    float k = (n + 0.5 * m + 0.25) * PI + 0.5;
    for (int i = 0; i < 5; i++) {
        float q = sqrt(max(k * k - m * m, 1e-4));
        float ph = q - m * acos(clamp(m / k, -1.0, 1.0)) - 0.25 * PI;
        k = max(k - (ph - n * PI) / max(q / k, 0.05), m + 0.01);
    }
    return k;
}

// Round plate: a - 1 nodal diameters, b nodal rings.
vec3 roundMode(vec2 q, float a, float b) {
    float m = a - 1.0;
    float r = max(length(q), 1e-4);
    // Angle from straight up, so every mode is mirror-symmetric left to right
    float th = atan(q.x, q.y);
    float k = ringK(m, b);
    vec2 J = besselJ(m, k * r) * sqrt(k) * 0.6;
    float c = cos(m * th), s = sin(m * th);
    vec2 rh = q / r;
    vec2 thh = vec2(rh.y, -rh.x);
    return vec3(J.x * c, k * J.y * c * rh - m * J.x * s / r * thh);
}

// Water dish (Faraday waves): plane waves from `fold` directions with wavenumber k, plus the
// concentric rings a round dish adds at the centre.
vec3 waterMode(vec2 q, float fold, float k) {
    vec3 acc = vec3(0.0);
    for (int j = 0; j < 12; j++) {
        if (float(j) >= fold) break;
        float a = 2.0 * PI * float(j) / fold;
        vec2 u = vec2(sin(a), cos(a));
        float ph = k * dot(u, q) + 0.25 * PI;
        acc += vec3(cos(ph), -k * sin(ph) * u);
    }
    acc /= fold;
    float r = max(length(q), 1e-4);
    // fading outward, so the lattice takes over away from the centre
    float fade = 0.4 * exp(-2.0 * r);
    vec2 J = besselJ(0.0, k * r) * sqrt(k);
    acc += fade * vec3(J.x, (k * J.y - 2.0 * J.x) * q / r);
    return acc;
}

vec3 plateMode(int shape, vec2 q, float a, float b) {
    // M = N: the partner is the same mode, and a mix of -45 degrees would cancel it to nothing
    if (a == b) return shape == 0 ? squareMode(q, a, a) : roundMode(q, a, a);
    if (shape == 0) return cos(ModeMix_) * squareMode(q, a, b) + sin(ModeMix_) * squareMode(q, b, a);
    return cos(ModeMix_) * roundMode(q, a, b) + sin(ModeMix_) * roundMode(q, b, a);
}

void main() {
    if (PASSINDEX == 0) { gl_FragColor = specPass(); return; }
    if (PASSINDEX == 1) { gl_FragColor = pitchPass(); return; }
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    float zoom = max(Scale_, 0.05);
    vec2 q = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0) / (zoom * PLATE_R);
    float qpx = 1.0 / (RENDERSIZE.y * zoom * PLATE_R);   // one pixel in plate units
    int shape = int(clamp(floor(PlateShape + 0.5), 0.0, 2.0));

    // Whole modes either side of M and N, and how far into each step's crossfade the plate is
    float win = mix(1.0, 0.06, ModeSnap);
    vec4 ps = texture(pitchState, vec2(0.5));
    float pitchOct = ps.a > 0.5 ? dec16(ps.gb) * 5.0 - 2.0 : 0.0;
    float pitchScale = mix(1.0, exp2(PitchRange_ * pitchOct), PitchFollow);
    float M = clamp(FrequencyM * pitchScale, 1.0, 16.0), N = clamp(FrequencyN * pitchScale, 1.0, 16.0);
    float m0 = floor(M), n0 = floor(N);
    float m1 = min(m0 + 1.0, 16.0), n1 = min(n0 + 1.0, 16.0);
    float tm = smoothstep(1.0 - win, 1.0, M - m0);
    float tn = smoothstep(1.0 - win, 1.0, N - n0);

    vec3 W;
    if (shape == 2) {
        float fold = clamp(floor(FrequencyL + 0.5), 3.0, 12.0);
        vec3 wn = waterMode(q, fold, (n0 + 1.0) * PI);
        if (tn > 0.0) wn = mix(wn, waterMode(q, fold, (n1 + 1.0) * PI), tn);
        vec3 wm = waterMode(q, fold, (m0 + 1.0) * PI);
        if (tm > 0.0) wm = mix(wm, waterMode(q, fold, (m1 + 1.0) * PI), tm);
        // Same ring count on both: one pattern (mixing it with itself could cancel it)
        W = (m0 == n0 && tm == tn) ? wn : cos(ModeMix_) * wn + sin(ModeMix_) * wm;
    } else {
        W = (1.0 - tm) * (1.0 - tn) * plateMode(shape, q, m0, n0);
        if (tm > 0.0) W += tm * (1.0 - tn) * plateMode(shape, q, m1, n0);
        if (tn > 0.0) W += (1.0 - tm) * tn * plateMode(shape, q, m0, n1);
        if (tm > 0.0 && tn > 0.0) W += tm * tn * plateMode(shape, q, m1, n1);
    }
    float w = W.x;
    vec2 grad = W.yz;

    // Plate outline: square for the square plate, round for the round plate and the dish
    float edge = shape == 0 ? max(abs(q.x), abs(q.y)) : length(q);
    float plateMask = 1.0 - smoothstep(1.0 - 1.5 * qpx, 1.0 + 1.5 * qpx, edge);
    float rim = exp(-pow((1.0 - edge) / max(0.006, 2.0 * qpx), 2.0)) * plateMask;

    // Between whole modes the plate doesn't hold a clean pattern, so the sand is thrown up
    float unsettle = max(4.0 * tm * (1.0 - tm), 4.0 * tn * (1.0 - tn));
    float settled = clamp(SandAccumulation, 0.0, 1.0) * (1.0 - 0.85 * unsettle);

    // Sand: a band around the nodal line, widening and thinning out the less it has settled
    float d = abs(w) / max(length(grad), 1e-4);
    float width = max(NodeSharpness_, 0.8 * qpx);
    float spread = 1.0 + (1.0 - settled) * 10.0;
    float band = width * spread;
    float coverage = exp(-(d * d) / (band * band)) * pow(spread, -0.6);
    // Grains: fixed on the lines, hopping where the plate moves most
    float amp = clamp(abs(w) / 0.7, 0.0, 1.0);
    vec2 cell = floor(q / max(0.0035, 1.2 * qpx));
    float hop = floor(VibrationPhase * 40.0);
    float g = mix(hash(cell), hash(cell + vec2(17.13, 3.71) * hop), smoothstep(0.15, 0.6, amp));
    float sand = smoothstep(g - 0.12, g + 0.12, coverage * 1.15);

    // Inverted: the moving surface instead of the sand, lit like water
    float osc = cos(VibrationPhase * 2.0 * PI * 2.0);
    vec3 nrm = normalize(vec3(-grad * osc * 0.02, 1.0));
    vec3 L = normalize(vec3(-0.4, 0.5, 0.75));
    float spec = pow(max(reflect(-L, nrm).z, 0.0), 24.0);
    float surface = 0.6 * pow(amp, 1.5) + 0.7 * spec;
    float visualSignal = mix(sand, surface, InvertMode);

    float acousticGlow = exp(-d / (width * 6.0)) * Glow_ * 0.3 * plateMask * (1.0 - 0.6 * InvertMode);

    int cMode = clamp(int(floor(PaletteMode + 0.5)), 0, 3);
    vec3 col;
    if (cMode == 0) {
        // Obsidian plate and gold sand
        vec3 plateCol = vec3(0.03, 0.03, 0.05);
        vec3 sandCol = vec3(1.0, 0.82, 0.25) * (0.8 + 0.4 * g);
        col = mix(plateCol, sandCol, visualSignal);
        col += vec3(1.0, 0.6, 0.1) * acousticGlow;
        col += vec3(0.25, 0.22, 0.18) * rim;
    } else if (cMode == 1) {
        // Electric blue and ultraviolet
        col = mix(vec3(0.04, 0.01, 0.08), vec3(0.0, 0.85, 1.0), visualSignal);
        col += vec3(0.3, 0.1, 1.0) * acousticGlow;
        col += vec3(0.2, 0.15, 0.35) * rim;
    } else if (cMode == 2) {
        // Prismatic: hue follows the wave and the radius
        float hue = fract(HueOffset + w * 0.3 + length(q) * 0.35);
        vec3 rainbow = hsv2rgb(vec3(hue, 0.85, 1.0));
        col = rainbow * (visualSignal + acousticGlow) + vec3(0.02);
        col += vec3(0.2) * rim;
    } else {
        // Bioluminescent emerald
        col = mix(vec3(0.01, 0.04, 0.03), vec3(0.1, 1.0, 0.45), visualSignal);
        col += vec3(0.0, 0.8, 0.3) * acousticGlow;
        col += vec3(0.1, 0.3, 0.2) * rim;
    }

    gl_FragColor = vec4(col * plateMask, plateMask);
}
