float mapSDF(vec3 p, out float outEdge, out vec3 outColorCoord, out float outDepth) {
    vec3 N[10];
    N[0] = vec3(-0.577350, -0.577350,  0.577350);
    N[1] = vec3(-0.577350,  0.577350,  0.577350);
    N[2] = vec3( 0.577350, -0.577350,  0.577350);
    N[3] = vec3( 0.577350,  0.577350,  0.577350);
    N[4] = vec3( 0.000000,  0.356822, -0.934172);
    N[5] = vec3( 0.000000,  0.356822,  0.934172);
    N[6] = vec3(-0.356822,  0.934172,  0.000000);
    N[7] = vec3( 0.356822,  0.934172,  0.000000);
    N[8] = vec3( 0.934172,  0.000000, -0.356822);
    N[9] = vec3( 0.934172,  0.000000,  0.356822);

    // Evaluate all 20 oriented face half-spaces: dot(p, n_i) - h0
    const float h0 = 0.82;
    float planeDist[20];
    for (int i = 0; i < 10; i++) {
        float val = dot(p, N[i]);
        planeDist[2 * i]     =  val - h0;
        planeDist[2 * i + 1] = -val - h0;
    }

    // Sort distances (v0 >= v1 >= v2 >= v3 >= v4)
    float v0 = -1e5, v1 = -1e5, v2 = -1e5, v3 = -1e5, v4 = -1e5;
    for (int i = 0; i < 20; i++) {
        float x = planeDist[i];
        if (x > v0)      { v4 = v3; v3 = v2; v2 = v1; v1 = v0; v0 = x; }
        else if (x > v1) { v4 = v3; v3 = v2; v2 = v1; v1 = x; }
        else if (x > v2) { v4 = v3; v3 = v2; v2 = x; }
        else if (x > v3) { v4 = v3; v3 = x; }
        else if (x > v4) { v4 = x; }
    }

    const float RD  = h0 * 1.41421356;
    const float Rg1 = h0 * 1.58990000;
    const float Rg2 = h0 * 1.90211303;

    float totalSdf;

    if (uControlX < 0.0) {
        // Trunk: A (v0) -> B (v1) -> D (v2)
        float tx = (uControlX + 1.0) * 2.0;
        if (tx <= 1.0) {
            totalSdf = mix(v0, v1, tx);
        } else {
            totalSdf = mix(v1, v2, tx - 1.0);
        }
    } else {
        float tx = clamp(uControlX, 0.0, 1.0);
        float ty = clamp(uControlY, 0.0, 1.0);

        // 1. Base D (Great Dodecahedron)
        float distD = v2;

        // 2. Corner g2 (Great Icosahedron: 12 pentagrammic vertex spikes)
        vec3 vtx[6];
        const float vtxNorm = 1.0 / sqrt(1.0 + PHI * PHI);
        vtx[0] = vec3(0.0,  1.0,  PHI) * vtxNorm;
        vtx[1] = vec3(0.0,  1.0, -PHI) * vtxNorm;
        vtx[2] = vec3( 1.0,  PHI, 0.0) * vtxNorm;
        vtx[3] = vec3(-1.0,  PHI, 0.0) * vtxNorm;
        vtx[4] = vec3( PHI, 0.0,  1.0) * vtxNorm;
        vtx[5] = vec3(-PHI, 0.0,  1.0) * vtxNorm;

        float distG2 = distD;
        float capG2 = mix(RD, Rg2, ty);
        for (int k = 0; k < 6; k++) {
            for (int s = -1; s <= 1; s += 2) {
                vec3 u_k = vtx[k] * float(s);
                float proj = dot(p, u_k);
                if (proj > 0.0) {
                    // Accumulate only the 5 face planes adjacent to this vertex
                    float cone5 = -1e5;
                    for (int i = 0; i < 20; i++) {
                        vec3 n = (i % 2 == 0) ? N[i / 2] : -N[i / 2];
                        if (dot(n, u_k) > 0.75) {
                            cone5 = max(cone5, planeDist[i]);
                        }
                    }
                    float spike = max(cone5, proj - capG2);
                    distG2 = min(distG2, spike);
                }
            }
        }

        // 3. Corner g1 (20 triangular face caps over D)
        float distG1 = distD;
        float capG1 = mix(RD, Rg1, tx);
        for (int i = 0; i < 10; i++) {
            for (int s = -1; s <= 1; s += 2) {
                vec3 u_m = N[i] * float(s);
                float proj = dot(p, u_m);
                if (proj > 0.0) {
                    // Accumulate the 3 neighbor face planes forming the triangle cap
                    float cone3 = -1e5;
                    for (int j = 0; j < 20; j++) {
                        vec3 n = (j % 2 == 0) ? N[j / 2] : -N[j / 2];
                        float dp = dot(n, u_m);
                        if (dp > 0.1 && dp < 0.9) {
                            cone3 = max(cone3, planeDist[j]);
                        }
                    }
                    float spike = max(cone3, proj - capG1);
                    distG1 = min(distG1, spike);
                }
            }
        }

        // 4. Corner H (60 Echidnahedron spikes)
        float distH = v4;

        // Bilinear blend across the independent branches
        float s0 = mix(distD,  distG1, tx);
        float s1 = mix(distG2, distH,  tx);
        totalSdf = mix(s0, s1, ty);
    }

    outEdge = v0 - v1;
    outColorCoord = vec3(v0, v1, v2);
    outDepth = length(p);

    return totalSdf;
}
