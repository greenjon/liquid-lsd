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

// 3D rotations & perspective
// TODO: Remove 3D Persp and Rotate X/Y (Pitch/Yaw) controls (held off for now)
uniform float uYaw;
uniform float uPitch;
uniform float uPersp;

uniform float uThickness;
uniform float uGlobalScale;
uniform float uGlobalRotation;
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

    // 3. 2D Position
    vec3 pos = vec3(localP, 0.0);

    // 4. Apply Roll (rotate around local Z-axis by uGlobalRotation)
    float cosRoll = cos(uGlobalRotation);
    float sinRoll = sin(uGlobalRotation);
    vec3 rPos = vec3(
        pos.x * cosRoll - pos.y * sinRoll,
        pos.x * sinRoll + pos.y * cosRoll,
        pos.z
    );

    // TODO: Remove 3D Persp and Rotate X/Y (Pitch/Yaw) controls (held off for now)
    // 5. Apply Pitch (rotate around X-axis by uPitch * PI)
    float cosPitch = cos(uPitch * PI);
    float sinPitch = sin(uPitch * PI);
    rPos = vec3(
        rPos.x,
        rPos.y * cosPitch - rPos.z * sinPitch,
        rPos.y * sinPitch + rPos.z * cosPitch
    );

    // 6. Apply Yaw (rotate around Y-axis by uYaw * PI)
    float cosYaw = cos(uYaw * PI);
    float sinYaw = sin(uYaw * PI);
    rPos = vec3(
        rPos.x * cosYaw + rPos.z * sinYaw,
        rPos.y,
        -rPos.x * sinYaw + rPos.z * cosYaw
    );

    // 7. Apply Perspective Projection
    // Interpolate between flat/orthographic (uPersp = 0.0) and full perspective (uPersp = 1.0)
    float cameraDistance = 2.0;
    float perspScale = 1.0 / (cameraDistance - rPos.z * uPersp);
    vec2 finalPos = rPos.xy * (perspScale * uGlobalScale);

    // 8. Aspect ratio correction
    finalPos.x /= uAspectRatio;

    gl_Position = vec4(finalPos, rPos.z * 0.1, 1.0);
}
