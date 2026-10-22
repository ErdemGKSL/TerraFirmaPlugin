# hud-thermometer-bar

Spec: master for the two-layer HUD bar `m2_clim_03_ambient_temp_bar` (64x8), frame + FULL fill in one image (`plans/hud-layered-redesign.md`).
Both layers are derived from this one master, never generated separately. Used by `tools/datagen` `HudLayouts.BAR_SPECS`.

Generated with the imagegen CLI (the MCP tools did not load in this session), 2026-10-22:

    npx -y codex-imagegen-mcp@0.2 generate "<prompt>" -a 21:9 -b transparent -n 2 -o art/raw/hud-thermometer-bar/

Prompt:

> Game HUD sprite, Minecraft-style pixel art, hard pixel edges, no anti-aliasing, no text, no letters, no numbers, flat shading, light from top-left, in the style of the TerraFirmaCraft mod HUD. Fully transparent background around the sprite. The sprite fills the whole image width, centred vertically. A thin horizontal thermometer bar, 7 times wider than tall, shown completely FULL. A crisp 1 pixel black outline with a small round bulb shape at the left end and a rounded right end, a 1 pixel grey glass rim inside the outline. The inside is filled edge to edge with a smooth stepped colour scale from icy blue at the left, through teal, green and yellow, to hot red at the right, a lighter highlight row along the top. No numbers, no tick labels.

Picked: `095424-game-hud-sprite-minecraft-style-pixel-ar-1.png` (the other variant is kept for comparison).

Post-process (master):

    python3 tools/images/postprocess.py art/raw/hud-thermometer-bar/095424-game-hud-sprite-minecraft-style-pixel-ar-1.png docs/art/hud/m2_clim_03_ambient_temp_bar_master.png --size 64x8 --trim --trim-alpha 128 --colors 24

Layers (bg + fill, same canvas, interior mask in the spec):

    python3 tools/images/hud_layers.py docs/art/hud/m2_clim_03_ambient_temp_bar_master.png tools/datagen/src/main/resources/dev/tfp/datagen/hud/m2_clim_03_ambient_temp_bar.layers.json tools/datagen/src/main/resources/dev/tfp/datagen/hud --check-dir <scratch>

Reviewer: look at the 8x overlay check (0/25/50/75/100 %) and in game at GUI scale 2 and 3. Status: generated, pending review.
