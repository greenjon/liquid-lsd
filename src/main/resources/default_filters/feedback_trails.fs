/*{
    "DESCRIPTION": "Multi-pass persistent temporal decay and motion trails",
    "CREDIT": "Liquid LSD Engine",
    "CATEGORIES": ["Distortion", "Stylize", "Utility"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "trailDecay",
            "LABEL": "Trail Decay",
            "TYPE": "float",
            "DEFAULT": 0.85,
            "MIN": 0.0,
            "MAX": 0.99
        }
    ],
    "PASSES": [
        {
            "TARGET": "historyPass",
            "PERSISTENT": true
        },
        {}
    ]
}*/

void main() {
    if (PASSINDEX == 0) {
        vec4 fresh = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord);
        vec4 prev = IMG_NORM_PIXEL(historyPass, isf_FragNormCoord) * trailDecay;
        gl_FragColor = max(fresh, prev);
    } else {
        gl_FragColor = IMG_NORM_PIXEL(historyPass, isf_FragNormCoord);
    }
}
