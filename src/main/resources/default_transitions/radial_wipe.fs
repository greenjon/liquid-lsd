/*{
    "DESCRIPTION": "Radial Wipe",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Wipe", "Geometric", "Transitions"],
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
            "MAX": 0.2,
            "DEFAULT": 0.02
        }
    ]
}*/

#define PI 3.14159265359

void main() {
    vec2 uv = isf_FragNormCoord;
    vec4 colA = IMG_NORM_PIXEL(startImage, uv);
    vec4 colB = IMG_NORM_PIXEL(endImage, uv);

    vec2 dir = uv - vec2(0.5);
    float angle = atan(dir.y, dir.x); // -PI to PI
    float normAngle = (angle + PI) / (2.0 * PI); // 0 to 1

    float p = clamp(progress, 0.0, 1.0);
    float s = max(softness, 0.001);

    float edge = smoothstep(p - s, p + s, normAngle);

    gl_FragColor = mix(colB, colA, edge);
}
