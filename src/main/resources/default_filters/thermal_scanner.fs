/*{
    "DESCRIPTION": "Tactical FLIR thermal camera and military night vision optics with heat bloom, sensor grain, and optic vignette",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Stylize", "Retro", "Optics"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "intensity",
            "LABEL": "Intensity",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "mode",
            "LABEL": "Sensor Mode",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 2.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "thermalBloom",
            "LABEL": "Edge Heat",
            "TYPE": "float",
            "DEFAULT": 0.3,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.3
        },
        {
            "NAME": "sensorGrain",
            "LABEL": "Sensor Grain",
            "TYPE": "float",
            "DEFAULT": 0.2,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.2
        },
        {
            "NAME": "vignette",
            "LABEL": "Optic Vignette",
            "TYPE": "float",
            "DEFAULT": 0.4,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        }
    ]
}*/

// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

// FLIR Ironbow thermal false-color mapping
vec3 flirIronbow(float t) {
    t = clamp(t, 0.0, 1.0);
    // Segmented polynomial fit to industry standard FLIR Ironbow ramp
    vec3 c = vec3(0.0);
    c.r = smoothstep(0.15, 0.55, t) + smoothstep(0.85, 1.0, t) * 0.5;
    c.g = smoothstep(0.45, 0.85, t) * (1.0 - smoothstep(0.85, 1.0, t) * 0.2);
    c.b = smoothstep(0.0, 0.35, t) * (1.0 - smoothstep(0.35, 0.65, t)) + smoothstep(0.85, 1.0, t);
    return clamp(c, 0.0, 1.0);
}

// Military NVG Green Phosphor
vec3 militaryNvg(float t) {
    t = clamp(t, 0.0, 1.0);
    vec3 darkGreen = vec3(0.0, 0.12, 0.02);
    vec3 brightGreen = vec3(0.1, 1.0, 0.25);
    vec3 hotWhite = vec3(0.85, 1.0, 0.9);

    if (t < 0.7) {
        return mix(darkGreen, brightGreen, t / 0.7);
    } else {
        return mix(brightGreen, hotWhite, (t - 0.7) / 0.3);
    }
}

// Standard Rainbow Heat Map
vec3 rainbowHeat(float t) {
    t = clamp(t, 0.0, 1.0);
    float r = clamp(1.5 - abs(t * 4.0 - 3.0), 0.0, 1.0);
    float g = clamp(1.5 - abs(t * 4.0 - 2.0), 0.0, 1.0);
    float b = clamp(1.5 - abs(t * 4.0 - 1.0), 0.0, 1.0);
    return vec3(r, g, b);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = isf_FragNormCoord;
    vec4 src = IMG_NORM_PIXEL(inputImage, uv);

    if (intensity <= 0.0001) {
        gl_FragColor = src;
        return;
    }

    float luma = dot(src.rgb, vec3(0.2126, 0.7152, 0.0722));

    // High-frequency thermal edge detection
    if (thermalBloom > 0.001) {
        vec2 texel = 1.5 / RENDERSIZE;
        float lumaR = dot(IMG_NORM_PIXEL(inputImage, uv + vec2(texel.x, 0.0)).rgb, vec3(0.2126, 0.7152, 0.0722));
        float lumaU = dot(IMG_NORM_PIXEL(inputImage, uv + vec2(0.0, texel.y)).rgb, vec3(0.2126, 0.7152, 0.0722));
        float edge = abs(luma - lumaR) + abs(luma - lumaU);
        luma += edge * thermalBloom * 1.5;
    }

    // Dynamic digital sensor grain
    if (sensorGrain > 0.001) {
        float grain = (hash(gl_FragCoord.xy + fract(TIME * 7.13)) - 0.5) * sensorGrain * 0.25;
        luma = clamp(luma + grain, 0.0, 1.0);
    }

    // False color palette lookup
    int sensorMode = int(floor(mode + 0.5));
    vec3 thermalRgb;
    if (sensorMode == 0) {
        thermalRgb = flirIronbow(luma);
    } else if (sensorMode == 1) {
        thermalRgb = militaryNvg(luma);
    } else {
        thermalRgb = rainbowHeat(luma);
    }

    // Spherical optical glass vignette
    if (vignette > 0.001) {
        vec2 p = uv - 0.5;
        float vigFactor = 1.0 - smoothstep(0.4, 0.85, length(p) * (1.0 + vignette * 0.8));
        thermalRgb *= vigFactor;
    }

    vec3 finalRgb = mix(src.rgb, thermalRgb, intensity);
    gl_FragColor = vec4(clamp(finalRgb, 0.0, 1.0), src.a);
}
