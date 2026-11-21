$input a_color0, a_position, a_texcoord0, i_data1, i_data2, i_data3
$output v_color0, v_fog, v_texcoord0, v_worldPos, v_center, v_cam, v_params

/*
 * Moreweather "3D Clouds" — Particle / Transparent (vertex), formato bgfx SC.
 * Igual ao vertex shader vanilla do jogo + 3 saídas extras (v_center, v_cam, v_params) usadas só pelo raymarching
 * das partículas weather:rm_cloud. Nas outras partículas essas saídas são ignoradas pelo fragment.
 * Evita matriz inversa/transposta e arrays: tudo por produto vetorial, pra compilar em qualquer backend do shaderc.
 */
#include <bgfx_shader.sh>

uniform vec4 FogAndDistanceControl;
uniform vec4 FogColor;

#define RM_FLIP_Y 1.0   // se a nuvem aparecer de cabeça pra baixo / deslocada na vertical, troque para -1.0
#define RM_FLIP_X 1.0   // idem na horizontal

// = RADIUS_TABLE / FLAT_TABLE em raymarch_clouds.js (índice 0..7)
float rm_radius(float i) {
    if (i < 0.5) return 300.0;
    if (i < 1.5) return 500.0;
    if (i < 2.5) return 750.0;
    if (i < 3.5) return 1000.0;
    if (i < 4.5) return 1400.0;
    if (i < 5.5) return 1900.0;
    if (i < 6.5) return 2600.0;
    return 3500.0;
}
float rm_flat(float i) {
    if (i < 0.5) return 0.15;
    if (i < 1.5) return 0.25;
    if (i < 2.5) return 0.35;
    if (i < 3.5) return 0.5;
    if (i < 4.5) return 0.65;
    if (i < 5.5) return 0.8;
    if (i < 6.5) return 0.9;
    return 1.0;
}

void main() {
#if INSTANCING__ON
    mat4 model = mtxFromCols(
        vec4(i_data1.x, i_data2.x, i_data3.x, 0.0),
        vec4(i_data1.y, i_data2.y, i_data3.y, 0.0),
        vec4(i_data1.z, i_data2.z, i_data3.z, 0.0),
        vec4(i_data1.w, i_data2.w, i_data3.w, 1.0));
    vec4 worldPos = mul(model, vec4(a_position, 1.0));
#else
    vec4 worldPos = mul(u_model[0], vec4(a_position, 1.0));
#endif
    vec4 clip = mul(u_viewProj, vec4(worldPos.xyz, 1.0));

    v_color0 = a_color0;
    v_fog = vec4(FogColor.xyz, clamp(((clip.z / FogAndDistanceControl.z) - FogAndDistanceControl.x) / (FogAndDistanceControl.y - FogAndDistanceControl.x), 0.0, 1.0));
    v_texcoord0 = a_texcoord0;
    v_worldPos = worldPos.xyz;

    // ---- 3D Clouds ----
    // Linhas da matriz view*proj (x_clip = dot(r0,p), y_clip = dot(r1,p), w_clip = dot(r3,p)), obtidas por multiplicação com os vetores da base
    // (assim não dependem de como cada backend guarda a matriz).
    vec4 cx = mul(u_viewProj, vec4(1.0, 0.0, 0.0, 0.0));
    vec4 cy = mul(u_viewProj, vec4(0.0, 1.0, 0.0, 0.0));
    vec4 cz = mul(u_viewProj, vec4(0.0, 0.0, 1.0, 0.0));
    vec4 cw = mul(u_viewProj, vec4(0.0, 0.0, 0.0, 1.0));
    vec3 r0 = vec3(cx.x, cy.x, cz.x);  float w0 = cw.x;
    vec3 r1 = vec3(cx.y, cy.y, cz.y);  float w1 = cw.y;
    vec3 r3 = vec3(cx.w, cy.w, cz.w);  float w3 = cw.w;
    vec3 camRight = normalize(r0) * RM_FLIP_X;
    vec3 camUp    = normalize(r1) * RM_FLIP_Y;
    // a câmera é o ponto onde x_clip = y_clip = w_clip = 0:  A*c = -b   (Cramer, sem inverse())
    vec3 bxd = cross(r1, r3);
    float det = dot(r0, bxd);
    vec3 cam = (-w0 * bxd - w1 * cross(r3, r0) - w3 * cross(r0, r1)) / (abs(det) < 0.000001 ? 0.000001 : det);
    v_cam = cam;

    // o UV do quad guarda a célula do atlas 8x8 (32x32, com 1 px de folga): X = achatamento, Y = raio
    vec2 g = a_texcoord0 * 8.0;
    vec2 cell = clamp(floor(g), vec2(0.0, 0.0), vec2(7.0, 7.0));
    float R = rm_radius(cell.y);
    vec2 t = (fract(g) - 0.03125) / 0.9375;               // 0..1 dentro do quad (canto a canto)
    vec2 o = vec2((t.x - 0.5) * 2.0 * R, (0.5 - t.y) * 2.0 * R);
    v_center = worldPos.xyz - camRight * o.x - camUp * o.y;
    v_params = vec2(R, rm_flat(cell.x));

    gl_Position = clip;
}
