/*{
    "DESCRIPTION": "Chromatic quantization",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Stylize"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "levels",
            "TYPE": "float",
            "MIN": 2.0,
            "MAX": 32.0,
            "DEFAULT": 8.0
        }
    ]
}*/

void main() {
    vec4 color = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord);
    vec3 posterized = floor(color.rgb * levels) / levels;
    gl_FragColor = vec4(posterized, color.a);
}
