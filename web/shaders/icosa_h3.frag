#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Icosahedral H3 Coxeter raymarcher: continuous Icosahedron/Dodecahedron duality morph crossfaded with an independent spike-and-blocker stellation CSG, with facet-family aware coloring.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "is3D": true,
    "CATEGORIES": [
        "Generator",
        "3D",
        "Geometric"
    ],
    "INPUTS": [
        { "NAME": "Morph", "LABEL": "Morph", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "StellationBoost", "LABEL": "Stellation Boost", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SpikeMode", "LABEL": "Spike Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SpikePhase", "LABEL": "Spike Phase", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 6.2831853 },
        { "NAME": "SpikeSharpness", "LABEL": "Spike Sharpness", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "BlockerSize", "LABEL": "Blocker Size", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "SupportH", "LABEL": "Support H", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 4.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueAnimSpeed", "LABEL": "Hue Anim Speed", "TYPE": "float", "DEFAULT": 0.0, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.85, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.95, "MIN": 0.0, "MAX": 2.0 },
        { "NAME": "Opacity", "LABEL": "Opacity", "TYPE": "float", "DEFAULT": 0.75, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "EdgeThickness", "LABEL": "Edge Thickness", "TYPE": "float", "DEFAULT": 0.025, "MIN": 0.0, "MAX": 0.15 },
        { "NAME": "EdgeBrightness", "LABEL": "Edge Brightness", "TYPE": "float", "DEFAULT": 1.2, "MIN": 0.0, "MAX": 2.0 },
        { "NAME": "RimGlow", "LABEL": "Rim Glow", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 2.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.1, "MAX": 5.0 },
        { "NAME": "RotateX", "LABEL": "Rotate X", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateY", "LABEL": "Rotate Y", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateZ", "LABEL": "Rotate Z", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 }
    ]
}
*/

const float PI  = 3.14159265358979323846;
const float PHI = 1.61803398874989484820;

// -----------------------------------------------------------------------------
// H3 Coxeter reflection group substrate (shared by both stellation families)
// -----------------------------------------------------------------------------
const vec3 n0 = vec3(1.0, 0.0, 0.0);
const vec3 n1 = vec3(-PHI * 0.5, -0.5, 0.5 / PHI);
const vec3 n2 = vec3(0.0, 1.0, 0.0);

// Symmetry axes (fundamental chamber vertices)
const vec3 C3 = vec3((PHI - 1.0) / 1.7320508075688772, 0.0, PHI / 1.7320508075688772); // 3-fold axis
const vec3 C5 = vec3(0.0, 1.0 / 1.902113032590307, PHI / 1.902113032590307);           // 5-fold axis
const vec3 C2 = vec3(0.0, 0.0, 1.0);                                                    // 2-fold axis (edge center)

mat3 rotateX(float a){ float c=cos(a),s=sin(a); return mat3(1.0,0.0,0.0, 0.0,c,-s, 0.0,s,c); }
mat3 rotateY(float a){ float c=cos(a),s=sin(a); return mat3(c,0.0,s, 0.0,1.0,0.0, -s,0.0,c); }
mat3 rotateZ(float a){ float c=cos(a),s=sin(a); return mat3(c,-s,0.0, s,c,0.0, 0.0,0.0,1.0); }

float smootherstep(float e0, float e1, float x){
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

vec3 palette(float t, vec3 a, vec3 b, vec3 c, vec3 d){
    return a + b * cos(6.2831853 * (c * t + d));
}

vec3 adjustColor(vec3 col, float sat, float bright){
    float gray = dot(col, vec3(0.299, 0.587, 0.114));
    return max(vec3(0.0), mix(vec3(gray), col, sat) * bright);
}

// General slerp, valid for t outside [0,1] (analytic continuation along the great circle)
vec3 slerpArc(vec3 p0, vec3 p1, float t){
    float d = clamp(dot(p0, p1), -1.0, 1.0);
    float theta = acos(d);
    float sinTheta = sin(theta);
    if (sinTheta < 0.001) return normalize(mix(p0, p1, t));
    float w0 = sin((1.0 - t) * theta) / sinTheta;
    float w1 = sin(t * theta) / sinTheta;
    return normalize(p0 * w0 + p1 * w1);
}

// Fast slerp between C3 and C5 for t in [0,1] using the exact known dihedral angle
vec3 getGenerator(float t){
    const float omega = 0.65235814;
    const float sinOmega = 0.60706200;
    float s0 = sin((1.0 - t) * omega) / sinOmega;
    float s1 = sin(t * omega) / sinOmega;
    return normalize(s0 * C3 + s1 * C5);
}

// Iterative H3 symmetry folding into the fundamental chamber
vec3 foldH3(vec3 p){
    for (int i = 0; i < 16; ++i) {
        p -= 2.0 * min(0.0, dot(p, n0)) * n0;
        p -= 2.0 * min(0.0, dot(p, n1)) * n1;
        p -= 2.0 * min(0.0, dot(p, n2)) * n2;
    }
    return p;
}

// 4-Phase duality evolution:
// 0.00-0.25 Icosahedron -> Dodecahedron
// 0.25-0.50 Dodecahedron -> Great Stellated Dodecahedron
// 0.50-0.75 Great Stellated Dodecahedron -> Great Icosahedron
// 0.75-1.00 Great Icosahedron -> Icosahedron
void getMorphState(float u, out float tGen, out float sHeight){
    float t = fract(u);
    if (t < 0.25) {
        float f = smootherstep(0.0, 1.0, t * 4.0);
        tGen = f; sHeight = 0.0;
    } else if (t < 0.5) {
        float f = smootherstep(0.0, 1.0, (t - 0.25) * 4.0);
        tGen = 1.0; sHeight = f;
    } else if (t < 0.75) {
        float f = smootherstep(0.0, 1.0, (t - 0.5) * 4.0);
        tGen = 1.0 - f; sHeight = 1.0;
    } else {
        float f = smootherstep(0.0, 1.0, (t - 0.75) * 4.0);
        tGen = 0.0; sHeight = 1.0 - f;
    }
}

// Evaluates the combined SDF: a smooth duality-morph family (Icosa<->Dodeca with
// truncation/cantellation duals) continuously crossfaded with an independent
// spike+blocker stellation CSG, driven by SpikeMode.
float mapSDF(vec3 p, out float outEdge, out vec3 outFoldedP, out float outFamily, out float outDepth){
    vec3 pFolded = foldH3(p);
    const float baseH = 0.82;

    // --- Smooth duality-morph family ---
    float tGen, sHeight;
    getMorphState(Morph, tGen, sHeight);
    float totalStell = clamp(sHeight + StellationBoost, 0.0, 1.0);

    vec3 v = getGenerator(tGen);
    vec3 vDual = getGenerator(1.0 - tGen);

    vec3 c3Adj = vec3(-C3.x, 0.0, C3.z);
    vec3 c5Adj = vec3(0.0, -C5.y, C5.z);
    const float omega = 0.65235814;
    const float sinOmega = 0.60706200;
    float gs0 = sin((1.0 - tGen) * omega) / sinOmega;
    float gs1 = sin(tGen * omega) / sinOmega;
    vec3 vAdj = normalize(gs0 * c3Adj + gs1 * c5Adj);
    vec3 vMorph = normalize(mix(v, vAdj, totalStell));

    float dPrimary = dot(pFolded, vMorph) - baseH;
    float hTrunc = baseH * (1.28 + min(0.0, SupportH) * 0.65);
    float dTrunc = dot(pFolded, vDual) - hTrunc;
    float hEdgeFace = baseH * (1.28 - max(0.0, SupportH) * 0.50);
    float dEdgeFace = dot(pFolded, C2) - hEdgeFace;

    float dSmooth = max(dPrimary, max(dTrunc, dEdgeFace));
    float familySmooth = (dPrimary >= dTrunc && dPrimary >= dEdgeFace) ? 0.0 : ((dTrunc >= dEdgeFace) ? 1.0 : 2.0);
    float seamSmooth = min(abs(dPrimary - dTrunc), min(abs(dPrimary - dEdgeFace), abs(dTrunc - dEdgeFace)));

    // --- Independent spike + blocker stellation CSG family ---
    vec3 chamberCenter = normalize(C3 + C5);
    float phaseOffset = asin(-1.0 / 3.0);
    float safeX = 1.5 * sin(SpikePhase + phaseOffset) + 0.5;

    vec3 corePole = slerpArc(C3, C5, safeX);
    vec3 adj1 = corePole - 2.0 * dot(corePole, n0) * n0;
    vec3 adj2 = corePole - 2.0 * dot(corePole, n2) * n2;
    vec3 spikePole1 = normalize(mix(corePole, adj1, SpikeSharpness));
    vec3 spikePole2 = normalize(mix(corePole, adj2, SpikeSharpness));

    float h1 = baseH * max(0.1, dot(spikePole1, chamberCenter));
    float h2 = baseH * max(0.1, dot(spikePole2, chamberCenter));
    float dSpike1 = dot(pFolded, spikePole1) - h1;
    float dSpike2 = dot(pFolded, spikePole2) - h2;
    float dSpikeShape = max(dSpike1, dSpike2);

    vec3 blockerPole = slerpArc(C3, C5, safeX + 1.0);
    float hBlocker = baseH * max(0.1, dot(blockerPole, chamberCenter));
    float blockerRadius = hBlocker + (1.0 - BlockerSize) * 1.5;
    float dBlocker = dot(pFolded, blockerPole) - blockerRadius;

    float dSpikeFinal = max(dSpikeShape, dBlocker);
    float familySpike = (dSpike1 >= dSpike2 && dSpike1 >= dBlocker) ? 0.0 : ((dSpike2 >= dBlocker) ? 1.0 : 2.0);
    float seamSpike = min(abs(dSpike1 - dSpike2), min(abs(dSpike1 - dBlocker), abs(dSpike2 - dBlocker)));

    // --- Crossfade the two families ---
    float mode = clamp(SpikeMode, 0.0, 1.0);
    float totalSdf = mix(dSmooth, dSpikeFinal, mode);
    float mirrorEdge = min(dot(pFolded, n0), min(dot(pFolded, n1), dot(pFolded, n2)));

    outEdge = min(mirrorEdge, mix(seamSmooth, seamSpike, mode));
    outFoldedP = pFolded;
    outFamily = mix(familySmooth, familySpike, mode);
    outDepth = length(p);

    return totalSdf;
}

vec3 getNormal(vec3 p){
    float dE, dD, dF; vec3 dC;
    vec2 e = vec2(0.001, 0.0);
    float d = mapSDF(p, dE, dC, dF, dD);
    return normalize(vec3(
        mapSDF(p + e.xyy, dE, dC, dF, dD) - d,
        mapSDF(p + e.yxy, dE, dC, dF, dD) - d,
        mapSDF(p + e.yyx, dE, dC, dF, dD) - d));
}

void main(){
    vec2 uv = isf_FragNormCoord * 2.0 - 1.0;
    uv.x *= RENDERSIZE.x / RENDERSIZE.y;

    float zoom = clamp(Zoom, 0.1, 5.0);
    vec3 ro = vec3(0.0, 0.0, -3.8 / zoom);
    vec3 rd = normalize(vec3(uv, 1.4));
    mat3 rot = rotateZ(RotateZ) * rotateY(RotateY) * rotateX(RotateX);
    ro = rot * ro; rd = rot * rd;
    vec3 lD = rot * normalize(vec3(0.3, 0.4, -1.0));

    vec3 palA = vec3(0.5), palB = vec3(0.5), palC = vec3(1.0), palD = vec3(0.0, 0.33, 0.67);
    float hueEff = fract(HueOffset + TIME * HueAnimSpeed * 0.05);

    vec4 acc = vec4(0.0);
    float t = max(0.0, (3.8 - 3.0) / zoom);
    float opacity = clamp(Opacity, 0.0, 1.0);
    float edgeThick = clamp(EdgeThickness, 0.0, 0.15);
    int cm = int(clamp(ColorMode, 0.0, 4.0) + 0.5);

    for (int i = 0; i < 120; i++) {
        vec3 p = ro + rd * t;
        float edge, depth, family; vec3 foldedP;
        float dist = mapSDF(p, edge, foldedP, family, depth);

        if (dist < 0.003) {
            vec3 n = getNormal(p);
            float diff = max(0.25, dot(n, lD));
            float spec = pow(max(0.0, dot(reflect(-lD, n), -rd)), 16.0) * 0.4;
            float rim = pow(1.0 - max(0.0, dot(n, -rd)), 3.0) * RimGlow;

            vec3 col;
            if (cm == 0) {
                // Facet Family: distinct hues per active half-space (primary/trunc/edge or spike1/spike2/blocker)
                float chamberCoord = dot(foldedP, n0) * 2.0 + dot(foldedP, n1) * 3.0 + dot(foldedP, n2) * 5.0;
                float palParam = fract(family / 3.0 * 0.6 + chamberCoord * 0.15 + hueEff);
                col = palette(palParam, palA, palB, palC, palD);
            } else if (cm == 1) {
                // Chamber Sectors: angular kaleidoscope within the fundamental domain
                float chamberCoord = dot(foldedP, n0) * 2.0 + dot(foldedP, n1) * 3.0 + dot(foldedP, n2) * 5.0;
                float angle = atan(foldedP.y, foldedP.x);
                float normAngle = mod(angle + 2.0 * PI, 2.0 * PI) / (2.0 * PI);
                col = palette(fract(chamberCoord * 0.5 + normAngle + hueEff), palA, palB, palC, palD);
            } else if (cm == 2) {
                // Depth Gradient
                float normDepth = clamp((depth - 0.7) / 1.6, 0.0, 1.0);
                col = palette(normDepth + hueEff, palA, palB, palC, palD);
            } else if (cm == 3) {
                // Facet Normal Spectrum
                vec3 nt = abs(n);
                col = palette(fract((nt.x + nt.y * 2.0 + nt.z * 3.0) * 0.33 + hueEff), palA, palB, palC, palD);
            } else {
                // Iridescent Fresnel: animated thin-film shimmer driven by viewing angle
                float fres = pow(1.0 - max(0.0, dot(n, -rd)), 2.5);
                col = palette(fract(fres * 0.8 + hueEff + TIME * 0.05), palA, palB, palC, palD);
            }

            col = adjustColor(col, Saturation, Brightness) * diff + vec3(spec);
            col += rim * adjustColor(palette(fract(hueEff + 0.5), palA, palB, palC, palD), Saturation, Brightness);

            if (edgeThick > 0.001) {
                float ef = 1.0 - smoothstep(0.0, edgeThick, edge);
                col = mix(col, adjustColor(vec3(1.0) - col * 0.5, Saturation, Brightness * EdgeBrightness), ef);
            }

            float w = (1.0 - acc.a) * opacity;
            acc.rgb += col * w; acc.a += w;
            if (acc.a > 0.96) break;
            t += max(0.006 / zoom, dist + 0.006 / zoom);
        } else {
            // Conservative under-relaxed step: stays safe on the acute needle ridges from the spike family
            t += max(0.003 / zoom, dist * 0.5);
        }
        if (t > (3.8 + 3.5) / zoom) break;
    }

    if (acc.a < 0.9) {
        float gE, gD, gF; vec3 gC;
        float gd = mapSDF(ro + rd * (3.8 / zoom), gE, gC, gF, gD);
        acc.rgb += palette(hueEff, palA, palB, palC, palD) * exp(-max(0.0, gd) * 4.0) * 0.15 * Brightness * (1.0 - acc.a);
    }

    gl_FragColor = vec4(acc.rgb, acc.a * uAlpha);
}
