/*{
    "DESCRIPTION": "Chromatic aberration, scanline dislocation, and digital noise",
    "CREDIT": "Liquid LSD Engine",
    "CATEGORIES": ["Glitch", "Distortion", "Stylize"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "glitchAmount",
            "LABEL": "Glitch Amount",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0
        },
        {
            "NAME": "scanlineSpeed",
            "LABEL": "Scanline Speed",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.0,
            "MAX": 5.0
        },
        {
            "NAME": "chromaSplit",
            "LABEL": "Chroma Split",
            "TYPE": "float",
            "DEFAULT": 0.02,
            "MIN": 0.0,
            "MAX": 0.1
        }
    ]
}*/

float rand(vec2 co) {
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

void main() {
    vec2 uv = isf_FragNormCoord;

    // Horizontal scanline dislocation
    float lineY = floor(uv.y * 50.0);
    float noiseVal = rand(vec2(lineY, floor(TIME * scanlineSpeed * 10.0)));

    if (noiseVal < glitchAmount * 0.3) {
        uv.x += (rand(vec2(TIME, lineY)) - 0.5) * glitchAmount * 0.1;
    }

    // Chromatic Aberration
    vec2 split = vec2(chromaSplit * glitchAmount, 0.0);
    float r = IMG_NORM_PIXEL(inputImage, uv + split).r;
    float g = IMG_NORM_PIXEL(inputImage, uv).g;
    float b = IMG_NORM_PIXEL(inputImage, uv - split).b;
    float a = IMG_NORM_PIXEL(inputImage, uv).a;

    gl_FragColor = vec4(r, g, b, a);
}
