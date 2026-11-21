#!/usr/bin/env bash
# Compila o material Particle com o shader de nuvem (3D Clouds) usando lazurite + shaderc.
# Entrada : materials_original/Particle.material.bin (original do jogo) + shaders/Particle/*.sc
# Saída   : pack/materials/Particle.material.bin  e  dist/Moreweather_3DClouds_Shader.mcpack
set -euo pipefail

SHADERC_URL="https://github.com/devendrn/newb-shader/releases/download/dev/shaderc-linux-x64"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$ROOT/.build"
PROJECT="$WORK/Particle"
VANILLA="$WORK/vanilla"
UNPACKED="$WORK/unpacked"
OUT="$WORK/output"

cd "$ROOT"
rm -rf "$WORK" dist
mkdir -p "$WORK" "$PROJECT/shaders" "$VANILLA" "$UNPACKED" "$OUT" pack/materials dist

python3 -m pip install --upgrade pip >/dev/null
python3 -m pip install --upgrade lazurite >/dev/null

echo "== lazurite: ajuda dos comandos (pra diagnóstico) =="
python3 -m lazurite --help || true
python3 -m lazurite unpack --help || true
python3 -m lazurite build --help || true

echo "== shaderc =="
if ! curl -fL "$SHADERC_URL" -o "$WORK/shaderc"; then
  echo "Não consegui baixar $SHADERC_URL. Arquivos disponíveis no release 'dev' do Newb X:" >&2
  curl -s https://api.github.com/repos/devendrn/newb-shader/releases/tags/dev | grep -o '"name": *"[^"]*"' | head -60 >&2 || true
  exit 1
fi
chmod +x "$WORK/shaderc"

echo "== material original =="
ORIGINAL="$ROOT/materials_original/Particle.material.bin"
[[ -f "$ORIGINAL" ]] || { echo "Falta $ORIGINAL (extraia do APK do Minecraft: assets/renderer/materials/Particle.material.bin)" >&2; exit 1; }
cp "$ORIGINAL" "$VANILLA/Particle.material.bin"
python3 -m lazurite unpack "$ORIGINAL" -o "$UNPACKED"
echo "-- conteúdo do unpack:"; find "$UNPACKED" -maxdepth 3 | sort | head -60

# o projeto parte do material desempacotado (material.json + passadas)
if [[ -d "$UNPACKED/Particle" ]]; then
  cp -a "$UNPACKED/Particle/." "$PROJECT/"
else
  cp -a "$UNPACKED/." "$PROJECT/"
fi
[[ -f "$PROJECT/material.json" ]] || { echo "material.json não encontrado após o unpack (veja o conteúdo acima)" >&2; exit 1; }

cat > "$PROJECT/config.json" <<'JSON'
{
  "compiler": { "type": "shaderc", "options": [] },
  "file_overwrite": {
    "passes": {
      "Transparent": {
        "entry_point": "main",
        "fragment": "shaders/Transparent.Fragment.sc",
        "vertex": "shaders/Transparent.Vertex.sc",
        "varying": "shaders/varying.def.sc"
      }
    }
  }
}
JSON

cat > "$WORK/project.json" <<'JSON'
{
  "base_profile": {
    "platforms": ["ESSL_300", "ESSL_310"],
    "merge_source": ["vanilla"],
    "include_patterns": ["Particle"],
    "exclude_patterns": [".*", "_*"],
    "include_search_paths": ["include"]
  },
  "profiles": {
    "android": { "platforms": ["ESSL_300", "ESSL_310"], "merge_source": ["vanilla"] }
  }
}
JSON

cp "$ROOT/shaders/Particle/Transparent.Fragment.sc" "$PROJECT/shaders/Transparent.Fragment.sc"
cp "$ROOT/shaders/Particle/Transparent.Vertex.sc"   "$PROJECT/shaders/Transparent.Vertex.sc"
cp "$ROOT/shaders/Particle/varying.def.sc"          "$PROJECT/shaders/varying.def.sc"

echo "== bgfx_shader.sh =="
mkdir -p "$WORK/include"
if [[ -f "$ROOT/shaders/Particle/bgfx_shader.sh" ]]; then
  cp "$ROOT/shaders/Particle/bgfx_shader.sh" "$PROJECT/shaders/bgfx_shader.sh"
elif curl -fL "https://raw.githubusercontent.com/SurvivalApparatusCommunication/RenderDragonSourceCodeInv/1.20.60/include/bgfx_shader.sh" -o "$PROJECT/shaders/bgfx_shader.sh"; then
  :
else
  curl -fL "https://raw.githubusercontent.com/bkaradzic/bgfx/master/src/bgfx_shader.sh" -o "$PROJECT/shaders/bgfx_shader.sh"
fi
# o include <bgfx_shader.sh> também precisa de bgfx_shader.sh / shaderlib.sh no caminho de includes
cp "$PROJECT/shaders/bgfx_shader.sh" "$WORK/include/bgfx_shader.sh"
curl -fsL "https://raw.githubusercontent.com/bkaradzic/bgfx/master/src/bgfx_compute.sh" -o "$WORK/include/bgfx_compute.sh" || true

echo "== compilar =="
python3 -m lazurite build "$WORK" -p android --shaderc "$WORK/shaderc" -o "$OUT"

built="$OUT/Particle.material.bin"
if [[ ! -f "$built" ]]; then
  built=$(find "$OUT" -name 'Particle.material.bin' -print -quit)
fi
[[ -n "${built:-}" && -f "$built" ]] || { echo "Particle.material.bin não foi gerado" >&2; find "$OUT" | head -40 >&2; exit 1; }
cp "$built" pack/materials/Particle.material.bin

python3 -m lazurite info pack/materials/Particle.material.bin || true

(cd pack && zip -qr ../dist/Moreweather_3DClouds_Shader.mcpack .)
sha256sum pack/materials/Particle.material.bin dist/Moreweather_3DClouds_Shader.mcpack
echo "Pronto: dist/Moreweather_3DClouds_Shader.mcpack"
