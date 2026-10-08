/*{
    "DESCRIPTION": "Cinematic anamorphic lens flare with horizontal exponential glare streaks, chromatic edge dispersion, highlight knee threshold, and starburst cross-flare",
    "CREDIT": "Liquid LSD (Cleanroom MIT)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Optics", "Glow", "Cinematic"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "streakIntensity",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.33333
        },
        {
            "NAME": "streakLength",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.47368
        },
        {
            "NAME": "threshold",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.65
        },
        {
            "NAME": "knee",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.38776
        },
        {
            "NAME": "colorMode",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 2.0,
            "STEP": 1,
            "DEFAULT": 0.0
        },
        {
            "NAME": "crossFlare",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.0
        }
    ]
}*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define streakIntensity_ (streakIntensity*3.0)
#define streakLength_ (0.05+streakLength*0.95)
#define knee_ (0.01+knee*0.49)


// Screen-space triangular dither hash
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Soft-knee_ highlight extractor
vec3 extractHighlight(vec3 col, float thresh, float kn) {
    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    float soft = clamp(luma - thresh + kn, 0.0, 2.0 * kn);
    soft = (soft * soft) / (4.0 * max(kn, 0.001));
    float factor = max(soft, luma - thresh) / max(luma, 0.0001);
    return col * clamp(factor, 0.0, 10.0);
}

void main() {
    vec2 uv = isf_FragNormCoord;
    vec4 srcColor = IMG_NORM_PIXEL(inputImage, uv);

    float dither = hash12(gl_FragCoord.xy) - 0.5;

    // Palette tint for streak wings
    vec3 tint;
    if (colorMode < 0.5) {
        // Sci-Fi Blue / Cyan Anamorphic Flare
        tint = vec3(0.2, 0.7, 1.3);
    } else if (colorMode < 1.5) {
        // Golden / Warm Sunset Starburst
        tint = vec3(1.3, 0.9, 0.4);
    } else {
        // Neutral / Source Tint
        tint = vec3(1.0, 1.0, 1.0);
    }

    vec3 streak = vec3(0.0);
    float totalWeight = 0.0;

    // 16-tap exponential blur along horizontal axis with chromatic fringe
    const int SAMPLES = 16;
    float maxDist = streakLength_ * 0.6;

    for (int i = -SAMPLES; i <= SAMPLES; i++) {
        if (i == 0) continue;
        float fi = float(i);
        float signFi = sign(fi);
        // Exponential spacing with jitter
        float normDist = pow(abs(fi) / float(SAMPLES), 1.6) * signFi;
        float offsetBase = normDist * maxDist + (dither / max(RENDERSIZE.x, 1.0));

        // Chromatic dispersion offsets: red disperses wider, blue tighter
        float offR = offsetBase * 1.06;
        float offG = offsetBase;
        float offB = offsetBase * 0.94;

        vec2 uvR = vec2(clamp(uv.x + offR, 0.0, 1.0), uv.y);
        vec2 uvG = vec2(clamp(uv.x + offG, 0.0, 1.0), uv.y);
        vec2 uvB = vec2(clamp(uv.x + offB, 0.0, 1.0), uv.y);

        vec3 tapR = extractHighlight(IMG_NORM_PIXEL(inputImage, uvR).rgb, threshold, knee_);
        vec3 tapG = extractHighlight(IMG_NORM_PIXEL(inputImage, uvG).rgb, threshold, knee_);
        vec3 tapB = extractHighlight(IMG_NORM_PIXEL(inputImage, uvB).rgb, threshold, knee_);

        float weight = exp(-abs(normDist) * 3.5);
        streak.r += tapR.r * weight;
        streak.g += tapG.g * weight;
        streak.b += tapB.b * weight;
        totalWeight += weight;
    }

    streak = (streak / max(totalWeight, 0.001)) * tint * streakIntensity_ * 3.5;

    // Optional cross-flare / starburst vertical streak
    if (crossFlare > 0.001) {
        vec3 vStreak = vec3(0.0);
        float vTotalWeight = 0.0;
        for (int i = -SAMPLES; i <= SAMPLES; i++) {
            if (i == 0) continue;
            float fi = float(i);
            float normDist = pow(abs(fi) / float(SAMPLES), 1.6) * sign(fi);
            float offset = normDist * maxDist * 0.7 + (dither / max(RENDERSIZE.y, 1.0));
            vec2 vUv = vec2(uv.x, clamp(uv.y + offset, 0.0, 1.0));
            vec3 tap = extractHighlight(IMG_NORM_PIXEL(inputImage, vUv).rgb, threshold, knee_);
            float weight = exp(-abs(normDist) * 4.0);
            vStreak += tap * weight;
            vTotalWeight += weight;
        }
        vStreak = (vStreak / max(vTotalWeight, 0.001)) * tint * streakIntensity_ * 2.5;
        streak = mix(streak, streak + vStreak * 0.7, clamp(crossFlare, 0.0, 1.0));
    }

    // Additive screen blend with source
    vec3 comp = 1.0 - (1.0 - srcColor.rgb) * (1.0 - clamp(streak, 0.0, 1.0));
    gl_FragColor = vec4(comp, srcColor.a);
}
