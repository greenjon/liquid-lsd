#version 330 core

layout(location = 0) in vec4 aPosA;
layout(location = 1) in vec4 aPosB;
layout(location = 2) in vec2 aHopfA;
layout(location = 3) in vec2 aHopfB;
layout(location = 4) in vec2 aCorner;

uniform int uPassType; // 0 = Strut Edge, 1 = Node Joint
uniform float uRotateXW;
uniform float uRotateYW;
uniform float uRotateZW;
uniform float uRotateX;
uniform float uRotateY;
uniform float uRotateZ;
uniform float uCameraDist;
uniform float uProjectionMode;
uniform float uZoom;
uniform float uThickness;
uniform float uNodeSize;
uniform vec2 uResolution;

out vec2 vCoord;
out vec4 vColorCoord; // x: hopfTheta, y: hopfPhi, z: w_depth, w: z_3d_depth
out float vAlphaFade;

vec4 rotate4D(vec4 p) {
    // 1. XW rotation
    float cxw = cos(uRotateXW), sxw = sin(uRotateXW);
    p = vec4(cxw * p.x - sxw * p.w, p.y, p.z, sxw * p.x + cxw * p.w);
    
    // 2. YW rotation
    float cyw = cos(uRotateYW), syw = sin(uRotateYW);
    p = vec4(p.x, cyw * p.y - syw * p.w, p.z, syw * p.y + cyw * p.w);

    // 3. ZW rotation
    float czw = cos(uRotateZW), szw = sin(uRotateZW);
    p = vec4(p.x, p.y, czw * p.z - szw * p.w, szw * p.z + czw * p.w);

    return p;
}

mat3 rotate3DMatrix() {
    float cx = cos(uRotateX), sx = sin(uRotateX);
    float cy = cos(uRotateY), sy = sin(uRotateY);
    float cz = cos(uRotateZ), sz = sin(uRotateZ);
    mat3 rx = mat3(1.0, 0.0, 0.0,  0.0, cx, -sx,  0.0, sx, cx);
    mat3 ry = mat3(cy, 0.0, sy,   0.0, 1.0, 0.0,  -sy, 0.0, cy);
    mat3 rz = mat3(cz, -sz, 0.0,  sz, cz, 0.0,   0.0, 0.0, 1.0);
    return rz * ry * rx;
}

vec3 project4Dto3D(vec4 p) {
    float d = max(1.01, uCameraDist);
    if (uProjectionMode > 0.5) {
        // Stereographic projection S3 -> R3
        float denom = max(0.01, 1.0 - p.w * (1.0 / d));
        return p.xyz / denom;
    } else {
        // Central 4D perspective projection
        float denom = max(0.05, d - p.w);
        return p.xyz / denom;
    }
}

void main() {
    float aspect = uResolution.x / max(1.0, uResolution.y);
    mat3 r3D = rotate3DMatrix();

    if (uPassType == 0) {
        // Strut edge ribbon
        vec4 rPosA = rotate4D(aPosA);
        vec4 rPosB = rotate4D(aPosB);

        vec3 p3A = r3D * project4Dto3D(rPosA);
        vec3 p3B = r3D * project4Dto3D(rPosB);

        // Standard 3D perspective camera (eye at z = 3.5)
        float camZ = 3.5;
        float zA = max(0.2, camZ - p3A.z);
        float zB = max(0.2, camZ - p3B.z);

        vec2 screenA = (p3A.xy / zA) * uZoom;
        vec2 screenB = (p3B.xy / zB) * uZoom;

        // Correct for screen aspect ratio
        screenA.x /= aspect;
        screenB.x /= aspect;

        vec2 dir = screenB - screenA;
        float len = length(dir);
        vec2 norm = (len > 1e-5) ? vec2(-dir.y, dir.x) / len : vec2(0.0, 1.0);

        float t = aCorner.x;
        float side = aCorner.y;

        vec2 centerPos = mix(screenA, screenB, t);
        float depthZ = mix(zA, zB, t);
        float wDepth = mix(rPosA.w, rPosB.w, t);
        vec2 hopf = mix(aHopfA, aHopfB, t);

        // Compute screen-space strut width with distance scaling
        float width = uThickness * (camZ / depthZ) * 2.0;
        vec2 vertexPos = centerPos + norm * (side * width);

        gl_Position = vec4(vertexPos, 0.0, 1.0);
        vCoord = vec2(t, side);
        vColorCoord = vec4(hopf.x, hopf.y, wDepth, depthZ);
        vAlphaFade = 1.0;
    } else {
        // Node Joint billboard
        // In node pass, aPosA contains node pos4, aPosB.xy contains hopf coords, aCorner contains quad offset
        vec4 rPos = rotate4D(aPosA);
        vec3 p3 = r3D * project4Dto3D(rPos);

        float camZ = 3.5;
        float depthZ = max(0.2, camZ - p3.z);
        vec2 screenPos = (p3.xy / depthZ) * uZoom;
        screenPos.x /= aspect;

        vec2 corner = aCorner;
        float size = uNodeSize * (camZ / depthZ) * 2.0;
        vec2 vertexPos = screenPos + vec2(corner.x / aspect, corner.y) * size;

        gl_Position = vec4(vertexPos, 0.0, 1.0);
        vCoord = corner; // in [-1, 1] x [-1, 1]
        vColorCoord = vec4(aPosB.x, aPosB.y, rPos.w, depthZ);
        vAlphaFade = 1.0;
    }
}
