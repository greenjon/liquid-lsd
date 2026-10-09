#version 300 es
precision highp float;
/*{
    "DESCRIPTION": "Authentic analog CRT monitor simulation with barrel tube curvature, RGB phosphor triad mask, and scanline rasterization",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Retro", "Stylize", "Glitch"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "scanlineIntensity",
            "LABEL": "Scanlines",
            "TYPE": "float",
            "DEFAULT": 0.3,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "scanlineCount",
            "LABEL": "Line Count",
            "TYPE": "float",
            "DEFAULT": 300.0,
            "MIN": 50.0,
            "MAX": 800.0,
            "STEP": 10,
            "IDENTITY": 300.0
        },
        {
            "NAME": "curvature",
            "LABEL": "Barrel Curve",
            "TYPE": "float",
            "DEFAULT": 0.25,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "phosphorMask",
            "LABEL": "RGB Phosphor",
            "TYPE": "float",
            "DEFAULT": 0.25,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "vignette",
            "LABEL": "Corner Vignette",
            "TYPE": "float",
            "DEFAULT": 0.4,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "brightnessBoost",
            "LABEL": "Phosphor Boost",
            "TYPE": "float",
            "DEFAULT": 0.33333,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.16667
        }
    ]
}*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define curvature_ (curvature*0.6)
#define brightnessBoost_ (0.8+brightnessBoost*1.2)


// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

void main() {
    vec2 uv = isf_FragNormCoord;

    // Fast path if completely disabled
    if (scanlineIntensity <= 0.001 && curvature_ <= 0.001 && phosphorMask <= 0.001 && vignette <= 0.001) {
        gl_FragColor = IMG_NORM_PIXEL(inputImage, uv);
        return;
    }

    // 1. CRT Barrel Distortion
    vec2 p = uv - 0.5;
    float r2 = dot(p, p);
    vec2 curvedUV = p * (1.0 + curvature_ * r2 * 1.5) + 0.5;

    // Bezel border clipping
    if (curvedUV.x < 0.0 || curvedUV.x > 1.0 || curvedUV.y < 0.0 || curvedUV.y > 1.0) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec4 color = IMG_NORM_PIXEL(inputImage, curvedUV);

    // 2. Horizontal Scanline Raster
    if (scanlineIntensity > 0.001) {
        float scanline = sin(curvedUV.y * scanlineCount * 6.2831853);
        scanline = (scanline * 0.5 + 0.5);
        color.rgb *= mix(1.0, scanline, scanlineIntensity);
    }

    // 3. RGB Phosphor Triad Sub-Pixel Stripe
    if (phosphorMask > 0.001) {
        int subPixel = int(mod(gl_FragCoord.x, 3.0));
        vec3 mask = vec3(1.0);
        if (subPixel == 0) mask = vec3(1.2, 0.9, 0.9);
        else if (subPixel == 1) mask = vec3(0.9, 1.2, 0.9);
        else mask = vec3(0.9, 0.9, 1.2);

        color.rgb *= mix(vec3(1.0), mask, phosphorMask);
    }

    // 4. Tube Glass Corner Vignette
    if (vignette > 0.001) {
        float vigFactor = 1.0 - smoothstep(0.4, 0.85, length(p) * (1.0 + vignette));
        color.rgb *= vigFactor;
    }

    color.rgb *= brightnessBoost_;
    gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), color.a);
}
