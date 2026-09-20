/*{
    "DESCRIPTION": "Multi-mode rhythmic strobe and persistent freeze gate with beat flash modes and duty cycle modulation",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Glitch", "Utility", "Rhythm"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "rate",
            "LABEL": "Strobe Rate",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 30.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "freezeHold",
            "LABEL": "Freeze Hold",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "strobeMode",
            "LABEL": "Flash Mode",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 3.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "dutyCycle",
            "LABEL": "Duty Cycle",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.05,
            "MAX": 0.95,
            "IDENTITY": 0.5
        }
    ],
    "PASSES": [
        {
            "TARGET": "freezePass",
            "PERSISTENT": true
        },
        {}
    ]
}*/

// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

void main() {
    if (PASSINDEX == 0) {
        // Persistent buffer pass: captures snapshot when freezeHold is disabled, holds when active
        if (freezeHold > 0.5 && FRAMEINDEX > 1) {
            gl_FragColor = IMG_THIS_PIXEL(freezePass);
        } else {
            gl_FragColor = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord);
        }
    } else {
        // Output stage
        vec4 color = (freezeHold > 0.5 && FRAMEINDEX > 1) 
            ? IMG_THIS_PIXEL(freezePass) 
            : IMG_NORM_PIXEL(inputImage, isf_FragNormCoord);

        if (rate > 0.01) {
            float phase = fract(TIME * rate);
            bool isFlash = (phase < dutyCycle);

            if (isFlash) {
                int mode = int(floor(strobeMode + 0.5));
                if (mode == 0) {
                    // Black Gate (Dip to black)
                    color.rgb = vec3(0.0);
                } else if (mode == 1) {
                    // White Flash (High key burst)
                    color.rgb = vec3(1.0);
                } else if (mode == 2) {
                    // Color Inversion / Negative
                    color.rgb = vec3(1.0) - color.rgb;
                } else {
                    // Spectral Hue Inversion (RGB swap)
                    color.rgb = color.bgr;
                }
            }
        }

        gl_FragColor = color;
    }
}
