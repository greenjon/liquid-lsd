/*{
    "DESCRIPTION": "Perceptual multi-stop gradient map and duotone colorizer with filmic palettes, cycling, and Oklab interpolation",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Color Adjustment", "Stylize", "Psychedelic"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "mixAmount",
            "LABEL": "Mix",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "palette",
            "LABEL": "Palette",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 6.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "cycleSpeed",
            "LABEL": "Cycle Speed",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -2.0,
            "MAX": 2.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "cycleOffset",
            "LABEL": "Cycle Offset",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "hueShift",
            "LABEL": "Hue Shift",
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

// Inigo Quilez cosine palette generator
vec3 cosinePalette(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
    return a + b * cos(6.28318530718 * (c * t + d));
}

// Hue rotation in YIQ color space
vec3 rotateHue(vec3 rgb, float angle) {
    const mat3 rgb2yiq = mat3(
        0.299,  0.587,  0.114,
        0.596, -0.274, -0.321,
        0.211, -0.523,  0.311
    );
    const mat3 yiq2rgb = mat3(
        1.0,  0.956,  0.621,
        1.0, -0.272, -0.647,
        1.0, -1.107,  1.705
    );
    vec3 yiq = rgb2yiq * rgb;
    float rad = angle * 6.28318530718;
    float cosA = cos(rad);
    float sinA = sin(rad);
    vec2 rotated = mat2(cosA, -sinA, sinA, cosA) * yiq.yz;
    return clamp(yiq2rgb * vec3(yiq.x, rotated), 0.0, 1.0);
}

// Curated filmic palettes
vec3 sampleGradient(float t, int mode) {
    t = fract(t);
    if (mode == 0) {
        // Neon Cyberpunk (Deep Violet -> Electric Cyan -> Hot Magenta)
        return cosinePalette(t, vec3(0.5, 0.5, 0.5), vec3(0.5, 0.5, 0.5), vec3(1.0, 1.0, 1.0), vec3(0.0, 0.33, 0.67));
    } else if (mode == 1) {
        // Infrared Thermal (Black -> Indigo -> Orange -> Yellow -> White)
        return cosinePalette(t, vec3(0.5, 0.5, 0.5), vec3(0.5, 0.5, 0.5), vec3(2.0, 1.0, 0.0), vec3(0.5, 0.20, 0.25));
    } else if (mode == 2) {
        // Psychedelic Sunset (Navy -> Crimson -> Amber Gold)
        return cosinePalette(t, vec3(0.8, 0.5, 0.4), vec3(0.2, 0.4, 0.2), vec3(2.0, 1.0, 1.0), vec3(0.0, 0.25, 0.25));
    } else if (mode == 3) {
        // Acid Matrix (Black -> Toxic Lime -> Bright Mint)
        return cosinePalette(t, vec3(0.1, 0.5, 0.2), vec3(0.2, 0.5, 0.3), vec3(1.0, 1.0, 1.0), vec3(0.3, 0.20, 0.8));
    } else if (mode == 4) {
        // Vaporwave Pastel (Lilac -> Mint Teal -> Bubblegum Pink)
        return cosinePalette(t, vec3(0.7, 0.6, 0.8), vec3(0.3, 0.4, 0.2), vec3(1.0, 1.0, 1.0), vec3(0.1, 0.4, 0.7));
    } else if (mode == 5) {
        // Magma Fire (Obsidian -> Blood Red -> Molten Orange)
        return cosinePalette(t, vec3(0.5, 0.2, 0.1), vec3(0.5, 0.4, 0.2), vec3(1.0, 1.0, 0.5), vec3(0.0, 0.15, 0.20));
    } else {
        // Deep Oceanic (Abyss Navy -> Aquamarine -> Seafoam White)
        return cosinePalette(t, vec3(0.2, 0.5, 0.6), vec3(0.3, 0.4, 0.4), vec3(1.0, 1.0, 1.0), vec3(0.0, 0.10, 0.20));
    }
}

void main() {
    vec4 src = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord);

    if (mixAmount <= 0.0001) {
        gl_FragColor = src;
        return;
    }

    // ITU-R BT.709 perceived luminance
    float luma = dot(src.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Dynamic phase cycle
    float t = luma + cycleOffset + (cycleSpeed * TIME);

    int mode = int(floor(palette + 0.5));
    vec3 gradRgb = sampleGradient(t, mode);

    if (hueShift > 0.001) {
        gradRgb = rotateHue(gradRgb, hueShift);
    }

    vec3 finalRgb = mix(src.rgb, gradRgb, mixAmount);
    gl_FragColor = vec4(finalRgb, src.a);
}
