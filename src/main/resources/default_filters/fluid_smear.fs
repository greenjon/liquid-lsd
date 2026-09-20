/*{
    "DESCRIPTION": "Persistent 2-pass fluid curl-noise advection simulating organic liquid melting, ink diffusion, and viscous paint smearing",
    "CREDIT": "Liquid LSD (MIT License)",
    "ISFVSN": "2.0",
    "CATEGORIES": ["Organic", "Feedback", "Psychedelic"],
    "INPUTS": [
        {
            "NAME": "inputImage",
            "TYPE": "image"
        },
        {
            "NAME": "smearAmount",
            "LABEL": "Smear",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": 0.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        },
        {
            "NAME": "curlScale",
            "LABEL": "Curl Scale",
            "TYPE": "float",
            "DEFAULT": 4.0,
            "MIN": 0.5,
            "MAX": 20.0,
            "IDENTITY": 4.0
        },
        {
            "NAME": "flowSpeed",
            "LABEL": "Flow Speed",
            "TYPE": "float",
            "DEFAULT": 0.5,
            "MIN": -2.0,
            "MAX": 2.0,
            "IDENTITY": 0.5
        },
        {
            "NAME": "decay",
            "LABEL": "Decay",
            "TYPE": "float",
            "DEFAULT": 0.96,
            "MIN": 0.8,
            "MAX": 1.0,
            "IDENTITY": 0.96
        },
        {
            "NAME": "gravity",
            "LABEL": "Gravity Drift",
            "TYPE": "float",
            "DEFAULT": 0.0,
            "MIN": -1.0,
            "MAX": 1.0,
            "IDENTITY": 0.0
        }
    ],
    "PASSES": [
        {
            "TARGET": "fluidPass",
            "PERSISTENT": true,
            "FLOAT": true
        },
        {}
    ]
}*/

// Liquid LSD Engine - Cleanroom ISF Shader
// License: MIT License

// 2D Simplex/Perlin-style hash gradient
vec2 hash2(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

// 2D Value Noise
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(
        mix(dot(hash2(i + vec2(0.0, 0.0)), f - vec2(0.0, 0.0)),
            dot(hash2(i + vec2(1.0, 0.0)), f - vec2(1.0, 0.0)), u.x),
        mix(dot(hash2(i + vec2(0.0, 1.0)), f - vec2(0.0, 1.0)),
            dot(hash2(i + vec2(1.0, 1.0)), f - vec2(1.0, 1.0)), u.x), u.y
    );
}

// 2D Incompressible Curl Vector Field (curl = (dPsi/dy, -dPsi/dx))
vec2 curlNoise(vec2 p) {
    const float eps = 0.01;
    float n1 = noise(p + vec2(0.0, eps));
    float n2 = noise(p - vec2(0.0, eps));
    float n3 = noise(p + vec2(eps, 0.0));
    float n4 = noise(p - vec2(eps, 0.0));

    float dy = (n1 - n2) / (2.0 * eps);
    float dx = (n3 - n4) / (2.0 * eps);

    return vec2(dy, -dx);
}

void main() {
    if (PASSINDEX == 0) {
        vec2 uv = isf_FragNormCoord;
        vec4 live = IMG_NORM_PIXEL(inputImage, uv);

        if (smearAmount <= 0.0001 || FRAMEINDEX < 2) {
            gl_FragColor = live;
            return;
        }

        float aspect = RENDERSIZE.x / max(RENDERSIZE.y, 1.0);
        vec2 noiseCoord = uv * vec2(aspect, 1.0) * curlScale + (TIME * flowSpeed * 0.2);

        // Calculate divergence-free curl flow velocity
        vec2 velocity = curlNoise(noiseCoord) * (smearAmount * 0.015);
        velocity.y -= gravity * (smearAmount * 0.01);
        velocity.x /= aspect;

        // Sample previous fluid state along back-traced streamline with mirror wrapping
        vec2 backtraced = abs(mod(uv - velocity, 2.0) - 1.0);
        vec4 advected = IMG_NORM_PIXEL(fluidPass, backtraced) * decay;

        // Advective blend with live frame
        float blendFactor = clamp(1.0 - smearAmount * 0.95, 0.05, 1.0);
        gl_FragColor = mix(advected, live, blendFactor);
    } else {
        gl_FragColor = IMG_THIS_PIXEL(fluidPass);
    }
}
