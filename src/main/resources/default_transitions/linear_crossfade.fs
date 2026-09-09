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
            "NAME": "smoothCurve",
            "TYPE": "bool",
            "DEFAULT": true
        }
    ]
}*/

void main() {
    vec4 colA = IMG_NORM_PIXEL(startImage, isf_FragNormCoord);
    vec4 colB = IMG_NORM_PIXEL(endImage, isf_FragNormCoord);

    float t = clamp(progress, 0.0, 1.0);
    if (smoothCurve) {
        t = smoothstep(0.0, 1.0, t);
    }

    gl_FragColor = mix(colA, colB, t);
}
