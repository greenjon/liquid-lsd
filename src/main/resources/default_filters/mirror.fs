/*{
    "DESCRIPTION": "Coordinate space folding and symmetry reflection",
    "CREDIT": "Liquid LSD Engine",
    "CATEGORIES": ["Geometry", "Distortion"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "symmetryMode",
            "LABEL": "Symmetry Mode",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 2.0
        },
        {
            "NAME": "originX",
            "LABEL": "Origin X",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0
        },
        {
            "NAME": "originY",
            "LABEL": "Origin Y",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0
        }
    ]
}*/

void main() {
    vec2 uv = isf_FragNormCoord;
    vec2 origin = vec2(originX, originY);

    int mode = int(floor(symmetryMode + 0.5));

    if (mode == 0) {
        // Quad Mirror (4-way horizontal & vertical)
        uv = abs(uv - origin) + origin;
    } else if (mode == 1) {
        // Diagonal Flip
        vec2 p = uv - origin;
        if (p.x < p.y) {
            float temp = p.x;
            p.x = p.y;
            p.y = temp;
        }
        uv = p + origin;
    } else {
        // Horizontal Mirror
        uv.x = abs(uv.x - origin.x) + origin.x;
    }

    gl_FragColor = IMG_NORM_PIXEL(inputImage, uv);
}
