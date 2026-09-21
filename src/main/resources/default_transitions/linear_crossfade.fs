/*{
    "DESCRIPTION": "Linear Crossfade",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Dissolve", "Transitions"],
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
            "NAME": "curveMode",
            "TYPE": "long",
            "VALUES": [0, 1, 2],
            "LABELS": ["Perceptual Cosine", "Linear", "Equal Power"],
            "DEFAULT": 0
        }
    ]
}*/

#define PI 3.14159265359

void main() {
    vec2 uv = isf_FragNormCoord;
    vec4 colA = IMG_NORM_PIXEL(startImage, uv);
    vec4 colB = IMG_NORM_PIXEL(endImage, uv);

    float p = clamp(progress, 0.0, 1.0);
    float t = p;

    if (curveMode == 0) {
        // Perceptual Cosine S-Curve (Smooth ease-in-out)
        t = 0.5 - 0.5 * cos(p * PI);
        gl_FragColor = mix(colA, colB, t);
    } else if (curveMode == 1) {
        // Pure linear mix
        gl_FragColor = mix(colA, colB, t);
    } else {
        // Equal Power (Constant acoustic/optical power crossfade)
        float wA = cos(p * PI * 0.5);
        float wB = sin(p * PI * 0.5);
        gl_FragColor = sqrt(colA * colA * wA * wA + colB * colB * wB * wB);
    }
}
