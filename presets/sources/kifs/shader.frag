#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform float uIterations;
uniform float uScale;
uniform float uFoldX;
uniform float uFoldY;
uniform float uFoldZ;
uniform float uFoldAngleX;
uniform float uFoldAngleY;
uniform float uFoldAngleZ;
uniform float uShapeMorph; // 0 = Box, 1 = Sphere
uniform float uZoom;
uniform float uColorShift;
uniform float uYaw;
uniform float uPitch;
uniform float uRoll;
uniform float uAlpha;
uniform vec2 uResolution;
uniform float uGlow;

const int MAX_RAY_STEPS = 80;
const float MIN_DIST = 0.001;
const float MAX_DIST = 10.0;

// Rotation matrices
mat3 rotateX(float a) {
    float c = cos(a), s = sin(a);
    return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c);
}
mat3 rotateY(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c);
}
mat3 rotateZ(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0);
}

// Distance estimators for basic shapes
float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdSphere(vec3 p, float s) {
    return length(p) - s;
}

float map(vec3 p, out float trap) {
    vec3 z = p;
    float dr = 1.0;
    trap = 1e10;
    
    // Construct rotation matrix for the loop
    mat3 rotMat = rotateX(uFoldAngleX) * rotateY(uFoldAngleY) * rotateZ(uFoldAngleZ);
    vec3 offset = vec3(uFoldX, uFoldY, uFoldZ);
    
    int maxIt = int(uIterations);
    for (int i = 0; i < 8; i++) {
        if (i >= maxIt) break;
        
        // 1. Fold/mirror space across planes
        z = abs(z);
        
        // Offset/Translate space
        z -= offset;
        
        // 2. Rotate space
        z = rotMat * z;
        
        // 3. Scale space
        z *= uScale;
        dr *= uScale;
        
        // Orbit trap for coloring
        trap = min(trap, dot(z, z));
    }
    
    // Evaluate base primitive and divide by accumulated scale
    float dBox = sdBox(z, vec3(1.0)) / dr;
    float dSphere = sdSphere(z, 1.0) / dr;
    
    return mix(dBox, dSphere, uShapeMorph);
}

// Calculate normal using central differences
vec3 getNormal(vec3 p) {
    float trap;
    vec2 eps = vec2(0.001, 0.0);
    return normalize(vec3(
        map(p + eps.xyy, trap) - map(p - eps.xyy, trap),
        map(p + eps.yxy, trap) - map(p - eps.yxy, trap),
        map(p + eps.yyx, trap) - map(p - eps.yyx, trap)
    ));
}

// Cosine based palette generators by Inigo Quilez
vec3 palette(in float t, in vec3 a, in vec3 b, in vec3 c, in vec3 d) {
    return a + b * cos(6.28318 * (c * t + d));
}

void main() {
    vec2 uv = vTexCoord * 2.0 - 1.0;
    float aspect = uResolution.x / uResolution.y;
    uv.x *= aspect;

    vec3 ro = vec3(0.0, 0.0, -3.0 / uZoom);
    vec3 rd = normalize(vec3(uv, 1.2));

    mat3 camRot = rotateZ(uRoll) * rotateY(uYaw) * rotateX(uPitch);
    ro = camRot * ro;
    rd = camRot * rd;

    float t = 0.0;
    float trap = 0.0;
    int stepsTaken = 0;
    bool hit = false;

    for (int i = 0; i < MAX_RAY_STEPS; i++) {
        vec3 p = ro + rd * t;
        float d = map(p, trap);
        if (d < MIN_DIST) {
            hit = true;
            break;
        }
        t += d;
        if (t > MAX_DIST) break;
        stepsTaken = i;
    }

    vec3 color = vec3(0.0);

    if (hit) {
        vec3 p = ro + rd * t;
        vec3 normal = getNormal(p);
        vec3 lightDir = normalize(vec3(1.0, 1.0, -1.0));
        float diff = max(dot(normal, lightDir), 0.0);
        float rim = pow(1.0 + dot(normal, rd), 4.0);

        float colVal = fract(log(trap + 1.0) * 0.5 + uColorShift);
        vec3 baseCol = palette(colVal, 
            vec3(0.5, 0.5, 0.5), 
            vec3(0.5, 0.5, 0.5), 
            vec3(1.0, 1.0, 1.0), 
            vec3(0.0, 0.33, 0.67)
        );

        color = baseCol * (diff * 0.8 + 0.2) + vec3(0.8, 0.9, 1.0) * rim * 0.4;
    }

    if (uGlow > 0.0) {
        float glowFactor = float(stepsTaken) / float(MAX_RAY_STEPS);
        vec3 glowCol = palette(fract(uColorShift), 
            vec3(0.5, 0.5, 0.5), 
            vec3(0.5, 0.5, 0.5), 
            vec3(1.0, 1.0, 1.0), 
            vec3(0.0, 0.33, 0.67)
        );
        color += glowCol * glowFactor * uGlow * 1.5;
    }

    fragColor = vec4(color, uAlpha);
}
