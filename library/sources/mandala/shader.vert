#version 330 core
layout (location = 0) in vec2 aPhaseSide; // x = phase (0..1), y = side (-1 or 1)

uniform float uL1;
uniform float uL2;
uniform float uL3;
uniform float uL4;
uniform float uA;
uniform float uB;
uniform float uC;
uniform float uD;

// 3D mode & projection uniforms
uniform float u3DMode;
uniform float uSphereWrapX;
uniform float uSphereWrapY;
uniform float uMirrorGroup;
uniform float uPermuteXY;
uniform float uPermuteYZ;
uniform float uPermuteZX;
uniform float uMaxR;

// 3D rotations & perspective
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

vec3 calculateChain(float timeParam) {
    vec3 f1 = vec3(uA, uB, 0.0);
    vec3 f2 = vec3(0.0, uC, uD);
    vec3 f3 = vec3(uD, 0.0, uA);
    vec3 f4 = vec3(uB, uC, uD);

    mat3 rot1 = rotationMatrixX(f1.x * timeParam) * rotationMatrixY(f1.y * timeParam) * rotationMatrixZ(f1.z * timeParam);
    mat3 rot2 = rotationMatrixX(f2.x * timeParam) * rotationMatrixY(f2.y * timeParam) * rotationMatrixZ(f2.z * timeParam);
    mat3 rot3 = rotationMatrixX(f3.x * timeParam) * rotationMatrixY(f3.y * timeParam) * rotationMatrixZ(f3.z * timeParam);
    mat3 rot4 = rotationMatrixX(f4.x * timeParam) * rotationMatrixY(f4.y * timeParam) * rotationMatrixZ(f4.z * timeParam);

    vec3 arm1 = rot1 * vec3(uL1, 0.0, 0.0);
    vec3 arm2 = rot1 * (rot2 * vec3(uL2, 0.0, 0.0));
    vec3 arm3 = rot1 * (rot2 * (rot3 * vec3(uL3, 0.0, 0.0)));
    vec3 arm4 = rot1 * (rot2 * (rot3 * (rot4 * vec3(uL4, 0.0, 0.0))));

    return arm1 + arm2 + arm3 + arm4;
}

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

    // 3. Compute 3D position based on active mode
    vec3 pos;
    if (u3DMode < 0.5) {
        // Mode 0: 2D
        pos = vec3(localP, 0.0);
    } else if (u3DMode < 1.5) {
        // Mode 1: Spherical Mapping
        vec2 pNorm = localP / max(0.001, uMaxR);
        float theta = (pNorm.y + 1.0) * 0.5 * PI * uSphereWrapY;
        float phi = pNorm.x * PI * uSphereWrapX;
        pos = vec3(
            sin(theta) * cos(phi),
            sin(theta) * sin(phi),
            cos(theta)
        ) * uMaxR;
    } else if (u3DMode < 2.5) {
        // Mode 2: Polyhedral Reflections (Cubic / Tetrahedral)
        vec2 pNorm = localP / max(0.001, uMaxR);
        float theta = (pNorm.y + 1.0) * 0.5 * PI * uSphereWrapY;
        float phi = pNorm.x * PI * uSphereWrapX;
        vec3 base3D = vec3(
            sin(theta) * cos(phi),
            sin(theta) * sin(phi),
            cos(theta)
        ) * uMaxR;

        float sx = 1.0;
        float sy = 1.0;
        float sz = 1.0;
        if (uMirrorGroup < 0.5) {
            // Cubic mirror: 8 instances (gl_InstanceID: 0..7)
            sx = ((gl_InstanceID & 1) != 0) ? -1.0 : 1.0;
            sy = ((gl_InstanceID & 2) != 0) ? -1.0 : 1.0;
            sz = ((gl_InstanceID & 4) != 0) ? -1.0 : 1.0;
        } else {
            // Tetrahedral: 4 instances (gl_InstanceID: 0..3)
            if (gl_InstanceID == 1) {
                sx = -1.0; sy = -1.0; sz = 1.0;
            } else if (gl_InstanceID == 2) {
                sx = 1.0; sy = -1.0; sz = -1.0;
            } else if (gl_InstanceID == 3) {
                sx = -1.0; sy = 1.0; sz = -1.0;
            }
        }
        pos = base3D * vec3(sx, sy, sz);
    } else if (u3DMode < 3.5) {
        // Mode 3: Coordinate Permutation (3 instances)
        if (gl_InstanceID == 0) {
            pos = vec3(localP.x, localP.y, 0.0) * uPermuteXY;
        } else if (gl_InstanceID == 1) {
            pos = vec3(0.0, localP.x, localP.y) * uPermuteYZ;
        } else {
            pos = vec3(localP.y, 0.0, localP.x) * uPermuteZX;
        }
    } else {
        // Mode 4: 3D Kinematic Chain
        vec3 centerPos = calculateChain(t);
        vec3 centerPosNext = calculateChain(t + 0.001);

        // Resolve mirroring reflections based on uMirrorGroup
        float sx = 1.0;
        float sy = 1.0;
        float sz = 1.0;

        if (uMirrorGroup > 0.5 && uMirrorGroup < 1.5) {
            // Cubic mirroring (8 instances: gl_InstanceID: 0..7)
            sx = ((gl_InstanceID & 1) != 0) ? -1.0 : 1.0;
            sy = ((gl_InstanceID & 2) != 0) ? -1.0 : 1.0;
            sz = ((gl_InstanceID & 4) != 0) ? -1.0 : 1.0;
        } else if (uMirrorGroup >= 1.5) {
            // Tetrahedral mirroring (4 instances: gl_InstanceID: 0..3)
            if (gl_InstanceID == 1) {
                sx = -1.0; sy = -1.0; sz = 1.0;
            } else if (gl_InstanceID == 2) {
                sx = 1.0; sy = -1.0; sz = -1.0;
            } else if (gl_InstanceID == 3) {
                sx = -1.0; sy = 1.0; sz = -1.0;
            }
        }

        centerPos = centerPos * vec3(sx, sy, sz);
        centerPosNext = centerPosNext * vec3(sx, sy, sz);

        vec3 tangent3D = centerPosNext - centerPos;
        if (length(tangent3D) > 0.0001) {
            tangent3D = normalize(tangent3D);
        } else {
            tangent3D = vec3(0.0);
        }

        vec3 normal3D = cross(tangent3D, vec3(0.0, 0.0, 1.0));
        if (length(normal3D) > 0.0001) {
            normal3D = normalize(normal3D);
        } else {
            normal3D = vec3(1.0, 0.0, 0.0);
        }

        pos = centerPos + normal3D * (side * uThickness * 0.5);
        vCurvePos = centerPos.xy; // Override for fragment shader radial depth coloring
    }

    // 4. Apply Roll (rotate around local Z-axis by uGlobalRotation)
    float cosRoll = cos(uGlobalRotation);
    float sinRoll = sin(uGlobalRotation);
    vec3 rPos = vec3(
        pos.x * cosRoll - pos.y * sinRoll,
        pos.x * sinRoll + pos.y * cosRoll,
        pos.z
    );

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
