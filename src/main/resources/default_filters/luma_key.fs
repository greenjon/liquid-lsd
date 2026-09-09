/*{
    "DESCRIPTION": "Luminance threshold keying",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Masking"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "threshold",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.5
        },
        {
            "NAME": "softness",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.1
        }
    ]
}*/

void main() {
    vec4 color = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord);
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    float alpha = smoothstep(threshold - softness, threshold + softness, luma);
    gl_FragColor = vec4(color.rgb, color.a * alpha);
}
