/*{
    "DESCRIPTION": "Aspect-preserving multi-lattice pixelation (Square, Diamond, Hexagonal Honeycomb) with optional retro color depth quantization",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Stylize", "Glitch", "Retro"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "pixelSize",
            "LABEL": "Pixel Size",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 1.0,
            "MAX": 128.0,
            "IDENTITY": 1.0
        },
        {
            "NAME": "latticeMode",
            "LABEL": "Lattice Mode",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 2.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "colorDepth",
            "LABEL": "Color Steps",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 32.0,
            "IDENTITY": 0.0
        }
    ]
}*/

// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

// Hexagonal lattice center calculation (Hexagonal cell partitioning)
vec2 hexCenter(vec2 p, float s) {
    vec2 r = vec2(s * 1.7320508, s);
    vec2 h = r * 0.5;
    vec2 a = mod(p, r) - h;
    vec2 b = mod(p - h, r) - h;
    vec2 centerA = p - a;
    vec2 centerB = p - b;
    return dot(a, a) < dot(b, b) ? centerA : centerB;
}

void main() {
    vec2 uv = isf_FragNormCoord;

    if (pixelSize <= 1.001 && colorDepth < 1.5) {
        gl_FragColor = IMG_NORM_PIXEL(inputImage, uv);
        return;
    }

    vec2 fragPixelCoord = uv * RENDERSIZE;
    vec2 samplePixelCoord = fragPixelCoord;

    int mode = int(floor(latticeMode + 0.5));
    float size = max(pixelSize, 1.0);

    if (mode == 0) {
        // Mode 0: Square Aspect-Preserving Blocks
        samplePixelCoord = floor(fragPixelCoord / size) * size + (size * 0.5);
    } else if (mode == 1) {
        // Mode 1: Diamond 45-Degree Rhombus Lattice
        const mat2 rot45 = mat2(0.70710678, -0.70710678, 0.70710678, 0.70710678);
        const mat2 unrot45 = mat2(0.70710678, 0.70710678, -0.70710678, 0.70710678);

        vec2 rotP = rot45 * fragPixelCoord;
        vec2 cellP = floor(rotP / size) * size + (size * 0.5);
        samplePixelCoord = unrot45 * cellP;
    } else {
        // Mode 2: Hexagonal Honeycomb Crystal Lattice
        samplePixelCoord = hexCenter(fragPixelCoord, size * 0.57735);
    }

    vec2 sampleNormCoord = clamp(samplePixelCoord / RENDERSIZE, vec2(0.0), vec2(1.0));
    vec4 color = IMG_NORM_PIXEL(inputImage, sampleNormCoord);

    // Optional retro color quantization
    float steps = floor(colorDepth + 0.5);
    if (steps >= 2.0) {
        color.rgb = floor(color.rgb * (steps - 1.0) + 0.5) / (steps - 1.0);
    }

    gl_FragColor = color;
}
