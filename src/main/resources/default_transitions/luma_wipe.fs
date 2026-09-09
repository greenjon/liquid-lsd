/*{
    "DESCRIPTION": "Luma Threshold Wipe",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Wipe", "Dissolve", "Transitions"],
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
            "MAX": 0.3,
            "DEFAULT": 0.1
        },
        {
            "NAME": "invertLuma",
            "TYPE": "bool",
            "DEFAULT": false
        }
    ]
}*/

void main() {
    vec2 uv = isf_FragNormCoord;
    vec4 colA = IMG_NORM_PIXEL(startImage, uv);
    vec4 colB = IMG_NORM_PIXEL(endImage, uv);

    float lumaA = dot(colA.rgb, vec3(0.299, 0.587, 0.114));
    float val = invertLuma ? (1.0 - lumaA) : lumaA;

    float p = clamp(progress, 0.0, 1.0);
    float s = max(softness, 0.001);

    float edge = smoothstep(p - s, p + s, val);

    gl_FragColor = mix(colB, colA, edge);
}
