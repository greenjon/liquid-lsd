/*{
    "DESCRIPTION": "Inverts colors",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Color"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "invertIntensity",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 1.0
        }
    ]
}*/

void main() {
    vec4 color = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord);
    vec3 inverted = 1.0 - color.rgb;
    gl_FragColor = vec4(mix(color.rgb, inverted, invertIntensity), color.a);
}
