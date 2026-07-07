#version 330 core
in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTextureLive;
uniform sampler2D uTextureHistory;

uniform float uDecay;
uniform float uGain;
uniform float uZoom;
uniform float uRotate;
uniform float uHueShift;
uniform float uBlur;
uniform float uChroma;
uniform float uFeedbackMode;
uniform float uKaleido;

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

// Helper to sample history with optional box blur
vec4 sampleHistory(vec2 uv) {
    if (uBlur > 0.0) {
        float offset = uBlur * 0.005;
        vec4 color = texture(uTextureHistory, uv);
        color += texture(uTextureHistory, uv + vec2(offset, 0.0));
        color += texture(uTextureHistory, uv + vec2(-offset, 0.0));
        color += texture(uTextureHistory, uv + vec2(0.0, offset));
        color += texture(uTextureHistory, uv + vec2(0.0, -offset));
        return color / 5.0;
    } else {
        return texture(uTextureHistory, uv);
    }
}

void main() {
    vec4 liveColor = texture(uTextureLive, vTexCoord);

    // Apply coordinate transformations around center (0.5, 0.5) for zoom/rotate feedback
    vec2 uv = vTexCoord - vec2(0.5);

    // Kaleidoscope / radial symmetry
    float segments = floor(uKaleido + 0.5);
    if (segments > 1.0) {
        float angle = atan(uv.y, uv.x);
        float radius = length(uv);
        float segmentAngle = 6.28318530718 / segments;
        
        angle = mod(angle, segmentAngle);
        angle = abs(angle - segmentAngle / 2.0);
        
        uv = vec2(radius * cos(angle), radius * sin(angle));
    }
    
    // Zoom factor (positive zooms in)
    uv *= (1.0 - uZoom);

    // Rotation factor (radians)
    float cosRot = cos(uRotate);
    float sinRot = sin(uRotate);
    uv = vec2(
        uv.x * cosRot - uv.y * sinRot,
        uv.x * sinRot + uv.y * cosRot
    );

    // Sample historical buffer with optional Chromatic Aberration split
    vec4 historyColor;
    if (uChroma > 0.0) {
        vec2 uvR = uv * (1.0 - uChroma * 0.05) + vec2(0.5);
        vec2 uvG = uv + vec2(0.5);
        vec2 uvB = uv * (1.0 + uChroma * 0.05) + vec2(0.5);
        
        float r = sampleHistory(uvR).r;
        float g = sampleHistory(uvG).g;
        float b = sampleHistory(uvB).b;
        float a = sampleHistory(uvG).a;
        historyColor = vec4(r, g, b, a);
    } else {
        historyColor = sampleHistory(uv + vec2(0.5));
    }

    // Apply decay and gain (scale RGB and alpha proportionally)
    historyColor.rgb *= uGain * (1.0 - uDecay);
    historyColor.a = clamp(historyColor.a - uDecay, 0.0, 1.0);

    // Apply hue shift to history
    if (uHueShift != 0.0 && historyColor.a > 0.0) {
        vec3 hsv = rgb2hsv(historyColor.rgb);
        hsv.x = fract(hsv.x + uHueShift);
        historyColor.rgb = hsv2rgb(hsv);
    }

    // Blend live frame with history using standard maximum or difference blend
    vec4 blended;
    if (uFeedbackMode >= 0.5) {
        blended = vec4(abs(liveColor.rgb - historyColor.rgb), max(liveColor.a, historyColor.a));
    } else {
        blended = max(liveColor, historyColor);
    }
    fragColor = blended;
}
