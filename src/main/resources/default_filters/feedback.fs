/*{
    "DESCRIPTION": "Full feedback loop with zoom, rotate, kaleidoscope, hue shift, chromatic aberration, blur, and mode blend",
    "CREDIT": "Liquid LSD Engine",
    "CATEGORIES": ["Feedback", "Distortion", "Stylize"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "fbDecay",
            "LABEL": "Decay",
            "TYPE": "float",
            "DEFAULT": 0.15,
            "MIN": 0.0,
            "MAX": 1.0
        },
        {
            "NAME": "fbGain",
            "LABEL": "Gain",
            "TYPE": "float",
            "DEFAULT": 0.98,
            "MIN": 0.0,
            "MAX": 2.0
        },
        {
            "NAME": "fbZoom",
            "LABEL": "Zoom",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -0.5,
            "MAX": 0.5
        },
        {
            "NAME": "fbRotate",
            "LABEL": "Rotate",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -3.14159,
            "MAX": 3.14159
        },
        {
            "NAME": "fbHueShift",
            "LABEL": "Hue Shift",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -1.0,
            "MAX": 1.0
        },
        {
            "NAME": "fbBlur",
            "LABEL": "Blur",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 1.0
        },
        {
            "NAME": "fbChroma",
            "LABEL": "Chromatic Aberration",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 1.0
        },
        {
            "NAME": "fbMode",
            "LABEL": "Blend Mode",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 1.0
        },
        {
            "NAME": "fbKaleido",
            "LABEL": "Kaleidoscope Segments",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 12.0
        }
    ],
    "PASSES": [
        {
            "TARGET": "historyPass",
            "PERSISTENT": true,
            "FLOAT": true
        },
        {}
    ]
}*/

// RGB to HSV helper
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.y - q.z) / (6.0 * d + e)), d / (q.x + e), q.x);
}

// HSV to RGB helper
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Helper to sample persistent history buffer with optional box blur
vec4 sampleHistory(vec2 uv) {
    if (fbBlur > 0.0) {
        float offset = fbBlur * 0.005;
        vec4 color = IMG_NORM_PIXEL(historyPass, uv);
        color += IMG_NORM_PIXEL(historyPass, uv + vec2(offset, 0.0));
        color += IMG_NORM_PIXEL(historyPass, uv + vec2(-offset, 0.0));
        color += IMG_NORM_PIXEL(historyPass, uv + vec2(0.0, offset));
        color += IMG_NORM_PIXEL(historyPass, uv + vec2(0.0, -offset));
        return color / 5.0;
    } else {
        return IMG_NORM_PIXEL(historyPass, uv);
    }
}

void main() {
    if (PASSINDEX == 0) {
        vec4 liveColor = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord);

        // Apply coordinate transformations around center (0.5, 0.5) for zoom/rotate feedback
        vec2 uv = isf_FragNormCoord - vec2(0.5);

        // Kaleidoscope / radial symmetry
        float segments = floor(fbKaleido + 0.5);
        if (segments > 1.0) {
            float angle = atan(uv.y, uv.x);
            float radius = length(uv);
            float segmentAngle = 6.28318530718 / segments;

            angle = mod(angle, segmentAngle);
            angle = abs(angle - segmentAngle / 2.0);

            uv = vec2(radius * cos(angle), radius * sin(angle));
        }

        // Zoom factor (positive zooms in)
        uv *= (1.0 - fbZoom);

        // Rotation factor (radians)
        float cosRot = cos(fbRotate);
        float sinRot = sin(fbRotate);
        uv = vec2(
            uv.x * cosRot - uv.y * sinRot,
            uv.x * sinRot + uv.y * cosRot
        );

        // Sample historical buffer with optional Chromatic Aberration split
        vec4 historyColor;
        if (fbChroma > 0.0) {
            vec2 uvR = uv * (1.0 - fbChroma * 0.05) + vec2(0.5);
            vec2 uvG = uv + vec2(0.5);
            vec2 uvB = uv * (1.0 + fbChroma * 0.05) + vec2(0.5);

            float r = sampleHistory(uvR).r;
            float g = sampleHistory(uvG).g;
            float b = sampleHistory(uvB).b;
            float a = sampleHistory(uvG).a;
            historyColor = vec4(r, g, b, a);
        } else {
            historyColor = sampleHistory(uv + vec2(0.5));
        }

        // Apply decay and gain (scale RGB and alpha proportionally)
        historyColor.rgb *= fbGain * (1.0 - fbDecay);
        historyColor.a = clamp(historyColor.a - fbDecay, 0.0, 1.0);

        // Apply hue shift to history
        if (fbHueShift != 0.0 && historyColor.a > 0.0) {
            vec3 hsv = rgb2hsv(historyColor.rgb);
            hsv.x = fract(hsv.x + fbHueShift);
            historyColor.rgb = hsv2rgb(hsv);
        }

        // Blend live frame with history using standard maximum or difference blend
        vec4 blended;
        if (fbMode >= 0.5) {
            blended = vec4(abs(liveColor.rgb - historyColor.rgb), max(liveColor.a, historyColor.a));
        } else {
            blended = max(liveColor, historyColor);
        }
        gl_FragColor = blended;
    } else {
        gl_FragColor = IMG_NORM_PIXEL(historyPass, isf_FragNormCoord);
    }
}
