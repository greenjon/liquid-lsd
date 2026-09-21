/*{
    "DESCRIPTION": "Noise Dissolve",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Dissolve", "Organic", "Transitions"],
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
            "NAME": "noiseScale",
            "TYPE": "float",
            "MIN": 2.0,
            "MAX": 20.0,
            "DEFAULT": 6.0
        },
        {
            "NAME": "softness",
            "TYPE": "float",
            "MIN": 0.01,
            "MAX": 0.4,
            "DEFAULT": 0.15
        },
        {
            "NAME": "chromaShift",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 0.5,
            "DEFAULT": 0.1
        }
    ]
}*/

float hash(vec2 p) {
    p = fract(p * vec2(213.12, 543.34));
    p += dot(p, p + 33.33);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(
        mix(hash(i + vec2(0.0, 0.0)), hash(i + vec2(1.0, 0.0)), u.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
        u.y
    );
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.8, -0.6, 0.6, 0.8);
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p = rot * p * 2.0;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec2 uv = isf_FragNormCoord;
    float p = clamp(progress, 0.0, 1.0);

    // Domain-warped coordinates for organic fluid curl
    vec2 q = vec2(fbm(uv * noiseScale), fbm(uv * noiseScale + vec2(5.2, 1.3)));
    float pattern = fbm(uv * noiseScale + q * 1.5);

    // Map progress to seamlessly cover 0.0 to 1.0
    float s = max(0.005, softness);
    float threshold = p * (1.0 + s * 2.0) - s;
    float alpha = smoothstep(threshold - s, threshold + s, pattern);

    // Subtle chromatic aberration on the threshold boundary
    float edgeFactor = (1.0 - abs(alpha - 0.5) * 2.0) * chromaShift * 0.04;
    vec4 colA;
    colA.r = IMG_NORM_PIXEL(startImage, uv + vec2(edgeFactor, 0.0)).r;
    colA.g = IMG_NORM_PIXEL(startImage, uv).g;
    colA.b = IMG_NORM_PIXEL(startImage, uv - vec2(edgeFactor, 0.0)).b;
    colA.a = IMG_NORM_PIXEL(startImage, uv).a;

    vec4 colB;
    colB.r = IMG_NORM_PIXEL(endImage, uv - vec2(edgeFactor, 0.0)).r;
    colB.g = IMG_NORM_PIXEL(endImage, uv).g;
    colB.b = IMG_NORM_PIXEL(endImage, uv + vec2(edgeFactor, 0.0)).b;
    colB.a = IMG_NORM_PIXEL(endImage, uv).a;

    gl_FragColor = mix(colB, colA, alpha);
}
