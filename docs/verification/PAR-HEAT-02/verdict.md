# PAR-HEAT-02 verification: heat tooltip in TFC colours and the metal line

- Date: 2026-11-13
- Tree: branch `push-to-main/dreamy-heisenberg-f5azym` at 725f771 plus the PAR-HEAT-02 review fix (the commit that adds
  this file; the fix only changes `tfc:chance` results, not the copper ingot).
- Server: Paper 1.21.11 (`spikes/client/server.sh`, `TFP_WORLD=1`, primed and restarted), CraftEngine, BetterModel,
  BetterHud and PacketEvents as in `docs/testing/headless.md`. Log: the module list ends with `heat_tooltip`; no
  `Tooltip refresh failed` and no exception from our code (only PacketEvents' update check, HTTP 403 from the sandbox).
- Client: vanilla 1.21.11 (`spikes/client/session.sh`), GUI scale 2, then a rejoin with `MC_OPTIONS="guiScale:3"`; the pack
  applied 3 s after each join.
- Run by the worker session itself: it had no subagent tool to delegate the run.

## Steps

1. `gamemode survival Bot`, `time set noon`, `weather clear`, `clear Bot`
2. `ce item give Bot tfp:metal/ingot/copper`, `tfp debug heat Bot 700` (level DARK_RED)
3. `E`, hover the ingot in hotbar slot 1 (the heat command repeated just before the hover: the ingot cools a few degrees
   per second and had dropped to two stars during the first attempt)
4. The same at GUI scale 3

## Evidence

- `01-inventory-hover-gui2.png`: inventory window with the tooltip, GUI scale 2.
- `02-tooltip-gui2-3x.png`: the tooltip alone at 3x (nearest neighbour).
- `03-inventory-hover-gui3.png`: inventory window with the tooltip, GUI scale 3.

Pixel colours of the glyph faces in the GUI scale 2 capture: "Dark Red٭٭٭" (170,0,0) = dark_red; " - Can Work"
(255,255,255) = white; "Melts into" and "(at" (170,170,170) = gray; "100 mB of Copper" (255,255,255) = white; "Orange٭٭٭٭"
(255,170,0) = gold. These are TFC's `Heat.DARK_RED` / `Heat.ORANGE` colours and the `§f - Can Work` and
`§7Melts into §f%s§7 (at %s§7)` runs of `IHeatView.addTooltipInfo` and `Tooltips.meltsInto` at c3b4d9536.

## Criteria

| Criterion | Result |
|---|---|
| Screenshot of a hot copper ingot tooltip matching TFC | Pass (01, 02, 03): `Dark Red٭٭٭ - Can Work` (level in dark red, not italic, suffix in white), then `Melts into 100 mB of Copper (at Orange٭٭٭٭)` in grey with the amount in white and the temperature in gold, as the last line. TFC 1.21 has no "Metal:" / "Melts at" lines (the plan's 1.12 wording; see the item's note). The size/weight line above them is PAR-SURV-06's. |
