#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Analytic tunnel flythroughs with no raymarching: a synthwave grid floor and ceiling with a horizon sun, a Truchet-tiled cylindrical tunnel, and an N-sided polygonal wormhole. Signed forward speed, spiral twist, glow, fog, camera sway.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Geometric"
    ],
    "LSD_PHASES": {"SpeedPhase": ["Speed"]},
    "INPUTS": [
        { "NAME": "SurfaceType", "LABEL": "Scene", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 2.0, "STEP": 1 },
        { "NAME": "Symmetries", "LABEL": "Sides", "TYPE": "float", "DEFAULT": 6.0, "MIN": 3.0, "MAX": 12.0, "STEP": 1 },
        { "NAME": "Speed", "LABEL": "Speed", "TYPE": "float", "DEFAULT": 0.35, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Twist", "LABEL": "Twist", "TYPE": "float", "DEFAULT": 0.15, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Fog", "LABEL": "Fog", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "LineWidth", "LABEL": "Line Width", "TYPE": "float", "DEFAULT": 0.35, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Sway", "LABEL": "Camera Sway", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.85, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define Twist_ (Twist*1.2)
#define Glow_ (Glow*2.0)
#define FogDens_ (0.02+Fog*0.28)
#define LineWidth_ (0.02+LineWidth*0.12)
#define Sway_ (Sway*0.12)
#define Brightness_ (Brightness*2.0)

const float TAU = 6.28318530718;
const float FOCAL = 1.7;
const float T_FAR = 300.0;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// Integer hash of a small non-negative integer cell id, result in [0,1).
float hash21(vec2 id) {
    uvec2 q = uvec2(ivec2(id));
    uint h = q.x * 1597334677u ^ q.y * 3812015801u;
    h = (h ^ (h >> 15)) * 2246822519u;
    h ^= h >> 13;
    h *= 3266489917u;
    h ^= h >> 16;
    return float(h & 0xFFFFFFu) / 16777216.0;
}

vec3 tint(float hue, float lift) {
    return hsv2rgb(vec3(fract(HueOffset + hue), Saturation, 1.0)) * lift;
}

// Antialiased coverage of a line of half-width w at distance d (d, w, fw in the same units).
// fw is the pixel footprint; once a pixel spans many lines the coverage relaxes to the average 2w.
float lineCov(float d, float w, float fw) {
    float c = 1.0 - smoothstep(max(w - fw, 0.0), w + fw, d);
    float far = smoothstep(0.35, 0.9, fw);
    return mix(c, min(2.0 * w, 1.0), far);
}

// Soft halo around a line, fades out when the line is sub-pixel.
float lineHalo(float d, float fw) {
    return exp(-d * 9.0) * (1.0 - smoothstep(0.2, 0.7, fw));
}

// Scene 0: grid floor and ceiling. Plane at height h: t = (h -/+ o.y) / |rd.y| (guarded at the horizon).
vec3 sceneGrid(vec2 oxy, vec3 rd, int layers, float aa, float travel) {
    vec3 col = vec3(0.0);
    float s = rd.y < 0.0 ? -1.0 : 1.0;
    float ay = max(abs(rd.y), 1e-3);
    float w = LineWidth_;
    vec3 lineCol = s < 0.0 ? tint(0.86, 1.0) : tint(0.52, 1.0);
    for (int k = 0; k < 3; k++) {
        if (k >= layers) break;
        float hk = 1.0 + 0.8 * float(k);
        float sp = k == 0 ? 1.0 : (k == 1 ? 2.0 : 4.0);
        float t = min((hk - s * oxy.y) / ay, T_FAR);
        vec2 g = vec2(oxy.x + rd.x * t, rd.z * t + travel) / sp;
        vec2 d = abs(fract(g + 0.5) - 0.5);
        vec2 fw = fwidth(g) * aa;
        float cov = max(lineCov(d.x, w, fw.x), lineCov(d.y, w, fw.y));
        float halo = max(lineHalo(d.x, fw.x), lineHalo(d.y, fw.y));
        float fog = exp(-t * FogDens_);
        float wk = 1.0 / (1.0 + 0.7 * float(k));
        // Surface haze plus lines; lines are brighter every fourth cell row for rhythm.
        float inten = cov + halo * 0.3 * Glow_ + 0.04;
        col += lineCol * inten * fog * wk;
    }
    // Horizon glow and sun at the vanishing point.
    vec2 q = rd.xy / rd.z;
    float hg = exp(-abs(q.y) * 5.0) * exp(-abs(q.x) * 0.8);
    col += tint(0.9, 1.0) * hg * (0.1 + 0.5 * Glow_);
    float ys = (q.y - 0.05) / 0.38;
    float disc = 1.0 - smoothstep(0.96, 1.0, length(vec2(q.x / 0.38, ys)));
    float lower = clamp(-ys, 0.0, 1.0);
    float cut = ys > 0.0 ? 1.0 : smoothstep(lower * 0.6 - 0.03, lower * 0.6 + 0.03, fract(ys * 4.0));
    vec3 sunCol = mix(tint(0.9, 1.0), tint(0.12, 1.0), clamp(ys * 0.5 + 0.5, 0.0, 1.0));
    col += sunCol * disc * cut * 0.9;
    return col;
}

// Scene 1: Truchet-tiled cylinder. Ray-cylinder: |o + t rd|_xy = r, with the camera inside.
vec3 sceneTruchet(vec2 oxy, vec3 rd, int layers, float aa, float travel) {
    vec3 col = vec3(0.0);
    float a = max(dot(rd.xy, rd.xy), 1e-5);
    float b = dot(oxy, rd.xy);
    float w = LineWidth_ * 1.2;
    for (int k = 0; k < 3; k++) {
        if (k >= layers) break;
        float fk = float(k);
        float r = 1.0 - 0.15 * fk;
        float c = dot(oxy, oxy) - r * r;
        float t = min((-b + sqrt(max(b * b - a * c, 0.0))) / a, T_FAR);
        vec2 p = oxy + rd.xy * t;
        float n = k == 0 ? 12.0 : (k == 1 ? 8.0 : 6.0);
        float len = TAU * r / n;
        vec2 uvw = vec2(atan(p.y, p.x) * (n / TAU), rd.z * t / len + mod(travel / len, 64.0));
        vec2 id = floor(uvw);
        vec2 f = fract(uvw);
        float h = hash21(vec2(mod(id.x, n) + 16.0 * fk, mod(id.y, 64.0)));
        if (h > 0.5) f.x = 1.0 - f.x;
        float d = min(abs(length(f) - 0.5), abs(length(f - 1.0) - 0.5));
        float fw = fwidth(d) * aa;
        float cov = lineCov(d, w, fw);
        float halo = lineHalo(d, fw);
        float fog = exp(-t * FogDens_);
        float wk = 1.0 / (1.0 + 0.6 * fk);
        float hue = 0.5 + 0.35 * fract(h * 7.0) + 0.02 * t;
        float pulse = 0.55 + 0.45 * h;
        col += tint(hue, 1.0) * (cov + halo * 0.3 * Glow_) * pulse * fog * wk;
    }
    // Light at the end of the tunnel.
    float rho = length(rd.xy) / rd.z;
    col += tint(0.58, 1.0) * exp(-rho * 5.0) * (0.1 + 0.6 * Glow_);
    return col;
}

// Scene 2: N-sided polygonal wormhole. The ray-prism hit is the nearest of N ray-plane hits.
vec3 sceneWormhole(vec2 oxy, vec3 rd, int sides, int layers, float aa, float travel) {
    vec3 col = vec3(0.0);
    float nf = float(sides);
    float w = LineWidth_;
    for (int k = 0; k < 3; k++) {
        if (k >= layers) break;
        float fk = float(k);
        float r = 1.0 - 0.14 * fk;
        float tmin = T_FAR;
        float face = 0.0;
        for (int i = 0; i < 12; i++) {
            if (i >= sides) break;
            float an = (float(i) + 0.5 + 0.5 * fk) * TAU / nf;
            vec2 nrm = vec2(cos(an), sin(an));
            float dn = dot(rd.xy, nrm);
            if (dn > 1e-4) {
                float ti = (r - dot(oxy, nrm)) / dn;
                if (ti < tmin) { tmin = ti; face = float(i); }
            }
        }
        float t = max(tmin, 0.0);
        float an = (face + 0.5 + 0.5 * fk) * TAU / nf;
        vec2 nrm = vec2(cos(an), sin(an));
        vec2 p = oxy + rd.xy * t;
        float half_ = r * tan(3.14159265 / nf);
        float sn = clamp(dot(p, vec2(-nrm.y, nrm.x)) / half_, -1.0, 1.0);
        float ringLen = 0.5;
        float rv = (rd.z * t + travel) / ringLen;
        float dRing = abs(fract(rv + 0.5) - 0.5);
        float dEdge = (1.0 - abs(sn)) * half_ / ringLen;
        float dChev = abs(fract(rv + abs(sn) * 0.5 + 0.5) - 0.5) * 0.9;
        float fwR = fwidth(rv) * aa;
        float fwE = fwidth(dEdge) * aa;
        float fwC = fwidth(dChev) * aa;
        float ringI = lineCov(dRing, w, fwR) + 0.3 * Glow_ * lineHalo(dRing, fwR);
        float edgeI = lineCov(dEdge, w * 1.3, fwE) + 0.3 * Glow_ * lineHalo(dEdge, fwE);
        float chevI = 0.5 * lineCov(dChev, w * 0.8, fwC);
        float idx = mod(floor(rv), 64.0);
        float flick = 0.6 + 0.4 * hash21(vec2(idx, 3.0 + fk));
        float fog = exp(-t * FogDens_);
        float wk = 1.0 / (1.0 + 0.6 * fk);
        vec3 cRing = tint(0.72 + 0.1 * mod(idx, 2.0) + 0.02 * t, 1.0);
        vec3 cEdge = tint(0.1 + 0.03 * face, 1.0);
        vec3 cChev = tint(0.55 + 0.03 * face, 1.0);
        col += (cRing * ringI * flick + cEdge * edgeI + cChev * chevI) * fog * wk;
    }
    float rho = length(rd.xy) / rd.z;
    col += tint(0.78, 1.0) * exp(-rho * 5.0) * (0.1 + 0.7 * Glow_);
    return col;
}

void main() {
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 uv = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0) * 2.0;

    // Camera sway (phases are fract-wrapped so precision never degrades; seamless in time).
    float a1 = TAU * fract(TIME * 0.071);
    float a2 = TAU * fract(TIME * 0.053);
    float a3 = TAU * fract(TIME * 0.037);
    vec2 oxy = Sway_ * vec2(sin(a1) + 0.5 * sin(2.0 * a2), 0.7 * cos(a2));
    float roll = 1.2 * Sway_ * sin(a3);

    // Twist: rotate the image about the vanishing point by an angle growing with depth (~1/rho).
    float rho0 = length(uv);
    float phi = roll + Twist_ / (rho0 + 0.25);
    float cs = cos(phi), sn_ = sin(phi);
    uv = vec2(cs * uv.x - sn_ * uv.y, sn_ * uv.x + cs * uv.y);

    vec3 rd = normalize(vec3(uv, FOCAL));

    int layers = 1 + int(clamp(Quality, 0.0, 1.0) * 2.999);
    float aa = mix(0.6, 1.4, Quality);
    int scene = clamp(int(floor(SurfaceType + 0.5)), 0, 2);
    int sides = clamp(int(floor(Symmetries + 0.5)), 3, 12);
    float travelRaw = SpeedPhase * 4.0;   // wrapped per scene to a whole number of pattern periods

    vec3 col;
    if (scene == 0) {
        col = sceneGrid(oxy, rd, layers, aa, mod(travelRaw, 64.0));
    } else if (scene == 1) {
        col = sceneTruchet(oxy, rd, layers, aa, travelRaw);
    } else {
        col = sceneWormhole(oxy, rd, sides, layers, aa, mod(travelRaw, 32.0));
    }

    col *= Brightness_;
    col = 1.0 - exp(-col * 1.3);
    col *= 1.0 - 0.25 * smoothstep(0.7, 1.8, length(uv));
    gl_FragColor = vec4(col, 1.0);
}
