/*{
    "DESCRIPTION": "Multi-axis polyhedral kaleidoscope with aspect-corrected polar folding and seamless reflection",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Geometry", "Distortion", "Psychedelic"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "segments",
            "LABEL": "Segments",
            "TYPE": "float",
            "DEFAULT": 6.0,
            "MIN": 2.0,
            "MAX": 24.0,
            "STEP": 1
        },
        {
            "NAME": "rotation",
            "LABEL": "Rotation",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -3.14159,
            "MAX": 3.14159,
            "IDENTITY": 0.0
        },
        {
            "NAME": "zoom",
            "LABEL": "Zoom",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.5
        },
        {
            "NAME": "centerX",
            "LABEL": "Center X",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.5
        },
        {
            "NAME": "centerY",
            "LABEL": "Center Y",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.5
        },
        {
            "NAME": "originOffset",
            "LABEL": "Origin Offset",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -1.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        }
    ]
}*/

// Inputs are normalized to 0..1 / -1..1; these remap them to the shader's working units.
#define zoom_ (zoom<0.5?pow(0.1,1.0-2.0*zoom):pow(5.0,2.0*zoom-1.0))


// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

void main() {
    vec2 center = vec2(centerX, centerY);
    vec2 p = isf_FragNormCoord - center;

    // Correct aspect ratio so circular sectors remain perfectly proportional
    float aspect = RENDERSIZE.x / max(RENDERSIZE.y, 1.0);
    p.x *= aspect;

    float r = length(p);
    float a = atan(p.y, p.x) + rotation;

    // Multi-axis sector folding
    float seg = max(floor(segments + 0.5), 2.0);
    float segAngle = 6.28318530718 / seg;
    float halfSeg = segAngle * 0.5;

    // Continuous triangle-wave angular reflection to avoid seam artifacts
    float fold = abs(mod(a, segAngle) - halfSeg);

    // Apply zoom_ and origin displacement with zero-division safeguard
    float safeZoom = max(zoom_, 0.001);
    float mappedR = (r / safeZoom) + originOffset;

    // Reconstruct Cartesian coordinates
    vec2 foldedCoord = vec2(cos(fold), sin(fold)) * mappedR;
    foldedCoord.x /= aspect;
    foldedCoord += center;

    // Continuous boundary reflection (mirror wrap) prevents texture clamping seams
    vec2 sampleCoord = abs(mod(foldedCoord, 2.0) - 1.0);

    gl_FragColor = IMG_NORM_PIXEL(inputImage, sampleCoord);
}
