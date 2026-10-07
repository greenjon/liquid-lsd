/*{
    "DESCRIPTION": "Luminous Flash",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Flash", "Transitions"],
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
        },
        {
            "NAME": "colorTemp",
            "TYPE": "float",
            "MIN": -1.0,
            "MAX": 1.0,
            "DEFAULT": 0.1
        },
        {
            "NAME": "spread",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.33333
        }
    ]
}*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define intensity_ (intensity*2.0)
#define spread_ (0.1+spread*0.9)


#define PI 3.14159265359

void main() {
    vec2 uv = isf_FragNormCoord;
    float p = clamp(progress, 0.0, 1.0);

    vec4 colA = IMG_NORM_PIXEL(startImage, uv);
    vec4 colB = IMG_NORM_PIXEL(endImage, uv);

    // Smoothstep crossfade base
    float t = smoothstep(0.0, 1.0, p);
    vec4 base = mix(colA, colB, t);

    // Midpoint flash envelope (Gaussian shape peaking at 0.5)
    float distMid = abs(p - 0.5);
    float sigma = max(0.01, spread_ * 0.25);
    float flashEnv = exp(-(distMid * distMid) / (2.0 * sigma * sigma)) * intensity_;

    // Flash tint based on colorTemp (-1 = icy cyan/blue strobe, 0 = pure white, 1 = warm tungsten/amber)
    vec3 flashTint = vec3(1.0);
    if (colorTemp > 0.0) {
        flashTint = mix(vec3(1.0), vec3(1.2, 0.95, 0.65), colorTemp);
    } else {
        flashTint = mix(vec3(1.0), vec3(0.65, 0.95, 1.2), -colorTemp);
    }

    // Additive luminous blast with filmic tonemapping
    vec3 flashColor = flashTint * flashEnv * 2.0;
    vec3 hdr = base.rgb + flashColor;

    // Filmic Reinhard compression to retain detail during peak flash
    vec3 ldr = hdr / (vec3(1.0) + hdr);
    // Un-compress smoothly back to normal when flash is zero
    vec3 finalRgb = mix(base.rgb, ldr * (1.0 + flashEnv), clamp(flashEnv, 0.0, 1.0));

    gl_FragColor = vec4(finalRgb, mix(colA.a, colB.a, t));
}
