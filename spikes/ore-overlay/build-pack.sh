#!/usr/bin/env bash
# PAR-WORLD-17 (spike S1): writes the tiny CraftEngine pack with the overlay item model `tfpspike:overlay` into a server's
# CraftEngine resources folder, so the next CraftEngine pack build serves it to the client. The texture is copied from the
# TFC checkout at run time (never vendored): one TFC ore overlay, normal_native_gold.
# Usage: build-pack.sh <run-dir> [tfc-checkout]
# The model is a single 16x16 quad in the z=8 plane facing +z (south), shade off, no ambient occlusion: the closest
# vanilla item model to the planned PAR-INFRA-15 overlay quad.
set -eu
DIR=$1 TFC=${2:-/opt/tfc}
P="$DIR/plugins/CraftEngine/resources/tfpspike"
A="$P/resourcepack/assets/tfpspike"
mkdir -p "$A/items" "$A/models/item" "$A/textures/item"
cat > "$P/pack.yml" <<'EOF'
author: tfp-spike
version: 1.0
description: PAR-WORLD-17 overlay quad (spike, not shipped)
namespace: tfpspike
EOF
cp "$TFC/src/main/resources/assets/tfc/textures/block/ore/normal_native_gold.png" "$A/textures/item/overlay.png"
cat > "$A/items/overlay.json" <<'EOF'
{"model": {"type": "minecraft:model", "model": "tfpspike:item/overlay"}}
EOF
cat > "$A/models/item/overlay.json" <<'EOF'
{
  "ambientocclusion": false,
  "textures": {"overlay": "tfpspike:item/overlay", "particle": "tfpspike:item/overlay"},
  "elements": [
    {"from": [0, 0, 8], "to": [16, 16, 8], "shade": false,
     "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#overlay"}}}
  ]
}
EOF
echo "spike pack written to $P"
