# hud-underwater-vignette

Spec: single-layer translucent overlay, 104x80, top-left screen corner while under water (`tfp_air < 300`); the top-right
corner uses a left-right mirrored copy made by the datagen (`HudArt.mirrored`). Opacity 0.6 in the layout.

Prompt (imagegen CLI, `-a 3:2 -b transparent -n 2`):

> Game HUD overlay sprite, Minecraft-style pixel art, hard pixel edges, no text, flat shading. The top-left corner of an underwater screen vignette: soft wavy caustic light ripples and a dark blue-green murky edge that is strongest in the extreme top-left corner and fades out to fully transparent towards the bottom-right; a few tiny rising bubbles. Colours: deep teal, blue-green, pale aqua highlights. Mostly transparent; only the corner area is coloured. Fully transparent background.

Picked: `094853-game-hud-overlay-sprite-minecraft-style-1.png`.

    python3 tools/images/postprocess.py art/raw/hud-underwater-vignette/094853-game-hud-overlay-sprite-minecraft-style-1.png docs/art/hud/m3_surv_06_underwater_vignette.png --size 104x80 --translucent --colors 24

then copied to `tools/datagen/src/main/resources/dev/tfp/datagen/hud/`. Translucency is allowed for this asset.
Status: generated, pending review (strength in game).
