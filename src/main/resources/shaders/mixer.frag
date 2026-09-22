#version 330 core
in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTex1; // Transition output (blendFBO)
uniform sampler2D uTexBG; // Deck BG layer
uniform float uProgress = 0.5; // 0.0 = Deck A, 1.0 = Deck B (ISF transition mode)
uniform float uBgAlpha = 1.0; // Background layer alpha multiplier
uniform float uLevelA = 1.0; // Deck A channel level multiplier
uniform float uLevelB = 1.0; // Deck B channel level multiplier
uniform float uLevelBG = 1.0; // Deck BG channel level multiplier
uniform float uMasterLevel = 1.0; // Master channel level multiplier

vec4 sampleBlended(vec2 uv) {
    float fgLevel = mix(uLevelA, uLevelB, clamp(uProgress, 0.0, 1.0));
    return texture(uTex1, uv) * fgLevel;
}

vec4 sampleComposite(vec2 uv) {
    vec4 bg = texture(uTexBG, uv) * (uBgAlpha * uLevelBG);
    vec4 fg = sampleBlended(uv);
    vec3 rgb = fg.rgb + bg.rgb * (1.0 - fg.a);
    float a = clamp(fg.a + bg.a * (1.0 - fg.a), 0.0, 1.0);
    return vec4(rgb, a);
}

void main() {
    vec4 baseColor = sampleComposite(vTexCoord);
    fragColor = baseColor * uMasterLevel;
}
