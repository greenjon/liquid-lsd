#version 330 core
in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTex1;
uniform sampler2D uTex2;
uniform int uMode; // 0 = ADD, 1 = SCREEN, 2 = MULT, 3 = MAX, 4 = XFADE
uniform float uBalance; // 0.0 = Tex1 (Deck A), 1.0 = Tex2 (Deck B)
uniform float uAlpha; // Master output alpha / gain
uniform float uBloom; // 0.0 = no bloom, 1.0 = full bloom

vec4 sampleBlended(vec2 uv) {
    vec4 color1 = texture(uTex1, uv);
    vec4 color2 = texture(uTex2, uv);
    float t = clamp(uBalance, 0.0, 1.0);
    vec4 blended = vec4(0.0);

    if (uMode == 0) { // ADD
        float w1 = clamp((1.0 - t) * 2.0, 0.0, 1.0);
        float w2 = clamp(t * 2.0, 0.0, 1.0);
        blended = color1 * w1 + color2 * w2;
    } else if (uMode == 1) { // SCREEN
        vec4 c1 = color1 * (1.0 - t);
        vec4 c2 = color2 * t;
        blended = c1 + c2 - c1 * c2;
    } else if (uMode == 2) { // MULT
        vec4 mult = color1 * color2;
        if (t < 0.5) {
            blended = mix(color1, mult, t * 2.0);
        } else {
            blended = mix(mult, color2, (t - 0.5) * 2.0);
        }
    } else if (uMode == 3) { // MAX
        blended = max(color1 * (1.0 - t), color2 * t);
    } else { // XFADE (mode 4)
        float w1 = clamp(1.0 - pow(t, 4.0), 0.0, 1.0);
        float w2 = clamp(1.0 - pow(1.0 - t, 4.0), 0.0, 1.0);
        blended = color1 * w1 + color2 * w2;
    }
    return blended;
}

void main() {
    vec4 baseColor = sampleBlended(vTexCoord);

    if (uBloom > 0.0) {
        float stepX = 0.004 * uBloom;
        float stepY = 0.004 * uBloom;

        vec4 blur = vec4(0.0);
        blur += sampleBlended(vTexCoord + vec2(-stepX, -stepY)) * 0.075;
        blur += sampleBlended(vTexCoord + vec2(0.0, -stepY)) * 0.125;
        blur += sampleBlended(vTexCoord + vec2(stepX, -stepY)) * 0.075;
        blur += sampleBlended(vTexCoord + vec2(-stepX, 0.0)) * 0.125;
        blur += baseColor * 0.2;
        blur += sampleBlended(vTexCoord + vec2(stepX, 0.0)) * 0.125;
        blur += sampleBlended(vTexCoord + vec2(-stepX, stepY)) * 0.075;
        blur += sampleBlended(vTexCoord + vec2(0.0, stepY)) * 0.125;
        blur += sampleBlended(vTexCoord + vec2(stepX, stepY)) * 0.075;

        // Screen-blend the blurred highlight additively
        fragColor = (baseColor + blur * uBloom * 1.5) * uAlpha;
    } else {
        fragColor = baseColor * uAlpha;
    }
}
