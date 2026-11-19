---
id: PAR-FARM-07
title: "Spreading bushes, canes and cranberries"
area: FARM
status: done
wave: 30
deps: [PAR-FARM-06]
size: M
aliases: ["M3-FARM-03 (spreading)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingBushBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingCaneBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/WaterloggedBerryBushBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/berry/spread/**"
  - "paper/src/test/java/dev/tfp/paper/berry/spread/**"
  - "core/src/main/java/dev/tfp/core/berry/Spread*"
  - "core/src/test/java/dev/tfp/core/berry/Spread*"
ui: true
in_game_steps:
  - "place raspberry bush; tfp time add months 4; screenshot"
plan:
  - "Spreading catch-up (open in core), cane geometry, cranberry in water."
  - "Done (core + Paper glue): core/berry SpreadTicker (lazy catch-up, per-cycle growth results) over SpreadRules; paper/berry/spread device kind tfp:spreading_bush (bush, cane, cranberry, dead bush, dead cane), catch-up kind, SpreadModule. The CE blocks, models, hitbox carriers, loot, worldgen could not be done inside files_owned (hitbox files are shared, content/generated is a datagen output): follow-up PAR-FARM-15. Pending in-game check (container cannot run client)"
tests:
  - "core: SpreadingGoldenTest + catch-up"
  - "core: SpreadTickerTest (13); paper: SpreadingBushMockBukkitTest (12)"
done_criteria:
  - "A raspberry bush spreads canes over a season"
---

# PAR-FARM-07: Spreading bushes, canes and cranberries

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Done: core `SpreadTicker` (+ `SpreadTickerTest`, 13), `paper/berry/spread` (`SpreadingBushDevice`, `SpreadEffects`, `PaperSpreadEffects`, `SpreadModule`) with `SpreadingBushMockBukkitTest` (12); ledger rows and divergences; CE visuals split to PAR-FARM-15. Pending in-game check (container cannot run client).
