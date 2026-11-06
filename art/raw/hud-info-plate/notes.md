# hud-info-plate

Spec: single-layer plate 124x22 behind the date and temperature lines (top-left), plain dark inside so white text reads.
Plain style (owner feedback 2026-10-22); the first, riveted attempt (files directly in this folder) is superseded.

Prompt (imagegen CLI, `-a 21:9 -b transparent -n 2 -o art/raw/hud-info-plate/v2/`):

> Simple clean flat rectangular information plate for a game HUD in plain Minecraft pixel-art style, 6 times wider than tall: crisp 1 pixel black outline, a 1 pixel plain grey border inside it, the inside one flat dark brown-grey colour with no pattern, no rivets, no ornaments, no text. Hard pixel edges, no anti-aliasing, very limited palette. Fully transparent background. The plate spans the whole image width, centred vertically.

Picked: `v2/100747-simple-clean-flat-rectangular-informatio-1.png`.

    python3 tools/images/postprocess.py art/raw/hud-info-plate/v2/100747-simple-clean-flat-rectangular-informatio-1.png docs/art/hud/m2_cal_02_info_plate.png --size 124x22 --trim --trim-alpha 128 --colors 6 --resample nearest

then copied to `tools/datagen/src/main/resources/dev/tfp/datagen/hud/`. Status: generated, pending review.

PAR-HUD-13 (2026-11-05): widened to 160x22 from the same raw (the date line "September 30, 1000  (Summer)" is 150 GUI px;
plate = text inset 5 + 150 + shadow 1 + margin 4, `BetterHudPackTest`):

    python3 tools/images/postprocess.py art/raw/hud-info-plate/v2/100747-simple-clean-flat-rectangular-informatio-1.png docs/art/hud/m2_cal_02_info_plate.png --size 160x22 --trim --trim-alpha 128 --colors 6 --resample nearest
    python3 tools/images/hud_palette.py docs/art/hud/m2_cal_02_info_plate.png tools/datagen/src/main/resources/dev/tfp/datagen/hud/m2_cal_02_info_plate.png

Status: in-game checked (docs/verification/PAR-HUD-13/).

PAR-HUD-15 (2026-11-06): widened to 189x22 from the same raw, for TFC's 12 season names in either hemisphere (longest
line "September 30, 1000  (Early Autumn)" = 179 GUI px; plate = inset 5 + 179 + shadow 1 + margin 4, `BetterHudPackTest`):

    python3 tools/images/postprocess.py art/raw/hud-info-plate/v2/100747-simple-clean-flat-rectangular-informatio-1.png docs/art/hud/m2_cal_02_info_plate.png --size 189x22 --trim --trim-alpha 128 --colors 6 --resample nearest
    python3 tools/images/hud_palette.py docs/art/hud/m2_cal_02_info_plate.png tools/datagen/src/main/resources/dev/tfp/datagen/hud/m2_cal_02_info_plate.png

Checked by eye at 6x (1 px black outline, 1 px grey border, plain inside, both edge columns opaque). In-game check:
PAR-HUD-16.
