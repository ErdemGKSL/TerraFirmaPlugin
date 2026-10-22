# hud-defringe

Owner report (2026-10-22): the black outline of the health bar (and of every other HUD bar) had a pink/red fringe.

Inspection (`python3 -I` + PIL over every master and derived layer): no semi-transparent pixels anywhere (postprocess.py
already binarizes alpha at 128), but the outermost opaque ring of every bar background held fill-coloured pixels at
the bar ends: the box/nearest downscale of a generated raw loses the 1 px black end caps of a thin bar, so the master's
first and last columns (rows 1..h-2) were fill colours (health (252,56,59), (198,20,28), (154,8,20)), and the old
`"ring": "shade"` only darkened them to (59,6,8)/(76,17,18): a dark-red end column on a black frame, the fringe. The same
happened on hunger (dark orange), thirst/air (dark blue), the thermometer (blue/red tube ends). The masters also kept
near-black off-hues ((4,0,0), (5,2,0), (1,9,18)) in the outline.

Fix in the pipeline (`tools/images/hud_layers.py`, `tools/images/hud_palette.py`), not by hand:
- alpha binarized on load (>= 128 opaque, else fully transparent);
- the interior quantized to a few of the master's OWN colours (`"colors"`, near-duplicates within RGB distance 16 merged
  into the most frequent; no averaging, no dithering);
- `"ring": "outline"`: every frame pixel (opaque, outside the interior) becomes pure black (0,0,0);
- one pinned palette per bar (`"palette"` in `layers.json`, black first, same index order in master/bg/fills), saved as
  4-bit (8-bit for the 33-colour thermometer ramp) indexed PNGs, index 0 the only transparent entry, compress_level 9 +
  optimize (oxipng/zopflipng when installed; neither is in this container);
- the cleaned master is written next to the layers (`<name>_master.png`), so a test can check fill over bg == master.
Regeneration was not needed: every master was recoverable.

Records: `health_corner_before_8x.png` (the old bg + fill, left 12 columns, 8x: dark-red end column) and
`health_corner_after_8x.png` (pure black end column).

Tests (`tools/datagen` `HudArtTest`): no semi-transparent pixel in any HUD PNG; the outer ring of every background and
overlay is dark neutral (max channel <= 64, spread <= 12); every bar frame pixel is pure black; each layer has a small
palette; every HUD PNG is indexed colour with one transparent entry; layers share the palette order and fill over bg
reproduces the cleaned master.

Bytes: the 13 HUD PNGs that existed before went from 4649 to 3083 bytes; the new files (armour layers, 6 cleaned masters)
add 1448.
