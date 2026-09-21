#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Domain-warped fluid simulation with multi-scale curl noise, dynamic vorticity, surface specular normals, and iridescent liquid marbling.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Organic",
        "Liquid"
    ],
    "INPUTS": [
        { "NAME": "WarpStrength", "LABEL": "Warp Strength", "TYPE": "float", "DEFAULT": 1.5, "MIN": 0.0, "MAX": 4.0 },
        { "NAME": "Swirl", "LABEL": "Swirl", "TYPE": "float", "DEFAULT": 1.0, "MIN": -3.0, "MAX": 3.0 },
        { "NAME": "Viscosity", "LABEL": "Viscosity", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.1, "MAX": 2.0 },
        { "NAME": "Speed", "LABEL": "Speed", "TYPE": "float", "DEFAULT": 0.35, "MIN": -2.0, "MAX": 2.0 },
        { "NAME": "Detail", "LABEL": "Detail", "TYPE": "float", "DEFAULT": 4.0, "MIN": 1.0, "MAX": 5.0 },
        { "NAME": "Gloss", "LABEL": "Specular Gloss", "TYPE": "float", "DEFAULT": 1.4, "MIN": 0.0, "MAX": 3.0 },
        { "NAME": "PaletteMode", "LABEL": "Palette Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 4.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueCycleSpeed", "LABEL": "Hue Cycle Speed", "TYPE": "float", "DEFAULT": 0.05, "MIN": -0.5, "MAX": 0.5 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.2, "MAX": 5.0 }
    ]
}
*/

vec2 hash2(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(
        mix(dot(hash2(i + vec2(0.0, 0.0)), f - vec2(0.0, 0.0)),
            dot(hash2(i + vec2(1.0, 0.0)), f - vec2(1.0, 0.0)), u.x),
        mix(dot(hash2(i + vec2(0.0, 1.0)), f - vec2(0.0, 1.0)),
            dot(hash2(i + vec2(1.0, 1.0)), f - vec2(1.0, 1.0)), u.x),
        u.y
    );
}

float fbm(vec2 p, int octaves) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 5; i++) {
        if (i >= octaves) break;
        v += a * noise(p);
        p = rot * p * 2.02 + vec2(100.0);
        a *= 0.5;
    }
    return v;
}

vec3 cosinePalette(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
    return a + b * cos(6.28318530718 * (c * t + d));
}

vec3 getPaletteColor(float t, int mode, float hue) {
    t = fract(t + hue);
    if (mode == 0) {
        // Psychedelic Neon
        return cosinePalette(t, vec3(0.5, 0.5, 0.5), vec3(0.5, 0.5, 0.5), vec3(1.0, 1.0, 1.0), vec3(0.0, 0.33, 0.67));
    } else if (mode == 1) {
        // Liquid Chrome
        vec3 col = cosinePalette(t, vec3(0.7, 0.75, 0.8), vec3(0.3, 0.25, 0.2), vec3(2.0, 2.0, 2.0), vec3(0.0, 0.1, 0.2));
        return mix(col, vec3(1.0), pow(t, 4.0) * 0.5);
    } else if (mode == 2) {
        // Oil Slick
        return cosinePalette(t, vec3(0.5, 0.5, 0.5), vec3(0.5, 0.5, 0.5), vec3(2.0, 1.0, 0.0), vec3(0.5, 0.2, 0.25));
    } else if (mode == 3) {
        // Opal Sunset
        return cosinePalette(t, vec3(0.8, 0.5, 0.4), vec3(0.2, 0.4, 0.2), vec3(2.0, 1.0, 1.0), vec3(0.0, 0.25, 0.25));
    } else {
        // Deep Ocean
        return cosinePalette(t, vec3(0.1, 0.4, 0.5), vec3(0.2, 0.5, 0.5), vec3(1.0, 1.0, 1.0), vec3(0.5, 0.7, 0.8));
    }
}

void main() {
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 uv = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0) / max(Zoom, 0.05);

    float t = TIME * Speed;
    int oct = clamp(int(floor(Detail + 0.5)), 1, 5);

    float swirlAngle = Swirl * 0.5;
    mat2 swirlMat = mat2(cos(swirlAngle), -sin(swirlAngle), sin(swirlAngle), cos(swirlAngle));

    vec2 q = vec2(
        fbm(uv + vec2(0.0, 0.0) + t * 0.2, oct),
        fbm(uv + vec2(5.2, 1.3) + t * 0.25, oct)
    );

    vec2 r = vec2(
        fbm(uv + WarpStrength * (swirlMat * q) + vec2(1.7, 9.2) + t * 0.15, oct),
        fbm(uv + WarpStrength * q + vec2(8.3, 2.8) + t * 0.126, oct)
    );

    vec2 pWarped = uv + WarpStrength * r;
    float f = fbm(pWarped / max(Viscosity, 0.1), oct);

    float eps = 0.005;
    float fx = fbm((pWarped + vec2(eps, 0.0)) / max(Viscosity, 0.1), oct);
    float fy = fbm((pWarped + vec2(0.0, eps)) / max(Viscosity, 0.1), oct);
    vec3 normal = normalize(vec3((f - fx) / eps * Gloss, (f - fy) / eps * Gloss, 1.0));

    vec3 lightDir = normalize(vec3(0.5, 0.8, 0.7));
    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    vec3 halfDir = normalize(lightDir + viewDir);

    float diff = max(dot(normal, lightDir), 0.0);
    float spec = pow(max(dot(normal, halfDir), 0.0), 16.0) * Gloss;
    float fresnel = pow(1.0 - max(dot(normal, viewDir), 0.0), 3.0);

    float totalHue = HueOffset + TIME * HueCycleSpeed;
    int mode = clamp(int(floor(PaletteMode + 0.5)), 0, 4);
    
    vec3 baseColor = getPaletteColor(f * 0.5 + 0.5, mode, totalHue);
    vec3 warpColor = getPaletteColor(length(q) * 0.7, (mode + 1) % 5, totalHue + 0.3);

    vec3 col = mix(baseColor, warpColor, clamp(length(r), 0.0, 1.0));
    col = col * (0.6 + 0.4 * diff) + vec3(spec) + vec3(fresnel * 0.4);

    gl_FragColor = vec4(col, 1.0);
}
