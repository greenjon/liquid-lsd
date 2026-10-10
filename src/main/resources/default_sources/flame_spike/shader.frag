#version 330 core
// Flame spike tone-map pass: density buffer (rgb = weighted palette color, a = hit weight) -> display color.
in vec2 vTexCoord;
out vec4 FragColor;

uniform sampler2D uDensity;
uniform float uExposure;
uniform float uBrightness;
uniform float uGamma;
uniform float uVibrancy;

void main() {
    vec4 d = texture(uDensity, vTexCoord);
    float a = d.a;
    vec3 hue = d.rgb / max(a, 1e-6);
    float v = clamp(log(1.0 + a * uExposure) * uBrightness, 0.0, 1.0);
    float ig = 1.0 / uGamma;
    // Vibrancy blends per-channel gamma (saturating) with gamma on the density only (hue-preserving).
    vec3 perChannel = pow(clamp(hue * v, 0.0, 1.0), vec3(ig));
    vec3 hueKeep = hue * pow(v, ig);
    FragColor = vec4(mix(perChannel, hueKeep, uVibrancy), 1.0);
}
