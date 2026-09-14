/*{
    "DESCRIPTION": "Elevates 2D visual sources into 3D space across Tri-Axial (3-Plane), Cube Cage (6-Plane), Hex-Planar (6-Plane), and Tetrahedral Kaleidoscope (24-Chamber) projections",
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
            "MIN": 0,
            "MAX": 3,
            "VALUES": [0, 1, 2, 3],
            "LABELS": ["Tri-Axial (3 Planes Intersecting)", "Hex-Planar (6 Planes Intersecting)", "Cube Cage (6-Plane Cube)", "Tetrahedral (Kaleidoscope)"]
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
            "MAX": 5.0
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
        },
        {
            "NAME": "blendMode",
            "LABEL": "Blend Mode",
            "TYPE": "long",
            "DEFAULT": 1,
            "VALUES": [0, 1],
            "LABELS": ["Alpha Over", "Additive Luminous"]
        },
        {
            "NAME": "roundness",
            "LABEL": "Roundness",
            "TYPE": "float",
            "DEFAULT": 1.0,
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
    if (mode == 1) {
        // Hex-Planar (6 Planes Intersecting @ 60°)
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
        // Tri-Axial (mode 0, 3 planes) or Cube Cage (mode 2, 6 planes)
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

        float baseOffset = (mode == 2) ? 1.0 : 0.0;
        p.center = p.normal * (baseOffset + sep);
    }
}

void main() {
    int intMode = int(mode3D);

    float aspect = RENDERSIZE.x / max(1.0, RENDERSIZE.y);

    if (intMode == 3) {
        // Mode 3: Tetrahedral Kaleidoscope (24-Chamber Space Folding)
        vec2 p2 = (isf_FragNormCoord - vec2(0.5)) * 2.0;
        p2.x *= aspect;

        float fov = 0.5 + perspective * 1.0;
        vec3 ray = normalize(vec3(p2 / max(0.01, zoom * fov), -1.0));

        mat3 rot = rotationMatrixY(yaw) * rotationMatrixX(pitch) * rotationMatrixZ(roll);
        vec3 p = rot * ray;

        for (int i = 0; i < 4; i++) {
            if (p.x + p.y < 0.0) p.xy = -p.yx;
            if (p.x + p.z < 0.0) p.xz = -p.zx;
            if (p.y + p.z < 0.0) p.yz = -p.zy;
            if (p.x < p.y) p.xy = p.yx;
            if (p.y < p.z) p.yz = p.zy;
            if (p.x < p.y) p.xy = p.yx;
        }

        float px = max(0.001, p.x);
        vec2 proj = vec2(p.y, p.z) / px;
        float cellScale = 1.0 + separation * 2.5;
        vec2 pCell = proj * cellScale;

        float squareDist = max(abs(pCell.x), abs(pCell.y));
        float circleDist = length(pCell);
        float shapeDist = mix(squareDist, circleDist, roundness);
        float borderFade = smoothstep(1.0, 0.96, shapeDist);

        if (borderFade <= 0.001) {
            gl_FragColor = vec4(0.0);
            return;
        }

        // Correct texture coordinates for inputImage aspect ratio (e.g. 16:9), preserving isotropic circular geometry
        vec2 sampleUV = vec2(
            pCell.x / max(1.0, aspect),
            pCell.y / max(1.0, 1.0 / aspect)
        ) * 0.5 + vec2(0.5);

        float dist = 1.0 / px;
        float depthFactor = 1.0 - (dist - 1.0) * 0.8 * depthDim;
        float minDim = max(0.02, 1.0 - depthDim);
        float atten = clamp(depthFactor, minDim, 1.0 + depthDim * 0.5);

        vec4 texColor = IMG_NORM_PIXEL(inputImage, clamp(sampleUV, 0.0, 1.0));

        float lum = max(texColor.r, max(texColor.g, texColor.b));
        float lumFactor = smoothstep(0.015, 0.08, lum);
        float alphaFromLum = lumFactor * clamp(lum * 1.5, 0.0, 1.0);
        float baseAlpha = (texColor.a < 0.999) ? min(texColor.a, alphaFromLum) : alphaFromLum;
        float effectiveAlpha = baseAlpha * borderFade;

        if (effectiveAlpha < 0.002 || lum < 0.01) {
            gl_FragColor = vec4(0.0);
            return;
        }

        vec3 rgb = texColor.rgb * atten * borderFade * lumFactor;
        if (blendMode >= 0.5) {
            rgb *= (1.0 + lum * 0.2);
        }

        gl_FragColor = vec4(rgb, effectiveAlpha);
        return;
    }

    // Modes 0..2: Tri-Axial, Cube Cage, Hex-Planar
    vec2 ndc = (isf_FragNormCoord - vec2(0.5)) * 2.0;

    // 1:1 Scale Normalization: at z=0, the quad spans [-1, 1] vertically at zoom=1.0, matching 2D flat mode exactly
    float safeZoom = max(0.01, zoom);
    vec3 pZero = vec3((ndc.x * aspect) / safeZoom, ndc.y / safeZoom, 0.0);

    // Camera setup: smooth transition from Orthographic (persp=0) to Perspective (persp=1)
    float camDist = 2.5;
    vec3 roView, rdView;
    if (perspective < 0.001) {
        roView = vec3(pZero.xy, camDist);
        rdView = vec3(0.0, 0.0, -1.0);
    } else {
        float zEye = camDist / perspective;
        roView = vec3(0.0, 0.0, zEye);
        rdView = normalize(pZero - roView);
    }

    mat3 rot = rotationMatrixY(yaw) * rotationMatrixX(pitch) * rotationMatrixZ(roll);
    mat3 invRot = transpose(rot);

    vec3 ro = invRot * roView;
    vec3 rd = invRot * rdView;

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
            float squareDist = max(abs(u), abs(v));
            float circleDist = length(vec2(u, v));
            float shapeDist = mix(squareDist, circleDist, roundness);
            float borderFade = smoothstep(1.0, 0.96, shapeDist);
            if (borderFade <= 0.001) continue;

            // Correct texture coordinates for inputImage aspect ratio (e.g. 16:9), preserving isotropic circular geometry
            vec2 texCoord = vec2(
                u / max(1.0, aspect),
                v / max(1.0, 1.0 / aspect)
            ) * 0.5 + vec2(0.5);
            vec4 texColor = IMG_NORM_PIXEL(inputImage, clamp(texCoord, 0.0, 1.0));

            float lum = max(texColor.r, max(texColor.g, texColor.b));
            if (lum < 0.01) continue;

            float lumFactor = smoothstep(0.015, 0.08, lum);
            float alphaFromLum = lumFactor * clamp(lum * 1.5, 0.0, 1.0);
            float baseAlpha = (texColor.a < 0.999) ? min(texColor.a, alphaFromLum) : alphaFromLum;
            float effectiveAlpha = baseAlpha * borderFade;

            if (effectiveAlpha < 0.002) continue;

            // Depth cueing / headlight falloff:
            // Measured relative to the focal center (z=0) in view space
            vec3 worldHit = roView + t * rdView;
            float vCameraDepth = worldHit.z;
            float depthFactor = 1.0 + (vCameraDepth * 0.6) * depthDim;
            float minDim = max(0.02, 1.0 - depthDim);
            float atten = clamp(depthFactor, minDim, 1.0 + depthDim * 0.5);

            vec3 rgb = texColor.rgb * atten * borderFade * lumFactor;
            if (blendMode >= 0.5) {
                rgb *= (1.0 + lum * 0.2);
            }

            hitT[hitCount] = t;
            hitColor[hitCount] = vec4(rgb, effectiveAlpha);
            hitCount++;
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

    // Composite hits
    vec4 composite = vec4(0.0);
    if (blendMode >= 0.5) {
        // Additive luminous blending (matches original glBlendFunc(GL_ONE, GL_ONE))
        for (int i = 0; i < 6; i++) {
            if (i >= hitCount) break;
            composite.rgb += hitColor[i].rgb;
            composite.a = clamp(composite.a + hitColor[i].a, 0.0, 1.0);
        }
    } else {
        // Premultiplied alpha over back-to-front (matches original glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA))
        for (int i = 0; i < 6; i++) {
            if (i >= hitCount) break;
            vec4 src = hitColor[i];
            composite.rgb = src.rgb + composite.rgb * (1.0 - src.a);
            composite.a = src.a + composite.a * (1.0 - src.a);
        }
    }

    gl_FragColor = composite;
}
