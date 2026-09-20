/*{
    "DESCRIPTION": "Stochastic dithered directional motion blur with angle, exponential decay, and bidirectional streak diffusion",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Blur", "Optics", "Stylize"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "blurAmount",
            "LABEL": "Amount",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "angle",
            "LABEL": "Angle",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -3.14159,
            "MAX": 3.14159,
            "IDENTITY": 0.0
        },
        {
            "NAME": "decay",
            "LABEL": "Decay",
            "TYPE": "float",
            "DEFAULT": 0.95,
            "MIN": 0.5,
            "MAX": 1.0,
            "IDENTITY": 0.95
        },
        {
            "NAME": "bidirectional",
            "LABEL": "Bidirectional",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 1.0
        },
        {
            "NAME": "exposure",
            "LABEL": "Exposure",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.2,
            "MAX": 2.0,
            "IDENTITY": 1.0
        }
    ]
}*/

// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

// Screen-space high-frequency hash for dithered tap distribution
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec2 uv = isf_FragNormCoord;

    if (blurAmount <= 0.0005) {
        gl_FragColor = IMG_NORM_PIXEL(inputImage, uv);
        return;
    }

    // Direction vector with aspect-ratio correction so angle remains geometrically true
    float aspect = RENDERSIZE.x / max(RENDERSIZE.y, 1.0);
    vec2 dir = vec2(cos(angle) / aspect, sin(angle));
    vec2 maxOffset = dir * (blurAmount * 0.25);

    float dither = hash(gl_FragCoord.xy);
    const int SAMPLES = 16;
    vec4 accum = vec4(0.0);
    float totalWeight = 0.0;
    float currentDecay = 1.0;

    float isBidir = bidirectional > 0.5 ? 1.0 : 0.0;

    for (int i = 0; i < SAMPLES; i++) {
        float stepFraction = (float(i) + dither) / float(SAMPLES);
        float offsetDist = isBidir > 0.5 ? (stepFraction * 2.0 - 1.0) : stepFraction;
        vec2 sampleCoord = clamp(uv + maxOffset * offsetDist, vec2(0.0), vec2(1.0));

        vec4 tap = IMG_NORM_PIXEL(inputImage, sampleCoord);
        accum += tap * currentDecay;
        totalWeight += currentDecay;
        currentDecay *= decay;
    }

    vec4 finalColor = (accum / max(totalWeight, 0.0001)) * exposure;
    finalColor.a = IMG_NORM_PIXEL(inputImage, uv).a;
    gl_FragColor = finalColor;
}
