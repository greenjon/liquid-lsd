#version 330 core

in vec2 vCoord;
in vec4 vColorCoord; // x: hopfTheta, y: hopfPhi, z: w_depth, w: depthZ
in float vAlphaFade;

out vec4 fragColor;

uniform int uPassType; // 0 = Strut Edge, 1 = Node Joint
uniform float uHueOffset;
uniform float uHueSpread;
uniform float uColorMode;
uniform float uSaturation;
uniform float uBrightness;
uniform float uGlow;
uniform float uOpacity;
uniform float uDepthFog;
uniform float uAlpha;

// Cosine palette generator (Inigo Quilez)
vec3 palette(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
    return a + b * cos(6.2831853 * (c * t + d));
}

vec3 adjustColor(vec3 col, float sat, float bright) {
    float g = dot(col, vec3(0.299, 0.587, 0.114));
    return max(vec3(0.0), mix(vec3(g), col, sat) * bright);
}

void main() {
    float radialDist = 0.0;
    float coreProfile = 0.0;
    float glowProfile = 0.0;

    if (uPassType == 0) {
        // Strut edge ribbon: vCoord.y is in [-1, 1]
        radialDist = abs(vCoord.y);
        if (radialDist > 1.0) discard;

        // Sharp anti-aliased core and soft exponential halo
        coreProfile = smoothstep(1.0, 0.0, radialDist);
        glowProfile = exp(-radialDist * 3.0) * uGlow;
    } else {
        // Node joint: vCoord is in [-1, 1] x [-1, 1]
        radialDist = length(vCoord);
        if (radialDist > 1.0) discard;

        coreProfile = smoothstep(1.0, 0.0, radialDist);
        glowProfile = exp(-radialDist * 3.5) * (uGlow * 1.5);
    }

    // Color generation
    float colorMetric = 0.0;
    if (uColorMode < 0.5) {
        // Hopf Fibration Coordinates: swirl along tori
        colorMetric = vColorCoord.x * 0.5 + vColorCoord.y * 1.5;
    } else if (uColorMode < 1.5) {
        // 4D W-Depth (continuous hyper-depth gradient)
        colorMetric = (vColorCoord.z + 1.0) * 0.5;
    } else {
        // 3D Eye Depth
        colorMetric = (vColorCoord.w - 1.5) * 0.25;
    }

    float t = fract(uHueOffset + colorMetric * uHueSpread);

    // Vivid neon palette
    vec3 palA = vec3(0.5, 0.5, 0.5);
    vec3 palB = vec3(0.5, 0.5, 0.5);
    vec3 palC = vec3(1.0, 1.0, 1.0);
    vec3 palD = vec3(0.0, 0.333, 0.667);

    vec3 baseCol = palette(t, palA, palB, palC, palD);
    baseCol = adjustColor(baseCol, uSaturation, uBrightness);

    // Blend core highlight (white hot center) with neon outer glow
    vec3 outColor = mix(baseCol * glowProfile, vec3(1.0) * uBrightness, coreProfile * coreProfile * 0.7);

    // Depth fog attenuation (distant 3D elements fade out smoothly)
    float fog = clamp((vColorCoord.w - 1.5) * 0.25 * uDepthFog, 0.0, 0.85);
    outColor = mix(outColor, vec3(0.0), fog);

    float finalAlpha = clamp((coreProfile * 0.8 + glowProfile * 0.4) * uOpacity * (1.0 - fog) * uAlpha, 0.0, 1.0);

    fragColor = vec4(outColor * finalAlpha, finalAlpha);
}
