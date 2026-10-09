#version 300 es
precision highp float;
/*{
    "DESCRIPTION": "2D surface normal gradient refraction simulating liquid marbling, oily fluids, and melting glass with chromatic dispersion",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Distortion", "Organic", "Psychedelic"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "refractAmount",
            "LABEL": "Displacement",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "smoothness",
            "LABEL": "Smoothness",
            "TYPE": "float",
            "DEFAULT": 0.2,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.2
        },
        {
            "NAME": "chromaDispersion",
            "LABEL": "Prism Fringe",
            "TYPE": "float",
            "DEFAULT": 0.2,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "flowSpeed",
            "LABEL": "Flow Speed",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -1.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        }
    ]
}*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define refractAmount_ (refractAmount*0.25)
#define smoothness_ (0.5+smoothness*7.5)
#define chromaDispersion_ (chromaDispersion*0.1)
#define flowSpeed_ (flowSpeed*3.0)


// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

float getLuma(vec2 coord) {
    vec3 c = IMG_NORM_PIXEL(inputImage, abs(mod(coord, 2.0) - 1.0)).rgb;
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec2 uv = isf_FragNormCoord;

    if (refractAmount_ <= 0.0001) {
        gl_FragColor = IMG_NORM_PIXEL(inputImage, uv);
        return;
    }

    float aspect = RENDERSIZE.x / max(RENDERSIZE.y, 1.0);
    vec2 texel = vec2(smoothness_) / RENDERSIZE;

    // Optional dynamic flow offset
    vec2 flowOffset = vec2(0.0);
    if (abs(flowSpeed_) > 0.001) {
        float t = TIME * flowSpeed_ * 0.5;
        flowOffset = vec2(sin(uv.y * 5.0 + t), cos(uv.x * 5.0 + t)) * 0.02;
    }

    vec2 baseCoord = uv + flowOffset;

    // Estimate spatial luminance gradients
    float lumaL = getLuma(baseCoord - vec2(texel.x, 0.0));
    float lumaR = getLuma(baseCoord + vec2(texel.x, 0.0));
    float lumaD = getLuma(baseCoord - vec2(0.0, texel.y));
    float lumaU = getLuma(baseCoord + vec2(0.0, texel.y));

    float gx = (lumaR - lumaL) * 0.5;
    float gy = (lumaU - lumaD) * 0.5;

    // 2D surface normal displacement vector
    vec2 normalDir = vec2(-gx, -gy);
    normalDir.x /= aspect;
    vec2 offset = normalDir * (refractAmount_ * 2.0);

    // Multi-spectral chromatic dispersion
    vec2 rCoord = abs(mod(uv + offset * (1.0 + chromaDispersion_ * 2.0), 2.0) - 1.0);
    vec2 gCoord = abs(mod(uv + offset, 2.0) - 1.0);
    vec2 bCoord = abs(mod(uv + offset * (1.0 - chromaDispersion_ * 2.0), 2.0) - 1.0);

    float r = IMG_NORM_PIXEL(inputImage, rCoord).r;
    vec4 gPixel = IMG_NORM_PIXEL(inputImage, gCoord);
    float b = IMG_NORM_PIXEL(inputImage, bCoord).b;

    gl_FragColor = vec4(r, gPixel.g, b, gPixel.a);
}
