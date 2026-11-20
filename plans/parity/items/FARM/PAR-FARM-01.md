---
id: PAR-FARM-01
title: "Farmland block: hydration from water (async), nutrients, hoe overlay readout"
area: FARM
status: done
wave: 27
deps: [PAR-INFRA-02, PAR-GATHER-05, PAR-INFRA-11, PAR-INFRA-13]
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
  - "Done: core/farm/FarmlandHydration (min/max/now hydration), paper/farm (FarmlandDevice kind tfp:farmland with lazy water reserve and a codec, HydrationSnapshot: findMinCostWater on a region-thread snapshot, FarmlandReadout computed off-thread, HoeOverlay: action bar every 10 ticks while a hoe is held and farmland is in view, FarmModule, lang keys farm.hydration and farm.nutrients). Tests: FarmlandHydrationTest (4), FarmlandDeviceTest (5). The tfp:farmland/<soil> CE blocks and hydration visual states are PAR-FARM-19 (the in-game check moves there; SoilListener already tills to those ids once they exist). Pending in-game check (container cannot run client)."
  - "CE farmland per soil with hydration visual states; N/P/K state per block (FarmlandState) with lazy water reserve; hydration from nearby water decided async (findMinCostWater snapshot); holding a hoe and looking at farmland shows N/P/K/hydration on the actionbar (replaces HoeOverlays)."
tests:
  - "core: farm goldens exist"
  - "paper: hydration decision snapshot"
done_criteria:
  - "Farmland next to water shows hydrated and the actionbar readout"
---

# PAR-FARM-01: Farmland block: hydration from water (async), nutrients, hoe overlay readout

> see_not_parity: NP-UI-01, NP-CLIENT-03 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
