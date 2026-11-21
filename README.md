# Moreweather — 3D Clouds shader

Shader de nuvem (raymarching) para as partículas `weather:rm_cloud` do addon Moreweather, para uso com o MB Loader.
Substitui só a passada **Transparent** do material **Particle**. As outras partículas do jogo seguem o caminho original.

## Estrutura
- `materials_original/` — `Particle.material.bin` e `particles.material` originais do Minecraft Bedrock Android (1.26.61).
- `shaders/Particle/` — vertex, fragment e `varying.def.sc` em bgfx SC (o que o shaderc compila).
- `shaders/Particle_original/` — código vanilla de referência (GLSL), só pra comparar.
- `pack/` — manifest do resource pack; o build coloca o `.material.bin` em `pack/materials/`.
- `build.sh` + `.github/workflows/build.yml` — compilam com lazurite + shaderc no GitHub Actions.

## Como compilar (pelo celular)
Aba **Actions** → **Build 3D Clouds shader** → **Run workflow**. Quando terminar, baixe o `.mcpack` em **Artifacts**.
Se falhar, abra o run, toque no passo que ficou vermelho e copie as últimas linhas do erro.

## Usar
MB Loader → Configurações → Import addon → o `.mcpack`; no jogo, ative em Configurações → Recursos Globais.
No Moreweather 0.18.321: menu → botão **3D Clouds** (ou `/scriptevent weather:clouds3d on`).
