# PAR-SURV-07 in-game check

- Date: 2026-11-07
- Code: the PAR-SURV-07 commit (base `6e51fd2`), jar built from this worktree with regenerated content.
- Server: Paper 1.21.11-132 (`c5eb079`), CraftEngine 26.9.2, BetterModel, BetterHud, PacketEvents 2.14.0, TFP world
  generator (`spikes/client/server.sh`, `TFP_WORLD=1`).
- Client: vanilla 1.21.11 under Xvfb, 1280 x 720 (`spikes/client/session.sh`), pack applied on join.
- Run by the worker session itself (no subagent tool was available in this session).

## Steps

1. `gamemode survival Bot`; press E (inventory). Screenshot `inv_s2.png` / `inv_s3.png`.
2. Left click outside the window on the drawn tab column. The calendar page opens (`cal2_s2.png`, `cal_s3.png`; the scale 2
   shot after `tfp time set midnight` shows the night sprite).
3. Click the nutrition tab button (chest slot 29), then the climate button (slot 30): `nut2_s2.png`, `cli2_s2.png`,
   `nut_s3.png`, `cli_s3.png`.
4. Repeated at GUI scale 3 (`MC_OPTIONS="guiScale:3"`).

## Pixel alignment

`align.py` (scratchpad) samples every opaque pixel of the generated page background at the centre of its scale x scale block
at the expected window origin (`((W/s - 176) / 2, (H/s - 186) / 2)`) and compares with a tolerance of 6 per channel; text,
overlays and items account for the misses. A one-GUI-pixel shift in any direction loses 8 to 19 points, so the zero shift is
pinned.

| page | scale | origin (px) | match | worst 1-px shift |
|---|---|---|---|---|
| calendar | 2 | (464, 174) | 0.937 | 0.843 |
| nutrition | 2 | (464, 174) | 0.920 | 0.732 |
| climate | 2 | (464, 174) | 0.924 | 0.831 |
| calendar | 3 | (375, 81) | 0.921 | 0.828 |
| nutrition | 3 | (375, 81) | 0.905 | 0.717 |
| climate | 3 | (375, 81) | 0.914 | 0.821 |

## Findings

- First run: on the nutrition page every bar after the first, and all text after it, was drawn left of its place
  (`nutrition_before_advance_fix_s2.png`). Cause: the client measures a bitmap glyph's advance from its rightmost
  non-transparent column, so a 46-pixel bar frame in a 93-pixel cell advanced 47, not 94. Fixed in datagen
  (`PlayerTabArt.keepAdvance`: an alpha-1 pixel in the right column of every frame whose right column is empty; the text
  shader discards it) and covered by `PlayerTabArtTest.everyFrameAdvancesItsFullWidth`. Re-checked: pass.
- The inventory screen shows TFC's tab column right of the window at both scales. Vanilla's effect list (with active
  effects) is drawn over it, as it is over anything right of the inventory.

## Verdict

| criterion | result |
|---|---|
| Screenshots of the three pages at GUI scale 2 and 3 aligned | pass |
