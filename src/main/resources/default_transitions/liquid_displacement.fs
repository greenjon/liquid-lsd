/*{
    "DESCRIPTION": "Liquid Displacement",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Distortion", "Fluid", "Transitions"],
    "INPUTS": [
        {
            "NAME": "startImage",
            "TYPE": "image"
        },
        {
            "NAME": "endImage",
            "TYPE": "image"
        },
        {
            "NAME": "progress",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.0
        },
        {
            "NAME": "warpStrength",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.35
        },
        {
            "NAME": "viscosity",
            "TYPE": "float",
            "MIN": 0.5,
            "MAX": 3.0,
            "DEFAULT": 1.2
        },
        {
            "NAME": "chromaticDispersion",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.3
        }
    ]
}*/

#define PI 3.14159265359

float luma(vec4 c) {
    return dot(c.rgb, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 uv = isf_FragNormCoord;
    float p = clamp(progress, 0.0, 1.0);

    // Wave distortion envelope peaking at transition midpoint
    float env = sin(p * PI);
    float displacementMag = env * warpStrength * 0.2;

    // Sample initial luminance fields to derive optical displacement vectors
    vec4 origA = IMG_NORM_PIXEL(startImage, uv);
    vec4 origB = IMG_NORM_PIXEL(endImage, uv);

    // Finite difference gradient for Deck A and Deck B
    vec2 step = vec2(0.005 * viscosity);
    float lumaAx = luma(IMG_NORM_PIXEL(startImage, uv + vec2(step.x, 0.0))) - luma(IMG_NORM_PIXEL(startImage, uv - vec2(step.x, 0.0)));
    float lumaAy = luma(IMG_NORM_PIXEL(startImage, uv + vec2(0.0, step.y))) - luma(IMG_NORM_PIXEL(startImage, uv - vec2(0.0, step.y)));
    vec2 gradA = vec2(lumaAx, lumaAy);

    float lumaBx = luma(IMG_NORM_PIXEL(endImage, uv + vec2(step.x, 0.0))) - luma(IMG_NORM_PIXEL(endImage, uv - vec2(step.x, 0.0)));
    float lumaBy = luma(IMG_NORM_PIXEL(endImage, uv + vec2(0.0, step.y))) - luma(IMG_NORM_PIXEL(endImage, uv - vec2(0.0, step.y)));
    vec2 gradB = vec2(lumaBx, lumaBy);

    // Cross-displace: Deck A is pushed by Deck B's vectors, Deck B is pushed by Deck A's vectors
    vec2 dirA = gradB + vec2(sin(uv.y * 10.0 + TIME * 2.0), cos(uv.x * 10.0 + TIME * 2.0)) * 0.2;
    vec2 dirB = -gradA + vec2(cos(uv.y * 10.0 - TIME * 2.0), sin(uv.x * 10.0 - TIME * 2.0)) * 0.2;

    vec2 uvA = clamp(uv + dirA * displacementMag * (1.0 - p * 0.5), 0.0, 1.0);
    vec2 uvB = clamp(uv + dirB * displacementMag * (0.5 + p * 0.5), 0.0, 1.0);

    // Chromatic dispersion along flow lines
    float disp = chromaticDispersion * displacementMag * 0.3;
    vec4 colA;
    colA.r = IMG_NORM_PIXEL(startImage, clamp(uvA + dirA * disp, 0.0, 1.0)).r;
    colA.g = IMG_NORM_PIXEL(startImage, uvA).g;
    colA.b = IMG_NORM_PIXEL(startImage, clamp(uvA - dirA * disp, 0.0, 1.0)).b;
    colA.a = IMG_NORM_PIXEL(startImage, uvA).a;

    vec4 colB;
    colB.r = IMG_NORM_PIXEL(endImage, clamp(uvB - dirB * disp, 0.0, 1.0)).r;
    colB.g = IMG_NORM_PIXEL(endImage, uvB).g;
    colB.b = IMG_NORM_PIXEL(endImage, clamp(uvB + dirB * disp, 0.0, 1.0)).b;
    colB.a = IMG_NORM_PIXEL(endImage, uvB).a;

    float t = smoothstep(0.0, 1.0, p);
    gl_FragColor = mix(colA, colB, t);
}
