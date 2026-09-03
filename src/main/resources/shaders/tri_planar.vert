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

    // Determine plane orientation based on gl_InstanceID
    // Instances 0..2: 3 Orthogonal Planes (XY, YZ, ZX)
    // Instances 3..5: Negative counterparts for Cube Cage mode (-XY, -YZ, -ZX)
    vec3 localPos;
    vec3 normal;

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

    // Offset plane along its normal by separation
    localPos += normal * uSeparation;

    // Apply 3D rotations: Roll (Z), Pitch (X), Yaw (Y)
    mat3 rot = rotationMatrixY(uYaw) * rotationMatrixX(uPitch) * rotationMatrixZ(uRoll);
    vec3 rPos = rot * localPos;

    // Perspective Projection
    // Camera is situated at (0, 0, 2.5) looking towards origin
    float cameraDistance = 2.5;
    float w = max(0.05, cameraDistance - rPos.z * uPersp);

    // Apply zoom and aspect ratio to clip coordinates so hardware division by w yields perspective-correct interpolation
    float clipX = (rPos.x * uZoom * 1.5) / uAspectRatio;
    float clipY = rPos.y * uZoom * 1.5;
    float clipZ = rPos.z * 0.1;

    vCameraDepth = rPos.z;
    gl_Position = vec4(clipX, clipY, clipZ, w);
}
