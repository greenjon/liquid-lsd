/*{
    "DESCRIPTION": "Cyber Datamosh",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Glitch", "Digital", "Transitions"],
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
            "NAME": "blockiness",
            "TYPE": "float",
            "MIN": 8.0,
            "MAX": 64.0,
            "DEFAULT": 28.0
        },
        {
            "NAME": "rgbSplit",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 0.2,
            "DEFAULT": 0.06
        },
        {
            "NAME": "packetLoss",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 2.0,
            "DEFAULT": 1.0
        }
    ]
}*/

#define PI 3.14159265359

float hash21(vec2 p) {
    p = fract(p * vec2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

void main() {
    vec2 uv = isf_FragNormCoord;
    float p = clamp(progress, 0.0, 1.0);

    // Glitch envelope peaking violently around crossfade midpoint
    float env = sin(p * PI);
    float glitchPower = pow(env, 1.4) * packetLoss;

    // Discrete macroblock coordinates
    vec2 blockCoord = floor(uv * vec2(blockiness, blockiness * 0.5));
    float blockRand = hash21(blockCoord + floor(TIME * 15.0));

    // Horizontal sync tearing on random scan bands
    float scanline = floor(uv.y * 120.0);
    float scanRand = hash21(vec2(scanline, floor(TIME * 24.0)));

    vec2 offset = vec2(0.0);
    if (scanRand < glitchPower * 0.6) {
        offset.x = (hash21(vec2(scanline, 1.0)) - 0.5) * 0.15 * glitchPower;
    }
    if (blockRand < glitchPower * 0.4) {
        offset += (vec2(hash21(blockCoord), hash21(blockCoord + 1.0)) - 0.5) * 0.2 * glitchPower;
    }

    vec2 glitchUV = clamp(uv + offset, 0.0, 1.0);

    // Chromatic shear: R and B split in opposite directions along glitch vectors
    float splitAmt = rgbSplit * glitchPower;
    vec2 splitVec = vec2(splitAmt, 0.0);

    vec4 colA;
    colA.r = IMG_NORM_PIXEL(startImage, clamp(glitchUV + splitVec, 0.0, 1.0)).r;
    colA.g = IMG_NORM_PIXEL(startImage, glitchUV).g;
    colA.b = IMG_NORM_PIXEL(startImage, clamp(glitchUV - splitVec, 0.0, 1.0)).b;
    colA.a = IMG_NORM_PIXEL(startImage, glitchUV).a;

    vec4 colB;
    colB.r = IMG_NORM_PIXEL(endImage, clamp(glitchUV - splitVec, 0.0, 1.0)).r;
    colB.g = IMG_NORM_PIXEL(endImage, glitchUV).g;
    colB.b = IMG_NORM_PIXEL(endImage, clamp(glitchUV + splitVec, 0.0, 1.0)).b;
    colB.a = IMG_NORM_PIXEL(endImage, glitchUV).a;

    // Digital invert flash on high packet loss blocks
    if (glitchPower > 0.6 && blockRand > 0.85) {
        colA.rgb = vec3(1.0) - colA.rgb;
        colB.rgb = vec3(1.0) - colB.rgb;
    }

    float t = smoothstep(0.0, 1.0, p);
    gl_FragColor = mix(colA, colB, t);
}
