/*{
    "DESCRIPTION": "Raytraced 3D chrome mirror sphere reflecting the visual environment with surface normals, Fresnel rim glow, and chromatic curvature",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Geometry", "Optics", "Psychedelic"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "sphereRadius",
            "LABEL": "Sphere Radius",
            "TYPE": "float",
            "DEFAULT": 0.45,
            "MIN": 0.0,
            "MAX": 1.0,
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
            "NAME": "fresnelGlow",
            "LABEL": "Fresnel Rim",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.5
        },
        {
            "NAME": "chromaFringe",
            "LABEL": "Chroma Fringe",
            "TYPE": "float",
            "DEFAULT": 0.02,
            "MIN": 0.0,
            "MAX": 0.1,
            "IDENTITY": 0.0
        },
        {
            "NAME": "backgroundMix",
            "LABEL": "Background Dim",
            "TYPE": "float",
            "DEFAULT": 0.25,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 1.0
        }
    ]
}*/

// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

void main() {
    vec2 uv = isf_FragNormCoord;
    vec4 background = IMG_NORM_PIXEL(inputImage, uv);

    if (sphereRadius <= 0.001) {
        gl_FragColor = background;
        return;
    }

    float aspect = RENDERSIZE.x / max(RENDERSIZE.y, 1.0);
    vec2 center = vec2(centerX, centerY);
    vec2 p = uv - center;
    p.x *= aspect;

    float r2 = dot(p, p);
    float rad = max(sphereRadius, 0.001);
    float rad2 = rad * rad;

    if (r2 <= rad2) {
        // Analytical ray-sphere intersection
        float z = sqrt(rad2 - r2);
        vec3 normal = vec3(p.x, p.y, z) / rad;

        // View vector pointing toward the screen
        vec3 viewDir = vec3(0.0, 0.0, -1.0);
        vec3 refl = reflect(viewDir, normal);

        // Map reflected vector to seamless UV coordinates
        vec2 reflCoord = refl.xy * 0.5 + 0.5;

        // Chromatic dispersion across reflection
        vec2 rCoord = abs(mod(reflCoord + refl.xy * chromaFringe, 2.0) - 1.0);
        vec2 gCoord = abs(mod(reflCoord, 2.0) - 1.0);
        vec2 bCoord = abs(mod(reflCoord - refl.xy * chromaFringe, 2.0) - 1.0);

        float r = IMG_NORM_PIXEL(inputImage, rCoord).r;
        vec4 gPixel = IMG_NORM_PIXEL(inputImage, gCoord);
        float b = IMG_NORM_PIXEL(inputImage, bCoord).b;
        vec3 sphereRgb = vec3(r, gPixel.g, b);

        // Fresnel edge grazing illumination (Schlick's approximation)
        float fresnel = pow(1.0 - max(normal.z, 0.0), 3.0) * fresnelGlow;
        sphereRgb += vec3(fresnel * 0.8, fresnel * 0.9, fresnel);

        // Specular glint highlight
        vec3 lightDir = normalize(vec3(0.5, 0.7, 1.0));
        float spec = pow(max(dot(refl, lightDir), 0.0), 24.0);
        sphereRgb += vec3(spec);

        // Anti-aliased boundary edge blending
        float edgeBlend = smoothstep(rad2, rad2 - (rad * 0.01), r2);
        vec3 finalRgb = mix(background.rgb * backgroundMix, clamp(sphereRgb, 0.0, 1.0), edgeBlend);
        gl_FragColor = vec4(finalRgb, 1.0);
    } else {
        // Outside sphere
        gl_FragColor = vec4(background.rgb * backgroundMix, background.a);
    }
}
