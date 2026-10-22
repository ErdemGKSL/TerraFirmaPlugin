# hud-info-plate

Spec: single-layer plate 124x22 behind the date and temperature lines (top-left), plain dark inside so white text reads.

Prompt (imagegen CLI, `-a 21:9 -b transparent -n 2`):

> Game HUD sprite, Minecraft-style pixel art, hard pixel edges, no anti-aliasing, no text, no letters, no numbers, flat shading, light from top-left, in the style of the TerraFirmaCraft mod HUD. Fully transparent background around the sprite. The sprite fills the whole image width, centred vertically. A small flat rectangular information plate for a game HUD, 6 times wider than tall: 1 pixel black outline, 1 pixel weathered stone-grey bevelled border with tiny rivets in the four corners, the inside a plain flat dark slate-brown panel with no pattern so that white text stays readable on it. No text.

Picked: `095424-game-hud-sprite-minecraft-style-pixel-ar-1.png`.

    python3 tools/images/postprocess.py art/raw/hud-info-plate/095424-game-hud-sprite-minecraft-style-pixel-ar-1.png docs/art/hud/m2_cal_02_info_plate.png --size 124x22 --trim --trim-alpha 128 --colors 16

then copied to `tools/datagen/src/main/resources/dev/tfp/datagen/hud/`. Status: generated, pending review.
