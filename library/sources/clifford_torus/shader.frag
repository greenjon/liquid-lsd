#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

// Modulatable parameters (from meta.json)
uniform float uZoom;
uniform float uRotateX;
uniform float uRotateY;
uniform float uRotateZ;
uniform float uRotateXW;
uniform float uRotateYW;
uniform float uRotateZW;
uniform float uMeshDensityTheta;
uniform float uMeshDensityPhi;
uniform float uThickness;
uniform float uWireframeMode;
uniform float uColorShift;
uniform float uGlow;

// Standard uniforms injected by Liquid LSD Desktop
uniform float uAlpha;
uniform vec2 uResolution;
uniform float uTime;

// 3D rotation helpers for camera orientation
mat3 rotateX3D(float theta) {
    float c = cos(theta), s = sin(theta);
    return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c);
}

mat3 rotateY3D(float theta) {
    float c = cos(theta), s = sin(theta);
    return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c);
}

mat3 rotateZ3D(float theta) {
    float c = cos(theta), s = sin(theta);
    return mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0);
}

// 4D Rotation Helpers
vec4 rotateXW(vec4 p, float a) {
    float c = cos(a), s = sin(a);
    return vec4(p.x * c - p.w * s, p.y, p.z, p.x * s + p.w * c);
}

vec4 rotateYW(vec4 p, float a) {
    float c = cos(a), s = sin(a);
    return vec4(p.x, p.y * c - p.w * s, p.z, p.y * s + p.w * c);
}

vec4 rotateZW(vec4 p, float a) {
    float c = cos(a), s = sin(a);
    return vec4(p.x, p.y, p.z * c - p.w * s, p.z * s + p.w * c);
}

vec4 rotateXY(vec4 p, float a) {
    float c = cos(a), s = sin(a);
    return vec4(p.x * c - p.y * s, p.x * s + p.y * c, p.z, p.w);
}

vec4 rotateYZ(vec4 p, float a) {
    float c = cos(a), s = sin(a);
    return vec4(p.x, p.y * c - p.z * s, p.y * s + p.z * c, p.w);
}

vec4 rotateXZ(vec4 p, float a) {
    float c = cos(a), s = sin(a);
    return vec4(p.x * c - p.z * s, p.y, p.x * s + p.z * c, p.w);
}

// Cosine based palette generator (by Inigo Quilez)
vec3 palette(in float t, in vec3 a, in vec3 b, in vec3 c, in vec3 d) {
    return a + b * cos(6.2831853 * (c * t + d));
}

// 3D Distance field evaluation
float map(vec3 p, out float trap) {
    // 1. Inverse stereographic projection of 3D point p to S^3 (4D unit sphere)
    float r2 = dot(p, p);
    vec4 q = vec4(2.0 * p, r2 - 1.0) / (1.0 + r2);
    
    // 2. Apply 4D Rotations
    vec4 qRot = q;
    // Apply fundamental turning rotations
    qRot = rotateXW(qRot, uRotateXW);
    qRot = rotateYW(qRot, uRotateYW);
    qRot = rotateZW(qRot, uRotateZW);
    
    // 3. Clifford Torus distance field in 4D
    // Clifford torus radii (on a unit 3-sphere, r1^2 + r2^2 = 1.0)
    const float r_torus1 = 0.70710678; // 1.0 / sqrt(2.0)
    const float r_torus2 = 0.70710678;
    
    float d1 = length(qRot.xy) - r_torus1;
    float d2 = length(qRot.zw) - r_torus2;
    float dSurface = length(vec2(d1, d2));
    
    // Evaluate angular coordinates for wireframe grid plotting
    float theta = atan(qRot.y, qRot.x);
    float phi = atan(qRot.w, qRot.z);
    
    const float pi = 3.14159265;
    // Clamp density values to prevent zero step size and GPU hang
    float densityTheta = max(uMeshDensityTheta, 4.0);
    float densityPhi = max(uMeshDensityPhi, 4.0);
    
    float stepTheta = 2.0 * pi / densityTheta;
    float stepPhi = 2.0 * pi / densityPhi;
    
    // Compute distance to nearest grid lines in angular space
    float dt = abs(mod(theta + pi + stepTheta * 0.5, stepTheta) - stepTheta * 0.5);
    float dp = abs(mod(phi + pi + stepPhi * 0.5, stepPhi) - stepPhi * 0.5);
    
    // Convert angular distance to 4D spatial arc lengths
    float dGridTheta = dt * length(qRot.xy);
    float dGridPhi = dp * length(qRot.zw);
    float dGridLines = min(dGridTheta, dGridPhi);
    
    // 4D wireframe distance
    float dWireframe = sqrt(dSurface * dSurface + dGridLines * dGridLines) - uThickness;
    
    // 4D solid surface distance
    float dSolid = dSurface - uThickness;
    
    // Blend between solid and wireframe based on slider parameter
    float d4D = mix(dSolid, dWireframe, uWireframeMode);
    
    // Orbit trap for coloring based on torus coordinates
    trap = fract((theta + phi) / (2.0 * pi));
    
    // 4. Stereographic distance field correction
    // Multiplying by reciprocal of inverse stereographic derivative to correct scaling in 3D
    float d3D = d4D * (1.0 + r2) * 0.5;
    
    // Safety scaling factor of 0.6 to prevent overstepping in raymarching
    return d3D * 0.6;
}

// Calculate normal via central differences
vec3 getNormal(vec3 p) {
    float trap;
    vec2 eps = vec2(0.001, 0.0);
    return normalize(vec3(
        map(p + eps.xyy, trap) - map(p - eps.xyy, trap),
        map(p + eps.yxy, trap) - map(p - eps.yxy, trap),
        map(p + eps.yyx, trap) - map(p - eps.yyx, trap)
    ));
}

void main() {
    // Normalize and scale coordinates, adjusting for aspect ratio
    vec2 uv = vTexCoord * 2.0 - 1.0;
    float aspect = uResolution.x / uResolution.y;
    uv.x *= aspect;

    // Camera setup: Zoom controls distance to origin
    vec3 ro = vec3(0.0, 0.0, -2.5 / uZoom);
    vec3 rd = normalize(vec3(uv, 1.2));

    // Camera rotations (view manipulation)
    mat3 camRot = rotateZ3D(uRotateZ) * rotateY3D(uRotateY) * rotateX3D(uRotateX);
    ro = camRot * ro;
    rd = camRot * rd;

    // Raymarching constants
    const int MAX_RAY_STEPS = 120;
    const float MIN_DIST = 0.0015;
    const float MAX_DIST = 15.0;

    float t = 0.0;
    float trap = 0.0;
    int stepsTaken = 0;
    bool hit = false;

    // Raymarching loop
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
        
        // Lighting vectors
        vec3 lightDir = normalize(vec3(1.0, 1.0, -1.0));
        float diff = max(dot(normal, lightDir), 0.0);
        float rim = pow(1.0 + dot(normal, rd), 4.0);
        float spec = pow(max(dot(reflect(-lightDir, normal), -rd), 0.0), 32.0);

        // Aesthetic cosine-based color palette shifted by orbit trap and Color Shift
        float colVal = fract(trap + uColorShift);
        vec3 baseCol = palette(colVal, 
            vec3(0.5, 0.5, 0.5), 
            vec3(0.5, 0.5, 0.5), 
            vec3(1.0, 1.0, 1.0), 
            vec3(0.0, 0.33, 0.67)
        );

        // Blended shading: ambient, diffuse, specular, and specular rim highlights
        color = baseCol * (diff * 0.7 + 0.3) + vec3(1.0) * spec * 0.3 + vec3(0.5, 0.8, 1.0) * rim * 0.4;
    }

    // Volumetric/Step-based Glow accumulation
    if (uGlow > 0.0) {
        float glowFactor = float(stepsTaken) / float(MAX_RAY_STEPS);
        vec3 glowCol = palette(fract(uColorShift + 0.4), 
            vec3(0.5, 0.5, 0.5), 
            vec3(0.5, 0.5, 0.5), 
            vec3(1.0, 1.0, 1.0), 
            vec3(0.3, 0.15, 0.75)
        );
        color += glowCol * glowFactor * uGlow * 2.0;
    }

    // Soft vignette effect
    vec2 distFromCenter = vTexCoord - 0.5;
    float vignette = 1.0 - dot(distFromCenter, distFromCenter) * 0.7;
    color *= vignette;

    // Apply alpha/master gain before output
    fragColor = vec4(color, uAlpha);
}
