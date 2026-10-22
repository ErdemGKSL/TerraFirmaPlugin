---
id: PAR-FARM-01
title: "Farmland block: hydration from water (async), nutrients, hoe overlay readout"
area: FARM
status: open
wave: 27
deps: [PAR-INFRA-02, PAR-GATHER-05, PAR-INFRA-11]
size: M
aliases: ["M3-FARM-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/soil/FarmlandBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/FarmlandBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/IFarmland.java"
  - "src/main/java/net/dries007/tfc/client/HoeOverlays.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/farm/**"
  - "core/src/test/java/dev/tfp/core/farm/**"
  - "core/src/test/resources/dev/tfp/core/farm/**"
  - "paper/src/main/java/dev/tfp/paper/farm/**"
  - "paper/src/test/java/dev/tfp/paper/farm/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/farm/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/farm/**"
  - "content/generated/content/craftengine/tfp/configuration/farm/**"
ui: true
in_game_steps:
  - "hoe dirt near water (PAR-GATHER-05)"
  - "hold hoe and look at it; screenshot actionbar"
plan:
  - "CE farmland per soil with hydration visual states; N/P/K state per block (FarmlandState) with lazy water reserve; hydration from nearby water decided async (findMinCostWater snapshot); holding a hoe and looking at farmland shows N/P/K/hydration on the actionbar (replaces HoeOverlays)."
tests:
  - "core: farm goldens exist"
  - "paper: hydration decision snapshot"
done_criteria:
  - "Farmland next to water shows hydrated and the actionbar readout"
---

# PAR-FARM-01: Farmland block: hydration from water (async), nutrients, hoe overlay readout

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
