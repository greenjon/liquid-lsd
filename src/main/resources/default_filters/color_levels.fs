/*{
    "DESCRIPTION": "Mastering-grade perceptual color grading in Oklab color space with ACES filmic highlight compression and anti-banding dither",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Color Adjustment", "Mastering"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "brightness",
            "LABEL": "Brightness",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -1.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "contrast",
            "LABEL": "Contrast",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.0,
            "MAX": 3.0,
            "IDENTITY": 1.0
        },
        {
            "NAME": "saturation",
            "LABEL": "Saturation",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.0,
            "MAX": 3.0,
            "IDENTITY": 1.0
        },
        {
            "NAME": "gamma",
            "LABEL": "Gamma",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.2,
            "MAX": 3.0,
            "IDENTITY": 1.0
        },
        {
            "NAME": "warmth",
            "LABEL": "Warmth",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -1.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "tint",
            "LABEL": "Tint",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -1.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "filmicTone",
            "LABEL": "Filmic Rolloff",
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

// Oklab perceptual color conversions (Björn Ottosson, public domain)
vec3 srgbToOklab(vec3 c) {
    float l = 0.4122214708 * c.r + 0.5363325363 * c.g + 0.0514459929 * c.b;
    float m = 0.2119034982 * c.r + 0.6806995451 * c.g + 0.1073969566 * c.b;
    float s = 0.0883024619 * c.r + 0.2817188376 * c.g + 0.6299787005 * c.b;

    float l_ = pow(max(l, 0.0), 1.0 / 3.0);
    float m_ = pow(max(m, 0.0), 1.0 / 3.0);
    float s_ = pow(max(s, 0.0), 1.0 / 3.0);

    return vec3(
        0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
        1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
        0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
    );
}

vec3 oklabToSrgb(vec3 c) {
    float l_ = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
    float m_ = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
    float s_ = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;

    float l = l_ * l_ * l_;
    float m = m_ * m_ * m_;
    float s = s_ * s_ * s_;

    return vec3(
        +4.0767434770 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    );
}

// Subtle high-frequency dither to eliminate 8-bit banding on dark gradients
float ditherNoise(vec2 coord) {
    return fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
}

void main() {
    vec4 src = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord);
    vec3 lab = srgbToOklab(clamp(src.rgb, 0.0, 1.0));

    // Perceptual luminance operations preserve original hue without shifting
    float l = lab.x + brightness;
    l = (l - 0.5) * contrast + 0.5;
    l = pow(max(l, 0.0), 1.0 / max(gamma, 0.01));
    lab.x = clamp(l, 0.0, 1.0);

    // Saturation and chromatic balance
    lab.y = lab.y * saturation + (tint * 0.1);
    lab.z = lab.z * saturation + (warmth * 0.1);

    vec3 rgb = oklabToSrgb(lab);

    // ACES filmic highlight rolloff prevents harsh specular clipping
    if (filmicTone > 0.001) {
        vec3 a = rgb * (2.51 * rgb + 0.03);
        vec3 b = rgb * (2.43 * rgb + 0.59) + 0.14;
        vec3 filmic = clamp(a / b, 0.0, 1.0);
        rgb = mix(rgb, filmic, filmicTone);
    }

    // Triangular anti-banding dither (1/256 amplitude)
    rgb += ditherNoise(gl_FragCoord.xy) * (1.0 / 255.0);

    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), src.a);
}
