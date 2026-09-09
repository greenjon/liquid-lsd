/*{
    "DESCRIPTION": "Multi-pass bloom highlights and diffusion blur",
    "CREDIT": "Liquid LSD Engine",
    "CATEGORIES": ["Distortion", "Stylize", "Blur"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "bloomIntensity",
            "LABEL": "Intensity",
            "TYPE": "float",
            "DEFAULT": 0.8,
            "MIN": 0.0,
            "MAX": 3.0
        },
        {
            "NAME": "threshold",
            "LABEL": "Threshold",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0
        },
        {
            "NAME": "blurAmount",
            "LABEL": "Blur Radius",
            "TYPE": "float",
            "DEFAULT": 1.0,
            "MIN": 0.1,
            "MAX": 3.0
        }
    ],
    "PASSES": [
        {
            "TARGET": "threshPass",
            "WIDTH": "$WIDTH/2.0",
            "HEIGHT": "$HEIGHT/2.0"
        },
        {
            "TARGET": "blurHPass",
            "WIDTH": "$WIDTH/2.0",
            "HEIGHT": "$HEIGHT/2.0"
        },
        {
            "TARGET": "blurVPass",
            "WIDTH": "$WIDTH/2.0",
            "HEIGHT": "$HEIGHT/2.0"
        },
        {}
    ]
}*/

void main() {
    if (PASSINDEX == 0) {
        // Bright pass isolation
        vec4 color = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord);
        float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
        if (luminance >= threshold) {
            gl_FragColor = color;
        } else {
            gl_FragColor = vec4(0.0, 0.0, 0.0, color.a);
        }
    } else if (PASSINDEX == 1) {
        // Horizontal Gaussian blur
        vec2 texelSize = vec2(blurAmount / RENDERSIZE.x, 0.0);
        vec4 sum = vec4(0.0);
        sum += IMG_NORM_PIXEL(threshPass, isf_FragNormCoord - texelSize * 2.0) * 0.12;
        sum += IMG_NORM_PIXEL(threshPass, isf_FragNormCoord - texelSize) * 0.25;
        sum += IMG_NORM_PIXEL(threshPass, isf_FragNormCoord) * 0.38;
        sum += IMG_NORM_PIXEL(threshPass, isf_FragNormCoord + texelSize) * 0.25;
        sum += IMG_NORM_PIXEL(threshPass, isf_FragNormCoord + texelSize * 2.0) * 0.12;
        gl_FragColor = sum;
    } else if (PASSINDEX == 2) {
        // Vertical Gaussian blur
        vec2 texelSize = vec2(0.0, blurAmount / RENDERSIZE.y);
        vec4 sum = vec4(0.0);
        sum += IMG_NORM_PIXEL(blurHPass, isf_FragNormCoord - texelSize * 2.0) * 0.12;
        sum += IMG_NORM_PIXEL(blurHPass, isf_FragNormCoord - texelSize) * 0.25;
        sum += IMG_NORM_PIXEL(blurHPass, isf_FragNormCoord) * 0.38;
        sum += IMG_NORM_PIXEL(blurHPass, isf_FragNormCoord + texelSize) * 0.25;
        sum += IMG_NORM_PIXEL(blurHPass, isf_FragNormCoord + texelSize * 2.0) * 0.12;
        gl_FragColor = sum;
    } else {
        // Composite bloom pass with raw inputImage
        vec4 orig = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord);
        vec4 bloom = IMG_NORM_PIXEL(blurVPass, isf_FragNormCoord);
        gl_FragColor = orig + bloom * bloomIntensity;
    }
}
