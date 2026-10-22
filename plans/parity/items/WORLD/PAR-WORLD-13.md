---
id: PAR-WORLD-13
title: "Remaining plant features: vines, kelp, epiphytes, creeping plants, cacti, algae, atoll corals"
area: WORLD
status: open
wave: 25
deps: [PAR-WORLD-12]
size: M
aliases: ["M2-GEN-13 (features)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/plant"
  - "src/main/java/net/dries007/tfc/world/feature/coral"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/KelpTreeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/BranchingCactusBlock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/plant/**"
  - "core/src/test/java/dev/tfp/core/worldgen/plant/**"
  - "core/src/test/resources/dev/tfp/core/flora/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/PlantResources.java"
ui: false
in_game_steps: []
plan:
  - "Port the 96 open features (berry bush and fruit tree placement stays in PAR-FARM items) with oracle goldens; forest groundcover."
tests:
  - "core: PlantGoldenTest + open_gaps.txt shrinks to the farm-owned ids"
done_criteria:
  - "Open gaps list only contains ids owned by PAR-FARM items"
---

# PAR-WORLD-13: Remaining plant features: vines, kelp, epiphytes, creeping plants, cacti, algae, atoll corals

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
