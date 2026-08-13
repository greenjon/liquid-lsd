#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform float uPower;
uniform float uIterations;
uniform float uGlow;
uniform float uZoom;
uniform float uColorShift;
uniform float uBailout;
uniform float uRotateY;
uniform float uRotateX;
uniform float uRotateZ;
uniform float uAlpha;
uniform vec2 uResolution;

const int MAX_RAY_STEPS = 80;
const float MIN_DIST = 0.001;
const float MAX_DIST = 10.0;

// Rotation matrix helpers
mat3 rotateY(float theta) {
    float c = cos(theta);
    float s = sin(theta);
    return mat3(
        vec3(c, 0, s),
        vec3(0, 1, 0),
        vec3(-s, 0, c)
    );
}

mat3 rotateX(float theta) {
    float c = cos(theta);
    float s = sin(theta);
    return mat3(
        vec3(1, 0, 0),
        vec3(0, c, -s),
        vec3(0, s, c)
    );
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


// Mandelbulb Distance Estimator
// Ref: http://iquilezles.org/www/articles/mandelbulb/mandelbulb.htm
float map(vec3 p, out float trap) {
    vec3 z = p;
    float dr = 1.0;
    float r = 0.0;
    trap = 1e10;
    
    int maxIt = int(uIterations);
    for (int i = 0; i < 15; i++) {
        if (i >= maxIt) break;
        
        r = length(z);
        if (r > uBailout) break;
        
        // Trap minimum distance for coloring
        trap = min(trap, dot(z.xz, z.xz));
        
        // Convert to polar coordinates
        float theta = acos(z.y / r);
        float phi = atan(z.x, z.z);
        dr = pow(r, uPower - 1.0) * uPower * dr + 1.0;
        
        // Scale and rotate potential
        float zr = pow(r, uPower);
        theta = theta * uPower;
        phi = phi * uPower;
        
        // Convert back to cartesian coordinates
        z = zr * vec3(sin(theta) * sin(phi), cos(theta), sin(theta) * cos(phi));
        z += p;
    }
    return 0.5 * log(r) * r / dr;
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
    // Standard screen coordinates normalized
    vec2 uv = vTexCoord * 2.0 - 1.0;
    float aspect = uResolution.x / uResolution.y;
    uv.x *= aspect;

    // Ray setup
    vec3 ro = vec3(0.0, 0.0, -2.5 / uZoom); // Ray origin (camera position)
    vec3 rd = normalize(vec3(uv, 1.2));     // Ray direction

    // Rotate camera based on Yaw/Pitch parameters
    mat3 camRot = rotateZ(uRotateZ) * rotateY(uRotateY) * rotateX(uRotateX);
    ro = camRot * ro;
    rd = camRot * rd;

    // Raymarching loop
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
        
        // Simple lighting (diffuse + ambient + rim)
        vec3 lightDir = normalize(vec3(1.0, 1.0, -1.0));
        float diff = max(dot(normal, lightDir), 0.0);
        float rim = pow(1.0 + dot(normal, rd), 4.0);
        
        // Base color based on orbit trap and color shift
        float colVal = fract(trap * 2.0 + uColorShift);
        vec3 baseCol = palette(colVal, 
            vec3(0.5, 0.5, 0.5), 
            vec3(0.5, 0.5, 0.5), 
            vec3(1.0, 1.0, 1.0), 
            vec3(0.0, 0.33, 0.67)
        );
        
        color = baseCol * (diff * 0.8 + 0.2) + vec3(0.8, 0.9, 1.0) * rim * 0.4;
    }

    // Add raymarching steps glow for premium volumetric feel
    if (uGlow > 0.0) {
        float glow = float(stepsTaken) / float(MAX_RAY_STEPS);
        vec3 glowColor = palette(uColorShift, 
            vec3(0.8, 0.5, 0.4), 
            vec3(0.2, 0.4, 0.2), 
            vec3(2.0, 1.0, 1.0), 
            vec3(0.0, 0.25, 0.25)
        );
        color += glow * glowColor * uGlow * 1.2;
    }

    fragColor = vec4(color, uAlpha);
}
