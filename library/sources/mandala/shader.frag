#version 330 core
in float vPhase;
in vec2 vCurvePos;

uniform float uHueOffset;
uniform float uHueSweep;
uniform float uAlpha;
uniform float uDepth;
uniform float uMaxR;

out vec4 fragColor;

// Helper to convert HSV to RGB color space
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    // Hue varies along the length of the curve
    float hue = fract(uHueOffset + vPhase * uHueSweep);

    // Constant saturation for rich, vibrant coloring (0.8)
    float saturation = 0.8;

    // Depth effect: closer to the center gets dimmer, outer reach gets brighter (radial variance)
    float r = length(vCurvePos);
    float rNorm = clamp(r / uMaxR, 0.0, 1.0);
    float f = pow(rNorm, 0.7);
    float v = 0.85 * (1.0 - uDepth + uDepth * f);

    vec3 rgb = hsv2rgb(vec3(hue, saturation, v));

    fragColor = vec4(rgb, uAlpha);
}
