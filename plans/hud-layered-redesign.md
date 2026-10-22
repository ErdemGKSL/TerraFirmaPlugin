# HUD layered redesign (two-layer bars)

Owner request, 2026-10-22: redesign every HUD image from scratch. Every bar is two layers that overlay pixel-perfect:
a **background** (empty bar: frame + empty interior) drawn behind, and a **foreground fill** (interior only, no frame)
drawn in front and clipped by the current value. Includes the new underwater air gauge. Step 1 (TFC geometry, air hidden
at full, degree sign, demo compass off) is commit `ee2e674`; this plan replaces its art.

## 1. How BetterHud draws and clips (read from the BetterHud 2.0 jar, `kr.toxicity.hud`)

- `images/` type `listener` + `split: N` + `split-type: left` pre-cuts the PNG into N+1 frames
  (`SplitType$LEFT`: frame i = `getSubimage(0, 0, w*i/N, h)` of the trimmed image) and shows frame
  `round(value * lastIndex)` (`ImageType$LISTENER`). That is our clip-by-value. `up` clips bottom-to-top the same way.
- Before splitting, `ImageType$LISTENER` and `$SINGLE` call `removeEmptySide`: every fully transparent border row and
  column is trimmed and the trim is stored as `xOffset`/`yOffset`.
- `HudImageParser` places an image at `layout x + xOffset` but at `layout y` **without** `yOffset`. So the horizontal
  trim is compensated and the vertical one is not: a sprite whose first opaque row is k is drawn k rows too high.
- A layout is centred on its images (`LayoutGroup`, default `offset: center`); every image of one layout gets the same
  shift, so layers that share a layout share the shift.
- Element keys: `layer` (z-order, higher in front), `conditions`, `outline` (text shadow ARGB), layout `animations`
  (`opacity-equation`) for a pulse.

Consequences, built into the datagen:
1. Both layers of a bar live in **one layout**, at the **same layout x** (the x trim is compensated by BetterHud).
2. The datagen reads both PNGs and sets each image's layout `y = barY + firstOpaqueRow(png)`, so the vertical trim is
   compensated exactly. A test asserts it.
3. The fill canvas is the background canvas size; its opaque pixels lie only inside the interior mask.
4. z-order: background `layer: 0`, fill `layer: 1`, text `layer: 2`.

Fallback if clipping misbehaves in game (e.g. a frame is off by one): `split-type: left` with `split` equal to the
interior width (one frame per pixel column), and if that still drifts, a `sequence` image whose frames are pre-cut
full-canvas PNGs written by the datagen (no trimming difference between frames).

## 2. Elements

GUI pixels from the bottom-centre anchor (`H` = GUI height). Vanilla: XP bar H-29..H-25, hotbar H-22..H-1, armour row
H-49..H-41 (left), XP number centred around H-35..H-28.

| element | id (catalogue) | canvas | interior (fill mask) | place | layers | value |
|---|---|---|---|---|---|---|
| health | `m3-surv-06-health-bar` | 81x10 | x 1..79, y 1..8 | x centre-90..-10, rows H-40..H-31 | bg, fill, text "850/850" (inside, TFC shadow) | listener `tfp_health` |
| hunger | `m3-surv-06-hunger-bar` | 81x6 | x 1..79, y 1..4 | x centre+11..+91, rows H-36..H-31 | bg, fill | listener `tfp_hunger` |
| thirst | `m3-surv-06-thirst-bar` | 81x6 | x 1..79, y 1..4 | same x, rows H-43..H-38 | bg, fill | listener `tfp_thirst` |
| underwater air gauge | `m3-surv-06-air-bar` | 140x18 | the bubble channel rectangle (measured on the master, stored in `layers.json`) | centred, rows H-70..H-53 | bg (frame, lungs porthole, ripple water line, empty channel), fill (bubbles), fill-low, fill-critical, seconds text in the right plate | listener `tfp_air`; shown while `tfp_air < 300` |
| underwater vignette | `m3-surv-06-underwater-vignette` (new, kind `bar`-less image) | 112x72 per corner, top-left + mirrored top-right | none (single layer, translucent) | screen corners (HUD gui 0/0 and 100/0) | one layer, `layer: -1` | shown while `tfp_air < 300` |
| ambient temperature | `m2-clim-03-ambient-temp-readout` | 60x8 thermometer bar next to the text | x 1..58, y 1..6 | top-left under the date | bg, fill, text | listener `tfp_ambient_temp` (new: (t + 20) / 60, clamped) |
| date and season | `m2-cal-02-date-readout` | text only on a plate 128x22 behind both lines | none | top-left | plate (single), text | text |
| held temperature popup | `m3-surv-06-held-temp-popup` | text only | none | centre, H-60 | text | text |
| compass | BetterHud demo | none | none | none | switched off by the installer (step 1) | none |

Low/empty state: the background interior is the dark "empty" texture derived from the fill (section 3), so an empty bar
reads as a dark recessed channel inside the frame. The gauge switches fill to `fill_low` (cooler, desaturated) below
100 air (5 s) and to `fill_critical` (red tinge) below 60 air (3 s), with a 1 s opacity pulse on the critical layout.

## 3. Art pipeline (one master per bar, both layers derived)

1. **Generate one master per bar** with the imagegen CLI (`npx -y codex-imagegen-mcp@0.2 generate`; the MCP tools do
   not load in this session): frame + full fill together, transparent background, prompt in `art/raw/<id>/notes.md`.
   Several variants, pick by looking at them.
2. **Post-process** the chosen raw to the exact canvas (`tools/images/postprocess.py --size WxH --trim`, palette
   limited).
3. **Derive both layers** with `tools/images/hud_layers.py <master.png> <layers.json> <out-dir>` (new, deterministic):
   - `layers.json` names the canvas size and the interior mask (rectangles in canvas pixels), plus optional fill variants
     (`low`, `critical` colour transforms).
   - `bg.png` = master; interior pixels replaced by the *empty* version of the master's interior (same pixels darkened
     and desaturated, fully opaque), so the empty channel keeps the fill's texture.
   - `fill.png` = transparent canvas of the same size; only the master's interior pixels copied in.
   - `fill_<variant>.png` = `fill.png` with the variant transform.
   - It also writes `overlay-check.png` (bg + fill at 0 %, 50 %, 100 %, 8x nearest) for the human check.
   - It fails if the master has a transparent pixel inside the interior (a hole would show through) or if the frame ring
     is empty.
4. The derived PNGs are the datagen inputs in `tools/datagen/src/main/resources/dev/tfp/datagen/hud/` together with
   their `layers.json`. Never edited by hand: rerun the script.
5. Look at every master, every derived layer and every overlay check with the Read tool.

## 4. Datagen changes

- `HudBarArt` (TFC slices) is replaced by `HudArt`: loads each bar's `bg`/`fill`(+variants) PNGs and `layers.json`
  from the resources, exposes canvas size, interior mask and first opaque row.
- `HudLayouts.BARS` becomes a table of `BarSpec(id, canvas, barX, barY, text?, shownWhile?, variants)`; images are
  emitted with `layer`, `y = barY + firstOpaqueRow`, the same `x` for both layers, `split = interior width`
  (one frame per pixel column), `split-type: left`.
- Gauge: three fill images in one layout with element `conditions` on `tfp_air` (normal >= 100, low 60..99,
  critical < 60); the critical one in its own layout with `animations: {duration: 20, opacity-equation: ...}`.
- Vignette: a `single` image, two layouts (top-left; top-right uses `flip: [x]` of the same PNG) in `tfp_bars_hud`
  with HUD gui anchors 0/0 and 100/0, condition `tfp_air < 300`.
- New placeholders/listeners (catalogue rows + `integration/betterhud/HudValues`): number `tfp_air_seconds`
  (`ceil(air / 20)`), listener `tfp_ambient_temp` ((t + 20) / 60 clamped to 0..1).
- Catalogue: section 1 rows (vignette, thermometer), section 5 rewritten as the layer table above, counts updated.

## 5. Tests

`tools/datagen` (`HudArtTest`, `HudLayoutsTest`):
- per bar: bg and every fill variant have identical dimensions;
- every opaque fill pixel is inside the interior mask; no fill pixel lies on a frame (non-interior opaque bg) pixel;
- every interior pixel is opaque in bg and in the full fill (no gaps, nothing shows through);
- the frame ring (bg opaque pixels outside the interior) is non-empty;
- emitted layout: both layers same `x`; each image `y = barY + firstOpaqueRow`; `layer` bg < fill < text;
  split-type left and split = interior width;
- placement: no bar overlaps the XP row (bottom <= H-31), the hotbar, the armour row (left column top >= H-40) or the
  XP number; the gauge and vignette are conditioned on `tfp_air < 300`; vignette corners never reach the hotbar rows;
- the PNG bytes in the pack are the resource bytes (no re-encoding drift);
- `tools/images/hud_layers.py` is deterministic (same input, same bytes) — checked by a datagen test that recomputes
  the masks from the PNGs, and by running the script twice in CI-free manual check (`notes.md`).

## 6. Screenshot checks (headless client, delegated to a Haiku agent)

GUI scale 2 and 3, Paper 1.21.11, RCON as in `plans/gaps.md` (full / low / submerged): crop the hotbar region and
the top corners; check at 8x zoom that the fill never covers the frame, that the empty channel shows at 0 %, that the
gauge appears only under water and switches to low/critical, that the vignette stays out of the hotbar and health
rows, that text sits inside the health bar.

## 7. Order of work and commits

1. This plan (commit).
2. `tools/images/hud_layers.py` + its use on the masters; raws, prompts, layers.json, derived PNGs (commit).
3. Datagen `HudArt`, layouts, placeholders/listeners, tests, catalogue (commit).
4. Gaps/ledger, screenshot instructions (commit, push).
