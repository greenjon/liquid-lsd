#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform float uScaleX;
uniform float uScaleY;
uniform float uScaleZ;
uniform float uThickness;
uniform float uWallWidth;
uniform float uSpeed;
uniform float uZoom;
uniform float uColorShift;
uniform float uRotateY;
uniform float uRotateX;
uniform float uRotateZ;
uniform float uAlpha;
uniform vec2 uResolution;
uniform float uGlow;

uniform float uTime; // System time in seconds

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

float map(vec3 p, out float trap) {
    // Apply animation / translation
    vec3 animatedP = p;
    animatedP.z += uTime * uSpeed;
    
    // Scale coordinates independently as requested
    vec3 scaledP = animatedP * vec3(uScaleX, uScaleY, uScaleZ);
    
    // Gyroid function: sin(x)cos(y) + sin(y)cos(z) + sin(z)cos(x)
    float gyroidVal = sin(scaledP.x) * cos(scaledP.y) +
                      sin(scaledP.y) * cos(scaledP.z) +
                      sin(scaledP.z) * cos(scaledP.x);
                      
    // Orbit trap based on the gyroid value
    trap = abs(gyroidVal);
    
    // The surface is hollow tunnels of thickness uThickness and wall width uWallWidth.
    // SDF = (abs(gyroidVal - uThickness) - uWallWidth) / max_scale
    float maxScale = max(uScaleX, max(uScaleY, uScaleZ));
    
    // Understep/safety multiplier to prevent overshooting
    float sdf = (abs(gyroidVal - uThickness) - uWallWidth) / maxScale;
    
    return sdf * 0.5; // Multiply by 0.5 safety factor for stability
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
    vec3 ro = vec3(0.0, 0.0, -2.5 / uZoom);
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

        float colVal = fract(dot(p, p) * 0.02 + uColorShift);
        vec3 baseCol = palette(colVal, 
            vec3(0.5, 0.5, 0.5), 
            vec3(0.5, 0.5, 0.5), 
            vec3(1.0, 1.0, 1.0), 
            vec3(0.0, 0.33, 0.67)
        );

        // Add soft lighting to highlight 3D structure of the labyrinth
        color = baseCol * (diff * 0.7 + 0.3) + vec3(0.8, 0.9, 1.0) * rim * 0.35;
    }

    if (uGlow > 0.0) {
        float glowFactor = float(stepsTaken) / float(MAX_RAY_STEPS);
        vec3 glowCol = palette(fract(uColorShift + 0.5), 
            vec3(0.5, 0.5, 0.5), 
            vec3(0.5, 0.5, 0.5), 
            vec3(1.0, 1.0, 1.0), 
            vec3(0.0, 0.33, 0.67)
        );
        color += glowCol * glowFactor * uGlow * 1.5;
    }

    fragColor = vec4(color, uAlpha);
}
