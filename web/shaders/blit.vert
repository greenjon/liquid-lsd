#version 300 es
precision highp float;

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;

out vec2 vTexCoord;

uniform float uZoom;
uniform float uRotateZ;
uniform float uAspectRatio;

void main() {
    float zoom = (uZoom > 0.0001) ? uZoom : 1.0;
    float rotZ = uRotateZ;
    float aspect = (uAspectRatio > 0.0001) ? uAspectRatio : 1.0;

    vec2 uv = aTexCoord - vec2(0.5);
    uv.x *= aspect;

    float cosRot = cos(rotZ);
    float sinRot = sin(rotZ);
    uv = vec2(
        uv.x * cosRot + uv.y * sinRot,
        -uv.x * sinRot + uv.y * cosRot
    );

    uv.x /= aspect;
    uv /= zoom;
    uv += vec2(0.5);

    vTexCoord = uv;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
