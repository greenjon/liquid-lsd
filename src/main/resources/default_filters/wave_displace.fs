/*{
    "DESCRIPTION": "Dual-mode fluid wave displacement: Cartesian cross-waves and aspect-corrected concentric liquid droplet ripples with seamless mirror wrapping",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Distortion", "Organic", "Psychedelic"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "amplitude",
            "LABEL": "Amplitude",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 0.2,
            "IDENTITY": 0.0
        },
        {
            "NAME": "frequency",
            "LABEL": "Frequency",
            "TYPE": "float",
            "DEFAULT": 10.0,
            "MIN": 0.5,
            "MAX": 50.0,
            "IDENTITY": 10.0
        },
        {
            "NAME": "speed",
            "LABEL": "Speed",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": -5.0,
            "MAX": 5.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "rippleMode",
            "LABEL": "Ripple Mode",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "centerX",
            "LABEL": "Center X",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.5
        },
        {
            "NAME": "centerY",
            "LABEL": "Center Y",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.5
        },
        {
            "NAME": "phaseOffset",
            "LABEL": "Phase Offset",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 6.28318,
            "IDENTITY": 0.0
        }
    ]
}*/

// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

void main() {
    vec2 uv = isf_FragNormCoord;

    if (amplitude <= 0.0001) {
        gl_FragColor = IMG_NORM_PIXEL(inputImage, uv);
        return;
    }

    float aspect = RENDERSIZE.x / max(RENDERSIZE.y, 1.0);
    float timePhase = (TIME * speed * 3.14159) + phaseOffset;
    vec2 offset = vec2(0.0);

    if (rippleMode < 0.5) {
        // Mode 0: Fluid Cartesian Cross Waves
        offset.x = sin(uv.y * frequency * 6.28318 + timePhase) * amplitude;
        offset.y = cos(uv.x * frequency * 6.28318 + timePhase) * (amplitude / aspect);
    } else {
        // Mode 1: Aspect-Corrected Concentric Liquid Droplet Ripples
        vec2 center = vec2(centerX, centerY);
        vec2 p = uv - center;
        p.x *= aspect;

        float r = length(p);
        if (r > 0.0001) {
            float wave = sin(r * frequency * 6.28318 - timePhase);
            // Attenuate slightly toward outer boundaries for natural pool ripple falloff
            float falloff = smoothstep(1.5, 0.0, r);
            vec2 dir = p / r;
            dir.x /= aspect;
            offset = dir * (wave * amplitude * falloff);
        }
    }

    // Seamless mirror boundary wrapping eliminates edge tearing
    vec2 sampleCoord = abs(mod(uv + offset, 2.0) - 1.0);
    gl_FragColor = IMG_NORM_PIXEL(inputImage, sampleCoord);
}
