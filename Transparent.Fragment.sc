$input v_color0, v_fog, v_texcoord0, v_worldPos, v_center, v_cam, v_params

/*
 * Moreweather "3D Clouds" — Particle / Transparent (fragment), formato bgfx SC.
 * Partículas normais: idêntico ao vanilla. Partículas weather:rm_cloud (marcadas por um "selo" na textura): raymarching de nuvem
 * dentro de um elipsoide.
 */
#include <bgfx_shader.sh>

SAMPLER2D(s_ParticleTexture, 0);
uniform vec4 LightWorldSpaceDirection;

#define RM_STEPS 22            // passos de visão  (celular: 16-24)
#define RM_LIGHT_STEPS 3       // passos de sombra (celular: 2-4)
#define RM_FOG_AMOUNT 0.55     // quanto da névoa do jogo vale pra nuvem (0 = nenhuma, 1 = igual às outras partículas)

float rm_hash(vec3 p) { p = fract(p * 0.1031); p += dot(p, p.zyx + 31.32); return fract((p.x + p.y) * p.z); }
float rm_noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(rm_hash(i), rm_hash(i + vec3(1.0, 0.0, 0.0)), f.x), mix(rm_hash(i + vec3(0.0, 1.0, 0.0)), rm_hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y);
    float b = mix(mix(rm_hash(i + vec3(0.0, 0.0, 1.0)), rm_hash(i + vec3(1.0, 0.0, 1.0)), f.x), mix(rm_hash(i + vec3(0.0, 1.0, 1.0)), rm_hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y);
    return mix(a, b, f.z);
}
float rm_fbm(vec3 p, float octaves) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < 4; i++) {
        if (float(i) < octaves) {
            s += a * rm_noise(p);
            p = p * 2.03 + vec3(11.7, 11.7, 11.7);
            a *= 0.5;
        }
    }
    return s;
}
// densidade no ponto q (coordenadas normalizadas do elipsoide, |q| <= 1): borda suave esculpida por ruído (ancorada no centro da nuvem)
float rm_density(vec3 q, float octaves) {
    float edge = 1.0 - dot(q, q);
    float n = rm_fbm(q * 3.2 + vec3(v_params.y * 7.0, 0.0, 0.0), octaves);
    return edge > 0.0 ? clamp((edge * 1.6 + n - 0.78) * 2.2, 0.0, 1.0) : 0.0;
}

void main() {
    // "selo": a textura rm_fallback.png tem o texel (0,0) com alfa = 2/255. Nenhuma outra partícula do jogo tem isso.
    float seal = texture2DLod(s_ParticleTexture, vec2(0.5 / 256.0, 0.5 / 256.0), 0.0).w;
    if (abs(seal - 2.0 / 255.0) < 0.5 / 255.0) {
        vec3 radii = vec3(v_params.x, v_params.x * v_params.y, v_params.x);
        vec3 d = normalize(v_worldPos - v_cam);
        vec3 o = (v_cam - v_center) / radii;           // origem do raio no espaço do elipsoide
        vec3 dn = d / radii;
        float A = dot(dn, dn);
        float B = dot(o, dn);
        float C = dot(o, o) - 1.0;
        float disc = B * B - A * C;
        if (disc <= 0.0) discard;
        float sq = sqrt(disc);
        float t0 = max((-B - sq) / A, 0.0);
        float t1 = (-B + sq) / A;
        if (t1 <= t0) discard;
        float dt = (t1 - t0) / float(RM_STEPS);

        vec3 sun = LightWorldSpaceDirection.xyz;
        if (dot(sun, sun) < 0.01) sun = vec3(0.35, 0.8, 0.25);   // uniforme vazio: sol fixo
        sun = normalize(sun);
        if (sun.y < 0.0) sun = -sun;                              // garante que a "luz" vem de cima
        vec3 sunN = sun / radii;

        float jitter = rm_hash(vec3(gl_FragCoord.x, gl_FragCoord.y, 7.0));
        float trans = 1.0;
        vec3 col = vec3(0.0, 0.0, 0.0);
        for (int i = 0; i < RM_STEPS; i++) {
            if (trans > 0.03) {
                float t = t0 + (float(i) + jitter) * dt;
                vec3 q = o + dn * t;                              // = (ponto - centro) / raios
                float dens = rm_density(q, 3.0);
                if (dens > 0.01) {
                    float shadow = 0.0;
                    for (int j = 1; j <= RM_LIGHT_STEPS; j++) {
                        shadow += rm_density(q + sunN * (float(j) * 0.28), 2.0);
                    }
                    float light = exp(-shadow * 1.3);
                    float powder = 1.0 - exp(-dens * 3.0);
                    vec3 lit = v_color0.xyz * (0.40 + 1.0 * light * (0.6 + 0.4 * powder));
                    float a = 1.0 - exp(-dens * dt * 5.0);
                    col += trans * a * lit;
                    trans *= (1.0 - a);
                }
            }
        }
        float alpha = 1.0 - trans;
        vec3 rgb = col / max(alpha, 0.0001);                      // blend do material: SRC_ALPHA, ONE_MINUS_SRC_ALPHA (não premultiplicado)
        rgb = mix(rgb, v_fog.xyz, v_fog.w * RM_FOG_AMOUNT);
        gl_FragColor = vec4(rgb, alpha * v_color0.w);
    } else {
        // caminho original (vanilla)
        vec4 tex = texture2D(s_ParticleTexture, v_texcoord0) * vec4(v_color0.xyz, v_color0.w);
        vec3 fogged = mix(tex.xyz, v_fog.xyz, v_fog.w);
        gl_FragColor = vec4(mix(fogged, v_fog.xyz, v_fog.w), tex.w);
    }
}
