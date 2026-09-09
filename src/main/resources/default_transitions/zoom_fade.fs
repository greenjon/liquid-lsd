/*{
    "DESCRIPTION": "Zoom Scale Fade",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Zoom", "Dissolve", "Transitions"],
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
            "NAME": "maxZoom",
            "TYPE": "float",
            "MIN": 1.0,
            "MAX": 3.0,
            "DEFAULT": 1.5
        }
    ]
}*/

void main() {
    vec2 uv = isf_FragNormCoord;
    float p = clamp(progress, 0.0, 1.0);

    // Zoom Deck A in as it fades out
    float scaleA = 1.0 + (maxZoom - 1.0) * p;
    vec2 uvA = (uv - vec2(0.5)) / scaleA + vec2(0.5);

    // Zoom Deck B out as it fades in
    float scaleB = maxZoom - (maxZoom - 1.0) * p;
    vec2 uvB = (uv - vec2(0.5)) / scaleB + vec2(0.5);

    vec4 colA = IMG_NORM_PIXEL(startImage, clamp(uvA, 0.0, 1.0));
    vec4 colB = IMG_NORM_PIXEL(endImage, clamp(uvB, 0.0, 1.0));

    float t = smoothstep(0.0, 1.0, p);
    gl_FragColor = mix(colA, colB, t);
}
