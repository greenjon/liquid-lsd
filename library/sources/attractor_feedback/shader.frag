#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform float uPlaneScale;
uniform float uColorShift;
uniform float uPersistence;

uniform float uScale0;
uniform float uRotate0;
uniform float uOffsetX0;
uniform float uOffsetY0;
uniform float uVarCoef0;
uniform float uJacobian0;

uniform float uScale1;
uniform float uRotate1;
uniform float uOffsetX1;
uniform float uOffsetY1;
uniform float uVarCoef1;
uniform float uJacobian1;

uniform sampler2D src;

// Plane mapping functions
float T(float x) {
    float scaled = x * uPlaneScale;
    return scaled / sqrt(1.0 + scaled * scaled) * 0.5 + 0.5;
}

vec2 T(vec2 p) {
    return vec2(T(p.x), T(p.y));
}

float P(float s) {
    float u = 2.0 * s - 1.0;
    u = clamp(u, -0.9999, 0.9999);
    float texscalei = 1.0 / uPlaneScale;
    return texscalei * u / sqrt(1.0 - u * u);
}

vec2 P(vec2 s) {
    return vec2(P(s.x), P(s.y));
}

// Jacobian functions
float jacobian0(vec2 t) {
    return 1.0;
}

float jacobian1(vec2 t) {
    float r2 = dot(t, t);
    if (r2 < 1e-6) return 0.0;
    return (1.0 - 2.0 * t.y * t.y / r2) / r2;
}

// Cosine-based color palette helper
vec3 palette(in float t, in vec3 a, in vec3 b, in vec3 c, in vec3 d) {
    return a + b * cos(6.2831853 * (c * t + d));
}

vec3 getBranchColor(float shift) {
    return palette(shift,
        vec3(0.5, 0.5, 0.5),
        vec3(0.5, 0.5, 0.5),
        vec3(1.0, 1.0, 1.0),
        vec3(0.0, 0.33, 0.67)
    );
}

// Seed term to inject new points at the origin
float originSeed(vec2 inv) {
    // Gaussian distribution centered at origin
    return exp(-dot(inv, inv) * 20.0) * 0.05;
}

// Branch 0 evaluation
vec4 f0(vec2 inv) {
    float area = 1e-2 + abs(uJacobian0 * jacobian0(inv));
    
    // Reconstruct Branch 0 transform
    float cos0 = cos(uRotate0);
    float sin0 = sin(uRotate0);
    vec2 w0x = uScale0 * vec2(cos0, -sin0);
    vec2 w0y = uScale0 * vec2(sin0, cos0);
    vec2 w0o = vec2(uOffsetX0, uOffsetY0);

    vec2 t = T(w0x * inv.x + w0y * inv.y + w0o);
    
    // Sample history and expand log compression
    vec4 srcColor = texture(src, t);
    vec4 density = (exp2(srcColor * 20.0) - 1.0) * uPersistence;
    
    // Inject origin seed
    density += vec4(originSeed(inv));

    return density / area;
}

vec4 nonlinear_inverse0(vec2 p) {
    p = p * uVarCoef0;
    return f0(p);
}

// Branch 1 evaluation
vec4 f1(vec2 inv) {
    float area = 1e-2 + abs(uJacobian1 * jacobian1(inv));
    
    // Reconstruct Branch 1 transform
    float cos1 = cos(uRotate1);
    float sin1 = sin(uRotate1);
    vec2 w1x = uScale1 * vec2(cos1, -sin1);
    vec2 w1y = uScale1 * vec2(sin1, cos1);
    vec2 w1o = vec2(uOffsetX1, uOffsetY1);

    vec2 t = T(w1x * inv.x + w1y * inv.y + w1o);
    
    // Sample history and expand log compression
    vec4 srcColor = texture(src, t);
    vec4 density = (exp2(srcColor * 20.0) - 1.0) * uPersistence;
    
    // Inject origin seed
    density += vec4(originSeed(inv));

    return density / area;
}

vec4 nonlinear_inverse1(vec2 p) {
    p = p * uVarCoef1;
    
    if (abs(p.x) < 1e-5) return vec4(0.0);
    float ix = 0.5 / p.x;
    float det = 1.0 - 4.0 * p.x * p.x * p.y * p.y;
    if (det >= 0.0) {
        float sq = sqrt(det);
        return f1(vec2(ix * (1.0 - sq), p.y))
             + f1(vec2(ix * (1.0 + sq), p.y));
    } else {
        return vec4(0.0);
    }
}

// Combine branches and apply log compression
vec4 sum_inverses(vec2 p) {
    vec4 sum = vec4(0.0);
    
    vec4 color0 = vec4(getBranchColor(uColorShift), 1.0);
    vec4 color1 = vec4(getBranchColor(uColorShift + 0.5), 1.0);
    
    sum += color0 * nonlinear_inverse0(p);
    sum += color1 * nonlinear_inverse1(p);
    
    return log2(sum + 1.0) * (1.0 / 20.0);
}

void main(void) {
    fragColor = sum_inverses(P(vTexCoord));
}
