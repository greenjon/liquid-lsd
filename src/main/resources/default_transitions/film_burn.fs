/*{
    "DESCRIPTION": "Film Burn",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Burn", "Dissolve", "Transitions"],
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
            "NAME": "burnSpread",
            "TYPE": "float",
            "MIN": 0.02,
            "MAX": 0.3,
            "DEFAULT": 0.12
        },
        {
            "NAME": "glowIntensity",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 3.0,
            "DEFAULT": 1.8
        },
        {
            "NAME": "tintMode",
            "TYPE": "long",
            "VALUES": [0, 1, 2],
            "LABELS": ["Fiery Ember", "Electric Violet", "Acid Green"],
            "DEFAULT": 0
        }
    ]
}*/

// Fast 2D hash
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// 2D Value Noise
float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(
        mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
        u.y
    );
}

// Multi-octave FBM
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = rot * p * 2.0;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec2 uv = isf_FragNormCoord;
    float p = clamp(progress, 0.0, 1.0);

    vec4 colA = IMG_NORM_PIXEL(startImage, uv);
    vec4 colB = IMG_NORM_PIXEL(endImage, uv);

    // Procedural organic burn texture
    vec2 noiseCoord = uv * 3.5 + vec2(sin(p * 3.1415), cos(p * 3.1415)) * 0.2;
    float n = fbm(noiseCoord);

    // Radial burn bias (burn tends to ignite from center or hot zones)
    float dist = length(uv - vec2(0.5)) * 0.4;
    float threshold = n * 0.7 + dist;

    // Remap progress to cover the entire threshold range smoothly
    float mappedP = p * 1.3 - 0.15;
    float diff = threshold - mappedP;

    // Base crossfade mix
    float cut = smoothstep(0.0, burnSpread, diff);
    vec4 baseColor = mix(colB, colA, cut);

    // Glowing ember ignition edge
    float edge = 1.0 - smoothstep(0.0, burnSpread, abs(diff));
    edge = pow(edge, 1.8);

    vec3 emberColor;
    if (tintMode == 0) {
        // Fiery Ember: white-hot center to orange/red halo
        emberColor = mix(vec3(1.0, 0.3, 0.05), vec3(1.2, 1.1, 0.8), edge * 0.7);
    } else if (tintMode == 1) {
        // Electric Violet / Magenta plasma
        emberColor = mix(vec3(0.6, 0.05, 1.0), vec3(0.9, 0.8, 1.2), edge * 0.7);
    } else {
        // Acid Green / Cyber toxic flame
        emberColor = mix(vec3(0.1, 1.0, 0.2), vec3(0.8, 1.2, 0.9), edge * 0.7);
    }

    vec3 finalRgb = baseColor.rgb + emberColor * edge * glowIntensity;
    gl_FragColor = vec4(finalRgb, mix(colA.a, colB.a, p));
}
