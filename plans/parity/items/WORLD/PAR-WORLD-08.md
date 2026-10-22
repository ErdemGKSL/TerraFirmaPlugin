---
id: PAR-WORLD-08
title: "Disc, pipe and kaolin veins; montane second pass; ore distribution statistics"
area: WORLD
status: open
wave: 19
deps: []
size: M
aliases: ["M2-GEN-10"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/vein/DiscVeinFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/vein/PipeVeinFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/vein/KaolinDiscVeinFeature.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/ore/**"
  - "core/src/test/java/dev/tfp/core/worldgen/ore/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/OreVeinResources.java"
ui: false
in_game_steps: []
plan:
  - "Port DiscVein, PipeVein and KaolinDiscVein with golden placement masks; the montane second pass; a 100-chunk distribution statistic per vein type compared with TFC (±10%)."
tests:
  - "core: OreVeinGoldenTest extended"
  - "core: distribution test"
done_criteria:
  - "All vein types generate; stats within 10% of TFC"
---

# PAR-WORLD-08: Disc, pipe and kaolin veins; montane second pass; ore distribution statistics

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
