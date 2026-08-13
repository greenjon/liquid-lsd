#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform float uMode; // < 0.5 = 2D, >= 0.5 = 3D
uniform float uFrequencyN;
uniform float uFrequencyM;
uniform float uFrequencyL;
uniform float uThickness;
uniform float uWallWidth;
uniform float uScale;
uniform float uSpeed;
uniform float uZoom;
uniform float uColorShift;
uniform float uRotateY;
uniform float uRotateX;
uniform float uRotateZ;
uniform float uAlpha;
uniform vec2 uResolution;
uniform float uGlow;

uniform float uTime;

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

// Bounding box SDF
float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float map(vec3 p, out float trap) {
    // Spatial scaling
    vec3 sp = p * uScale;
    
    // Wave animation offset/phase modulation
    float timePhase = uTime * uSpeed;
    
    float val = 0.0;
    float maxFreq = max(uFrequencyN, uFrequencyM);
    
    if (uMode < 0.5) {
        // 2D Chladni Resonance Pattern on a plate
        val = cos(uFrequencyN * sp.x + timePhase) * cos(uFrequencyM * sp.y) - 
              cos(uFrequencyM * sp.x) * cos(uFrequencyN * sp.y + timePhase);
              
        trap = abs(val);
        
        // Nodal line distance approximation
        float dLines = (abs(val - uThickness) - uWallWidth) / maxFreq;
        
        // Suspended thin plate boundary (box from -1.0 to 1.0 on X,Y; very thin on Z)
        float dPlate = sdBox(p, vec3(1.0, 1.0, 0.04));
        
        // CSG intersection: lines only exist inside the boundaries of the thin plate
        return max(dPlate, dLines * 0.5);
    } else {
        // 3D Chladni Resonance Volume (Nodal Sheets in a cube)
        maxFreq = max(maxFreq, uFrequencyL);
        val = cos(uFrequencyN * sp.x + timePhase) * cos(uFrequencyM * sp.y) * cos(uFrequencyL * sp.z) - 
              cos(uFrequencyM * sp.x) * cos(uFrequencyL * sp.y) * cos(uFrequencyN * sp.z + timePhase);
              
        trap = abs(val);
        
        // Bounded nodal surfaces inside a unit cube
        float dSurfaces = (abs(val - uThickness) - uWallWidth) / maxFreq;
        float dCube = sdBox(p, vec3(1.0));
        
        // CSG intersection: surfaces only exist inside the boundaries of the cube
        return max(dCube, dSurfaces * 0.5);
    }
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

        float colVal = fract(dot(p, p) * 0.1 + uColorShift);
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
