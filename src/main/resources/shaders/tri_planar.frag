#version 330 core

in vec2 vTexCoord;
in float vCameraDepth;

uniform sampler2D uTexture;
uniform float uDepthDim;      // 0.0 = uniform brightness, 1.0 = deep proximity falloff
uniform float uAlpha;         // Global alpha
uniform float uBlendAdditive; // 0.0 = standard alpha, 1.0 = additive luminous boost
uniform float uRoundness;     // 0.0 = square quad, 1.0 = circular disc

out vec4 fragColor;

void main() {
    vec4 texColor = texture(uTexture, vTexCoord);

    // Luminance-derived transparency:
    // In visual synthesizers, 2D shader sources render on black backgrounds (RGB=0, A=1).
    // Empty background space must be 100% transparent so intersecting 3D planes do not
    // cast semitransparent gray shadows over the background deck.
    float lum = max(texColor.r, max(texColor.g, texColor.b));

    // Threshold noise floor: drop residual feedback trails and black pedestals (< 1.5% brightness) to true zero
    float lumFactor = smoothstep(0.015, 0.08, lum);
    float alphaFromLum = lumFactor * clamp(lum * 1.5, 0.0, 1.0);

    // If source texture already has its own transparency (e.g. Mandala with a < 1.0), respect it;
    // otherwise derive opacity strictly from luminance.
    float baseAlpha = (texColor.a < 0.999) ? min(texColor.a, alphaFromLum) : alphaFromLum;

    // Shape boundary: smooth transition from square quad (0.0) to circular disc (1.0)
    vec2 p = (vTexCoord - vec2(0.5)) * 2.0;
    float squareDist = max(abs(p.x), abs(p.y));
    float circleDist = length(p);
    float shapeDist = mix(squareDist, circleDist, uRoundness);
    float borderFade = smoothstep(1.0, 0.96, shapeDist);

    float effectiveAlpha = baseAlpha * uAlpha * borderFade;

    // Discard any fragment that has no visible light or opacity
    if (effectiveAlpha < 0.002 || lum < 0.01 || borderFade <= 0.001) {
        discard;
    }

    // Depth cueing / headlight falloff:
    // vCameraDepth (rPos.z) ranges roughly from -1.5 to +1.5.
    // Near points (vCameraDepth > 0) stay bright or catch a soft flare;
    // Far points (vCameraDepth < 0) smoothly dim down into atmospheric haze.
    float depthFactor = 1.0 + (vCameraDepth * 0.6) * uDepthDim;
    float minDim = max(0.02, 1.0 - uDepthDim);
    float atten = clamp(depthFactor, minDim, 1.0 + uDepthDim * 0.5);

    vec3 rgb = texColor.rgb * atten * borderFade * lumFactor;

    if (uBlendAdditive > 0.5) {
        rgb *= (1.0 + lum * 0.2);
    }

    fragColor = vec4(rgb, effectiveAlpha);
}
