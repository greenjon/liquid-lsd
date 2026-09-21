/*{
    "DESCRIPTION": "Dynamic cellular Voronoi crystal gem facets with 3D prism refraction, surface normal glints, specular bevel highlights, and chromatic dispersion",
    "CREDIT": "Liquid LSD (Cleanroom MIT)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Stylize", "Optics", "Prism"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "facetScale",
            "TYPE": "float",
            "MIN": 3.0,
            "MAX": 60.0,
            "DEFAULT": 18.0
        },
        {
            "NAME": "refraction",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.45
        },
        {
            "NAME": "dispersion",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.35
        },
        {
            "NAME": "bevelStrength",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.6
        },
        {
            "NAME": "facetTilt",
            "TYPE": "float",
            "MIN": 0.0,
            "MAX": 1.0,
            "DEFAULT": 0.5
        }
    ]
}*/

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

vec2 mirrorCoords(vec2 p) {
    vec2 m = mod(p, 2.0);
    return mix(m, 2.0 - m, step(1.0, m));
}

void main() {
    vec2 uv = isf_FragNormCoord;
    float aspect = RENDERSIZE.x / max(RENDERSIZE.y, 1.0);
    vec2 aspectUv = vec2(uv.x * aspect, uv.y);

    vec2 st = aspectUv * facetScale;
    vec2 iSt = floor(st);
    vec2 fSt = fract(st);

    // Voronoi F1 and F2 distance search
    float d1 = 1e5;
    float d2 = 1e5;
    vec2 minPointOffset = vec2(0.0);
    vec2 cellCenterWorld = vec2(0.0);

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 neighbor = vec2(float(x), float(y));
            vec2 pt = hash22(iSt + neighbor);
            vec2 diff = neighbor + pt - fSt;
            float dist = dot(diff, diff);

            if (dist < d1) {
                d2 = d1;
                d1 = dist;
                minPointOffset = diff;
                cellCenterWorld = (iSt + neighbor + pt) / facetScale;
            } else if (dist < d2) {
                d2 = dist;
            }
        }
    }

    d1 = sqrt(d1);
    d2 = sqrt(d2);

    // Facet surface normal tilt derived from vector towards cell center
    vec2 normal2D = -minPointOffset;
    vec2 refrOffset = normal2D * (refraction * 0.08) * facetTilt;

    // Chromatic dispersion offsets
    float disp = dispersion * 0.25;
    vec2 offR = refrOffset * (1.0 + disp);
    vec2 offG = refrOffset;
    vec2 offB = refrOffset * (1.0 - disp);

    vec2 uvR = mirrorCoords(uv + vec2(offR.x / aspect, offR.y));
    vec2 uvG = mirrorCoords(uv + vec2(offG.x / aspect, offG.y));
    vec2 uvB = mirrorCoords(uv + vec2(offB.x / aspect, offB.y));

    float r = IMG_NORM_PIXEL(inputImage, uvR).r;
    float g = IMG_NORM_PIXEL(inputImage, uvG).g;
    float b = IMG_NORM_PIXEL(inputImage, uvB).b;
    float a = IMG_NORM_PIXEL(inputImage, uvG).a;

    vec3 gemColor = vec3(r, g, b);

    // Edge bevel highlight calculation
    float borderDist = d2 - d1;
    float bevelWidth = 0.08;
    float bevel = 1.0 - smoothstep(0.0, bevelWidth, borderDist);

    // Specular highlight glint
    vec3 lightDir = normalize(vec3(0.5, 0.7, 1.0));
    vec3 facetNormal = normalize(vec3(normal2D.x, normal2D.y, 1.5 / max(facetTilt * refraction, 0.05)));
    float spec = pow(max(dot(facetNormal, lightDir), 0.0), 16.0) * 0.6;

    gemColor = gemColor + vec3(bevel * bevelStrength * 0.7) + vec3(spec * bevelStrength);

    gl_FragColor = vec4(gemColor, a);
}
