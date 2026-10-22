# hud-air-bar

Spec: master for the two-layer HUD bar `m3_surv_06_air_bar` (81x7), frame + FULL fill in one image (`plans/hud-layered-redesign.md`).
Both layers are derived from this one master, never generated separately. Used by `tools/datagen` `HudLayouts.BAR_SPECS`.
Style (owner feedback 2026-10-22): plain and restrained like TFC's own bars. Compact, directly above thirst, hidden at
full air, red fill below 60 air.

History: the first, ornate attempt (files directly in this folder, if any) and the bubble-row versions `v2/`, `v3/` are
superseded. The in-game review (2026-10-22, `ui/hud-v2` screenshots) found the bubble segments busy when empty, so `v4/`
is a plain flat fill like hunger and thirst, in a paler sky blue than thirst so the two stay distinct. No bubble pixels.

Generated with the imagegen CLI (the MCP tools did not load in this session), 2026-10-22:

    npx -y codex-imagegen-mcp@0.2 generate "<prompt>" -a 21:9 -b transparent -n 2 -o art/raw/hud-air-bar/v4/

Prompt (v4):

> Simple clean game HUD bar sprite in plain Minecraft pixel-art style, like the plain bars of the TerraFirmaCraft mod: a crisp 1 pixel black outline with square ends cut by one pixel at the corners, flat fill colour with only one slightly lighter row along the top and one slightly darker row along the bottom, very limited palette of 3 to 4 colours, no gloss, no segments, no bubbles, no circles, no ornaments, no icons, no glow, no texture noise, no text. Hard pixel edges, no anti-aliasing. Fully transparent background. The bar spans the whole image width and is centred vertically. A very long thin bar 13 times wider than tall, shown completely FULL, filled with flat calm light sky blue (main light blue, pale cyan-white top row, slightly deeper blue bottom row).

Picked: `v4/103328-simple-clean-game-hud-bar-sprite-in-plai-2.png` (paler than the thirst blue; `-1` was too close to it).

Post-process (master; 5 colours keep the top and bottom shading rows, 4 merged the bottom row into the main fill):

    python3 tools/images/postprocess.py art/raw/hud-air-bar/v4/103328-simple-clean-game-hud-bar-sprite-in-plai-2.png docs/art/hud/m3_surv_06_air_bar_master.png --size 81x7 --trim --trim-alpha 128 --colors 5 --resample nearest

Layers (bg + fill + red critical fill, same canvas; `tint_amount` raised 0.7 -> 0.9 in `layers.json` because the paler
base made the 0.7 tint read pink, not red):

    python3 tools/images/hud_layers.py docs/art/hud/m3_surv_06_air_bar_master.png tools/datagen/src/main/resources/dev/tfp/datagen/hud/m3_surv_06_air_bar.layers.json tools/datagen/src/main/resources/dev/tfp/datagen/hud --check-dir <scratch>

Run twice: identical bytes (deterministic). Reviewer: look at the 8x overlay check (0/25/50/75/100 %) and in game at GUI
scale 2 and 3, under water (air bar above thirst, red below 3 s). Status: generated, pending in-game review.
