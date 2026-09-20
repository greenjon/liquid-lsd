/*{
    "DESCRIPTION": "Aspect-preserving spherical lens deformation with smooth Hermite cubic falloff (Bulge / Magnify vs Pinch / Funnel)",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Distortion", "Optics", "Geometry"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "amount",
            "LABEL": "Amount",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -1.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "radius",
            "LABEL": "Radius",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.05,
            "MAX": 1.5,
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
        }
    ]
}*/

// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

void main() {
    vec2 uv = isf_FragNormCoord;

    if (abs(amount) <= 0.0005) {
        gl_FragColor = IMG_NORM_PIXEL(inputImage, uv);
        return;
    }

    float aspect = RENDERSIZE.x / max(RENDERSIZE.y, 1.0);
    vec2 center = vec2(centerX, centerY);
    vec2 p = uv - center;
    p.x *= aspect;

    float r = length(p);
    float safeRad = max(radius, 0.01);

    if (r < safeRad) {
        // Normalized distance within lens [0..1]
        float t = r / safeRad;
        // Cubic Hermite smoothstep guarantees zero tangent at border, eliminating circular edge creases
        float falloff = smoothstep(1.0, 0.0, t);

        float factor;
        if (amount >= 0.0) {
            // Bulge: pull source coordinate inward toward center
            factor = 1.0 - (amount * 0.6 * falloff * falloff);
        } else {
            // Pinch: push source coordinate outward away from center
            factor = 1.0 + (-amount * 1.5 * falloff * falloff);
        }

        vec2 warpedP = p * factor;
        warpedP.x /= aspect;
        uv = center + warpedP;
    }

    vec2 sampleCoord = clamp(uv, vec2(0.0), vec2(1.0));
    gl_FragColor = IMG_NORM_PIXEL(inputImage, sampleCoord);
}
