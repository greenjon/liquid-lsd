#version 330 core

in vec2 vTexCoord;
in float vCameraDepth;

uniform sampler2D uTexture;
uniform float uDepthDim;      // 0.0 = uniform brightness, 1.0 = deep proximity falloff
uniform float uAlpha;         // Global alpha
uniform float uBlendAdditive; // 0.0 = standard alpha, 1.0 = additive luminous boost

out vec4 fragColor;

void main() {
    vec4 texColor = texture(uTexture, vTexCoord);

    // Subtle edge border softening to prevent harsh quad rectangular seams in 3D
    vec2 edgeDist = min(vTexCoord, 1.0 - vTexCoord);
    float borderFade = smoothstep(0.0, 0.015, min(edgeDist.x, edgeDist.y));

    // Depth cueing / headlight falloff:
    // vCameraDepth (rPos.z) ranges roughly from -1.5 to +1.5.
    // Near points (vCameraDepth > 0) stay bright or catch a soft flare;
    // Far points (vCameraDepth < 0) smoothly dim down into atmospheric haze.
    float depthFactor = 1.0 + (vCameraDepth * 0.6) * uDepthDim;
    float minDim = max(0.02, 1.0 - uDepthDim);
    float atten = clamp(depthFactor, minDim, 1.0 + uDepthDim * 0.5);

    vec3 rgb = texColor.rgb * atten * borderFade;

    // Luminance-derived transparency:
    // In visual synthesizers, black background pixels on a 2D source should act as transparent
    // so intersecting 3D planes do not occlude each other with black boxes.
    float lum = max(texColor.r, max(texColor.g, texColor.b));
    float effectiveAlpha = mix(texColor.a, lum * texColor.a, 0.9) * uAlpha * borderFade;

    if (effectiveAlpha < 0.002) {
        discard;
    }

    fragColor = vec4(rgb, effectiveAlpha);
}
