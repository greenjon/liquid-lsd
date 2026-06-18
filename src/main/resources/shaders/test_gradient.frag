#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform float uTime;

void main() {
    // Create a colorful gradient pattern based on UV coordinates and time
    vec2 uv = vTexCoord;
    
    // Animated gradient
    float r = 0.5 + 0.5 * sin(uv.x * 3.14159 + uTime);
    float g = 0.5 + 0.5 * sin(uv.y * 3.14159 + uTime * 0.7);
    float b = 0.5 + 0.5 * cos((uv.x + uv.y) * 3.14159 + uTime * 0.5);
    
    fragColor = vec4(r, g, b, 1.0);
}
