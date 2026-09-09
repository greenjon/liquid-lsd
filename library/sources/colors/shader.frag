/*{
    "DESCRIPTION": "Colors",
    "CREDIT": "Liquid LSD",
    "INPUTS": [
        { "NAME": "Style", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.0, "MAX": 2.0 },
        { "NAME": "Hue", "TYPE": "float", "DEFAULT": 0.0, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Sat", "TYPE": "float", "DEFAULT": 0.8, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Val", "TYPE": "float", "DEFAULT": 0.5, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Sweep", "TYPE": "float", "DEFAULT": 0.2, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Speed", "TYPE": "float", "DEFAULT": 0.2, "MIN": 0.0, "MAX": 1.0 },
        { "NAME": "Zoom", "TYPE": "float", "DEFAULT": 1.0, "MIN": 0.1, "MAX": 10.0 }
    ]
}*/

#version 330 core
in vec2 vTexCoord;
out vec4 fragColor;

uniform float Style;
uniform float Hue;
uniform float Sat;
uniform float Val;
uniform float Sweep;
uniform float Speed;
uniform float Zoom;

uniform float TIME;
uniform float uAlpha;

// HSV to RGB helper
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    int style = int(Style + 0.5);
    if (style <= 0) {
        fragColor = vec4(0.0, 0.0, 0.0, 0.0);
        return;
    }

    vec3 rgb = vec3(0.0);

    if (style == 1) { // Solid Color
        rgb = hsv2rgb(vec3(Hue, Sat, Val));
    } else { // Plasma
        float time = TIME * Speed * 2.0;
        
        // Center coordinates around (0,0) and scale by Zoom
        vec2 p = (vTexCoord - vec2(0.5)) * Zoom;
        
        float v1 = sin(p.x * 10.0 + time);
        float v2 = sin(10.0 * (p.x * sin(time / 2.0) + p.y * cos(time / 3.0)) + time);
        float cx = p.x + 0.5 * sin(time / 5.0);
        float cy = p.y + 0.5 * cos(time / 3.0);
        float v3 = sin(sqrt(100.0 * (cx * cx + cy * cy) + 1.0) + time);
        
        float val = (v1 + v2 + v3) / 3.0;
        float hue = fract(Hue + val * Sweep);
        rgb = hsv2rgb(vec3(hue, Sat, Val));
    }

    fragColor = vec4(rgb, uAlpha);
}
