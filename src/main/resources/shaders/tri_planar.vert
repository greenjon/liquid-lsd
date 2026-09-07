#version 330 core

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform float uPitch;
uniform float uYaw;
uniform float uRoll;
uniform float uZoom;
uniform float uPersp;
uniform float uSeparation;
uniform float uAspectRatio;
uniform int u3DMode; // 1 = Tri-Axial (3P @ 90°), 2 = Cube Cage (6P), 3 = Hex-Planar (6P @ 60°)

out vec2 vTexCoord;
out float vCameraDepth;

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

void main() {
    vTexCoord = aTexCoord;

    vec3 localPos;
    vec3 normal;

    if (u3DMode == 3) {
        // Hex-Planar: 6 reflection planes of the tetrahedral group A3 (intersecting at 60°)
        const float invSqrt2 = 0.70710678;
        int inst = gl_InstanceID % 6;

        if (inst == 0) {
            // Plane X - Y = 0 (Normal: (1, -1, 0) / sqrt(2))
            localPos = (aPosition.x * invSqrt2) * vec3(1.0, 1.0, 0.0) + aPosition.y * vec3(0.0, 0.0, 1.0);
            normal = vec3(1.0, -1.0, 0.0) * invSqrt2;
        } else if (inst == 1) {
            // Plane X + Y = 0 (Normal: (1, 1, 0) / sqrt(2))
            localPos = (aPosition.x * invSqrt2) * vec3(-1.0, 1.0, 0.0) + aPosition.y * vec3(0.0, 0.0, 1.0);
            normal = vec3(1.0, 1.0, 0.0) * invSqrt2;
        } else if (inst == 2) {
            // Plane Y - Z = 0 (Normal: (0, 1, -1) / sqrt(2))
            localPos = (aPosition.x * invSqrt2) * vec3(0.0, 1.0, 1.0) + aPosition.y * vec3(1.0, 0.0, 0.0);
            normal = vec3(0.0, 1.0, -1.0) * invSqrt2;
        } else if (inst == 3) {
            // Plane Y + Z = 0 (Normal: (0, 1, 1) / sqrt(2))
            localPos = (aPosition.x * invSqrt2) * vec3(0.0, -1.0, 1.0) + aPosition.y * vec3(1.0, 0.0, 0.0);
            normal = vec3(0.0, 1.0, 1.0) * invSqrt2;
        } else if (inst == 4) {
            // Plane Z - X = 0 (Normal: (-1, 0, 1) / sqrt(2))
            localPos = (aPosition.x * invSqrt2) * vec3(1.0, 0.0, 1.0) + aPosition.y * vec3(0.0, 1.0, 0.0);
            normal = vec3(-1.0, 0.0, 1.0) * invSqrt2;
        } else {
            // Plane Z + X = 0 (Normal: (1, 0, 1) / sqrt(2))
            localPos = (aPosition.x * invSqrt2) * vec3(1.0, 0.0, -1.0) + aPosition.y * vec3(0.0, 1.0, 0.0);
            normal = vec3(1.0, 0.0, 1.0) * invSqrt2;
        }
    } else {
        // Instances 0..2: 3 Orthogonal Planes (XY, YZ, ZX)
        // Instances 3..5: Negative counterparts for Cube Cage mode (-XY, -YZ, -ZX)
        int inst = gl_InstanceID % 3;
        float signVal = (gl_InstanceID >= 3) ? -1.0 : 1.0;

        if (inst == 0) {
            // XY plane (normal along Z)
            localPos = vec3(aPosition.x, aPosition.y, 0.0);
            normal = vec3(0.0, 0.0, 1.0) * signVal;
        } else if (inst == 1) {
            // YZ plane (normal along X)
            localPos = vec3(0.0, aPosition.x, aPosition.y);
            normal = vec3(1.0, 0.0, 0.0) * signVal;
        } else {
            // ZX plane (normal along Y)
            localPos = vec3(aPosition.y, 0.0, aPosition.x);
            normal = vec3(0.0, 1.0, 0.0) * signVal;
        }
    }

    // Offset plane along its normal by separation.
    // In Mode 2 (Cube Cage), the 6 planes have a unit base offset (1.0) along their normals to form a true cube box.
    float baseOffset = (u3DMode == 2) ? 1.0 : 0.0;
    localPos += normal * (baseOffset + uSeparation);

    // Apply 3D rotations: Roll (Z), Pitch (X), Yaw (Y)
    mat3 rot = rotationMatrixY(uYaw) * rotationMatrixX(uPitch) * rotationMatrixZ(uRoll);
    vec3 rPos = rot * localPos;

    // Perspective Projection
    // Camera is situated at (0, 0, 2.5) looking towards origin
    float cameraDistance = 2.5;
    float w = max(0.05, cameraDistance - rPos.z * uPersp);

    // Scale clip coordinates by cameraDistance so that at uZoom = 1.0 (with rPos.z = 0 and uPersp = 0.0),
    // hardware division by w yields NDC [-1, 1], exactly filling the frame height identical to 2D Flat mode.
    float clipX = (rPos.x * uZoom * cameraDistance) / uAspectRatio;
    float clipY = rPos.y * uZoom * cameraDistance;
    float clipZ = rPos.z * 0.1;

    vCameraDepth = rPos.z;
    gl_Position = vec4(clipX, clipY, clipZ, w);
}
