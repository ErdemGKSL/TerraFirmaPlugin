# hud-armor-bar

Spec: master for the two-layer HUD bar `m3_surv_06_armor_bar` (81x7), frame + FULL fill in one image (`plans/hud-layered-redesign.md`).
Both layers are derived from this one master, never generated separately. Used by `tools/datagen` `HudLayouts.BAR_SPECS`.
Owner request (2026-10-22): an armour bar, thin like thirst/hunger, directly above the health bar (same x, one empty row
between), plain neutral steel grey, no ornament, same TFC-like plain style as the other bars. Shows `tfp_armor` (vanilla
armour attribute, 0..20 like vanilla's row; TFC's best full set is 20), hidden at 0 armour; vanilla's armour row is hidden.

Generated with the imagegen CLI (the MCP tools did not load in this session), 2026-10-22, on a transparent background
(no key colour: nothing to spill into the outline):

    npx -y codex-imagegen-mcp@0.2 generate "<prompt>" -a 21:9 -b transparent -n 2 -o art/raw/hud-armor-bar/v1/

Prompt:

> Simple clean game HUD bar sprite in plain Minecraft pixel-art style, like the plain bars of the TerraFirmaCraft mod: a crisp 1 pixel black outline with square ends cut by one pixel at the corners, flat fill colour with only one slightly lighter row along the top and one slightly darker row along the bottom, very limited palette of 3 to 4 colours, no gloss, no segments, no rivets, no ornaments, no icons, no glow, no texture noise, no text. Hard pixel edges, no anti-aliasing. Fully transparent background. The bar spans the whole image width and is centred vertically. A very long thin bar 13 times wider than tall, shown completely FULL, filled with flat neutral steel grey (main mid grey, light grey top row, darker grey bottom row), no colour tint.

Picked: `v1/105812-simple-clean-game-hud-bar-sprite-in-plai-2.png` (both were plain and usable; `-2` has the more even rows).

Post-process (master; 6 colours keep the darker bottom row at grey 88, 5 merged it into a near-black 53 that read as a
thick bottom outline):

    python3 tools/images/postprocess.py art/raw/hud-armor-bar/v1/105812-simple-clean-game-hud-bar-sprite-in-plai-2.png docs/art/hud/m3_surv_06_armor_bar_master.png --size 81x7 --trim --trim-alpha 128 --colors 6 --resample nearest

Layers (cleaned master + bg + fill, one pinned palette, 4-bit indexed PNGs; spec in `layers.json`):

    python3 -I tools/images/hud_layers.py docs/art/hud/m3_surv_06_armor_bar_master.png tools/datagen/src/main/resources/dev/tfp/datagen/hud/m3_surv_06_armor_bar.layers.json tools/datagen/src/main/resources/dev/tfp/datagen/hud --check-dir <scratch>

Run twice: identical bytes. Reviewer: the 8x overlay check (0/25/50/75/100 %) and in game at GUI scale 2 and 3 with no
armour (no bar), partial armour and full armour. Status: generated, pending in-game review.
