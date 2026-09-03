#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform float uZoom;
uniform float uRotateZ;
uniform float uAspectRatio;

void main() {
    vec2 uv = vTexCoord - vec2(0.5);

    // Aspect-ratio correction on X for isotropic rotation in non-square viewports (e.g. 16:9)
    uv.x *= uAspectRatio;

    // Rotate Z (in-plane roll) matching 3D roll direction
    float cosRot = cos(uRotateZ);
    float sinRot = sin(uRotateZ);
    uv = vec2(
        uv.x * cosRot + uv.y * sinRot,
        -uv.x * sinRot + uv.y * cosRot
    );

    // Restore aspect ratio
    uv.x /= uAspectRatio;

    // Zoom factor (1.0 = 1:1 framing, >1.0 zooms in, <1.0 zooms out)
    uv /= max(0.001, uZoom);

    // Shift back to [0, 1] texture coordinates
    uv += vec2(0.5);

    // Out-of-bounds border handling (transparent black)
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        fragColor = vec4(0.0);
    } else {
        fragColor = texture(uTexture, uv);
    }
}
