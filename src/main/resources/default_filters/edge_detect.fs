/*{
    "DESCRIPTION": "Sobel convolution edge detection",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Stylize"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "edgeStrength",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 5.0,
            "DEFAULT": 1.0
        }
    ]
}*/

void main() {
    vec2 texel = 1.0 / RENDERSIZE;

    float gx = 0.0;
    float gy = 0.0;

    // Sobel kernels
    // -1 0 1
    // -2 0 2
    // -1 0 1

    // -1 -2 -1
    //  0  0  0
    //  1  2  1

    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            vec4 col = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord + vec2(float(i), float(j)) * texel);
            float l = dot(col.rgb, vec3(0.299, 0.587, 0.114));

            float kx = 0.0;
            if (i == -1) kx = (j == 0) ? -2.0 : -1.0;
            else if (i == 1) kx = (j == 0) ? 2.0 : 1.0;

            float ky = 0.0;
            if (j == -1) ky = (i == 0) ? -2.0 : -1.0;
            else if (j == 1) ky = (i == 0) ? 2.0 : 1.0;

            gx += l * kx;
            gy += l * ky;
        }
    }

    float edge = sqrt(gx * gx + gy * gy) * edgeStrength;
    gl_FragColor = vec4(vec3(edge), 1.0);
}
