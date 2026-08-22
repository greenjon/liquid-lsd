#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

// Uniforms provided by DynamicVisualSource
uniform float uMorph;          // 0.0->0.25 (Ico->Dod), 0.25->0.5 (Dod->StellDod), 0.5->0.75 (StellDod->StellIco), 0.75->1.0 (StellIco->Ico)
uniform float uStellation;      // 0.0 -> 1.0 (Manual stellation boost/override)
uniform float uSupportH;        // -1.0 -> 1.0 (Support plane distance offset)
uniform float uColorMethod;     // 0.0 (Chamber Sectors) -> 1.0 (Depth Gradient) -> 2.0 (Normal Spectrum)
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

// -----------------------------------------------------------------------------
// H3 Coxeter Reflection Group Substrate
// -----------------------------------------------------------------------------
// Exact unit-length mirror normals with dihedral angles (pi/5, pi/3, pi/2):
// n0 . n2 = 0        (angle pi/2)
// n0 . n1 = -phi/2   (angle 4pi/5 -> reflection angle pi/5)
// n1 . n2 = -1/2     (angle 2pi/3 -> reflection angle pi/3)
const vec3 n0 = vec3(1.0, 0.0, 0.0);
const vec3 n1 = vec3(-PHI * 0.5, -0.5, 0.5 / PHI);
const vec3 n2 = vec3(0.0, 1.0, 0.0);

// Symmetry axes (chamber vertices):
// C3: 3-fold axis (intersection of n1 and n2) -> Icosahedron faces / Dodecahedron vertices
const vec3 C3 = vec3((PHI - 1.0) / 1.7320508075688772, 0.0, PHI / 1.7320508075688772);
// C5: 5-fold axis (intersection of n0 and n1) -> Dodecahedron faces / Icosahedron vertices
const vec3 C5 = vec3(0.0, 1.0 / 1.902113032590307, PHI / 1.902113032590307);
// C2: 2-fold axis (intersection of n0 and n2) -> Edge centers
const vec3 C2 = vec3(0.0, 0.0, 1.0);

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

// Quintic smootherstep (C² continuous: zero 1st and 2nd derivative at endpoints)
float smootherstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
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

// Spherical linear interpolation between fundamental 3-fold and 5-fold axes
vec3 getGenerator(float t) {
    const float omega = 0.65235814;
    const float sinOmega = 0.60706200;
    float s0 = sin((1.0 - t) * omega) / sinOmega;
    float s1 = sin(t * omega) / sinOmega;
    return normalize(s0 * C3 + s1 * C5);
}

// Iterative H3 symmetry folding into the fundamental chamber
vec3 foldH3(vec3 p) {
    for (int i = 0; i < 16; ++i) {
        p -= 2.0 * min(0.0, dot(p, n0)) * n0;
        p -= 2.0 * min(0.0, dot(p, n1)) * n1;
        p -= 2.0 * min(0.0, dot(p, n2)) * n2;
    }
    return p;
}

// 4-Phase Shape Evolution:
// 0.00 -> 0.25: Icosahedron -> Dodecahedron (no stellation)
// 0.25 -> 0.50: Dodecahedron -> Great Stellated Dodecahedron
// 0.50 -> 0.75: Great Stellated Dodecahedron -> Great Icosahedron
// 0.75 -> 1.00: Great Icosahedron -> Icosahedron
void getMorphState(float u, out float tGen, out float sHeight) {
    float t = fract(u);
    if (t < 0.25) {
        float f = smootherstep(0.0, 1.0, t * 4.0);
        tGen = f;
        sHeight = 0.0;
    } else if (t < 0.5) {
        float f = smootherstep(0.0, 1.0, (t - 0.25) * 4.0);
        tGen = 1.0;
        sHeight = f;
    } else if (t < 0.75) {
        float f = smootherstep(0.0, 1.0, (t - 0.5) * 4.0);
        tGen = 1.0 - f;
        sHeight = 1.0;
    } else {
        float f = smootherstep(0.0, 1.0, (t - 0.75) * 4.0);
        tGen = 0.0;
        sHeight = 1.0 - f;
    }
}

// Evaluates the H3 symmetry-folded SDF and edge distances
float mapSDF(vec3 p, out float outEdge, out vec3 outFoldedP, out float outDepth) {
    float tGen, sHeight;
    getMorphState(uMorph, tGen, sHeight);

    // Apply manual stellation boost
    float totalStell = clamp(sHeight + uStellation, 0.0, 1.0);
    float baseH = 0.82;

    // Fold point into fundamental H3 chamber
    vec3 pFolded = foldH3(p);

    // Active generator vector on the 3-fold <-> 5-fold arc
    vec3 v = getGenerator(tGen);

    // Dual vertex truncation vector (opposite axis: 5-fold when v is 3-fold, 3-fold when v is 5-fold)
    vec3 vDual = getGenerator(1.0 - tGen);

    // Adjacent reflection vector for stellation facet plane
    // C3 reflects across n0: (-C3.x, 0, C3.z)
    // C5 reflects across n2: (0, -C5.y, C5.z)
    vec3 c3Adj = vec3(-C3.x, 0.0, C3.z);
    vec3 c5Adj = vec3(0.0, -C5.y, C5.z);
    const float omega = 0.65235814;
    const float sinOmega = 0.60706200;
    float s0 = sin((1.0 - tGen) * omega) / sinOmega;
    float s1 = sin(tGen * omega) / sinOmega;
    vec3 vAdj = normalize(s0 * c3Adj + s1 * c5Adj);

    // Continuous stellation plane: tilts from flat face v(t) at s=0 into star facets vAdj(t) at s=1
    vec3 vMorph = normalize(mix(v, vAdj, totalStell));

    // 1. Primary face plane
    float dPrimary = dot(pFolded, vMorph) - baseH;

    // 2. Vertex Truncation & Edge Cantellation via Support H:
    // Support H < 0 cuts vertices inward (Truncation / Buckyball / Duality)
    // Support H > 0 cuts edges inward (Cantellation / Rhombicosidodecahedron)
    float hTrunc = baseH * (1.28 + min(0.0, uSupportH) * 0.65);
    float dTrunc = dot(pFolded, vDual) - hTrunc;

    float hEdge = baseH * (1.28 - max(0.0, uSupportH) * 0.50);
    float dEdge = dot(pFolded, C2) - hEdge;

    // Convex intersection of primary faces, vertex truncations, and edge cuts
    float totalSdf = max(dPrimary, max(dTrunc, dEdge));

    // Edge proximity: distance to fundamental mirror walls + facet intersection seams
    float dM0 = dot(pFolded, n0);
    float dM1 = dot(pFolded, n1);
    float dM2 = dot(pFolded, n2);
    float mirrorEdge = min(dM0, min(dM1, dM2));

    // Intersection seam detection when truncation / cantellation planes become active
    float seamEdge = 100.0;
    if (uSupportH < -0.01) {
        seamEdge = abs(dPrimary - dTrunc);
    } else if (uSupportH > 0.01) {
        seamEdge = min(abs(dPrimary - dEdge), abs(dTrunc - dEdge));
    }

    outEdge = min(mirrorEdge, seamEdge);
    outFoldedP = pFolded;
    outDepth = length(p);

    return totalSdf;
}

// Normal calculation via central differences
vec3 getNormal(vec3 p) {
    float dummyEdge, dummyDepth;
    vec3 dummyFolded;
    vec2 eps = vec2(0.001, 0.0);
    float d = mapSDF(p, dummyEdge, dummyFolded, dummyDepth);
    return normalize(vec3(
        mapSDF(p + eps.xyy, dummyEdge, dummyFolded, dummyDepth) - d,
        mapSDF(p + eps.yxy, dummyEdge, dummyFolded, dummyDepth) - d,
        mapSDF(p + eps.yyx, dummyEdge, dummyFolded, dummyDepth) - d
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
    float t = max(0.0, (3.2 - 1.8) / zoom);
    const int MAX_STEPS = 80;
    float opacity = clamp(uOpacity, 0.0, 1.0);
    float edgeThick = clamp(uEdgeThickness, 0.0, 0.15);

    for (int step = 0; step < MAX_STEPS; step++) {
        vec3 p = ro + rd * t;
        float edge, depth;
        vec3 foldedP;
        float dist = mapSDF(p, edge, foldedP, depth);

        if (dist < 0.003) {
            // Hit a face or facet
            vec3 n = getNormal(p);
            vec3 lightDir = normalize(vec3(0.577, 0.577, -0.577));
            float diff = max(0.25, dot(n, lightDir));
            float spec = pow(max(0.0, dot(reflect(-lightDir, n), -rd)), 16.0) * 0.4;

            // Surface coloring
            vec3 surfaceCol = vec3(1.0);
            if (uColorMethod < 0.66) {
                // Method 0: H3 Fundamental Chamber & Angular Sectors
                float chamberCoord = (dot(foldedP, n0) * 2.0 + dot(foldedP, n1) * 3.0 + dot(foldedP, n2) * 5.0);
                float angle = atan(foldedP.y, foldedP.x);
                float normAngle = mod(angle + 2.0 * PI, 2.0 * PI) / (2.0 * PI);
                float palParam = fract(chamberCoord * 0.5 + normAngle + uHueOffset);
                surfaceCol = palette(palParam, palA, palB, palC, palD);
            } else if (uColorMethod < 1.33) {
                // Method 1: Depth Gradient (radial distance from centroid)
                float normDepth = clamp((depth - 0.7) / 1.3, 0.0, 1.0);
                surfaceCol = palette(normDepth + uHueOffset, palA, palB, palC, palD);
            } else {
                // Method 2: Facet Normal Spectrum
                vec3 normalTint = abs(n);
                float normParam = fract((normalTint.x + normalTint.y * 2.0 + normalTint.z * 3.0) * 0.33 + uHueOffset);
                surfaceCol = palette(normParam, palA, palB, palC, palD);
            }

            surfaceCol = adjustColor(surfaceCol, uSaturation, uBrightness);
            surfaceCol = surfaceCol * diff + vec3(spec);

            // Wireframe edge detection & contrasting highlight
            if (edgeThick > 0.001) {
                float edgeFactor = 1.0 - smoothstep(0.0, edgeThick, edge);
                vec3 edgeCol = vec3(1.0) - surfaceCol * 0.5; // Contrasting complementary edge tint
                edgeCol = adjustColor(edgeCol, uSaturation, uBrightness * uEdgeBrightness);
                surfaceCol = mix(surfaceCol, edgeCol, edgeFactor);
            }

            // Alpha blending & crystal transparency accumulation (Front-to-back)
            float layerAlpha = opacity;
            float weight = (1.0 - accumColor.a) * layerAlpha;
            accumColor.rgb += surfaceCol * weight;
            accumColor.a   += weight;

            if (accumColor.a > 0.96) {
                break;
            }

            // Step slightly past the surface (scaled with zoom) to catch internal facets
            t += max(0.008 / zoom, dist + 0.008 / zoom);
        } else {
            // Under-relaxation step (0.65) for sharp mirror folds & stellation spike stability
            t += max(0.004 / zoom, dist * 0.65);
        }

        if (t > (3.2 + 2.2) / zoom) break;
    }

    // Proximity ambient glow for atmospheric depth
    if (accumColor.a < 0.9) {
        float glowEdge, glowDepth;
        vec3 glowFolded;
        float glowDist = mapSDF(ro + rd * (3.2 / zoom), glowEdge, glowFolded, glowDepth);
        float glow = exp(-max(0.0, glowDist) * 4.0) * 0.15 * uBrightness;
        accumColor.rgb += palette(uHueOffset, palA, palB, palC, palD) * glow * (1.0 - accumColor.a);
    }

    // Final color with global alpha
    fragColor = vec4(accumColor.rgb, accumColor.a * uAlpha);
}

