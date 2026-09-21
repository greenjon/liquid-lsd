/*{
    "DESCRIPTION": "Vortex Swirl",
    "CREDIT": "Liquid LSD",
    "CATEGORIES": ["Distortion", "Geometric", "Transitions"],
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
            "NAME": "rotations",
            "TYPE": "float",
            "MIN": 0.5,
            "MAX": 5.0,
            "DEFAULT": 2.0
        },
        {
            "NAME": "radius",
            "TYPE": "float",
            "MIN": 0.3,
            "MAX": 1.5,
            "DEFAULT": 0.75
        },
        {
            "NAME": "chromatic",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.4
        }
    ]
}*/

#define PI 3.14159265359

vec2 swirl(vec2 uv, float angle, float rad) {
    vec2 dir = uv - vec2(0.5);
    float dist = length(dir);
    if (dist < rad) {
        float percent = (rad - dist) / rad;
        float theta = percent * percent * angle;
        float s = sin(theta);
        float c = cos(theta);
        dir = vec2(dot(dir, vec2(c, -s)), dot(dir, vec2(s, c)));
    }
    return vec2(0.5) + dir;
}

void main() {
    vec2 uv = isf_FragNormCoord;
    float p = clamp(progress, 0.0, 1.0);

    // Dynamic swirl angle peaking at midpoint
    float midEnv = sin(p * PI);
    float maxAngle = rotations * 2.0 * PI;
    float angleA = (p) * maxAngle;
    float angleB = -(1.0 - p) * maxAngle;

    float r = max(0.1, radius);

    // Swirl UVs
    vec2 uvA = swirl(uv, angleA, r);
    vec2 uvB = swirl(uv, angleB, r);

    // Chromatic dispersion around swirl center
    float cShift = chromatic * midEnv * 0.03;
    vec2 shiftDir = normalize(uv - vec2(0.5) + vec2(0.0001));

    vec4 colA;
    colA.r = IMG_NORM_PIXEL(startImage, clamp(uvA + shiftDir * cShift, 0.0, 1.0)).r;
    colA.g = IMG_NORM_PIXEL(startImage, clamp(uvA, 0.0, 1.0)).g;
    colA.b = IMG_NORM_PIXEL(startImage, clamp(uvA - shiftDir * cShift, 0.0, 1.0)).b;
    colA.a = IMG_NORM_PIXEL(startImage, clamp(uvA, 0.0, 1.0)).a;

    vec4 colB;
    colB.r = IMG_NORM_PIXEL(endImage, clamp(uvB - shiftDir * cShift, 0.0, 1.0)).r;
    colB.g = IMG_NORM_PIXEL(endImage, clamp(uvB, 0.0, 1.0)).g;
    colB.b = IMG_NORM_PIXEL(endImage, clamp(uvB + shiftDir * cShift, 0.0, 1.0)).b;
    colB.a = IMG_NORM_PIXEL(endImage, clamp(uvB, 0.0, 1.0)).a;

    float t = smoothstep(0.0, 1.0, p);
    gl_FragColor = mix(colA, colB, t);
}
