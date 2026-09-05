#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform float uPitch;
uniform float uYaw;
uniform float uRoll;
uniform float uZoom;
uniform float uPersp;
uniform float uSeparation;
uniform float uDepthDim;
uniform float uAlpha;
uniform float uBlendAdditive;
uniform float uAspectRatio;

mat3 rotationMatrixX(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat3(
        1.0, 0.0, 0.0,
        0.0, c,   s,
        0.0, -s,  c
    );
}

mat3 rotationMatrixY(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat3(
        c,   0.0, -s,
        0.0, 1.0, 0.0,
        s,   0.0, c
    );
}

mat3 rotationMatrixZ(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat3(
        c,   s,   0.0,
        -s,  c,   0.0,
        0.0, 0.0, 1.0
    );
}

void main() {
    // Centered aspect-corrected coordinates
    vec2 p2 = (vTexCoord - vec2(0.5)) * 2.0;
    p2.x *= uAspectRatio;

    // Camera ray setup: perspective camera looking down -Z
    float fov = mix(0.5, 2.5, uPersp);
    vec3 ray = normalize(vec3(p2 / max(0.01, uZoom * 1.5), -fov));

    // Apply 3D rotation: Roll, Pitch, Yaw
    mat3 rot = rotationMatrixY(uYaw) * rotationMatrixX(uPitch) * rotationMatrixZ(uRoll);
    vec3 p = rot * ray;

    // Track camera depth for headlight dimming
    float depthFactor = 1.0 + (p.z * 0.6) * uDepthDim;
    float minDim = max(0.02, 1.0 - uDepthDim);
    float atten = clamp(depthFactor, minDim, 1.0 + uDepthDim * 0.5);

    // Iterative folding into the fundamental domain of Coxeter group A3 (Tetrahedral group)
    // 6 reflection planes divide space into 24 fundamental chambers.
    for (int i = 0; i < 3; i++) {
        // Reflection across x - y = 0
        if (p.x < p.y) p.xy = p.yx;
        // Reflection across x + y = 0
        if (p.x + p.y < 0.0) p.xy = -p.yx;

        // Reflection across y - z = 0
        if (p.y < p.z) p.yz = p.zy;
        // Reflection across y + z = 0
        if (p.y + p.z < 0.0) p.yz = -p.zy;

        // Reflection across z - x = 0
        if (p.z < p.x) p.zx = p.xz;
        // Reflection across z + x = 0
        if (p.z + p.x < 0.0) p.zx = -p.xz;
    }

    // Now p lies inside the tetrahedral fundamental chamber
    // Project the folded vector onto 2D texture coordinates centered at (0.5, 0.5)
    float cellScale = 1.2 + uSeparation * 2.5;
    vec2 sampleUV = vec2(0.5) + vec2(p.y, p.z) * cellScale;

    // Subtle edge border softening at the 2D texture boundary
    vec2 edgeDist = min(sampleUV, 1.0 - sampleUV);
    float borderFade = smoothstep(0.0, 0.02, min(edgeDist.x, edgeDist.y));

    vec4 texColor = texture(uTexture, clamp(sampleUV, 0.0, 1.0));

    // Luminance-derived transparency
    float lum = max(texColor.r, max(texColor.g, texColor.b));
    float lumFactor = smoothstep(0.015, 0.08, lum);
    float alphaFromLum = lumFactor * clamp(lum * 1.5, 0.0, 1.0);

    float baseAlpha = (texColor.a < 0.999) ? min(texColor.a, alphaFromLum) : alphaFromLum;
    float effectiveAlpha = baseAlpha * uAlpha * borderFade;

    if (effectiveAlpha < 0.002 || lum < 0.01 || borderFade <= 0.001) {
        discard;
    }

    vec3 rgb = texColor.rgb * atten * borderFade * lumFactor;

    if (uBlendAdditive > 0.5) {
        rgb *= (1.0 + lum * 0.2);
    }

    fragColor = vec4(rgb, effectiveAlpha);
}
