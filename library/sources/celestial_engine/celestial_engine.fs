/*
{
    "DESCRIPTION": "Multi-symmetry sacred geometry and op-art generator combining Flower of Life folding, concentric harmonic interference rings, torus knot stereographic projections, and moiré fringes.",
    "CREDIT": "Liquid LSD",
    "ISFVSN": "2.0",
    "CATEGORIES": [
        "Generator",
        "Geometric",
        "Optical"
    ],
    "INPUTS": [
        { "NAME": "Symmetries", "LABEL": "Symmetries", "TYPE": "float", "DEFAULT": 6.0, "MIN": 3.0, "MAX": 24.0 },
        { "NAME": "RingDensity", "LABEL": "Ring Density", "TYPE": "float", "DEFAULT": 12.0, "MIN": 2.0, "MAX": 40.0 },
        { "NAME": "PhaseTwist", "LABEL": "Phase Twist", "TYPE": "float", "DEFAULT": 0.5, "MIN": -2.0, "MAX": 2.0 },
        { "NAME": "MoireStrength", "LABEL": "Moiré Strength", "TYPE": "float", "DEFAULT": 0.6, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "FlowerFold", "LABEL": "Flower of Life Fold", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "PulseWave", "LABEL": "Pulse Wave", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 2.0 },
        { "NAME": "Speed", "LABEL": "Speed", "TYPE": "float", "DEFAULT": 0.3, "MIN": -2.0, "MAX": 2.0 },
        { "NAME": "LineWidth", "LABEL": "Line Width", "TYPE": "float", "DEFAULT": 0.35, "MIN": 0.05, "MAX": 1.0 },
        { "NAME": "Glow", "LABEL": "Glow", "TYPE": "float", "DEFAULT": 1.5, "MIN": 0.2, "MAX": 4.0 },
        { "NAME": "ColorMode", "LABEL": "Color Mode", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 3.0 },
        { "NAME": "HueOffset", "LABEL": "Hue Offset", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "HueSweep", "LABEL": "Hue Sweep", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 2.0 },
        { "NAME": "Scale", "LABEL": "Scale", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.1, "MAX": 3.0 }
    ]
}
*/

const float PI = 3.14159265358979323846;
const float TWO_PI = 6.28318530717958647692;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    float aspect = RENDERSIZE.x / RENDERSIZE.y;
    vec2 p = (isf_FragNormCoord - 0.5) * vec2(aspect, 1.0) / max(Scale, 0.05);

    float r = length(p);
    float angle = atan(p.y, p.x);

    float t = TIME * Speed;
    float sym = max(floor(Symmetries + 0.5), 2.0);
    float sector = TWO_PI / sym;

    // Phase twist rotation along radius
    float twistedAngle = angle + r * PhaseTwist + t * 0.2;

    // Fold angle into fundamental rotational wedge
    float foldedAngle = abs(mod(twistedAngle, sector) - sector * 0.5);

    // Reconstruct folded 2D coordinate
    vec2 pFold = vec2(cos(foldedAngle), sin(foldedAngle)) * r;

    // 1. Concentric harmonic waves & moiré interference
    float wave1 = sin(r * RingDensity - t * 3.0);
    float wave2 = cos(r * RingDensity * (1.0 + MoireStrength * 0.5) + foldedAngle * sym);
    float moireField = abs(wave1 * wave2);

    // 2. Flower of Life geometric circles
    // Center of adjacent petal ring
    float petalDist = 0.5;
    vec2 cPetal = vec2(petalDist, 0.0);
    float dPetal = abs(length(pFold - cPetal) - petalDist);
    float dPetal2 = abs(length(pFold - vec2(petalDist * cos(sector), petalDist * sin(sector))) - petalDist);
    float flowerField = min(dPetal, dPetal2);

    // 3. Audio pulse shockwave ring
    float pulseDist = 100.0;
    if (PulseWave > 0.01) {
        float pulseR = fract(t * 0.5) * 1.5;
        pulseDist = abs(r - pulseR);
    }

    // Blend components
    float geometricDist = mix(moireField * 0.1, flowerField, FlowerFold);
    geometricDist = min(geometricDist, pulseDist);

    // Gaussian glow intensity
    float lineThickness = LineWidth * 0.02;
    float intensity = exp(-pow(geometricDist / lineThickness, 1.5)) * Glow;
    intensity += (0.05 / (geometricDist + 0.02)) * (Glow * 0.3);

    // Color mapping
    int cMode = clamp(int(floor(ColorMode + 0.5)), 0, 3);
    float colorCoord = fract(HueOffset + r * HueSweep + foldedAngle / TWO_PI);

    vec3 col = vec3(0.0);
    if (cMode == 0) {
        // Sacred Gold / Emerald / Violet
        vec3 g1 = vec3(1.0, 0.8, 0.2); // Gold
        vec3 g2 = vec3(0.6, 0.1, 0.9); // Violet
        vec3 g3 = vec3(0.1, 0.9, 0.5); // Emerald
        col = mix(g1, g2, sin(colorCoord * TWO_PI) * 0.5 + 0.5);
        col = mix(col, g3, cos(colorCoord * TWO_PI * 2.0) * 0.5 + 0.5);
    } else if (cMode == 1) {
        // Neon Laser: Cyan / Hot Pink
        vec3 laserCyan = vec3(0.0, 1.0, 0.9);
        vec3 laserPink = vec3(1.0, 0.05, 0.6);
        col = mix(laserCyan, laserPink, sin(colorCoord * TWO_PI) * 0.5 + 0.5);
    } else if (cMode == 2) {
        // Prismatic Spectral Rainbow
        col = hsv2rgb(vec3(colorCoord, 0.85, 1.0));
    } else {
        // Monochrome Op-Art Silver
        col = vec3(1.0) * (0.8 + 0.2 * sin(colorCoord * TWO_PI * 4.0));
    }

    col *= intensity;

    // Center focal star halo
    float centerGlow = exp(-r * 4.0) * (Glow * 0.5);
    col += vec3(centerGlow) * hsv2rgb(vec3(HueOffset, 0.5, 1.0));

    gl_FragColor = vec4(col, 1.0);
}
