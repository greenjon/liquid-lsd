#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform float uControlX;       //  0.0 (Convex core) -> 1.0 (Kepler-Poinsot spikes)
uniform float uStellationSpike;
uniform float uBlockerSize;       //  0.0 (Icosahedron 20) -> 0.5 (Crystal 60) -> 1.0 (Dodecahedron 12)
uniform float uColorMethod;    //  0 Chamber | 1 Depth | 2 Normal
uniform float uHueOffset;
uniform float uSaturation;
uniform float uBrightness;
uniform float uOpacity;
uniform float uEdgeThickness;
uniform float uEdgeBrightness;
uniform float uZoom;
uniform float uRotateX;
uniform float uRotateY;
uniform float uRotateZ;



uniform float uSupportH;     // Base radius (default to your old h0: 0.82)
uniform float uAlpha;
uniform vec2  uResolution;
uniform float uTime;

const float PI  = 3.14159265358979323846;
const float PHI = 1.61803398874989484820;

mat3 rotateX(float a){float c=cos(a),s=sin(a);return mat3(1,0,0,0,c,-s,0,s,c);}
mat3 rotateY(float a){float c=cos(a),s=sin(a);return mat3(c,0,s,0,1,0,-s,0,c);}
mat3 rotateZ(float a){float c=cos(a),s=sin(a);return mat3(c,-s,0,s,c,0,0,0,1);}

vec3 palette(float t,vec3 a,vec3 b,vec3 c,vec3 d){return a+b*cos(6.2831853*(c*t+d));}
vec3 adjustColor(vec3 col,float sat,float bright){
    float g=dot(col,vec3(0.299,0.587,0.114));
    return max(vec3(0.0),mix(vec3(g),col,sat)*bright);
}

// =============================================================================
// Icosahedral Continuous Stellation Signed Distance Field (k-th max)
// =============================================================================

const float nc_PHI = 1.618033988749895;
const vec3 n0 = vec3(1.0, 0.0, 0.0);
const vec3 n1 = vec3(-nc_PHI/2.0, -0.5, 0.5/nc_PHI);
const vec3 n2 = vec3(0.0, 1.0, 0.0);

vec3 foldSpace(vec3 p) {
    for(int i = 0; i < 16; i++) {
        p -= 2.0 * min(0.0, dot(p, n0)) * n0;
        p -= 2.0 * min(0.0, dot(p, n1)) * n1;
        p -= 2.0 * min(0.0, dot(p, n2)) * n2;
    }
    return p;
}

vec3 slerp(vec3 p0, vec3 p1, float t) {
    float dotp = clamp(dot(p0, p1), -1.0, 1.0);
    float theta = acos(dotp);
    float sinTheta = sin(theta);
    if (sinTheta < 0.001) return normalize(mix(p0, p1, t));
    float w0 = sin((1.0 - t) * theta) / sinTheta;
    float w1 = sin(t * theta) / sinTheta;
    return normalize(p0 * w0 + p1 * w1);
}

float mapSDF(vec3 p, out float outEdge, out vec3 outColorCoord, out float outDepth) {
    vec3 pFolded = foldSpace(p);

    vec3 pole3 = normalize(vec3(1.0, 0.0, nc_PHI + 1.0));
    vec3 pole5 = normalize(vec3(0.0, 1.0, nc_PHI));
    vec3 chamberCenter = normalize(pole3 + pole5);

    // Map uControlX to a safe ping-pong phase between -1.0 and 2.0
    // This prevents the planes from flipping inside-out and breaking the SDF volume.
    float phaseOffset = asin(-1.0 / 3.0);
    float safeX = 1.5 * sin(uControlX + phaseOffset) + 0.5;

    // Continuous sweep around the symmetry group's safe hemisphere
    vec3 corePole = slerp(pole3, pole5, safeX);

    // Reflect across the non-shared mirror planes to find adjacent faces
    vec3 adj1 = corePole - 2.0 * dot(corePole, n0) * n0;
    vec3 adj2 = corePole - 2.0 * dot(corePole, n2) * n2;

    // Tilt face normals toward adjacent faces to extrude pyramid spikes
    vec3 spikePole1 = normalize(mix(corePole, adj1, uStellationSpike));
    vec3 spikePole2 = normalize(mix(corePole, adj2, uStellationSpike));

    // Dynamic Support H: scale the distance offset by the dot product with the chamber center.
    // This mathematically guarantees the object stays EXACTLY the same size on screen, 
    // no matter how deeply the planes tilt!
    float h1 = uSupportH * max(0.1, dot(spikePole1, chamberCenter));
    float h2 = uSupportH * max(0.1, dot(spikePole2, chamberCenter));

    // The spiked shape is the intersection of these tilted planes
    float dSpike1 = dot(pFolded, spikePole1) - h1;
    float dSpike2 = dot(pFolded, spikePole2) - h2;
    float stellationSpike = max(dSpike1, dSpike2);

    // The blocker chops off the tips. 
    // We use the "dual-like" face one phase step ahead.
    vec3 blockerPole = slerp(pole3, pole5, safeX + 1.0);
    float hBlocker = uSupportH * max(0.1, dot(blockerPole, chamberCenter));
    float blockerRadius = hBlocker + (1.0 - uBlockerSize) * 1.5;
    float blocker = dot(pFolded, blockerPole) - blockerRadius;

    // CSG Intersection: max() acts as boolean AND.
    float finalShape = max(stellationSpike, blocker);

    // Calculate a nice dynamic edge based on the difference between the two spike planes
    outEdge = abs(dSpike1 - dSpike2);
    outColorCoord = pFolded;
    outDepth = length(p);

    return finalShape;
}
vec3 getNormal(vec3 p){
    float dE,dD; vec3 dC;
    vec2 e=vec2(0.001,0.0);
    float d=mapSDF(p,dE,dC,dD);
    return normalize(vec3(
        mapSDF(p+e.xyy,dE,dC,dD)-d,
        mapSDF(p+e.yxy,dE,dC,dD)-d,
        mapSDF(p+e.yyx,dE,dC,dD)-d));
}

void main(){
    vec2 uv=vTexCoord*2.0-1.0;
    uv.x*=uResolution.x/uResolution.y;

    float zoom=clamp(uZoom,0.1,5.0);
    vec3 ro=vec3(0.0,0.0,-3.8/zoom);
    vec3 rd=normalize(vec3(uv,1.4));
    mat3 rot=rotateZ(uRotateZ)*rotateY(uRotateY)*rotateX(uRotateX);
    ro=rot*ro; rd=rot*rd;
    vec3 lD = rot * vec3(0.0, 0.0, -1.0);

    vec3 palA=vec3(0.5),palB=vec3(0.5),palC=vec3(1.0),palD=vec3(0.0,0.33,0.67);

    vec4  acc=vec4(0.0);
    float t=max(0.0,(3.8-3.0)/zoom);
    float opacity=clamp(uOpacity,0.0,1.0);
    float edgeThick=clamp(uEdgeThickness,0.0,0.15);

    for(int i=0;i<120;i++){
        vec3  p=ro+rd*t;
        float edge,depth; vec3 cC;
        float dist=mapSDF(p,edge,cC,depth);

        if(dist<0.003){
            vec3  n=getNormal(p);
            float diff=max(0.25,dot(n,lD));
            float spec=pow(max(0.0,dot(reflect(-lD,n),-rd)),16.0)*0.4;

            vec3 col;
            if(uColorMethod<0.66){
                float cc = cC.x * 3.0 + cC.y * 2.0 + cC.z * 1.0;
                col=palette(fract(cc*0.5+uHueOffset),palA,palB,palC,palD);
            }else if(uColorMethod<1.33){
                col=palette(clamp((depth-0.7)/1.6,0.0,1.0)+uHueOffset,palA,palB,palC,palD);
            }else{
                vec3 nt=abs(n);
                col=palette(fract((nt.x+nt.y*2.0+nt.z*3.0)*0.33+uHueOffset),palA,palB,palC,palD);
            }
            col=adjustColor(col,uSaturation,uBrightness)*diff+vec3(spec);

            if(edgeThick>0.001){
                float ef=1.0-smoothstep(0.0,edgeThick,edge);
                col=mix(col,adjustColor(vec3(1.0)-col*0.5,uSaturation,uBrightness*uEdgeBrightness),ef);
            }

            float w=(1.0-acc.a)*opacity;
            acc.rgb+=col*w; acc.a+=w;
            if(acc.a>0.96) break;
            t+=max(0.006/zoom,dist+0.006/zoom);
        }else{
            // Lower step multiplier prevents overstepping on acute needle ridges
            t+=max(0.003/zoom,dist*0.5);
        }
        if(t>(3.8+3.5)/zoom) break;
    }

    if(acc.a<0.9){
        float gE,gD; vec3 gC;
        float gd=mapSDF(ro+rd*(3.8/zoom),gE,gC,gD);
        acc.rgb+=palette(uHueOffset,palA,palB,palC,palD)*exp(-max(0.0,gd)*4.0)*0.15*uBrightness*(1.0-acc.a);
    }

    fragColor=vec4(acc.rgb,acc.a*uAlpha);
}
