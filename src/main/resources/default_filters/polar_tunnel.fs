/*{
    "DESCRIPTION": "Aspect-preserving infinite log-polar tunnel with spiral twist, azimuthal symmetry, and singularity depth fog",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Geometry", "Distortion", "Psychedelic"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "depth",
            "LABEL": "Depth",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.1,
            "MAX": 5.0,
            "IDENTITY": 1.0
        },
        {
            "NAME": "twist",
            "LABEL": "Twist",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -3.14159,
            "MAX": 3.14159,
            "IDENTITY": 0.0
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
            "NAME": "zoom",
            "LABEL": "Zoom",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.1,
            "MAX": 5.0,
            "IDENTITY": 1.0
        },
        {
            "NAME": "symmetry",
            "LABEL": "Symmetry",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 1.0,
            "MAX": 8.0,
            "IDENTITY": 1.0
        },
        {
            "NAME": "depthFog",
            "LABEL": "Depth Fog",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        }
    ]
}*/

// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

void main() {
    vec2 center = vec2(centerX, centerY);
    vec2 p = isf_FragNormCoord - center;

    // Aspect ratio correction guarantees circular tunnel geometry on any display aspect
    float aspect = RENDERSIZE.x / max(RENDERSIZE.y, 1.0);
    p.x *= aspect;

    float r = length(p);
    float a = atan(p.y, p.x); // [-PI, PI]

    // Symmetry repeats around the tunnel circumference
    float sym = max(floor(symmetry + 0.5), 1.0);
    float u = (a / 6.28318530718 + 0.5) * sym;

    // Perspective depth projection with singularity protection
    float safeZoom = max(zoom, 0.001);
    float scaledR = max(r / safeZoom, 0.0001);
    float v = (1.0 / scaledR) * depth + (u * twist);

    // Seamless mirror wrapping eliminates vertical/horizontal boundary seams
    vec2 tunnelCoord = abs(mod(vec2(u, v), 2.0) - 1.0);

    vec4 color = IMG_NORM_PIXEL(inputImage, tunnelCoord);

    // Singularity depth fog attenuates pixel swimming at infinite distance center
    if (depthFog > 0.001) {
        float fogFactor = smoothstep(0.0, 0.25 * depthFog, r);
        color.rgb *= fogFactor;
    }

    gl_FragColor = color;
}
