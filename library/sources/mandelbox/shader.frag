#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform float uScale;
uniform float uMinRadius;
uniform float uFixedRadius;
uniform float uIterations;
uniform float uFoldLimit;
uniform float uZoom;
uniform float uColorShift;
uniform float uRotateY;
uniform float uRotateX;
uniform float uRotateZ;
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

mat3 rotateZ(float theta) {
    float c = cos(theta);
    float s = sin(theta);
    return mat3(
        vec3(c, -s, 0.0),
        vec3(s, c, 0.0),
        vec3(0.0, 0.0, 1.0)
    );
}

mat3 rotateY(float theta) {
    float c = cos(theta), s = sin(theta);
    return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c);
}

// Box fold
void boxFold(inout vec3 z, float limit) {
    z = clamp(z, -limit, limit) * 2.0 * limit - z;
}

// Sphere fold
void sphereFold(inout vec3 z, inout float dr, float minRad2, float fixedRad2) {
    float r2 = dot(z, z);
    if (r2 < minRad2) {
        float temp = (fixedRad2 / minRad2);
        z *= temp;
        dr *= temp;
    } else if (r2 < fixedRad2) {
        float temp = (fixedRad2 / r2);
        z *= temp;
        dr *= temp;
    }
}

float map(vec3 p, out float trap) {
    vec3 z = p;
    float dr = 1.0;
    trap = 1e10;
    
    float minRad2 = uMinRadius * uMinRadius;
    float fixedRad2 = uFixedRadius * uFixedRadius;
    int maxIt = int(uIterations);
    
    for (int i = 0; i < 15; i++) {
        if (i >= maxIt) break;
        
        boxFold(z, uFoldLimit);
        sphereFold(z, dr, minRad2, fixedRad2);
        
        z = uScale * z + p;
        dr = dr * abs(uScale) + 1.0;
        
        // Track orbit minimum distance for coloring
        trap = min(trap, dot(z, z));
    }
    
    return length(z) / abs(dr);
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

    mat3 camRot = rotateZ(uRotateZ) * rotateY(uRotateY) * rotateX(uRotateX);
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
