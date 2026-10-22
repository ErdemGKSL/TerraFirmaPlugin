# m3-surv-06-health-bar

Spec: HUD bar fill, 82x10 px, transparent outside the bar, pixel-art style, one colour family per bar. Used by HudLayouts.BARS (betterhud/tfp/images/tfp-bars.yml, split 50, left). Background track derived from the fill (Pillow darken, see scratchpad make_bg.py) for the _bg image.

Raw: 054753-use-case-game-hud-sprite-asset-type-a-si.png (aspect 21:9, background transparent, one generate_image call).

Post-process:
  python3 tools/images/postprocess.py art/raw/m3-surv-06-health-bar/054753-use-case-game-hud-sprite-asset-type-a-si.png docs/art/hud/m3_surv_06_health_bar_fill.png --size 82x10 --trim --colors 8

Status: generated, pending review. In-game check open (plans/gaps.md, M3-SURV-06).

Superseded 2026-10-22 (HUD remaster): the HUD bars were first sliced from TFC's overlay sheet, then redesigned as two-layer bars from new masters (`art/raw/hud-*`, `plans/hud-layered-redesign.md`); this image is no longer used.
