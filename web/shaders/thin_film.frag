#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Oil-slick iridescence: a slowly flowing, domain-warped noise height field is read as film thickness, and per-wavelength thin-film interference (optical path 2 n d cos theta) colours it with view-angle dependence and a specular sheen.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Organic"
    ],
    "LSD_PHASES": {"FlowPhase": ["FlowSpeed"]},
    "INPUTS": [
        { "NAME": "FlowSpeed", "LABEL": "Flow Speed", "TYPE": "float", "DEFAULT": 0.3, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Scale", "LABEL": "Scale", "TYPE": "float", "DEFAULT": 0.3, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "FilmThickness", "LABEL": "Film Thickness", "TYPE": "float", "DEFAULT": 0.35, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "ThicknessRange", "LABEL": "Thickness Range", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Contrast", "LABEL": "Contrast", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Turbulence", "LABEL": "Turbulence", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Sheen", "LABEL": "Sheen", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Detail", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 2.0, "MIN": 1.0, "MAX": 4.0, "STEP": 1 },
        { "NAME": "HueShift", "LABEL": "Hue Shift", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.75, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define Scale_ (1.0+Scale*5.0)
#define FilmThickness_ (150.0+FilmThickness*750.0)
#define ThicknessRange_ (60.0+ThicknessRange*840.0)
#define Contrast_ (0.4+Contrast*1.6)
#define Turbulence_ (Turbulence*1.4)
#define Sheen_ (Sheen*2.0)
#define Saturation_ (Saturation*1.6)
#define Brightness_ (Brightness*2.0)

// Value noise on a 256x256 periodic lattice, so the flow offset can be wrapped without a seam.
float hash2(ivec2 c) {
    uint x = uint(c.x & 255);
    uint y = uint(c.y & 255);
    uint h = x * 1597334677u ^ y * 3812015801u;
    h ^= h >> 15;
    h *= 2246822519u;
    h ^= h >> 13;
    return float(h >> 8) * (1.0 / 16777216.0);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    ivec2 c = ivec2(i);
    float a = hash2(c);
    float b = hash2(c + ivec2(1, 0));
    float d = hash2(c + ivec2(0, 1));
    float e = hash2(c + ivec2(1, 1));
    return mix(mix(a, b, u.x), mix(d, e, u.x), u.y);
}

// Octave offsets are integer vectors so wrapping the flow phase at 256 is seamless in every octave.
float heightField(vec2 p, float tw, int oct, float gain) {
    float sum = 0.0;
    float amp = 1.0;
    float norm = 0.0;
    for (int k = 0; k < 4; k++) {
        if (k >= oct) break;
        vec2 dir = k == 0 ? vec2(1.0, 0.0) : (k == 1 ? vec2(-1.0, 1.0) : (k == 2 ? vec2(0.0, -1.0) : vec2(1.0, 1.0)));
        float fk = exp2(float(k));
        sum += amp * vnoise(p * fk + tw * dir + vec2(13.0 * float(k), 7.0 * float(k)));
        norm += amp;
        amp *= gain;
    }
    return sum / norm;
}

vec3 hueRotate(vec3 c, float a) {
    // Rotation about the grey axis (1,1,1)/sqrt(3).
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
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 uv = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0);
    int oct = clamp(int(floor(Detail + 0.5)), 1, 4);
    float tw = mod(FlowPhase * 0.25, 256.0);
    vec2 p = uv * Scale_ + vec2(40.0, 25.0);

    // Two-stage domain warp gives the swirling, advected look.
    float wf = Turbulence_;
    vec2 w1 = vec2(vnoise(p * 0.6 + tw * vec2(-1.0, 1.0) + vec2(17.0, 31.0)),
                   vnoise(p * 0.6 + tw * vec2(1.0, 1.0) + vec2(71.0, 5.0))) - 0.5;
    vec2 q = p + w1 * 2.0 * wf;
    vec2 w2 = vec2(vnoise(q * 0.9 + tw * vec2(1.0, -1.0) + vec2(3.0, 59.0)),
                   vnoise(q * 0.9 + tw * vec2(-1.0, -1.0) + vec2(97.0, 11.0))) - 0.5;
    q = p + (w1 + w2) * 1.5 * wf;

    float gain = 0.5 + 0.12 * wf;
    float e = 0.02;
    float h0 = heightField(q, tw, oct, gain);
    float hx = heightField(q + vec2(e, 0.0), tw, oct, gain);
    float hy = heightField(q + vec2(0.0, e), tw, oct, gain);

    // Contrast stretches the thickness spread about its mean.
    float h = clamp(0.5 + (h0 - 0.5) * 2.0 * Contrast_, 0.0, 1.0);

    // Surface normal from the finite-difference gradient (slope per noise unit).
    vec2 grad = vec2(hx - h0, hy - h0) / e;
    vec3 n = normalize(vec3(-grad * (0.35 + 0.35 * Contrast_), 1.0));
    vec3 v = normalize(vec3(uv * 0.7, 1.0));
    float cosI = clamp(dot(n, v), 0.0, 1.0);

    // Film thickness in nm; refraction into the film at index nFilm gives cos(theta_t).
    float d = max(FilmThickness_ + (h - 0.5) * ThicknessRange_, 5.0);
    const float nFilm = 1.45;
    float sin2t = (1.0 - cosI * cosI) / (nFilm * nFilm);
    float cosT = sqrt(max(1.0 - sin2t, 0.0));
    float opd = 2.0 * nFilm * d * cosT;

    // Two-beam interference per channel; the top reflection flips phase, the bottom does not,
    // so reflectance = 0.5 (1 - cos(2 pi opd / lambda)). Thick films lose coherence and wash to grey.
    vec3 lambda = vec3(610.0, 540.0, 460.0);
    vec3 phase = 6.28318531 * opd / lambda;
    vec3 refl = 0.5 - 0.5 * cos(phase);
    float vis = exp(-d / 1500.0);
    refl = mix(vec3(0.5), refl, vis);

    // Fresnel weight and dark-water backdrop.
    float fres = 0.04 + 0.96 * pow(1.0 - cosI, 5.0);
    vec3 film = refl * (0.55 + 1.2 * fres);

    // Specular sheen from a fixed light.
    vec3 L = normalize(vec3(-0.4, 0.5, 0.75));
    vec3 H = normalize(L + v);
    float nh = max(dot(n, H), 0.0);
    float spec = pow(nh, 90.0) * 1.2 + pow(nh, 12.0) * 0.12;

    vec3 col = film;
    col = hueRotate(col, HueShift * 6.28318531);
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, Saturation_);
    col *= Brightness_;
    col += vec3(spec * Sheen_) * (0.4 + 0.6 * Brightness_);

    col = clamp(col, 0.0, 1.0);
    gl_FragColor = blackToAlpha(col);
}
