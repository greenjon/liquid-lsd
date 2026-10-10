#version 330 core
// Flame spike tone-map pass: density buffer (rgb = weighted palette color, a = hit weight) -> display color.
in vec2 vTexCoord;
out vec4 FragColor;

uniform sampler2D uDensity;
uniform float uExposure;
uniform float uBrightness;
uniform float uGamma;
uniform float uVibrancy;
uniform float uToe;
uniform float uDecay;

void main() {
    vec4 d = texture(uDensity, vTexCoord);
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
