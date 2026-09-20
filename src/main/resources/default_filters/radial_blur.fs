/*{
    "DESCRIPTION": "Stochastic dithered radial zoom blur and explosive light shafts",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Blur", "Stylize", "Optics"],
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
            "NAME": "centerX",
            "LABEL": "Center X",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.5
        },
        {
            "NAME": "centerY",
            "LABEL": "Center Y",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.5
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
            "NAME": "exposure",
            "LABEL": "Exposure",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.1,
            "MAX": 2.5,
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

    // Fast-path bypass when blur amount is near zero
    if (blurAmount <= 0.0005) {
        gl_FragColor = IMG_NORM_PIXEL(inputImage, uv);
        return;
    }

    vec2 center = vec2(centerX, centerY);
    vec2 delta = (center - uv) * (blurAmount * 0.5);

    // Stochastic sample jitter prevents concentric stepping bands at large radii
    float dither = hash(gl_FragCoord.xy);

    const int NUM_SAMPLES = 16;
    vec4 accum = vec4(0.0);
    float totalWeight = 0.0;
    float currentDecay = 1.0;

    for (int i = 0; i < NUM_SAMPLES; i++) {
        float stepFraction = (float(i) + dither) / float(NUM_SAMPLES);
        vec2 sampleCoord = uv + delta * stepFraction;

        // Soft border clamp to avoid edge streaking outside [0..1]
        sampleCoord = clamp(sampleCoord, vec2(0.0), vec2(1.0));

        vec4 tap = IMG_NORM_PIXEL(inputImage, sampleCoord);
        accum += tap * currentDecay;
        totalWeight += currentDecay;
        currentDecay *= decay;
    }

    vec4 finalColor = (accum / max(totalWeight, 0.0001)) * exposure;
    finalColor.a = IMG_NORM_PIXEL(inputImage, uv).a;

    gl_FragColor = finalColor;
}
