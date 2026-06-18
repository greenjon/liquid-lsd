#version 330 core

layout(location = 0) out vec4 fragColor;

void main() {
    // Just output solid green - no inputs needed at all
    fragColor = vec4(0.0, 1.0, 0.0, 1.0);
}
