# hud-air-bar

Spec: master for the two-layer HUD bar `m3_surv_06_air_bar` (81x7), frame + FULL fill in one image (`plans/hud-layered-redesign.md`).
Both layers are derived from this one master, never generated separately. Used by `tools/datagen` `HudLayouts.BAR_SPECS`.
Style (owner feedback 2026-10-22): plain and restrained like TFC's own bars; the first, ornate attempt (files directly in
this folder, if any) is superseded by the plain one in the subfolder named below.

Generated with the imagegen CLI (the MCP tools did not load in this session), 2026-10-22:

    npx -y codex-imagegen-mcp@0.2 generate "<prompt>" -a 21:9 -b transparent -n 2 -o art/raw/hud-air-bar/<v>/

Prompt:

> Simple clean game HUD bar sprite in plain Minecraft pixel-art style, like the plain bars of the TerraFirmaCraft mod: a crisp 1 pixel black outline with square ends cut by one pixel at the corners, very limited palette of 3 to 4 colours, no gloss, no ornaments, no glow, no texture noise, no text. Hard pixel edges, no anti-aliasing. Fully transparent background. The bar spans the whole image width and is centred vertically. A very long thin bar 12 times wider than tall, shown completely FULL, filled with exactly ten large evenly spaced round pale light-blue air bubbles that touch the top and bottom of the bar, on a flat medium-blue background, each bubble a plain light circle with a single white pixel highlight.

Picked: `v3/100924-simple-clean-game-hud-bar-sprite-in-plai-2.png`.

Post-process (master):

    python3 tools/images/postprocess.py art/raw/hud-air-bar/v3/100924-simple-clean-game-hud-bar-sprite-in-plai-2.png docs/art/hud/m3_surv_06_air_bar_master.png --size 81x7 --trim --trim-alpha 128 --colors 6

Layers (bg + fill, same canvas, interior mask and frame shading in the spec):

    python3 tools/images/hud_layers.py docs/art/hud/m3_surv_06_air_bar_master.png tools/datagen/src/main/resources/dev/tfp/datagen/hud/m3_surv_06_air_bar.layers.json tools/datagen/src/main/resources/dev/tfp/datagen/hud --check-dir <scratch>

Reviewer: look at the 8x overlay check (0/25/50/75/100 %) and in game at GUI scale 2 and 3. Status: generated, pending review.
