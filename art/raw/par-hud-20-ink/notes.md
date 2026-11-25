# par-hud-20-ink: ink and glow-ink splatter overlays (PAR-HUD-20)

No generation: TFC already has the art (image-generation skill, section 1). Derived deterministically from
`assets/tfc/textures/misc/ink_splatter.png` and `glow_ink_splatter.png` @ c3b4d9536 (256x256 RGBA), attribution in NOTICE.

TFC (`IngameOverlays.renderTextureOverlay`) stretches the square texture over the whole screen. BetterHud cannot stretch an
image to the screen, so the texture is pre-stretched to the 16:9 shape (480x270, bilinear, as the GPU does) and the layout
draws it at image scale 2 (960x540 GUI px) centred on the screen.

Command (from the repo root):

    python3 -c "from PIL import Image; D='tools/datagen/src/main/resources/dev/tfp/datagen/hud/'; [Image.open('/opt/tfc/src/main/resources/assets/tfc/textures/misc/%s.png'%s).convert('RGBA').resize((480,270),Image.BILINEAR).save(D+d+'.png',optimize=True) for s,d in [('ink_splatter','par_hud_20_ink_splatter'),('glow_ink_splatter','par_hud_20_glow_ink_splatter')]]"

Outputs: `par_hud_20_ink_splatter.png`, `par_hud_20_glow_ink_splatter.png` in that resource folder (`hud/ink/`; `HudArt.bytes` finds them by file name, and they stay out of the bar-art PNG rules, which are for pixel art: this is a soft-edged stretched texture).
