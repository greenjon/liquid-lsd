#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

void main() {
    // Simple UV-based color (no uniforms needed)
    fragColor = vec4(vTexCoord.x, vTexCoord.y, 0.5, 1.0);
}
