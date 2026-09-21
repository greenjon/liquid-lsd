/*{
    "DESCRIPTION": "Authentic 4-plate CMYK lithographic screen angles with anti-aliased dot rosettes, monochrome halftone, and paper texture tinting",
    "CREDIT": "Liquid LSD (Cleanroom MIT)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Stylize", "Retro", "Print"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "dotScale",
            "TYPE": "float",
            "MIN": 5.0,
            "MAX": 150.0,
            "DEFAULT": 45.0
        },
        {
            "NAME": "screenAngle",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 90.0,
            "DEFAULT": 0.0
        },
        {
            "NAME": "mode",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 2.0,
            "DEFAULT": 0.0
        },
        {
            "NAME": "smoothness",
            "TYPE": "float",
            "MIN": 0.02,
            "MAX": 0.8,
            "DEFAULT": 0.15
        },
        {
            "NAME": "paperTint",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.12
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

#define PI 3.14159265358979323846

vec2 rotateVec(vec2 p, float angleRad) {
    float s = sin(angleRad);
    float c = cos(angleRad);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float evaluateDot(vec2 uv, float angleRad, float scale, float coverage, float smoothVal) {
    if (coverage <= 0.001) return 0.0;
    if (coverage >= 0.999) return 1.0;

    vec2 pRot = rotateVec(uv, angleRad) * scale;
    vec2 gridFrac = fract(pRot) - 0.5;
    float dist = length(gridFrac);

    float targetRadius = sqrt(clamp(coverage, 0.0, 1.0)) * 0.5 * 1.4142;
    float fw = length(fwidth(pRot)) * 0.5 + smoothVal * 0.1;
    return 1.0 - smoothstep(targetRadius - fw, targetRadius + fw, dist);
}

void main() {
    vec2 uv = isf_FragNormCoord;
    vec4 srcColor = IMG_NORM_PIXEL(inputImage, uv);

    float aspect = RENDERSIZE.x / max(RENDERSIZE.y, 1.0);
    vec2 aspectCoord = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);

    float globalAngle = screenAngle * (PI / 180.0);
    vec3 outRgb;

    if (mode < 0.5) {
        // Mode 0: 4-Plate CMYK Rosette (C: 15 deg, M: 75 deg, Y: 0 deg, K: 45 deg)
        float r = clamp(srcColor.r, 0.0, 1.0);
        float g = clamp(srcColor.g, 0.0, 1.0);
        float b = clamp(srcColor.b, 0.0, 1.0);

        float k = 1.0 - max(max(r, g), b);
        float denom = max(1.0 - k, 0.0001);
        float c = clamp((1.0 - r - k) / denom, 0.0, 1.0);
        float m = clamp((1.0 - g - k) / denom, 0.0, 1.0);
        float y = clamp((1.0 - b - k) / denom, 0.0, 1.0);

        float dotC = evaluateDot(aspectCoord, globalAngle + 15.0 * (PI / 180.0), dotScale, c, smoothness);
        float dotM = evaluateDot(aspectCoord, globalAngle + 75.0 * (PI / 180.0), dotScale, m, smoothness);
        float dotY = evaluateDot(aspectCoord, globalAngle + 0.0 * (PI / 180.0), dotScale, y, smoothness);
        float dotK = evaluateDot(aspectCoord, globalAngle + 45.0 * (PI / 180.0), dotScale, k, smoothness);

        vec3 paper = mix(vec3(1.0), vec3(0.97, 0.95, 0.91), paperTint);
        vec3 inkC = vec3(0.0, 0.75, 0.95);
        vec3 inkM = vec3(0.92, 0.05, 0.55);
        vec3 inkY = vec3(0.98, 0.92, 0.05);
        vec3 inkK = vec3(0.06, 0.06, 0.07);

        vec3 comp = paper;
        comp = mix(comp, comp * (vec3(1.0) - inkC * 0.9), dotC);
        comp = mix(comp, comp * (vec3(1.0) - inkM * 0.9), dotM);
        comp = mix(comp, comp * (vec3(1.0) - inkY * 0.9), dotY);
        comp = mix(comp, comp * (vec3(1.0) - inkK * 0.95), dotK);

        outRgb = comp;
    } else if (mode < 1.5) {
        // Mode 1: Monochrome Dot Screen (Newsprint)
        float luma = dot(srcColor.rgb, vec3(0.2126, 0.7152, 0.0722));
        float coverage = 1.0 - clamp(luma, 0.0, 1.0);
        float dotMono = evaluateDot(aspectCoord, globalAngle + 45.0 * (PI / 180.0), dotScale, coverage, smoothness);

        vec3 paper = mix(vec3(1.0), vec3(0.95, 0.93, 0.88), paperTint);
        vec3 ink = vec3(0.08, 0.08, 0.09);
        outRgb = mix(paper, ink, dotMono);
    } else {
        // Mode 2: RGB Dots (Color Video Monitor Screen)
        float dotR = evaluateDot(aspectCoord, globalAngle + 0.0 * (PI / 180.0), dotScale, srcColor.r, smoothness);
        float dotG = evaluateDot(aspectCoord, globalAngle + 60.0 * (PI / 180.0), dotScale, srcColor.g, smoothness);
        float dotB = evaluateDot(aspectCoord, globalAngle + 120.0 * (PI / 180.0), dotScale, srcColor.b, smoothness);
        outRgb = vec3(dotR, dotG, dotB);
    }

    vec3 finalColor = mix(srcColor.rgb, outRgb, clamp(mixRatio, 0.0, 1.0));
    gl_FragColor = vec4(finalColor, srcColor.a);
}
