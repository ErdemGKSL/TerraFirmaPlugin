# hud-breath-gauge

Spec: master for the two-layer HUD bar `m3_surv_06_air_bar` (150x24), frame + FULL fill in one image (`plans/hud-layered-redesign.md`).
Both layers are derived from this one master, never generated separately. Used by `tools/datagen` `HudLayouts.BAR_SPECS`.

Generated with the imagegen CLI (the MCP tools did not load in this session), 2026-10-22:

    npx -y codex-imagegen-mcp@0.2 generate "<prompt>" -a 21:9 -b transparent -n 2 -o art/raw/hud-breath-gauge/

Prompt:

> Game HUD sprite, Minecraft-style pixel art, hard pixel edges, no anti-aliasing, no text, no letters, no numbers, flat shading, light from top-left, in the style of the TerraFirmaCraft mod HUD. Fully transparent background around the sprite. The sprite fills the whole image width, centred vertically. A long thin horizontal underwater breath gauge, about 8 times wider than tall, shown completely FULL. On the far left a round porthole with a stone-grey rim containing a small pair of lungs made of pale blue air bubbles; to its right a long recessed horizontal channel with a 1 pixel black outline and a bevelled stone-grey rim, the channel completely filled edge to edge with a single row of round pale light-blue air bubbles, each with a darker blue rim and a tiny white highlight; along the top edge of the frame a wavy teal water-line ripple; at the right end a small empty dark round plate. Colours: slate blue, teal, pale aqua, stone grey, black outline.

Picked: `095425-game-hud-sprite-minecraft-style-pixel-ar-1.png` (the other variant is kept for comparison).

Post-process (master):

    python3 tools/images/postprocess.py art/raw/hud-breath-gauge/095425-game-hud-sprite-minecraft-style-pixel-ar-1.png docs/art/hud/m3_surv_06_air_bar_master.png --size 150x24 --trim --trim-alpha 128 --colors 32

Layers (bg + fill, same canvas, interior mask in the spec):

    python3 tools/images/hud_layers.py docs/art/hud/m3_surv_06_air_bar_master.png tools/datagen/src/main/resources/dev/tfp/datagen/hud/m3_surv_06_air_bar.layers.json tools/datagen/src/main/resources/dev/tfp/datagen/hud --check-dir <scratch>

Reviewer: look at the 8x overlay check (0/25/50/75/100 %) and in game at GUI scale 2 and 3. Status: generated, pending review.
