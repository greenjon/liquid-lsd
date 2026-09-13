/*{
    "DESCRIPTION": "Maximum Blend Transition",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Blend Modes", "Transitions"],
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
        }
    ]
}*/

void main() {
    vec4 colA = IMG_NORM_PIXEL(startImage, isf_FragNormCoord);
    vec4 colB = IMG_NORM_PIXEL(endImage, isf_FragNormCoord);

    float t = clamp(progress, 0.0, 1.0);
    gl_FragColor = max(colA * (1.0 - t), colB * t);
}
