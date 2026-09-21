/*{
    "DESCRIPTION": "Logarithmic gravitational accretion vortex swirl with aspect-ratio preservation, cubic Hermite boundary falloff, and chromatic angular dispersion",
    "CREDIT": "Liquid LSD (Cleanroom MIT)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Distortion", "Psychedelic", "Geometry"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "twist",
            "TYPE": "float",
            "MIN": -3.0,
            "MAX": 3.0,
            "DEFAULT": 0.8
        },
        {
            "NAME": "radius",
            "TYPE": "float",
            "MIN": 0.1,
            "MAX": 2.5,
            "DEFAULT": 0.85
        },
        {
            "NAME": "dispersion",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.35
        },
        {
            "NAME": "spiralArms",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 12.0,
            "DEFAULT": 0.0
        },
        {
            "NAME": "centerX",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.5
        },
        {
            "NAME": "centerY",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.5
        }
    ]
}*/

vec2 mirrorCoords(vec2 p) {
    vec2 m = mod(p, 2.0);
    return mix(m, 2.0 - m, step(1.0, m));
}

vec2 rotateCoord(vec2 p, float angleRad) {
    float s = sin(angleRad);
    float c = cos(angleRad);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

void main() {
    vec2 uv = isf_FragNormCoord;
    float aspect = RENDERSIZE.x / max(RENDERSIZE.y, 1.0);

    vec2 center = vec2(centerX, centerY);
    vec2 p = vec2((uv.x - center.x) * aspect, uv.y - center.y);
    float dist = length(p);

    // Cubic Hermite smooth falloff towards vortex boundary
    float maxR = max(radius, 0.001);
    float weight = smoothstep(maxR, 0.0, dist);

    if (weight <= 0.0001) {
        gl_FragColor = IMG_NORM_PIXEL(inputImage, uv);
        return;
    }

    // Accretion logarithmic spiral angle
    float baseSwirl = twist * 5.0 * weight * weight * (1.0 + log(1.0 + weight * 2.5));

    // Optional spiral arm ripple modulation
    if (spiralArms >= 0.5) {
        float theta = atan(p.y, p.x);
        baseSwirl += sin(theta * floor(spiralArms + 0.5)) * 0.15 * weight;
    }

    // Chromatic angular dispersion
    float dispFactor = dispersion * 0.1;
    float swirlR = baseSwirl * (1.0 + dispFactor);
    float swirlG = baseSwirl;
    float swirlB = baseSwirl * (1.0 - dispFactor);

    vec2 rotPR = rotateCoord(p, swirlR);
    vec2 rotPG = rotateCoord(p, swirlG);
    vec2 rotPB = rotateCoord(p, swirlB);

    vec2 uvR = mirrorCoords(vec2(rotPR.x / aspect + center.x, rotPR.y + center.y));
    vec2 uvG = mirrorCoords(vec2(rotPG.x / aspect + center.x, rotPG.y + center.y));
    vec2 uvB = mirrorCoords(vec2(rotPB.x / aspect + center.x, rotPB.y + center.y));

    float r = IMG_NORM_PIXEL(inputImage, uvR).r;
    float g = IMG_NORM_PIXEL(inputImage, uvG).g;
    float b = IMG_NORM_PIXEL(inputImage, uvB).b;
    float a = IMG_NORM_PIXEL(inputImage, uvG).a;

    gl_FragColor = vec4(r, g, b, a);
}
