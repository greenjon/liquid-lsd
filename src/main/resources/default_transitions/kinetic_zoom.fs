/*{
    "DESCRIPTION": "Kinetic Zoom",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Zoom", "Kinetic", "Transitions"],
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
            "NAME": "zoomFactor",
            "TYPE": "float",
            "MIN": 1.0,
            "MAX": 4.0,
            "DEFAULT": 2.2
        },
        {
            "NAME": "motionBlur",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.5
        },
        {
            "NAME": "chromatic",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.4
        }
    ]
}*/

#define PI 3.14159265359

void main() {
    vec2 uv = isf_FragNormCoord;
    float p = clamp(progress, 0.0, 1.0);

    // Zoom curves: Deck A rushes toward camera, Deck B swoops in from wide
    float scaleA = 1.0 + (zoomFactor - 1.0) * pow(p, 1.5);
    float scaleB = 1.0 / (1.0 + (zoomFactor - 1.0) * pow(1.0 - p, 1.5));

    vec2 center = vec2(0.5);
    vec2 dir = uv - center;

    // Kinetic velocity peaks near midpoint
    float vel = sin(p * PI) * motionBlur * 0.15;
    float chromaDelta = chromatic * vel * 0.5;

    // Multi-tap radial streak blur sampling
    vec4 accA = vec4(0.0);
    vec4 accB = vec4(0.0);
    const int SAMPLES = 6;
    float invSamples = 1.0 / float(SAMPLES);

    for (int i = 0; i < SAMPLES; i++) {
        float f = float(i) * invSamples - 0.5;
        vec2 sampleDir = dir * (1.0 + f * vel);

        // Deck A sample with chromatic aberration
        vec2 coordA = center + sampleDir / scaleA;
        float rA = IMG_NORM_PIXEL(startImage, clamp(coordA + dir * chromaDelta, 0.0, 1.0)).r;
        float gA = IMG_NORM_PIXEL(startImage, clamp(coordA, 0.0, 1.0)).g;
        float bA = IMG_NORM_PIXEL(startImage, clamp(coordA - dir * chromaDelta, 0.0, 1.0)).b;
        float aA = IMG_NORM_PIXEL(startImage, clamp(coordA, 0.0, 1.0)).a;
        accA += vec4(rA, gA, bA, aA);

        // Deck B sample with chromatic aberration
        vec2 coordB = center + sampleDir / scaleB;
        float rB = IMG_NORM_PIXEL(endImage, clamp(coordB - dir * chromaDelta, 0.0, 1.0)).r;
        float gB = IMG_NORM_PIXEL(endImage, clamp(coordB, 0.0, 1.0)).g;
        float bB = IMG_NORM_PIXEL(endImage, clamp(coordB + dir * chromaDelta, 0.0, 1.0)).b;
        float aB = IMG_NORM_PIXEL(endImage, clamp(coordB, 0.0, 1.0)).a;
        accB += vec4(rB, gB, bB, aB);
    }

    vec4 colA = accA * invSamples;
    vec4 colB = accB * invSamples;

    float t = smoothstep(0.0, 1.0, p);
    gl_FragColor = mix(colA, colB, t);
}
