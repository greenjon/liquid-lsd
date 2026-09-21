#version 300 es
precision highp float;
/*
{
    "DESCRIPTION": "Raymarched Triply Periodic Minimal Surfaces (TPMS) with continuous morphing between Gyroid, Schwarz P, and Neovius minimal surfaces, volumetric internal glow, and 3D camera flight.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "is3D": true,
    "CATEGORIES": [
        "Generator",
        "3D",
        "Geometric"
    ],
    "INPUTS": [
        { "NAME": "SurfaceType", "LABEL": "Surface Type", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 2.0 },
        { "NAME": "WallThickness", "LABEL": "Wall Thickness", "TYPE": "float", "DEFAULT": 0.25, "MIN": 0.02, "MAX": 0.8 },
        { "NAME": "Frequency", "LABEL": "Frequency", "TYPE": "float", "DEFAULT": 1.2, "MIN": 0.3, "MAX": 3.0 },
        { "NAME": "FlightSpeed", "LABEL": "Flight Speed", "TYPE": "float", "DEFAULT": 0.5, "MIN": -2.0, "MAX": 2.0 },
        { "NAME": "WireframeMode", "LABEL": "Wireframe Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "CoreGlow", "LABEL": "Core Glow", "TYPE": "float", "DEFAULT": 0.8, "MIN": 0.0, "MAX": 3.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Saturation", "LABEL": "Saturation", "TYPE": "float", "DEFAULT": 0.85, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Brightness", "LABEL": "Brightness", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 2.0 },
        { "NAME": "Zoom", "LABEL": "Zoom", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.1, "MAX": 5.0 },
        { "NAME": "RotateX", "LABEL": "Rotate X", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateY", "LABEL": "Rotate Y", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 },
        { "NAME": "RotateZ", "LABEL": "Rotate Z", "TYPE": "float", "DEFAULT": 0.0, "MIN": -3.14159265, "MAX": 3.14159265 }
    ]
}
*/

const float PI = 3.14159265358979323846;

mat3 rotX(float a) { float c = cos(a), s = sin(a); return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c); }
mat3 rotY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c); }
mat3 rotZ(float a) { float c = cos(a), s = sin(a); return mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0); }

// TPMS implicit field evaluation
float tpmsField(vec3 p, float type) {
    // Gyroid
    float g = dot(sin(p), cos(p.zxy));
    // Schwarz P
    float sp = cos(p.x) + cos(p.y) + cos(p.z);
    // Neovius
    float nv = 3.0 * (cos(p.x) + cos(p.y) + cos(p.z)) + 4.0 * cos(p.x) * cos(p.y) * cos(p.z);

    if (type < 1.0) {
        return mix(g, sp, smoothstep(0.0, 1.0, type));
    } else {
        return mix(sp, nv * 0.25, smoothstep(1.0, 2.0, type));
    }
}

// Signed distance to the TPMS shell
float mapSDF(vec3 p, float type, float freq, float thick, float wire) {
    vec3 sp = p * freq;
    float val = tpmsField(sp, type);
    // Approximate gradient norm
    float d = (abs(val) - thick) / (freq * 1.732);

    if (wire > 0.01) {
        vec3 grid = abs(fract(sp / PI) - 0.5);
        float strut = max(grid.x, max(grid.y, grid.z)) - (0.5 - 0.06 * wire);
        d = max(d, -strut / freq);
    }
    return d;
}

vec3 calcNormal(vec3 p, float type, float freq, float thick, float wire) {
    vec2 e = vec2(0.002, 0.0);
    return normalize(vec3(
        mapSDF(p + e.xyy, type, freq, thick, wire) - mapSDF(p - e.xyy, type, freq, thick, wire),
        mapSDF(p + e.yxy, type, freq, thick, wire) - mapSDF(p - e.yxy, type, freq, thick, wire),
        mapSDF(p + e.yyx, type, freq, thick, wire) - mapSDF(p - e.yyx, type, freq, thick, wire)
    ));
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 uv = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0);

    // Camera setup
    float fov = 1.2 / max(Zoom, 0.05);
    vec3 ro = vec3(0.0, 0.0, -2.5 + TIME * FlightSpeed);
    vec3 rd = normalize(vec3(uv * fov, 1.0));

    // Apply 3D rotation
    mat3 rot = rotZ(RotateZ) * rotY(RotateY) * rotX(RotateX);
    ro = rot * ro;
    rd = rot * rd;

    float t = 0.0;
    float maxDist = 18.0;
    float glow = 0.0;
    float hitDist = -1.0;
    vec3 hitP = vec3(0.0);

    // Sphere tracing
    for (int i = 0; i < 70; i++) {
        vec3 p = ro + rd * t;
        float d = mapSDF(p, SurfaceType, Frequency, WallThickness, WireframeMode);
        
        // Accumulate volumetric core glow around minimal surface boundaries
        glow += exp(-abs(d) * 4.0) * (0.015 * CoreGlow);

        if (d < 0.001) {
            hitDist = t;
            hitP = p;
            break;
        }
        t += max(d * 0.75, 0.008);
        if (t > maxDist) break;
    }

    vec3 col = vec3(0.0);

    if (hitDist > 0.0) {
        vec3 n = calcNormal(hitP, SurfaceType, Frequency, WallThickness, WireframeMode);
        vec3 viewDir = -rd;
        float fresnel = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);
        float diff = max(dot(n, normalize(vec3(0.5, 0.8, -0.3))), 0.0);

        vec3 surfaceCol = vec3(1.0);
        int cMode = clamp(int(floor(ColorMode + 0.5)), 0, 3);

        if (cMode == 0) {
            // Surface Normal Spectrum
            surfaceCol = hsv2rgb(vec3(fract(HueOffset + (n.x * 0.3 + n.y * 0.3 + n.z * 0.3)), Saturation, Brightness));
        } else if (cMode == 1) {
            // Distance Depth Gradient
            float depthHue = fract(HueOffset + hitDist * 0.08);
            surfaceCol = hsv2rgb(vec3(depthHue, Saturation, Brightness));
        } else if (cMode == 2) {
            // Iridescent Fresnel
            surfaceCol = hsv2rgb(vec3(fract(HueOffset + fresnel * 0.8), Saturation, Brightness * (1.0 + fresnel)));
        } else {
            // Monochrome Cyber Gold
            surfaceCol = vec3(1.0, 0.85, 0.4) * Brightness * (diff * 0.7 + 0.3);
        }

        col = surfaceCol * (0.3 + 0.7 * diff) + vec3(fresnel * 0.8 * Brightness);

        // Distance atmospheric fog
        float fog = smoothstep(0.0, maxDist, hitDist);
        col = mix(col, vec3(0.02, 0.01, 0.05), fog * 0.9);
    } else {
        // Background abyss
        col = vec3(0.02, 0.01, 0.04);
    }

    // Add volumetric core glow
    vec3 glowCol = hsv2rgb(vec3(fract(HueOffset + 0.5), Saturation, 1.0));
    col += glowCol * glow;

    gl_FragColor = vec4(col, 1.0);
}
