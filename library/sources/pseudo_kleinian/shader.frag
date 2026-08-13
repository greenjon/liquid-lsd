#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform float uScale;
uniform float uRadius;
uniform float uCX;
uniform float uCY;
uniform float uCZ;
uniform float uRotateX;
uniform float uRotateY;
uniform float uRotateZ;
uniform float uIterations;
uniform float uZoom;
uniform float uColorShift;
uniform float uCamRotateY;
uniform float uCamRotateX;
uniform float uCamRotateZ;
uniform float uAlpha;
uniform vec2 uResolution;
uniform float uGlow;

const int MAX_RAY_STEPS = 80;
const float MIN_DIST = 0.001;
const float MAX_DIST = 10.0;

// Rotation helpers
mat3 rotateX(float theta) {
    float c = cos(theta), s = sin(theta);
    return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c);
}
mat3 rotateY(float theta) {
    float c = cos(theta), s = sin(theta);
    return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c);
}
mat3 rotateZ(float theta) {
    float c = cos(theta), s = sin(theta);
    return mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0);
}

float map(vec3 p, out float trap) {
    vec3 z = p;
    float dr = 1.0;
    trap = 1e10;
    
    vec3 offset = vec3(uCX, uCY, uCZ);
    mat3 rotMat = rotateX(uRotateX) * rotateY(uRotateY) * rotateZ(uRotateZ);
    
    int maxIt = int(uIterations);
    float minRad2 = uRadius * uRadius;
    
    for (int i = 0; i < 15; i++) {
        if (i >= maxIt) break;
        
        // 1. Component-wise box fold
        z = 2.0 * clamp(z, -1.0, 1.0) - z;
        
        // 2. Conditional sphere inversion fold
        float r2 = dot(z, z);
        if (r2 < minRad2) {
            float temp = 1.0 / max(minRad2, 0.0001);
            z *= temp;
            dr *= temp;
        }
        
        // 3. Scaling and Translation
        z = z * uScale + offset;
        dr = dr * abs(uScale) + 1.0;
        
        // 4. Rotation Matrix (injected right after translation)
        z = rotMat * z;
        
        // Track orbit minimum distance for coloring
        trap = min(trap, dot(z, z));
    }
    
    return 0.5 * length(z) / abs(dr);
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

    // Camera setup
    vec3 ro = vec3(0.0, 0.0, -3.0 / uZoom);
    vec3 rd = normalize(vec3(uv, 1.2));

    mat3 camRot = rotateZ(uCamRotateZ) * rotateY(uCamRotateY) * rotateX(uCamRotateX);
    ro = camRot * ro;
    rd = camRot * rd;

    float t = 0.0;
    float trap = 0.0;
    int stepsTaken = 0;
    bool hit = false;
    
    // Fudge/step multiplier to prevent overshooting of the fractal details
    float fudge = 0.85;

    for (int i = 0; i < MAX_RAY_STEPS; i++) {
        vec3 p = ro + rd * t;
        float d = map(p, trap);
        if (d < MIN_DIST) {
            hit = true;
            break;
        }
        t += d * fudge;
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
