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
uniform float uRoundness;

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
    // Calibrated so that at uZoom = 1.0 and default uPersp = 0.5, the central facet fills the frame height
    float fov = 0.5 + uPersp * 1.0;
    vec3 ray = normalize(vec3(p2 / max(0.01, uZoom * fov), -1.0));

    // Apply 3D rotation: Roll, Pitch, Yaw
    mat3 rot = rotationMatrixY(uYaw) * rotationMatrixX(uPitch) * rotationMatrixZ(uRoll);
    vec3 p = rot * ray;

    // Iterative folding into the fundamental domain of Coxeter group A3 (Tetrahedral group)
    // 6 reflection planes divide space into 24 fundamental chambers.
    for (int i = 0; i < 4; i++) {
        if (p.x + p.y < 0.0) p.xy = -p.yx;
        if (p.x + p.z < 0.0) p.xz = -p.zx;
        if (p.y + p.z < 0.0) p.yz = -p.zy;
        if (p.x < p.y) p.xy = p.yx;
        if (p.y < p.z) p.yz = p.zy;
        if (p.x < p.y) p.xy = p.yx;
    }

    // In the fundamental chamber, p.x >= p.y >= |p.z| >= 0 with p.x > 0.
    // Project ray onto the boundary face at x = 1:
    float px = max(0.001, p.x);
    vec2 proj = vec2(p.y, p.z) / px;

    // Separation expands/contracts the facet scale
    float cellScale = 1.0 + uSeparation * 2.5;
    vec2 pCell = proj * cellScale;

    // Shape boundary: smooth transition from square quad (0.0) to circular disc (1.0)
    float squareDist = max(abs(pCell.x), abs(pCell.y));
    float circleDist = length(pCell);
    float shapeDist = mix(squareDist, circleDist, uRoundness);
    float borderFade = smoothstep(1.0, 0.96, shapeDist);

    // Map planar projection into [0, 1] texture coordinates centered at (0.5, 0.5)
    vec2 sampleUV = vec2(0.5) + pCell * 0.5;

    // Headlight depth dimming based on ray intersection distance
    float dist = 1.0 / px;
    float depthFactor = 1.0 - (dist - 1.0) * 0.8 * uDepthDim;
    float minDim = max(0.02, 1.0 - uDepthDim);
    float atten = clamp(depthFactor, minDim, 1.0 + uDepthDim * 0.5);

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
