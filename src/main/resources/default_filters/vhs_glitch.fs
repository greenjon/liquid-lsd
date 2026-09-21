/*{
    "DESCRIPTION": "Authentic magnetic VHS videotape degradation with tracking error jitter, bottom head-switching bar, Y/C chrominance delay, RF tape dropouts, and analog static",
    "CREDIT": "Liquid LSD (Cleanroom MIT)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Retro", "Glitch", "Stylize"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "trackingJitter",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.4
        },
        {
            "NAME": "headSwitching",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.6
        },
        {
            "NAME": "ycDelay",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.35
        },
        {
            "NAME": "rfDropouts",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.3
        },
        {
            "NAME": "tapeNoise",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.35
        },
        {
            "NAME": "mixRatio",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 1.0
        }
    ]
}*/

// Fast pseudo-random hash functions
float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Seamless mirror coordinate wrapping
vec2 mirrorCoords(vec2 p) {
    vec2 m = mod(p, 2.0);
    return mix(m, 2.0 - m, step(1.0, m));
}

void main() {
    vec2 uv = isf_FragNormCoord;
    vec4 srcColor = IMG_NORM_PIXEL(inputImage, uv);

    float t = TIME;
    vec2 displacedUv = uv;

    // 1. Helical Scan Tracking Error Bar (moves down or up screen)
    if (trackingJitter > 0.001) {
        float barPos = fract(t * 0.2);
        float distToBar = abs(uv.y - barPos);
        distToBar = min(distToBar, 1.0 - distToBar); // Cyclic wrap
        float barWidth = 0.12 + trackingJitter * 0.08;

        if (distToBar < barWidth) {
            float strength = smoothstep(barWidth, 0.0, distToBar);
            // High-frequency jitter per scanline inside the tracking error bar
            float lineNoise = (hash11(floor(gl_FragCoord.y) + floor(t * 60.0)) - 0.5) * 0.04;
            float waveDisplace = sin(uv.y * 30.0 + t * 5.0) * 0.025;
            displacedUv.x += (waveDisplace + lineNoise) * strength * trackingJitter;
        }

        // Global slight line jitter (sync instability)
        float globalLineJitter = (hash11(floor(gl_FragCoord.y) + floor(t * 30.0)) - 0.5) * 0.003 * trackingJitter;
        displacedUv.x += globalLineJitter;
    }

    // 2. VCR Head-Switching Noise Bar (bottom 2.5% to 4% of frame)
    float headBarHeight = 0.035 * headSwitching;
    float headStaticNoise = 0.0;
    if (headSwitching > 0.001 && uv.y < headBarHeight) {
        float normY = uv.y / max(headBarHeight, 0.001);
        float headDisplace = sin(normY * 12.0 + t * 40.0) * 0.06 * (1.0 - normY) * headSwitching;
        displacedUv.x += headDisplace;
        headStaticNoise = hash12(gl_FragCoord.xy + fract(t * 100.0)) * (1.0 - normY) * headSwitching;
    }

    // 3. Y/C Delay (Chroma Lag / Color Bleed)
    // In composite/S-video tape, color bandwidth is narrow and lags behind luma
    float chromaOffset = ycDelay * 0.012;
    vec2 uvLuma = mirrorCoords(displacedUv);
    vec2 uvRed = mirrorCoords(displacedUv + vec2(chromaOffset * 1.1, 0.0));
    vec2 uvBlue = mirrorCoords(displacedUv - vec2(chromaOffset * 0.9, 0.0));

    float r = IMG_NORM_PIXEL(inputImage, uvRed).r;
    float g = IMG_NORM_PIXEL(inputImage, uvLuma).g;
    float b = IMG_NORM_PIXEL(inputImage, uvBlue).b;
    vec3 vhsRgb = vec3(r, g, b);

    // 4. RF Tape Dropouts (horizontal white/black streaks from loss of magnetic particles)
    if (rfDropouts > 0.001) {
        float lineId = floor(gl_FragCoord.y * 0.5);
        float timeId = floor(t * 24.0);
        float dropRand = hash12(vec2(lineId, timeId));
        if (dropRand > (1.0 - rfDropouts * 0.03)) {
            float dropPos = hash12(vec2(lineId + 1.0, timeId));
            float dropLen = 0.08 + hash12(vec2(lineId + 2.0, timeId)) * 0.15;
            if (uv.x > dropPos && uv.x < dropPos + dropLen) {
                float intensity = hash11(gl_FragCoord.x);
                vhsRgb = mix(vhsRgb, vec3(intensity > 0.5 ? 1.0 : 0.0), 0.85);
            }
        }
    }

    // 5. Tape Magnetic Hiss Noise and Head Switching Blend
    if (tapeNoise > 0.001 || headStaticNoise > 0.0) {
        float staticNoise = (hash12(gl_FragCoord.xy * 0.5 + fract(t * 50.0)) - 0.5) * tapeNoise * 0.18;
        vhsRgb = clamp(vhsRgb + staticNoise + headStaticNoise * 0.7, 0.0, 1.0);
    }

    vec3 finalColor = mix(srcColor.rgb, vhsRgb, clamp(mixRatio, 0.0, 1.0));
    gl_FragColor = vec4(finalColor, srcColor.a);
}
