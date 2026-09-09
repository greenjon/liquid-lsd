/*{
    "DESCRIPTION": "Horizontal Wipe",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Wipe", "Transitions"],
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
            "NAME": "softness",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 0.5,
            "DEFAULT": 0.05
        },
        {
            "NAME": "reverseDirection",
            "TYPE": "bool",
            "DEFAULT": false
        }
    ]
}*/

void main() {
    vec2 uv = isf_FragNormCoord;
    vec4 colA = IMG_NORM_PIXEL(startImage, uv);
    vec4 colB = IMG_NORM_PIXEL(endImage, uv);

    float pos = reverseDirection ? (1.0 - uv.x) : uv.x;
    float p = clamp(progress, 0.0, 1.0);

    float s = max(softness, 0.001);
    float edge = smoothstep(p - s, p + s, pos);

    gl_FragColor = mix(colB, colA, edge);
}
