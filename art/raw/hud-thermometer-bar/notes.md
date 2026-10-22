# hud-thermometer-bar

Spec: master for the two-layer HUD bar `m2_clim_03_ambient_temp_bar` (64x8), frame + FULL fill in one image (`plans/hud-layered-redesign.md`).
Both layers are derived from this one master, never generated separately. Used by `tools/datagen` `HudLayouts.BAR_SPECS`.
Style (owner feedback 2026-10-22): plain and restrained like TFC's own bars; the first, ornate attempt (files directly in
this folder, if any) is superseded by the plain one in the subfolder named below.

Generated with the imagegen CLI (the MCP tools did not load in this session), 2026-10-22:

    npx -y codex-imagegen-mcp@0.2 generate "<prompt>" -a 21:9 -b transparent -n 2 -o art/raw/hud-thermometer-bar/<v>/

Prompt:

> Simple clean game HUD bar sprite in plain Minecraft pixel-art style, like the plain bars of the TerraFirmaCraft mod: a crisp 1 pixel black outline with square ends cut by one pixel at the corners, flat fill colour with only one slightly lighter row along the top and one slightly darker row along the bottom, very limited palette of 3 to 4 colours, no gloss, no segments, no ornaments, no icons, no glow, no texture noise, no text. Hard pixel edges, no anti-aliasing. Fully transparent background. The bar spans the whole image width and is centred vertically. A thin thermometer bar 7 times wider than tall, shown completely FULL, a small round bulb at the left end, filled with a few flat colour bands stepping from blue at the left through green and yellow to red at the right, no gradients between bands.

Picked: `v2/100746-simple-clean-game-hud-bar-sprite-in-plai-2.png`.

Post-process (master):

    python3 tools/images/postprocess.py art/raw/hud-thermometer-bar/v2/100746-simple-clean-game-hud-bar-sprite-in-plai-2.png docs/art/hud/m2_clim_03_ambient_temp_bar_master.png --size 64x8 --trim --trim-alpha 128 --resample nearest

Layers (bg + fill, same canvas, interior mask and frame shading in the spec):

    python3 tools/images/hud_layers.py docs/art/hud/m2_clim_03_ambient_temp_bar_master.png tools/datagen/src/main/resources/dev/tfp/datagen/hud/m2_clim_03_ambient_temp_bar.layers.json tools/datagen/src/main/resources/dev/tfp/datagen/hud --check-dir <scratch>

Reviewer: look at the 8x overlay check (0/25/50/75/100 %) and in game at GUI scale 2 and 3. Status: generated, pending review.
