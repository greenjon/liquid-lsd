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
uniform float uRotateY;
uniform float uRotateX;
uniform float uRotateZ;
uniform float uAlpha;
uniform vec2 uResolution;
uniform float uGlow;

// New uniforms for advanced raymarching tricks
uniform float uRepeatSpacing;
uniform float uRepeat3D;
uniform float uFlySpeed;
uniform float uTrapMode;
uniform float uTrapGlow;
uniform float uSmoothness;
uniform float uTime;
uniform float uNormalColoring;
uniform float uNormalFrequency;

const int MAX_RAY_STEPS = 80;
const float MIN_DIST = 0.001;
const float MAX_DIST = 10.0;

// Smooth minimum and maximum helper functions (Inigo Quilez)
float smin(float a, float b, float k) {
    if (k <= 0.0001) return min(a, b);
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float smax(float a, float b, float k) {
    if (k <= 0.0001) return max(a, b);
    float h = clamp(0.5 - 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) + k * h * (1.0 - h);
}

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

float sdTetrahedron(vec3 p, float r) {
    vec3 q = abs(p);
    float d = max(q.x + q.y, max(q.y + q.z, q.z + q.x));
    return (d - r) / sqrt(3.0);
}

float sdDodecahedron(vec3 p, float r) {
    float phi = 1.6180339887;
    vec3 n = normalize(vec3(phi, 1.0, 0.0));
    p = abs(p);
    float d = dot(p, n) - r;
    d = max(d, dot(p.yxz, n) - r);
    d = max(d, dot(p.zyx, n) - r);
    d = max(d, max(p.x, max(p.y, p.z)) * (1.0 / sqrt(3.0)) - r);
    return d;
}

float sdTruncatedIcosahedron(vec3 p, float r) {
    float phi = 1.6180339887;
    vec3 n1 = normalize(vec3(0.0, 1.0, phi));
    vec3 n2 = normalize(vec3(1.0, phi, 0.0));
    vec3 n3 = normalize(vec3(phi, 0.0, 1.0));
    p = abs(p);
    float d = dot(p, n1);
    d = max(d, dot(p, n2));
    d = max(d, dot(p, n3));
    d = max(d, dot(p, vec3(1.0/sqrt(3.0))) - r * 1.1547);
    return d - r;
}

vec3 getHexFold(vec3 z, float k) {
    z.x = k > 0.0001 ? smax(z.x, -z.x, k) : abs(z.x);
    z.y = k > 0.0001 ? smax(z.y, -z.y, k) : abs(z.y);
    
    vec2 nHex = vec2(0.86602540378, -0.5);
    float t = dot(z.xy, nHex);
    z.xy -= 2.0 * smin(0.0, t, k) * nHex;
    
    z.z = k > 0.0001 ? smax(z.z, -z.z, k) : abs(z.z);
    return z;
}

float map(vec3 p, out float trap) {
    vec3 z = p;
    
    // 1. Apply camera flight translation
    z.z -= uTime * uFlySpeed;
    
    // 2. Apply domain repetition
    if (uRepeatSpacing > 0.0) {
        vec3 rep = vec3(uRepeatSpacing, mix(1e10, uRepeatSpacing, uRepeat3D), uRepeatSpacing);
        z = mod(z + rep * 0.5, rep) - rep * 0.5;
    }
    
    float dr = 1.0;
    trap = 1e10;
    
    // Construct rotation matrix for the loop
    vec3 offset = vec3(uFoldX, uFoldY, uFoldZ);
    mat3 rot = rotateX(uFoldAngleX) * rotateY(uFoldAngleY) * rotateZ(uFoldAngleZ);
    
    int maxIt = int(uIterations);
    for (int i = 0; i < 8; i++) {
        if (i >= maxIt) break;
        
        // 3. Rotate space BEFORE folding so that the kaleidoscope twists nicely
        z = rot * z;
        
        // 4. Fixed 6-Fold Radial Kaleidoscope Fold
        z = getHexFold(z, uSmoothness);
        
        // Offset/Translate space
        z -= offset;
        
        // 5. Scale space
        z *= uScale;
        dr *= uScale;
        
        // 6. Orbit trap for coloring
        float distVal = 0.0;
        if (uTrapMode < 0.5) {
            distVal = dot(z, z); // Origin (squared distance)
        } else if (uTrapMode < 1.5) {
            distVal = min(abs(z.x), min(abs(z.y), abs(z.z))); // Fold planes
        } else if (uTrapMode < 2.5) {
            distVal = abs(z.x); // X Fold Plane
        } else if (uTrapMode < 3.5) {
            distVal = abs(z.y); // Y Fold Plane
        } else {
            distVal = abs(z.z); // Z Fold Plane
        }
        trap = min(trap, distVal);
    }
    
    // Evaluate base primitive and divide by accumulated scale
    float dCube   = sdBox(z, vec3(1.0));
    float dSphere = sdSphere(z, 1.0);
    float dTetra  = sdTetrahedron(z, 1.0);
    float dDodeca = sdDodecahedron(z, 1.0);
    float dSoccer = sdTruncatedIcosahedron(z, 1.0);

    float finalShape = 0.0;
    float m = clamp(uShapeMorph, 0.0, 1.0) * 4.0;

    if (m < 1.0) {
        finalShape = mix(dCube, dSphere, m);
    } else if (m < 2.0) {
        finalShape = mix(dSphere, dTetra, m - 1.0);
    } else if (m < 3.0) {
        finalShape = mix(dTetra, dDodeca, m - 2.0);
    } else {
        finalShape = mix(dDodeca, dSoccer, clamp(m - 3.0, 0.0, 1.0));
    }
    
    return finalShape / dr;
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

    mat3 camRot = rotateZ(uRotateZ) * rotateY(uRotateY) * rotateX(uRotateX);
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

        float colValOrbit = fract(log(trap + 1.0) * 0.5 + uColorShift);
        float normalVal = dot(normal, vec3(0.5, 0.3, 0.8)) * uNormalFrequency;
        float colValNormal = fract(normalVal + uTime * 0.2 + uColorShift);
        float colVal = mix(colValOrbit, colValNormal, uNormalColoring);

        vec3 baseCol = palette(colVal, 
            vec3(0.5, 0.5, 0.5), 
            vec3(0.5, 0.5, 0.5), 
            vec3(2.0, 1.0, 0.0), 
            vec3(0.5, 0.20, 0.25)
        );

        color = baseCol * (diff * 0.8 + 0.2) + vec3(0.8, 0.9, 1.0) * rim * 0.4;
        
        // Add neon orbit trap surface glow in crevices/edges
        if (uTrapGlow > 0.0) {
            vec3 trapGlowCol = palette(colVal + 0.3, 
                vec3(0.5, 0.5, 0.5), 
                vec3(0.5, 0.5, 0.5), 
                vec3(2.0, 1.0, 0.0), 
                vec3(0.5, 0.20, 0.25)
            );
            color += trapGlowCol * (uTrapGlow / (0.01 + trap));
        }
    }

    if (uGlow > 0.0) {
        float glowFactor = float(stepsTaken) / float(MAX_RAY_STEPS);
        vec3 glowCol = palette(fract(uColorShift), 
            vec3(0.5, 0.5, 0.5), 
            vec3(0.5, 0.5, 0.5), 
            vec3(2.0, 1.0, 0.0), 
            vec3(0.5, 0.20, 0.25)
        );
        color += glowCol * glowFactor * uGlow * 1.5;
    }

    fragColor = vec4(color, uAlpha);
}
