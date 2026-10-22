# HUD layered redesign (two-layer bars)

Owner request, 2026-10-22: redesign every HUD image from scratch. Every bar is two layers that overlay pixel-perfect:
a **background** (empty bar: frame + empty interior) drawn behind, and a **foreground fill** (interior only, no frame)
drawn in front and clipped by the current value. Includes the air bar above food and thirst. Step 1 (TFC geometry, air hidden
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

Owner feedback (2026-10-22, applied): **plain style** close to TFC's own bars (1 px dark frame, flat fill with one
lighter and one darker row, 3-4 colours per bar, no ornaments, ripples, glows or busy motifs) and a **compact air bar**
directly above the food and thirst bars (no large gauge, no screen overlay).

GUI pixels from the bottom-centre anchor (`H` = GUI height). Vanilla: XP bar H-29..H-25, hotbar H-22..H-1, armour row
H-49..H-41 (left), XP number centred around H-35..H-28.

| element | id (catalogue) | canvas | interior (fill mask) | place | layers | value |
|---|---|---|---|---|---|---|
| health | `m3-surv-06-health-bar` | 81x10 | x 1..79, y 1..8 | x centre-90..-10, rows H-40..H-31 | bg, fill, text "850/850" (inside, TFC shadow) | `tfp_health` / `tfp_max_health` |
| hunger | `m3-surv-06-hunger-bar` | 81x7 | x 1..79, y 1..5 | x centre+11..+91, rows H-37..H-31 | bg, fill | listener `tfp_hunger` |
| thirst | `m3-surv-06-thirst-bar` | 81x7 | x 1..79, y 1..5 | same x, rows H-45..H-39 | bg, fill | listener `tfp_thirst` |
| air | `m3-surv-06-air-bar` | 81x7 | x 1..79, y 1..5 | same x, rows H-53..H-47 (directly above thirst) | bg, fill (simple bubble row), fill_critical (red tint below 60 air) | listener `tfp_air`; shown while `tfp_air < 300` |
| ambient temperature | `m2-clim-03-ambient-temp-bar` | 64x8 | tube x 1..62 y 2..5 + bulb | top-left under the date (screen 6,13) | bg, fill | listener `tfp_ambient_temp` ((t + 20) / 60, clamped) |
| date and season, temperature text | `m2-cal-02-date-readout`, `m2-clim-03-ambient-temp-readout` | text on a plain plate 124x22 (`m2-cal-02-info-plate`, single layer) | none | top-left | plate, text | text |
| held temperature popup | `m3-surv-06-held-temp-popup` | text only | none | centre, H-60 | text | text |
| compass | BetterHud demo | none | none | none | switched off by the installer (step 1) | none |

Low/empty state: the background interior is the darkened, desaturated version of the fill (section 3), so an empty bar
reads as a dark channel inside the frame and a low bar as a short coloured stub at its left.

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
   - `fill_<variant>.png` = `fill.png` with the variant transform (the air bar's red `critical` fill).
   - `"ring": "shade"` darkens frame pixels that are not already dark, so the frame is one plain dark border even where
     downscaling blended the outline with the fill.
   - It also writes `overlay-check.png` (bg + fill at 0 %, 50 %, 100 %, 8x nearest) for the human check.
   - It fails if the master has a transparent pixel inside the interior (a hole would show through) or if the frame ring
     is empty.
4. The derived PNGs are the datagen inputs in `tools/datagen/src/main/resources/dev/tfp/datagen/hud/` together with
   their `layers.json`. Never edited by hand: rerun the script.
5. Look at every master, every derived layer and every overlay check with the Read tool.

## 4. Datagen changes

- `HudBarArt` (TFC slices) is replaced by `HudArt`: loads each bar's `bg`/`fill`(+variants) PNGs and `layers.json`
  from the resources, exposes canvas size, interior mask and first opaque row.
- `HudLayouts.BAR_SPECS` holds `BarSpec(art, x, y, hud, texts, shownWhile, variants, split)`; layouts get
  `offset: left`, both layers the same `x`, `y = barY + firstOpaqueRow`, `layer` 1 (bg) / 2 (fill) / 3 (text), and
  `split = interior width` (one frame per pixel column), `split-type: left`. A variant is an extra fill image in the same
  layout with element `conditions`; the plain fill gets the negated condition.
- `HudLayouts.OVERLAYS`: single-layer images (the info plate), written into the info HUD of `BetterHudPack`.
- New listener `tfp_ambient_temp` (`integration/betterhud/HudValues.thermometer`).
- Catalogue: rows for the thermometer bar and the plate (kind `overlay`), section 5 = the layer table, counts updated.

## 5. Tests

`tools/datagen` (`HudArtTest`, `HudLayoutsTest`):
- per bar: bg and every fill variant have identical dimensions;
- every opaque fill pixel is inside the interior mask; no fill pixel lies on a frame (non-interior opaque bg) pixel;
- every interior pixel is opaque in bg and in the full fill (no gaps, nothing shows through);
- the frame ring (bg opaque pixels outside the interior) is non-empty;
- emitted layout: both layers same `x`; each image `y = barY + firstOpaqueRow`; `layer` bg < fill < text;
  split-type left and split = interior width;
- placement: no bar overlaps the XP row (bottom <= H-31), the hotbar, the armour row (left column top >= H-48) or the
  XP number; the right column has one x, one size and one empty row between bars, air on top; the air bar is
  conditioned on `tfp_air < 300` and swaps to the red fill below 60;
- the PNG bytes in the pack are the resource bytes (no re-encoding drift);
- `tools/images/hud_layers.py` is deterministic (same input, same bytes) — checked by a datagen test that recomputes
  the masks from the PNGs, and by running the script twice in CI-free manual check (`notes.md`).

## 6. Screenshot checks (headless client, delegated to a Haiku agent)

GUI scale 2 and 3, Paper 1.21.11, RCON as in `plans/gaps.md` (full / low / submerged): crop the hotbar region and
the top-left corner; check at 8x zoom that the fill never covers the frame, that the empty channel shows at 0 %, that
the air bar appears only under water directly above thirst and turns red below 3 s, that the text sits inside the
health bar and on the plate.

## 7. Order of work and commits

1. This plan (commit).
2. `tools/images/hud_layers.py` + its use on the masters; raws, prompts, layers.json, derived PNGs (commit).
3. Datagen `HudArt`, layouts, placeholders/listeners, tests, catalogue (commit).
4. Gaps/ledger, screenshot instructions (commit, push).
