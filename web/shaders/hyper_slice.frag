#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Raymarched 3D cross-section MRI scan through 4D 120-cell and 600-cell polychora via H4 Coxeter domain folding with 4D hyper-rotations and Wythoff facet morphing.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "is3D": true,
    "CATEGORIES": [
        "Generator",
        "3D",
        "Geometric"
    ],
    "INPUTS": [
        { "NAME": "SliceOffset", "LABEL": "Slice Offset W", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.5, "MAX": 1.5 },
        { "NAME": "RotateXW", "LABEL": "Rotate XW (4D)", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 6.2831853 },
        { "NAME": "RotateYW", "LABEL": "Rotate YW (4D)", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 6.2831853 },
        { "NAME": "RotateZW", "LABEL": "Rotate ZW (4D)", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 6.2831853 },
        { "NAME": "RotateX", "LABEL": "Rotate X", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateY", "LABEL": "Rotate Y", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateZ", "LABEL": "Rotate Z", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "Morph", "LABEL": "Polychoron Morph", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SupportH", "LABEL": "Facet Support H", "TYPE": "float", "DEFAULT": 0.85, "MIN": 0.3, "MAX": 1.8 },
        { "NAME": "ColorMethod", "LABEL": "Color Method", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 2.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.85, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 1.1, "MIN": 0.0, "MAX": 2.0 },
        { "NAME": "Opacity", "LABEL": "Face Opacity", "TYPE": "float", "DEFAULT": 0.8, "MIN": 0.1, "MAX": 1.0 },
        { "NAME": "EdgeThickness", "LABEL": "Edge Thickness", "TYPE": "float", "DEFAULT": 0.015, "MIN": 0.002, "MAX": 0.08 },
        { "NAME": "EdgeBrightness", "LABEL": "Edge Brightness", "TYPE": "float", "DEFAULT": 1.5, "MIN": 0.0, "MAX": 3.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 3.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.2, "MAX": 4.0 }
    ]
}
*/

const float PI  = 3.14159265358979323846;
const float PHI = 1.61803398874989484820;

// 4D Hyper-Rotations
vec4 rotate4D(vec4 p) {
    float cxw = cos(RotateXW), sxw = sin(RotateXW);
    p = vec4(cxw * p.x - sxw * p.w, p.y, p.z, sxw * p.x + cxw * p.w);

    float cyw = cos(RotateYW), syw = sin(RotateYW);
    p = vec4(p.x, cyw * p.y - syw * p.w, p.z, syw * p.y + cyw * p.w);

    float czw = cos(RotateZW), szw = sin(RotateZW);
    p = vec4(p.x, p.y, czw * p.z - szw * p.w, szw * p.z + czw * p.w);

    return p;
}

mat3 rotate3DMatrix() {
    float cx = cos(RotateX), sx = sin(RotateX);
    float cy = cos(RotateY), sy = sin(RotateY);
    float cz = cos(RotateZ), sz = sin(RotateZ);
    mat3 rx = mat3(1.0, 0.0, 0.0, 0.0, cx, -sx, 0.0, sx, cx);
    mat3 ry = mat3(cy, 0.0, sy, 0.0, 1.0, 0.0, -sy, 0.0, cy);
    mat3 rz = mat3(cz, -sz, 0.0, sz, cz, 0.0, 0.0, 0.0, 1.0);
    return rz * ry * rx;
}

// H4 Coxeter Reflection Group Mirrors
const vec4 n0 = vec4(1.0, 0.0, 0.0, 0.0);
const vec4 n1 = vec4(-PHI * 0.5, -0.5, 0.5 / PHI, 0.0);
const vec4 n2 = vec4(0.0, 1.0, 0.0, 0.0);
const vec4 n3 = vec4(0.0, -PHI * 0.5, -0.5, 0.5 / PHI);

vec4 foldSpace4D(vec4 p, out float foldCount) {
    float folds = 0.0;
    for (int i = 0; i < 20; i++) {
        float d0 = dot(p, n0);
        if (d0 < 0.0) { p -= 2.0 * d0 * n0; folds += 1.0; }
        float d1 = dot(p, n1);
        if (d1 < 0.0) { p -= 2.0 * d1 * n1; folds += 1.0; }
        float d2 = dot(p, n2);
        if (d2 < 0.0) { p -= 2.0 * d2 * n2; folds += 1.0; }
        float d3 = dot(p, n3);
        if (d3 < 0.0) { p -= 2.0 * d3 * n3; folds += 1.0; }
    }
    foldCount = folds;
    return p;
}

vec4 slerp4D(vec4 p0, vec4 p1, float t) {
    float dotp = clamp(dot(p0, p1), -1.0, 1.0);
    float theta = acos(dotp);
    float sinTheta = sin(theta);
    if (sinTheta < 0.001) return normalize(mix(p0, p1, t));
    float w0 = sin((1.0 - t) * theta) / sinTheta;
    float w1 = sin(t * theta) / sinTheta;
    return normalize(p0 * w0 + p1 * w1);
}

const vec4 pole600 = vec4(0.0, 0.0, 0.0, 1.0);
const vec4 pole120 = vec4(1.0, 0.0, 0.0, 0.0);

float mapSDF(vec3 p3, out float outEdge, out vec4 outColorCoord) {
    vec4 p4 = vec4(p3, SliceOffset);
    vec4 pRot = rotate4D(p4);

    float folds = 0.0;
    vec4 pFold = foldSpace4D(pRot, folds);

    vec4 genPole = slerp4D(pole600, pole120, clamp(Morph, 0.0, 1.0));
    float dFacet = dot(pFold, genPole) - SupportH;

    float m0 = dot(pFold, n0);
    float m1 = dot(pFold, n1);
    float m2 = dot(pFold, n2);
    float m3 = dot(pFold, n3);
    float minMirror = min(min(m0, m1), min(m2, m3));

    outEdge = smoothstep(EdgeThickness, 0.0, minMirror);
    outColorCoord = vec4(folds, minMirror, pRot.w, length(p3));

    return dFacet;
}

vec3 calcNormal(vec3 p) {
    float dummyEdge;
    vec4 dummyCoord;
    float d = mapSDF(p, dummyEdge, dummyCoord);
    vec2 e = vec2(0.002, 0.0);
    return normalize(vec3(
        mapSDF(p + e.xyy, dummyEdge, dummyCoord) - d,
        mapSDF(p + e.yxy, dummyEdge, dummyCoord) - d,
        mapSDF(p + e.yyx, dummyEdge, dummyCoord) - d
    ));
}

vec3 palette(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
    return a + b * cos(6.2831853 * (c * t + d));
}

vec3 adjustColor(vec3 col, float sat, float bright) {
    float g = dot(col, vec3(0.299, 0.587, 0.114));
    return max(vec3(0.0), mix(vec3(g), col, sat) * bright);
}

void main() {
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 uv = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0);

    mat3 r3D = rotate3DMatrix();

    float camDist = 3.0 / max(0.1, Zoom);
    vec3 ro = r3D * vec3(0.0, 0.0, camDist);
    vec3 rd = r3D * normalize(vec3(uv, -1.8));

    // Bounding sphere acceleration
    float bRadius = 2.5;
    float bDot = dot(ro, rd);
    float bDisc = bDot * bDot - dot(ro, ro) + bRadius * bRadius;

    if (bDisc < 0.0) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float tNear = max(0.0, -bDot - sqrt(bDisc));
    float tFar = -bDot + sqrt(bDisc);

    float t = tNear;
    float hitEdge = 0.0;
    vec4 hitCoord = vec4(0.0);
    bool hit = false;
    float accumGlow = 0.0;

    for (int i = 0; i < 72; i++) {
        vec3 p = ro + t * rd;
        float edge;
        vec4 coord;
        float d = mapSDF(p, edge, coord);

        accumGlow += exp(-max(0.0, d) * 6.0) * 0.02;

        if (d < 0.001) {
            hit = true;
            hitEdge = edge;
            hitCoord = coord;
            break;
        }

        t += d * 0.75;
        if (t > tFar) break;
    }

    if (!hit) {
        float glowAlpha = clamp(accumGlow * Glow, 0.0, 1.0);
        vec3 glowCol = palette(HueOffset, vec3(0.5), vec3(0.5), vec3(1.0), vec3(0.0, 0.33, 0.67));
        glowCol = adjustColor(glowCol, Saturation, Brightness);
        gl_FragColor = vec4(glowCol * glowAlpha, 1.0);
        return;
    }

    vec3 hitPos = ro + t * rd;
    vec3 normal = calcNormal(hitPos);

    float colorMetric = 0.0;
    if (ColorMethod < 0.5) {
        colorMetric = hitCoord.x * 0.08;
    } else if (ColorMethod < 1.5) {
        colorMetric = (hitCoord.z + 1.0) * 0.5;
    } else {
        colorMetric = dot(normal, vec3(0.3, 0.5, 0.2)) * 0.5 + 0.5;
    }

    float hueVal = fract(HueOffset + colorMetric);
    vec3 baseCol = palette(hueVal, vec3(0.5, 0.5, 0.5), vec3(0.5, 0.5, 0.5), vec3(1.0, 1.0, 1.0), vec3(0.0, 0.333, 0.667));
    baseCol = adjustColor(baseCol, Saturation, Brightness);

    vec3 lightDir = normalize(vec3(0.5, 0.8, 1.0));
    vec3 viewDir = -rd;
    vec3 halfDir = normalize(lightDir + viewDir);

    float diff = max(0.0, dot(normal, lightDir));
    float spec = pow(max(0.0, dot(normal, halfDir)), 24.0) * 0.6;
    float fresnel = pow(1.0 - max(0.0, dot(normal, viewDir)), 3.0);

    vec3 shadedColor = baseCol * (0.25 + diff * 0.75) + vec3(spec) + baseCol * fresnel * 0.5;
    shadedColor += vec3(1.0) * (hitEdge * EdgeBrightness);
    shadedColor += baseCol * (accumGlow * Glow);

    gl_FragColor = vec4(shadedColor * Opacity, 1.0);
}
