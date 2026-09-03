#version 300 es
precision highp float;
layout (location = 0) in vec2 aPhaseSide; // x = phase (0..1), y = side (-1 or 1)

uniform float uL1;
uniform float uL2;
uniform float uL3;
uniform float uL4;
uniform float uA;
uniform float uB;
uniform float uC;
uniform float uD;

uniform float uThickness;
uniform float uAspectRatio;

out float vPhase;
out vec2 vCurvePos;

const float PI = 3.14159265359;

void main() {
    float phase = aPhaseSide.x;
    float side = aPhaseSide.y;
    vPhase = phase;

    float t = phase * 2.0 * PI;

    // 1. Calculate 2D position on the curve (x, y)
    float x = uL1 * cos(t * uA) + uL2 * cos(t * uB) + uL3 * cos(t * uC) + uL4 * cos(t * uD);
    float y = uL1 * sin(t * uA) + uL2 * sin(t * uB) + uL3 * sin(t * uC) + uL4 * sin(t * uD);
    vCurvePos = vec2(x, y); // Pass unscaled 2D local position to fragment shader for depth sweep

    // 2. Calculate derivative (tangent in XY plane) to find the 2D normal vector
    float dx = -uA * uL1 * sin(t * uA) - uB * uL2 * sin(t * uB) - uC * uL3 * sin(t * uC) - uD * uL4 * sin(t * uD);
    float dy =  uA * uL1 * cos(t * uA) + uB * uL2 * cos(t * uB) + uC * uL3 * cos(t * uC) + uD * uL4 * cos(t * uD);
    vec2 tangent = vec2(dx, dy);

    vec2 normal = vec2(-tangent.y, tangent.x);
    if (length(normal) > 0.0001) {
        normal = normalize(normal);
    } else {
        normal = vec2(0.0);
    }

    // Offset position along local normal to construct ribbon geometry in 2D
    vec2 localP = vec2(
        x + normal.x * (side * uThickness * 0.5),
        y + normal.y * (side * uThickness * 0.5)
    );

    // 3. 2D Position scaled to normalized device coordinates
    vec2 finalPos = localP * 0.5;

    // 4. Aspect ratio correction
    finalPos.x /= uAspectRatio;

    gl_Position = vec4(finalPos, 0.0, 1.0);
}
