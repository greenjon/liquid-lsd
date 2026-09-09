/*{
    "DESCRIPTION": "Elevates 2D visual sources into 3D space across Tri-Axial (3-Plane), Cube Cage (6-Plane), and Hex-Planar (6-Plane) projections",
    "CREDIT": "Liquid LSD Engine",
    "CATEGORIES": ["3D", "Distortion", "Geometry"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "mode3D",
            "LABEL": "3D Mode",
            "TYPE": "long",
            "DEFAULT": 0,
            "VALUES": [0, 1, 2],
            "LABELS": ["Tri-Axial (3-Plane)", "Cube Cage (6-Plane)", "Hex-Planar (6-Plane)"]
        },
        {
            "NAME": "pitch",
            "LABEL": "Pitch (X)",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -3.14159,
            "MAX": 3.14159
        },
        {
            "NAME": "yaw",
            "LABEL": "Yaw (Y)",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -3.14159,
            "MAX": 3.14159
        },
        {
            "NAME": "roll",
            "LABEL": "Roll (Z)",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -3.14159,
            "MAX": 3.14159
        },
        {
            "NAME": "zoom",
            "LABEL": "Zoom",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.1,
            "MAX": 3.0
        },
        {
            "NAME": "separation",
            "LABEL": "Plane Separation",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -1.0,
            "MAX": 2.0
        },
        {
            "NAME": "perspective",
            "LABEL": "Perspective Strength",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0
        },
        {
            "NAME": "depthDim",
            "LABEL": "Depth Falloff",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0
        }
    ]
}*/

mat3 rotationMatrixX(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat3(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c);
}

mat3 rotationMatrixY(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c);
}

mat3 rotationMatrixZ(float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return mat3(c, s, 0.0, -s, c, 0.0, 0.0, 0.0, 1.0);
}

struct Plane {
    vec3 center;
    vec3 normal;
    vec3 uDir;
    vec3 vDir;
};

void getPlane(int idx, int mode, float sep, out Plane p) {
    if (mode == 2) {
        // Hex-Planar (6-Plane @ 60°)
        float invSqrt2 = 0.70710678;
        if (idx == 0) {
            p.normal = vec3(1.0, -1.0, 0.0) * invSqrt2;
            p.uDir = vec3(1.0, 1.0, 0.0) * invSqrt2;
            p.vDir = vec3(0.0, 0.0, 1.0);
        } else if (idx == 1) {
            p.normal = vec3(1.0, 1.0, 0.0) * invSqrt2;
            p.uDir = vec3(-1.0, 1.0, 0.0) * invSqrt2;
            p.vDir = vec3(0.0, 0.0, 1.0);
        } else if (idx == 2) {
            p.normal = vec3(0.0, 1.0, -1.0) * invSqrt2;
            p.uDir = vec3(0.0, 1.0, 1.0) * invSqrt2;
            p.vDir = vec3(1.0, 0.0, 0.0);
        } else if (idx == 3) {
            p.normal = vec3(0.0, 1.0, 1.0) * invSqrt2;
            p.uDir = vec3(0.0, -1.0, 1.0) * invSqrt2;
            p.vDir = vec3(1.0, 0.0, 0.0);
        } else if (idx == 4) {
            p.normal = vec3(-1.0, 0.0, 1.0) * invSqrt2;
            p.uDir = vec3(1.0, 0.0, 1.0) * invSqrt2;
            p.vDir = vec3(0.0, 1.0, 0.0);
        } else {
            p.normal = vec3(1.0, 0.0, 1.0) * invSqrt2;
            p.uDir = vec3(1.0, 0.0, -1.0) * invSqrt2;
            p.vDir = vec3(0.0, 1.0, 0.0);
        }
        p.center = p.normal * sep;
    } else {
        // Tri-Axial (mode 0, 3 planes) or Cube Cage (mode 1, 6 planes)
        int planeType = idx % 3;
        float signVal = (idx >= 3) ? -1.0 : 1.0;

        if (planeType == 0) {
            // XY plane (normal along Z)
            p.normal = vec3(0.0, 0.0, 1.0) * signVal;
            p.uDir = vec3(1.0, 0.0, 0.0);
            p.vDir = vec3(0.0, 1.0, 0.0);
        } else if (planeType == 1) {
            // YZ plane (normal along X)
            p.normal = vec3(1.0, 0.0, 0.0) * signVal;
            p.uDir = vec3(0.0, 1.0, 0.0);
            p.vDir = vec3(0.0, 0.0, 1.0);
        } else {
            // ZX plane (normal along Y)
            p.normal = vec3(0.0, 1.0, 0.0) * signVal;
            p.uDir = vec3(0.0, 0.0, 1.0);
            p.vDir = vec3(1.0, 0.0, 0.0);
        }

        float baseOffset = (mode == 1) ? 1.0 : 0.0;
        p.center = p.normal * (baseOffset + sep);
    }
}

void main() {
    vec2 aspectVec = vec2(RENDERSIZE.x / max(1.0, RENDERSIZE.y), 1.0);
    vec2 st = (isf_FragNormCoord - vec2(0.5)) * aspectVec;

    // Camera setup
    float camDist = 2.5;
    mat3 rot = rotationMatrixY(yaw) * rotationMatrixX(pitch) * rotationMatrixZ(roll);
    mat3 invRot = transpose(rot);

    vec3 ro = invRot * vec3(0.0, 0.0, camDist);

    float fovScale = mix(1.0, 2.5, perspective) / max(0.01, zoom);
    vec3 rdView = normalize(vec3(st * fovScale, -camDist));
    vec3 rd = invRot * rdView;

    int intMode = int(mode3D);
    int numPlanes = (intMode == 0) ? 3 : 6;

    // Hit collection arrays
    float hitT[6];
    vec4 hitColor[6];
    int hitCount = 0;

    for (int i = 0; i < 6; i++) {
        if (i >= numPlanes) break;

        Plane pl;
        getPlane(i, intMode, separation, pl);

        float denom = dot(rd, pl.normal);
        if (abs(denom) < 1e-5) continue;

        float t = dot(pl.center - ro, pl.normal) / denom;
        if (t <= 0.0) continue;

        vec3 hitPos = ro + t * rd;
        vec3 localP = hitPos - pl.center;

        float u = dot(localP, pl.uDir);
        float v = dot(localP, pl.vDir);

        if (abs(u) <= 1.0 && abs(v) <= 1.0) {
            vec2 texCoord = vec2(u, v) * 0.5 + vec2(0.5);
            vec4 texColor = IMG_NORM_PIXEL(inputImage, texCoord);

            float lum = max(texColor.r, max(texColor.g, texColor.b));
            float lumFactor = smoothstep(0.015, 0.08, lum);
            float alphaFromLum = lumFactor * clamp(lum * 1.5, 0.0, 1.0);
            float baseAlpha = (texColor.a < 0.999) ? min(texColor.a, alphaFromLum) : alphaFromLum;

            if (baseAlpha > 0.002 && lum > 0.01) {
                // Depth attenuation
                vec3 worldHit = rot * hitPos;
                float depthFactor = 1.0 + (worldHit.z * 0.6) * depthDim;
                float minDim = max(0.02, 1.0 - depthDim);
                float atten = clamp(depthFactor, minDim, 1.0 + depthDim * 0.5);

                vec4 finalColor = vec4(texColor.rgb * atten * lumFactor, baseAlpha);

                hitT[hitCount] = t;
                hitColor[hitCount] = finalColor;
                hitCount++;
            }
        }
    }

    // Sort hits back-to-front (largest t first)
    for (int i = 0; i < 5; i++) {
        for (int j = 0; j < 5; j++) {
            if (j + 1 < hitCount && hitT[j] < hitT[j + 1]) {
                float tmpT = hitT[j];
                hitT[j] = hitT[j + 1];
                hitT[j + 1] = tmpT;

                vec4 tmpC = hitColor[j];
                hitColor[j] = hitColor[j + 1];
                hitColor[j + 1] = tmpC;
            }
        }
    }

    // Composite back-to-front
    vec4 composite = vec4(0.0);
    for (int i = 0; i < 6; i++) {
        if (i >= hitCount) break;
        vec4 src = hitColor[i];
        composite.rgb = src.rgb * src.a + composite.rgb * (1.0 - src.a);
        composite.a = src.a + composite.a * (1.0 - src.a);
    }

    gl_FragColor = composite;
}
