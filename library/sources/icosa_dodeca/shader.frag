#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

// Uniforms provided by DynamicVisualSource
uniform float uMorph;          // 0.0 (Icosahedron) -> 0.5 (Icosidodecahedron) -> 1.0 (Dodecahedron)
uniform float uStellation;      // 0.0 (Base solid) -> 1.0 (Great Stellation)
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
// Icosahedral Symmetry Plane Sets (Normals)
// -----------------------------------------------------------------------------

// 10 pairs of 3-fold axes (Icosahedron face normals)
vec3 getIcoNormal(int i) {
    float a = 1.0 / sqrt(3.0);
    float b = 1.0 / sqrt(1.0 + PHI * PHI);
    float c = PHI / sqrt(1.0 + PHI * PHI);
    if (i == 0) return vec3(a, a, a);
    if (i == 1) return vec3(-a, a, a);
    if (i == 2) return vec3(a, -a, a);
    if (i == 3) return vec3(a, a, -a);
    if (i == 4) return vec3(0.0, b, c);
    if (i == 5) return vec3(0.0, -b, c);
    if (i == 6) return vec3(b, c, 0.0);
    if (i == 7) return vec3(-b, c, 0.0);
    if (i == 8) return vec3(c, 0.0, b);
    return vec3(c, 0.0, -b); // i == 9
}

// 6 pairs of 5-fold axes (Dodecahedron face normals)
vec3 getDodNormal(int i) {
    float b = 1.0 / sqrt(1.0 + PHI * PHI);
    float c = PHI / sqrt(1.0 + PHI * PHI);
    if (i == 0) return vec3(0.0, b, c);
    if (i == 1) return vec3(0.0, -b, c);
    if (i == 2) return vec3(b, c, 0.0);
    if (i == 3) return vec3(-b, c, 0.0);
    if (i == 4) return vec3(c, 0.0, b);
    return vec3(c, 0.0, -b); // i == 5
}

// Evaluates the dual morph + stellation SDF and extracts edge factor and symmetry info
float mapSDF(vec3 p, out float outEdge, out vec3 outAxis, out float outDepth) {
    float m = clamp(uMorph, 0.0, 1.0);
    float s = clamp(uStellation, 0.0, 1.0);

    // Plane threshold distances for dual rectification
    // At m=0: icosahedron planes dominate; at m=0.5: icosidodecahedron; at m=1: dodecahedron planes dominate.
    float rIco = 0.82 + 1.25 * (m * m);
    float rDod = 0.82 + 1.25 * ((1.0 - m) * (1.0 - m));

    float maxIco1 = -1e5;
    float maxIco2 = -1e5;
    vec3 bestIcoAxis = vec3(0.0, 1.0, 0.0);

    for (int i = 0; i < 10; i++) {
        vec3 n = getIcoNormal(i);
        float d = abs(dot(p, n)) - rIco;
        if (d > maxIco1) {
            maxIco2 = maxIco1;
            maxIco1 = d;
            bestIcoAxis = n;
        } else if (d > maxIco2) {
            maxIco2 = d;
        }
    }

    float maxDod1 = -1e5;
    float maxDod2 = -1e5;
    vec3 bestDodAxis = vec3(0.0, 1.0, 0.0);

    for (int j = 0; j < 6; j++) {
        vec3 n = getDodNormal(j);
        float d = abs(dot(p, n)) - rDod;
        if (d > maxDod1) {
            maxDod2 = maxDod1;
            maxDod1 = d;
            bestDodAxis = n;
        } else if (d > maxDod2) {
            maxDod2 = d;
        }
    }

    // Base convex polyhedron SDF
    float baseSdf = max(maxIco1, maxDod1);

    // Closest secondary plane distance gives edge proximity
    float edgeIco = maxIco1 - maxIco2;
    float edgeDod = maxDod1 - maxDod2;
    float baseEdge = min(edgeIco, edgeDod);

    // -------------------------------------------------------------------------
    // Stellation: Star spikes along 5-fold (dodeca) or 3-fold (icosa) axes
    // -------------------------------------------------------------------------
    float starSdf = 1e5;
    float starEdge = 1e5;

    if (s > 0.001) {
        // Spike height and tapering
        float spikeIcoHeight = 0.95 * s * (1.0 - m * 0.5);
        float spikeDodHeight = 1.15 * s * (0.5 + m * 0.5);

        // 3-fold star spikes (for Great Icosahedron)
        float dIcoStar = -1e5;
        for (int i = 0; i < 10; i++) {
            vec3 n = getIcoNormal(i);
            float proj = abs(dot(p, n));
            vec3 perp = p - n * dot(p, n);
            float perpDist = length(perp);
            float cone = perpDist * 2.1 - (rIco + spikeIcoHeight - proj);
            dIcoStar = max(dIcoStar, -cone);
        }

        // 5-fold star spikes (for Great Stellated Dodecahedron)
        float dDodStar = -1e5;
        for (int j = 0; j < 6; j++) {
            vec3 n = getDodNormal(j);
            float proj = abs(dot(p, n));
            vec3 perp = p - n * dot(p, n);
            float perpDist = length(perp);
            float cone = perpDist * 2.3 - (rDod + spikeDodHeight - proj);
            dDodStar = max(dDodStar, -cone);
        }

        float mixedStar = mix(dIcoStar, dDodStar, m);
        // Union base shape with star spikes
        baseSdf = min(baseSdf, mixedStar);
    }

    outEdge = baseEdge;
    outAxis = mix(bestIcoAxis, bestDodAxis, m);
    outDepth = length(p);

    return baseSdf;
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

    // Palette base vectors
    vec3 palA = vec3(0.5, 0.5, 0.5);
    vec3 palB = vec3(0.5, 0.5, 0.5);
    vec3 palC = vec3(1.0, 1.0, 1.0);
    vec3 palD = vec3(0.0, 0.33, 0.67);

    // Multi-layer crystal accumulation for inner-face transparency reveal
    vec4 accumColor = vec4(0.0);
    float t = 0.1;
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
                float normDepth = clamp((dVal - 0.7) / 1.4, 0.0, 1.0);
                surfaceCol = palette(normDepth + uHueOffset, palA, palB, palC, palD);
            } else {
                // Method 4: 5-Fold Sectors
                // Azimuthal angle around closest symmetry axis
                vec3 refX = normalize(cross(axis, vec3(0.0, 1.0, 0.001)));
                vec3 refY = cross(axis, refX);
                float angle = atan(dot(p, refY), dot(p, refX));
                float sector = floor(mod((angle / (2.0 * PI / 5.0)) + uHueOffset * 5.0, 5.0)) / 5.0;
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
            t += max(0.015, dist * 0.7);
        }

        if (t > 7.0) break;
    }

    // Final color with global alpha
    fragColor = vec4(accumColor.rgb, accumColor.a * uAlpha);
}
