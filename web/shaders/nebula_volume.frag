#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Volumetric nebula: ray-marched fbm gas filaments with emission, self-shadowing from a drifting inner light, domain-warped turbulence and a starfield, with 3D camera flight.",
    "CREDIT": "Liquid LSD",
    "LSD_COST": 3,
    "ISFVSN": "2.0",
    "is3D": true,
    "CATEGORIES": [
        "Generator",
        "3D",
        "Volumetric"
    ],
    "LSD_PHASES": {"FlightPhase": ["FlightSpeed"], "DriftPhase": ["Drift"]},
    "INPUTS": [
        { "NAME": "Density", "LABEL": "Density", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Coverage", "LABEL": "Coverage", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Scale", "LABEL": "Scale", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Turbulence", "LABEL": "Turbulence", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Detail", "LABEL": "Detail", "TYPE": "float", "DEFAULT": 2.0, "MIN": 1.0, "MAX": 5.0, "STEP": 1 },
        { "NAME": "Quality", "LABEL": "Quality", "TYPE": "float", "DEFAULT": 0.15, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Lighting", "LABEL": "Lighting", "TYPE": "float", "DEFAULT": 0.4, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "FlightSpeed", "LABEL": "Flight Speed", "TYPE": "float", "DEFAULT": 0.25, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "Drift", "LABEL": "Drift", "TYPE": "float", "DEFAULT": 0.3, "MIN": -1.0, "MAX": 1.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0, "STEP": 1 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.75, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "RotateX", "LABEL": "Rotate X", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateY", "LABEL": "Rotate Y", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateZ", "LABEL": "Rotate Z", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 }
    ]
}
*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define Density_ (0.4+Density*2.6)
#define Coverage_ (0.92-Coverage*0.4)
#define Scale_ (0.35+Scale*1.65)
#define Turbulence_ (Turbulence*1.4)
#define Glow_ (Glow*2.5)
#define Brightness_ (Brightness*2.0)
#define Zoom_ (Zoom<0.5?pow(0.1,1.0-2.0*Zoom):pow(5.0,2.0*Zoom-1.0))

// Cost: at most MAX_STEPS view samples (Quality 0..1 -> 20..72), each one fbm of 2..5 octaves (Detail), and
// at most 4 light-march taps per sample (Lighting scales the tap count), each 2 octaves. Early-out at
// transmittance < 0.02 and on ray exit; empty space is crossed at 1.8x step. Worst case per sample: 1 warp noise +
// Detail octaves + 4 light taps x 1 octave.
const int MAX_STEPS = 72;
const int MAX_LIGHT = 4;
const float FAR = 11.0;
const float PI = 3.14159265358979323846;

mat3 rotX(float a) { float c = cos(a), s = sin(a); return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c); }
mat3 rotY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c); }
mat3 rotZ(float a) { float c = cos(a), s = sin(a); return mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0); }

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x),
                   mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
                   mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

// Ridged fbm: bright filaments where the noise crosses 0.5.
float fbm(vec3 p, int oct) {
    float a = 0.5, s = 0.0, norm = 0.0;
    for (int i = 0; i < 5; i++) {
        if (i >= oct) break;
        float n = 1.0 - abs(2.0 * vnoise(p) - 1.0);
        s += a * n * n;
        norm += a;
        p = p * 2.03 + vec3(17.1, 9.2, 5.7);
        a *= 0.5;
    }
    return s / max(norm, 1e-4);
}

float drift_;   // gas drift distance: Drift integrated by the host (DriftPhase), times the Drift_ scale

// Gas density at p: domain-warped ridged fbm, thresholded by Coverage, faded by distance from the core.
float density(vec3 p, int oct, bool warp) {
    vec3 q = p * Scale_;
    q.z += drift_;
    if (warp) {                                          // one low-frequency noise pushes q along a slowly turning direction
        float w0 = vnoise(q * 0.7 + 3.1) - 0.5;
        q += w0 * Turbulence_ * 3.0 * vec3(sin(q.y * 0.9 + 1.0), sin(q.z * 0.8 + 2.0), sin(q.x * 0.7 + 3.0));
    }
    float d = fbm(q, oct);
    float r = length(p.xy);
    float envelope = exp(-r * r * 0.02);                 // wide cylindrical envelope around the flight path
    return max(d - Coverage_, 0.0) * 3.0 * envelope * Density_;
}

vec3 gasColor(float d, vec3 p, int cMode) {
    float h;
    if (cMode == 0)      h = HueOffset + 0.60 + 0.45 * clamp(d * 1.2, 0.0, 1.0) + p.z * 0.01;
    else if (cMode == 1) h = HueOffset + 0.05 + 0.5 * vnoise(p * 0.15);
    else if (cMode == 2) h = HueOffset + length(p.xy) * 0.05 + p.z * 0.02;
    else                 h = HueOffset + 0.58;
    float sat = (cMode == 3) ? Saturation * 0.3 : Saturation;
    return hsv2rgb(vec3(fract(h), sat, 1.0));
}

float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }

void main() {
    drift_ = DriftPhase * 0.25;
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 uv = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0);

    float fov = 1.2 / max(Zoom_, 0.05);
    vec3 ro = vec3(0.0, 0.0, -6.0 + FlightPhase * 1.5);
    vec3 rd = normalize(vec3(uv * fov, 1.0));
    mat3 rot = rotZ(RotateZ) * rotY(RotateY) * rotX(RotateX);
    // rotate around the point 6 units ahead, so the camera orbits the gas instead of spinning in place
    vec3 pivot = vec3(0.0, 0.0, FlightPhase * 1.5);
    ro = rot * (ro - pivot) + pivot;
    rd = rot * rd;

    int steps = int(20.0 + Quality * 52.0);
    int oct = clamp(int(floor(Detail + 0.5)), 1, 5);
    int lightTaps = int(floor(Lighting * float(MAX_LIGHT) + 0.5));
    int cMode = clamp(int(floor(ColorMode + 0.5)), 0, 3);

    // inner light drifts through the nebula ahead of the camera
    vec3 lp = pivot + vec3(2.5 * sin(TIME * 0.17), 1.8 * cos(TIME * 0.23), 3.0 + 2.0 * sin(TIME * 0.11));

    float dt = FAR / float(steps);
    float t = dt * ign(gl_FragCoord.xy + float(FRAMEINDEX % 64) * 7.0);
    vec3 acc = vec3(0.0);
    float trans = 1.0;

    for (int i = 0; i < MAX_STEPS; i++) {
        if (i >= steps || trans < 0.02) break;
        vec3 p = ro + rd * t;
        float d = density(p, oct, true);
        if (d > 0.002) {
            float absorb = d * dt * 0.9;
            vec3 base = gasColor(d, p, cMode);

            // emission: dense filaments glow
            vec3 emit = base * (d * d * Glow_ * 1.4);

            // in-scatter from the inner light, shadowed by gas between sample and light
            float lightAtt = 1.0;
            if (lightTaps > 0) {
                vec3 toL = lp - p;
                float dl = length(toL);
                vec3 ld = toL / max(dl, 1e-3);
                float tau = 0.0;
                float ls = min(dl, 3.0) / float(MAX_LIGHT);
                for (int j = 1; j <= MAX_LIGHT; j++) {
                    if (j > lightTaps) break;
                    tau += density(p + ld * ls * float(j), 1, false) * ls;
                }
                lightAtt = exp(-tau * 1.6);
            }
            float lightFall = 1.0 / (1.0 + 0.08 * dot(lp - p, lp - p));
            vec3 scatter = base * (0.6 + 1.2 * lightFall) * lightAtt * d * Lighting * 1.3;

            vec3 col = (emit + scatter + base * d * 0.15) * Brightness_;
            acc += trans * col * dt * 1.6;
            trans *= exp(-absorb);
        }
        t += (d > 0.002) ? dt : dt * 1.8;                 // leap over empty space
    }

    // starfield behind the gas (direction-hashed cells, brighter point cores)
    vec3 sd = rd * 90.0;
    vec3 cell = floor(sd);
    float star = step(0.985, hash13(cell));
    vec3 sf = fract(sd) - 0.5;
    float core = star * smoothstep(0.35, 0.0, length(sf));
    vec3 bg = vec3(0.008, 0.008, 0.02) + core * mix(vec3(0.7, 0.8, 1.0), vec3(1.0, 0.85, 0.6), hash13(cell + 5.0)) * Brightness_;

    vec3 col = acc + bg * trans;
    col = 1.0 - exp(-col);                                // soft tone-map keeps dense cores from clipping
    if (!(col.r == col.r && col.g == col.g && col.b == col.b)) col = vec3(0.0);
    gl_FragColor = vec4(col, 1.0);
}
