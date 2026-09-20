/*{
    "DESCRIPTION": "Directional Sobel edge detection with angle-to-hue glowing neon color palettes and dark-stage or overlay compositing",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Stylize", "Glitch", "Psychedelic"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "edgeStrength",
            "LABEL": "Intensity",
            "TYPE": "float",
            "DEFAULT": 2.0,
            "MIN": 0.0,
            "MAX": 10.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "threshold",
            "LABEL": "Threshold",
            "TYPE": "float",
            "DEFAULT": 0.05,
            "MIN": 0.0,
            "MAX": 0.5,
            "IDENTITY": 0.05
        },
        {
            "NAME": "glowSpread",
            "LABEL": "Spread",
            "TYPE": "float",
            "DEFAULT": 1.5,
            "MIN": 0.5,
            "MAX": 5.0,
            "IDENTITY": 1.0
        },
        {
            "NAME": "palette",
            "LABEL": "Neon Palette",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 3.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "backgroundBlend",
            "LABEL": "Source Background",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        }
    ]
}*/

// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

// Inigo Quilez cosine palette
vec3 cosinePalette(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
    return a + b * cos(6.28318530718 * (c * t + d));
}

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec2 uv = isf_FragNormCoord;

    if (edgeStrength <= 0.001 && backgroundBlend >= 0.999) {
        gl_FragColor = IMG_NORM_PIXEL(inputImage, uv);
        return;
    }

    vec2 texel = (vec2(glowSpread) / RENDERSIZE);

    // 3x3 Sobel directional sampling
    float tl = luma(IMG_NORM_PIXEL(inputImage, uv + vec2(-texel.x,  texel.y)).rgb);
    float tc = luma(IMG_NORM_PIXEL(inputImage, uv + vec2(     0.0,  texel.y)).rgb);
    float tr = luma(IMG_NORM_PIXEL(inputImage, uv + vec2( texel.x,  texel.y)).rgb);

    float ml = luma(IMG_NORM_PIXEL(inputImage, uv + vec2(-texel.x,      0.0)).rgb);
    float mr = luma(IMG_NORM_PIXEL(inputImage, uv + vec2( texel.x,      0.0)).rgb);

    float bl = luma(IMG_NORM_PIXEL(inputImage, uv + vec2(-texel.x, -texel.y)).rgb);
    float bc = luma(IMG_NORM_PIXEL(inputImage, uv + vec2(     0.0, -texel.y)).rgb);
    float br = luma(IMG_NORM_PIXEL(inputImage, uv + vec2( texel.x, -texel.y)).rgb);

    // Convolve gradients
    float gx = (tr + 2.0 * mr + br) - (tl + 2.0 * ml + bl);
    float gy = (tl + 2.0 * tc + tr) - (bl + 2.0 * bc + br);

    float magnitude = sqrt(gx * gx + gy * gy);
    float angle = atan(gy, gx); // [-PI, PI]

    // High-pass edge threshold
    float edgeFactor = smoothstep(threshold, threshold * 2.5 + 0.01, magnitude) * edgeStrength;

    // Map edge angle to neon color palettes
    int palMode = int(floor(palette + 0.5));
    vec3 neonHue;

    if (palMode == 0) {
        // Cyber Rainbow
        float t = angle / 6.2831853 + 0.5;
        neonHue = cosinePalette(t, vec3(0.5), vec3(0.5), vec3(1.0), vec3(0.0, 0.33, 0.67));
    } else if (palMode == 1) {
        // Electric Cyan / Hot Pink
        float t = sin(angle) * 0.5 + 0.5;
        neonHue = mix(vec3(0.0, 1.0, 1.0), vec3(1.0, 0.05, 0.75), t);
    } else if (palMode == 2) {
        // Toxic Acid Green / Mint
        float t = cos(angle) * 0.5 + 0.5;
        neonHue = mix(vec3(0.05, 1.0, 0.2), vec3(0.4, 1.0, 0.8), t);
    } else {
        // Magma Flame (Amber / Crimson / Gold)
        float t = sin(angle) * 0.5 + 0.5;
        neonHue = mix(vec3(1.0, 0.1, 0.0), vec3(1.0, 0.85, 0.1), t);
    }

    vec4 src = IMG_NORM_PIXEL(inputImage, uv);
    vec3 neonOutput = neonHue * edgeFactor;

    // Background blend: 0 = Pure dark neon outline, 1 = Luminous additive overlay on source
    vec3 finalRgb = mix(neonOutput, src.rgb + neonOutput, backgroundBlend);
    gl_FragColor = vec4(clamp(finalRgb, 0.0, 1.0), src.a);
}
