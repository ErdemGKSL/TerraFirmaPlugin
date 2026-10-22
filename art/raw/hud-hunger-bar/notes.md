# hud-hunger-bar

Spec: master for the two-layer HUD bar `m3_surv_06_hunger_bar` (81x6), frame + FULL fill in one image (`plans/hud-layered-redesign.md`).
Both layers are derived from this one master, never generated separately. Used by `tools/datagen` `HudLayouts.BAR_SPECS`.

Generated with the imagegen CLI (the MCP tools did not load in this session), 2026-10-22:

    npx -y codex-imagegen-mcp@0.2 generate "<prompt>" -a 21:9 -b transparent -n 2 -o art/raw/hud-hunger-bar/

Prompt:

> Game HUD sprite, Minecraft-style pixel art, hard pixel edges, no anti-aliasing, no text, no letters, no numbers, flat shading, light from top-left, in the style of the TerraFirmaCraft mod HUD. Fully transparent background around the sprite. The sprite fills the whole image width, centred vertically. A very long thin horizontal hunger bar, 13 times wider than tall, shown completely FULL. A crisp 1 pixel black outline around it with slightly rounded ends. The whole inside is filled edge to edge with warm orange: light golden-orange highlight row on top, saturated orange middle, darker burnt-orange bottom row, faint vertical segment notches every fifth of the length. No icons.

Picked: `095425-game-hud-sprite-minecraft-style-pixel-ar-1.png` (the other variant is kept for comparison).

Post-process (master):

    python3 tools/images/postprocess.py art/raw/hud-hunger-bar/095425-game-hud-sprite-minecraft-style-pixel-ar-1.png docs/art/hud/m3_surv_06_hunger_bar_master.png --size 81x6 --trim --trim-alpha 128 --colors 12

Layers (bg + fill, same canvas, interior mask in the spec):

    python3 tools/images/hud_layers.py docs/art/hud/m3_surv_06_hunger_bar_master.png tools/datagen/src/main/resources/dev/tfp/datagen/hud/m3_surv_06_hunger_bar.layers.json tools/datagen/src/main/resources/dev/tfp/datagen/hud --check-dir <scratch>

Reviewer: look at the 8x overlay check (0/25/50/75/100 %) and in game at GUI scale 2 and 3. Status: generated, pending review.
