/*{
    "DESCRIPTION": "Dual-mode chromatic aberration and RGB glitch with 3-tap channel split and 12-tap continuous spectral lens dispersion",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Glitch", "Optics", "Color Adjustment"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "amount",
            "LABEL": "Displacement",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 0.1,
            "IDENTITY": 0.0
        },
        {
            "NAME": "angle",
            "LABEL": "Angle",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -3.14159,
            "MAX": 3.14159,
            "IDENTITY": 0.0
        },
        {
            "NAME": "radialMode",
            "LABEL": "Radial Mode",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "dispersionMode",
            "LABEL": "Lens Dispersion",
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

// Spectral weighting function approximating wavelength response (400nm - 700nm)
vec3 spectralWeight(float t) {
    // t in [-1.0, 1.0] from violet/blue to red
    float r = smoothstep(-0.2, 0.6, t);
    float g = 1.0 - abs(t) * 1.5;
    float b = 1.0 - smoothstep(-0.6, 0.2, t);
    return max(vec3(r, g, b), vec3(0.0));
}

void main() {
    vec2 uv = isf_FragNormCoord;

    // Fast-path bypass when displacement is zero
    if (amount <= 0.0001) {
        gl_FragColor = IMG_NORM_PIXEL(inputImage, uv);
        return;
    }

    // Determine displacement vector: directional angle vs radial barrel falloff
    vec2 offsetDir;
    if (radialMode > 0.5) {
        vec2 fromCenter = uv - vec2(0.5);
        float dist = length(fromCenter);
        // Quadratic distance weighting creates natural optical barrel fringing
        offsetDir = fromCenter * (dist * amount * 2.0);
    } else {
        offsetDir = vec2(cos(angle), sin(angle)) * amount;
    }

    if (dispersionMode < 0.5) {
        // Mode 0: Fast 3-Tap Discrete RGB Glitch
        vec2 rCoord = clamp(uv + offsetDir, vec2(0.0), vec2(1.0));
        vec2 gCoord = uv;
        vec2 bCoord = clamp(uv - offsetDir, vec2(0.0), vec2(1.0));

        float r = IMG_NORM_PIXEL(inputImage, rCoord).r;
        vec4 gPixel = IMG_NORM_PIXEL(inputImage, gCoord);
        float b = IMG_NORM_PIXEL(inputImage, bCoord).b;

        gl_FragColor = vec4(r, gPixel.g, b, gPixel.a);
    } else {
        // Mode 1: 12-Tap Continuous Spectral Lens Dispersion
        const int TAPS = 12;
        vec3 accumColor = vec3(0.0);
        vec3 totalWeight = vec3(0.0);

        for (int i = 0; i < TAPS; i++) {
            // Normalized parameter t from -1.0 (blue) to +1.0 (red)
            float t = (float(i) / float(TAPS - 1)) * 2.0 - 1.0;
            vec2 sampleCoord = clamp(uv + offsetDir * t, vec2(0.0), vec2(1.0));
            vec3 tap = IMG_NORM_PIXEL(inputImage, sampleCoord).rgb;
            vec3 weight = spectralWeight(t);

            accumColor += tap * weight;
            totalWeight += weight;
        }

        vec3 finalRgb = accumColor / max(totalWeight, vec3(0.0001));
        float alpha = IMG_NORM_PIXEL(inputImage, uv).a;
        gl_FragColor = vec4(finalRgb, alpha);
    }
}
