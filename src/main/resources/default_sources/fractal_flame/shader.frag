#version 330 core
// Fractal Flame tone-map pass: density buffer (rgb = weighted palette color, a = hit weight) -> display color.
in vec2 vTexCoord;
out vec4 FragColor;

uniform sampler2D uDensity;
uniform float uExposure;
uniform float uBrightness;
uniform float uGamma;
uniform float uVibrancy;
uniform float uToe;
uniform float uDecay;
uniform float uSmooth;   // density-estimation blur strength, 0 = off
uniform float uNorm;     // hit-count normalisation (points fraction, passes, sprite area)

const int LEVELS = 6;    // mip levels 0..5 (up to 32 px footprints)

// Density estimation: a sparse pixel is a noisy estimate, so trust the finest mip level whose mean density is
// reliable (>= T hits' worth) and fall back to coarser levels elsewhere. Mips preserve the mean, so total light is kept.
vec4 estimate(vec2 uv) {
    float T = uSmooth * 3.0;
    vec4 res = textureLod(uDensity, uv, float(LEVELS - 1));
    for (int l = LEVELS - 2; l >= 0; l--) {
        vec4 s = textureLod(uDensity, uv, float(l));
        float w = smoothstep(0.0, T, s.a * (1.0 - uDecay) * uNorm);
        res = mix(res, s, w);
    }
    return res;
}

void main() {
    vec4 d = uSmooth > 0.001 ? estimate(vTexCoord) : textureLod(uDensity, vTexCoord, 0.0);
    vec3 hue = d.rgb / max(d.a, 1e-6);
    // Steady-state density under decay d is ~1/(1-d); normalise so Decay changes smoothness, not brightness.
    float a = max(d.a * (1.0 - uDecay) - uToe, 0.0);   // toe: drop isolated single hits
    float v = clamp(log(1.0 + a * uExposure) * uBrightness, 0.0, 1.0);
    float ig = 1.0 / uGamma;
    // Vibrancy blends per-channel gamma (saturating) with gamma on the density only (hue-preserving).
    vec3 perChannel = pow(clamp(hue * v, 0.0, 1.0), vec3(ig));
    vec3 hueKeep = hue * pow(v, ig);
    FragColor = vec4(mix(perChannel, hueKeep, uVibrancy), 1.0);
}
