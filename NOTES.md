# Notas do shader

## Papel
O addon emite partículas `weather:rm_cloud` (material `particles_blend`, textura `rm_fallback.png`). Cada uma é um quad que representa um
elipsoide de nuvem; o fragment shader faz o raymarching dentro dele.

## Como o shader reconhece uma nuvem
`rm_fallback.png` tem o texel (0,0) com alfa = 2/255. O fragment lê esse texel; se bater, faz o raymarch. O resto segue o código vanilla.

## Protocolo (igual a `raymarch_clouds.js`, no addon)
- UV do quad → célula do atlas 8x8 (32x32 px, 1 px de folga): célula X = achatamento vertical, célula Y = raio (blocos). Lado do quad = 2 * raio.
- Cor RGB = tom da nuvem; alfa = densidade * fade.

## Suposições NÃO verificadas (testar no aparelho)
1. Posição da câmera recuperada resolvendo `u_viewProj` (x_clip = y_clip = w_clip = 0), sem supor espaço camera-relative.
2. Direita/cima do billboard vêm das linhas 0 e 1 de `u_viewProj`. Se a nuvem sair espelhada: `RM_FLIP_X` / `RM_FLIP_Y` no vertex.
3. `LightWorldSpaceDirection` pode vir zerado em partículas; nesse caso usa um sol fixo.
4. Blend de `particles_blend` = SRC_ALPHA / ONE_MINUS_SRC_ALPHA (por isso a cor é "des-premultiplicada" no fim).
5. Custo: 22 passos + 3 de sombra por pixel; ajuste `RM_STEPS` / `RM_LIGHT_STEPS` no topo do fragment.
6. Os comandos do lazurite em `build.sh` (`unpack`, `build`, `info`, formato do `config.json`/`project.json`) foram escritos a partir da
   documentação e podem precisar de ajuste no primeiro build; o script imprime a ajuda de cada comando e o conteúdo do unpack pra facilitar.
