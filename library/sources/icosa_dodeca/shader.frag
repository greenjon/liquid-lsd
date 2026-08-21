#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

// Uniforms provided by DynamicVisualSource
uniform float uMorph;          // 0.0->0.25 (Ico->Dod), 0.25->0.5 (Dod->StellDod), 0.5->0.75 (StellDod->StellIco), 0.75->1.0 (StellIco->Ico)
uniform float uColorMethod;     // 0.0 (Depth Gradient) -> 1.0 (5-Fold Sectors)
uniform float uDepthFrame;      // 0.0 (Local shape frame) -> 1.0 (World frame)
uniform float uHueOffset;       // 0.0 -> 1.0
uniform float uSaturation;      // 0.0 -> 1.0
uniform float uBrightness;      // 0.0 -> 2.0
uniform float uOpacity;         // 0.0 -> 1.0 (Sweet spot: 0.6 - 0.8 for crystal reveal)
uniform float uEdgeThickness;   // 0.0 -> 0.15
uniform float uEdgeBrightness;  // 0.0 -> 2.0
uniform float uZoom;            // 0.1 -> 5.0
uniform float uRotateX;         // -PI -> PI
uniform float uRotateY;         // -PI -> PI
uniform float uRotateZ;         // -PI -> PI

// System uniforms from Renderer
uniform float uAlpha;
uniform vec2 uResolution;
uniform float uTime;

const float PI = 3.14159265358979323846;
const float PHI = 1.61803398874989484820; // Golden ratio (1 + sqrt(5)) / 2

// Rotation helpers
mat3 rotateX(float theta) {
    float c = cos(theta), s = sin(theta);
    return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c);
}

mat3 rotateY(float theta) {
    float c = cos(theta), s = sin(theta);
    return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c);
}

mat3 rotateZ(float theta) {
    float c = cos(theta), s = sin(theta);
    return mat3(vec3(c, -s, 0.0), vec3(s, c, 0.0), vec3(0.0, 0.0, 1.0));
}

// Cosine-based color palette
vec3 palette(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
    return a + b * cos(6.2831853 * (c * t + d));
}

// Adjust saturation and brightness of an RGB color
vec3 adjustColor(vec3 col, float sat, float bright) {
    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(gray), col, sat);
    return max(vec3(0.0), col * bright);
}

// -----------------------------------------------------------------------------
// Exact Icosahedral Symmetry Plane Sets (Normals)
// -----------------------------------------------------------------------------

// 10 pairs of 3-fold axes (Icosahedron face normals / Dodecahedron vertices)
// All vectors are unit length (norm == 1.0)
vec3 getIcoNormal(int i) {
    float a = 1.0 / sqrt(3.0);
    float b = (1.0 / PHI) / sqrt(3.0);
    float c = PHI / sqrt(3.0);
    if (i == 0) return vec3(a, a, a);
    if (i == 1) return vec3(-a, a, a);
    if (i == 2) return vec3(a, -a, a);
    if (i == 3) return vec3(a, a, -a);
    if (i == 4) return vec3(0.0, c, b);
    if (i == 5) return vec3(0.0, -c, b);
    if (i == 6) return vec3(b, 0.0, c);
    if (i == 7) return vec3(-b, 0.0, c);
    if (i == 8) return vec3(c, b, 0.0);
    return vec3(-c, b, 0.0); // i == 9
}

// 6 pairs of 5-fold axes (Dodecahedron face normals / Icosahedron vertices)
// All vectors are unit length (norm == 1.0)
vec3 getDodNormal(int i) {
    float b = 1.0 / sqrt(1.0 + PHI * PHI);
    float c = PHI / sqrt(1.0 + PHI * PHI);
    if (i == 0) return vec3(0.0, b, c);
    if (i == 1) return vec3(0.0, -b, c);
    if (i == 2) return vec3(c, 0.0, b);
    if (i == 3) return vec3(-c, 0.0, b);
    if (i == 4) return vec3(b, c, 0.0);
    return vec3(-b, c, 0.0); // i == 5
}

// 4-Phase Shape Evolution:
// 0.00 -> 0.25: Icosahedron -> Dodecahedron (no stellation)
// 0.25 -> 0.50: Dodecahedron -> Great Stellated Dodecahedron
// 0.50 -> 0.75: Great Stellated Dodecahedron -> Great Icosahedron
// 0.75 -> 1.00: Great Icosahedron -> Icosahedron
void getMorphState(float u, out float m, out float s) {
    float t = fract(u);
    if (t < 0.25) {
        float f = t * 4.0;
        m = f;
        s = 0.0;
    } else if (t < 0.5) {
        float f = (t - 0.25) * 4.0;
        m = 1.0;
        s = f;
    } else if (t < 0.75) {
        float f = (t - 0.5) * 4.0;
        m = 1.0 - f;
        s = 1.0;
    } else {
        float f = (t - 0.75) * 4.0;
        m = 0.0;
        s = 1.0 - f;
    }
}

// Evaluates the dual morph + stellation SDF
float mapSDF(vec3 p, out float outEdge, out vec3 outAxis, out float outDepth) {
    float m, s;
    getMorphState(uMorph, m, s);

    // Plane threshold distances for dual rectification:
    // Base radius is 0.82.
    // At m=0: icosahedron planes at 0.82, dodecahedron planes at ~1.03 (inactive).
    // At m=0.5: both sets of planes at ~0.93 (icosidodecahedron).
    // At m=1: dodecahedron planes at 0.82, icosahedron planes at ~1.03 (inactive).
    float r0 = 0.82;
    float rRatio = 1.25840857; // R / r for Icosahedron/Dodecahedron
    float rIco = r0 * mix(1.0, rRatio, m);
    float rDod = r0 * mix(rRatio, 1.0, m);

    float maxIco1 = -1e5;
    float maxIco2 = -1e5;
    vec3 bestIcoAxis = vec3(0.0, 1.0, 0.0);
    float bestIcoDot = 0.0;

    for (int i = 0; i < 10; i++) {
        vec3 n = getIcoNormal(i);
        float dDot = dot(p, n);
        float d = abs(dDot) - rIco;
        if (d > maxIco1) {
            maxIco2 = maxIco1;
            maxIco1 = d;
            bestIcoAxis = (dDot >= 0.0) ? n : -n;
            bestIcoDot = abs(dDot);
        } else if (d > maxIco2) {
            maxIco2 = d;
        }
    }

    float maxDod1 = -1e5;
    float maxDod2 = -1e5;
    vec3 bestDodAxis = vec3(0.0, 1.0, 0.0);
    float bestDodDot = 0.0;

    for (int j = 0; j < 6; j++) {
        vec3 n = getDodNormal(j);
        float dDot = dot(p, n);
        float d = abs(dDot) - rDod;
        if (d > maxDod1) {
            maxDod2 = maxDod1;
            maxDod1 = d;
            bestDodAxis = (dDot >= 0.0) ? n : -n;
            bestDodDot = abs(dDot);
        } else if (d > maxDod2) {
            maxDod2 = d;
        }
    }

    // Base convex polyhedron SDF
    float baseSdf = max(maxIco1, maxDod1);

    // Edge proximity: distance between primary and secondary planes
    float d1 = baseSdf;
    float d2 = max(max(maxIco2, maxDod2), min(maxIco1, maxDod1));
    float baseEdge = d1 - d2;

    // -------------------------------------------------------------------------
    // Stellation: Kepler-Poinsot Star Spikes
    // -------------------------------------------------------------------------
    float totalSdf = baseSdf;

    if (s > 0.001) {
        // 3-fold star spikes (for Great Icosahedron at m -> 0)
        float spikeHIco = s * 0.95 * (1.0 - m);
        if (spikeHIco > 0.001) {
            float hIco = bestIcoDot;
            if (hIco > rIco * 0.7) {
                vec3 vPerp = p - bestIcoAxis * dot(p, bestIcoAxis);
                float rPerp = length(vPerp);
                vec3 refV = vec3(0.0);
                float maxDot = -1.0;
                for (int k = 0; k < 6; k++) {
                    vec3 v = getDodNormal(k);
                    float d = dot(v, bestIcoAxis);
                    if (abs(d) > maxDot + 0.001) {
                        maxDot = abs(d);
                        refV = sign(d) * v;
                    }
                }
                vec3 e1 = normalize(refV - bestIcoAxis * dot(refV, bestIcoAxis));
                vec3 e2 = cross(bestIcoAxis, e1);
                float angle = atan(dot(vPerp, e2), dot(vPerp, e1));
                float foldAngle = mod(angle, 2.0 * PI / 3.0) - PI / 3.0;
                float rEdge = rPerp * cos(foldAngle);
                float rBase = rIco * 0.381966; // 1.0 / (PHI * PHI)
                float heightFrac = clamp((hIco - rIco) / spikeHIco, 0.0, 1.0);
                float rCone = rBase * (1.0 - heightFrac);
                float dCone = (rEdge - rCone) / sqrt(1.0 + (rBase / spikeHIco) * (rBase / spikeHIco));
                float spikeSdf = max(dCone, hIco - (rIco + spikeHIco));
                float finiteConeSdf = max(spikeSdf, rIco - hIco);
                totalSdf = min(totalSdf, finiteConeSdf);
            }
        }

        // 5-fold star spikes (for Great Stellated Dodecahedron at m -> 1)
        float spikeHDod = s * 1.15 * m;
        if (spikeHDod > 0.001) {
            float hDod = bestDodDot;
            if (hDod > rDod * 0.7) {
                vec3 vPerp = p - bestDodAxis * dot(p, bestDodAxis);
                float rPerp = length(vPerp);
                vec3 refV = vec3(0.0);
                float maxDot = -1.0;
                for (int k = 0; k < 10; k++) {
                    vec3 v = getIcoNormal(k);
                    float d = dot(v, bestDodAxis);
                    if (abs(d) > maxDot + 0.001) {
                        maxDot = abs(d);
                        refV = sign(d) * v;
                    }
                }
                vec3 e1 = normalize(refV - bestDodAxis * dot(refV, bestDodAxis));
                vec3 e2 = cross(bestDodAxis, e1);
                float angle = atan(dot(vPerp, e2), dot(vPerp, e1));
                float foldAngle = mod(angle, 2.0 * PI / 5.0) - PI / 5.0;
                float rEdge = rPerp * cos(foldAngle);
                float rBase = rDod * 0.618034; // 1.0 / PHI
                float heightFrac = clamp((hDod - rDod) / spikeHDod, 0.0, 1.0);
                float rCone = rBase * (1.0 - heightFrac);
                float dCone = (rEdge - rCone) / sqrt(1.0 + (rBase / spikeHDod) * (rBase / spikeHDod));
                float spikeSdf = max(dCone, hDod - (rDod + spikeHDod));
                float finiteConeSdf = max(spikeSdf, rDod - hDod);
                totalSdf = min(totalSdf, finiteConeSdf);
            }
        }
    }

    outEdge = baseEdge;
    outAxis = (m < 0.5) ? bestIcoAxis : bestDodAxis;
    outDepth = length(p);

    return totalSdf;
}

// Normal calculation via central differences
vec3 getNormal(vec3 p) {
    float dummyEdge, dummyDepth;
    vec3 dummyAxis;
    vec2 eps = vec2(0.002, 0.0);
    float d = mapSDF(p, dummyEdge, dummyAxis, dummyDepth);
    return normalize(vec3(
        mapSDF(p + eps.xyy, dummyEdge, dummyAxis, dummyDepth) - d,
        mapSDF(p + eps.yxy, dummyEdge, dummyAxis, dummyDepth) - d,
        mapSDF(p + eps.yyx, dummyEdge, dummyAxis, dummyDepth) - d
    ));
}

void main() {
    vec2 uv = vTexCoord * 2.0 - 1.0;
    float aspect = uResolution.x / uResolution.y;
    uv.x *= aspect;

    // Camera setup with Zoom and 3-axis rotation
    float zoom = clamp(uZoom, 0.1, 5.0);
    vec3 ro = vec3(0.0, 0.0, -3.2 / zoom);
    vec3 rd = normalize(vec3(uv, 1.4));

    mat3 rot = rotateZ(uRotateZ) * rotateY(uRotateY) * rotateX(uRotateX);
    mat3 invRot = transpose(rot);

    // Transform camera ray to local shape coordinates
    ro = rot * ro;
    rd = rot * rd;

    // Palette base vectors (vibrant cosine spectrum)
    vec3 palA = vec3(0.5, 0.5, 0.5);
    vec3 palB = vec3(0.5, 0.5, 0.5);
    vec3 palC = vec3(1.0, 1.0, 1.0);
    vec3 palD = vec3(0.0, 0.33, 0.67);

    // Multi-layer crystal accumulation for inner-face transparency reveal
    vec4 accumColor = vec4(0.0);
    float t = 0.5;
    const int MAX_STEPS = 64;
    float opacity = clamp(uOpacity, 0.0, 1.0);
    float edgeThick = clamp(uEdgeThickness, 0.0, 0.2);

    for (int step = 0; step < MAX_STEPS; step++) {
        vec3 p = ro + rd * t;
        float edge, depth;
        vec3 axis;
        float dist = mapSDF(p, edge, axis, depth);

        if (dist < 0.003) {
            // Hit a face or facet
            vec3 n = getNormal(p);
            vec3 lightDir = normalize(vec3(0.577, 0.577, -0.577));
            float diff = max(0.25, dot(n, lightDir));
            float spec = pow(max(0.0, dot(reflect(-lightDir, n), -rd)), 16.0) * 0.4;

            // 1. Color calculation
            vec3 surfaceCol = vec3(1.0);
            if (uColorMethod < 0.5) {
                // Method 5: Depth Gradient
                float dVal = (uDepthFrame > 0.5) ? length(invRot * p) : depth;
                float normDepth = clamp((dVal - 0.7) / 1.3, 0.0, 1.0);
                surfaceCol = palette(normDepth + uHueOffset, palA, palB, palC, palD);
            } else {
                // Method 4: Symmetry Sectors (3-Fold or 5-Fold)
                // Azimuthal angle around closest symmetry axis
                vec3 refV = vec3(0.0);
                float maxDot = -1.0;
                float mState, sState;
                getMorphState(uMorph, mState, sState);
                if (mState < 0.5) {
                    for (int k = 0; k < 6; k++) {
                        vec3 v = getDodNormal(k);
                        float d = dot(v, axis);
                        if (abs(d) > maxDot + 0.001) {
                            maxDot = abs(d);
                            refV = sign(d) * v;
                        }
                    }
                } else {
                    for (int k = 0; k < 10; k++) {
                        vec3 v = getIcoNormal(k);
                        float d = dot(v, axis);
                        if (abs(d) > maxDot + 0.001) {
                            maxDot = abs(d);
                            refV = sign(d) * v;
                        }
                    }
                }
                vec3 refX = normalize(refV - axis * dot(refV, axis));
                vec3 refY = cross(axis, refX);
                float angle = atan(dot(p, refY), dot(p, refX));
                float folds = (mState < 0.5) ? 3.0 : 5.0;
                float sector = floor(mod((angle / (2.0 * PI / folds)) + uHueOffset * folds, folds)) / folds;
                surfaceCol = palette(sector, palA, palB, palC, palD);
            }

            surfaceCol = adjustColor(surfaceCol, uSaturation, uBrightness);
            surfaceCol = surfaceCol * diff + vec3(spec);

            // 2. Wireframe edge detection & contrasting highlight
            if (edgeThick > 0.001) {
                float edgeFactor = 1.0 - smoothstep(0.0, edgeThick, edge);
                vec3 edgeCol = vec3(1.0) - surfaceCol * 0.5; // Contrasting complementary edge tint
                edgeCol = adjustColor(edgeCol, uSaturation, uBrightness * uEdgeBrightness);
                surfaceCol = mix(surfaceCol, edgeCol, edgeFactor);
            }

            // 3. Alpha blending & crystal transparency accumulation
            float layerAlpha = opacity;
            vec4 layerCol = vec4(surfaceCol, layerAlpha);
            
            // Front-to-back accumulation
            accumColor += layerCol * (1.0 - accumColor.a);

            if (accumColor.a > 0.96) {
                break;
            }

            // Step slightly past the surface to catch internal self-intersecting facets
            t += max(0.04, dist + 0.04);
        } else {
            t += max(0.012, dist * 0.8);
        }

        if (t > 8.0) break;
    }

    // Final color with global alpha
    fragColor = vec4(accumColor.rgb, accumColor.a * uAlpha);
}
