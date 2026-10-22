# hud-info-plate

Spec: single-layer plate 124x22 behind the date and temperature lines (top-left), plain dark inside so white text reads.
Plain style (owner feedback 2026-10-22); the first, riveted attempt (files directly in this folder) is superseded.

Prompt (imagegen CLI, `-a 21:9 -b transparent -n 2 -o art/raw/hud-info-plate/v2/`):

> Simple clean flat rectangular information plate for a game HUD in plain Minecraft pixel-art style, 6 times wider than tall: crisp 1 pixel black outline, a 1 pixel plain grey border inside it, the inside one flat dark brown-grey colour with no pattern, no rivets, no ornaments, no text. Hard pixel edges, no anti-aliasing, very limited palette. Fully transparent background. The plate spans the whole image width, centred vertically.

Picked: `v2/100747-simple-clean-flat-rectangular-informatio-1.png`.

    python3 tools/images/postprocess.py art/raw/hud-info-plate/v2/100747-simple-clean-flat-rectangular-informatio-1.png docs/art/hud/m2_cal_02_info_plate.png --size 124x22 --trim --trim-alpha 128 --colors 6 --resample nearest

then copied to `tools/datagen/src/main/resources/dev/tfp/datagen/hud/`. Status: generated, pending review.
