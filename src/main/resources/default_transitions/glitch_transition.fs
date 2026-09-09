/*{
    "DESCRIPTION": "Glitch Transition",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Glitch", "Transitions"],
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
            "NAME": "intensity",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.5
        }
    ]
}*/

float rand(vec2 co) {
    return fract(sin(dot(co.xy ,vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = isf_FragNormCoord;
    float p = clamp(progress, 0.0, 1.0);

    // Glitch peaks around crossfade midpoint (progress = 0.5)
    float glitchAmount = sin(p * 3.14159265) * intensity;

    float blockY = floor(uv.y * 30.0);
    float noise = rand(vec2(blockY, floor(TIME * 20.0)));

    vec2 offset = vec2(0.0);
    if (noise < glitchAmount) {
        offset.x = (rand(vec2(blockY, 1.0)) - 0.5) * 0.2 * glitchAmount;
    }

    vec2 glitchUV = clamp(uv + offset, 0.0, 1.0);

    vec4 colA = IMG_NORM_PIXEL(startImage, glitchUV);
    vec4 colB = IMG_NORM_PIXEL(endImage, glitchUV);

    // Color channel split near mid transition
    if (glitchAmount > 0.3 && noise < glitchAmount * 0.5) {
        colA.r = IMG_NORM_PIXEL(startImage, clamp(glitchUV + vec2(0.01 * glitchAmount, 0.0), 0.0, 1.0)).r;
        colB.b = IMG_NORM_PIXEL(endImage, clamp(glitchUV - vec2(0.01 * glitchAmount, 0.0), 0.0, 1.0)).b;
    }

    float t = smoothstep(0.0, 1.0, p);
    gl_FragColor = mix(colA, colB, t);
}
