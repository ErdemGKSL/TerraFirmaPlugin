---
id: PAR-FARM-06
title: "Stationary berry bushes: lifecycle, picking, propagation"
area: FARM
status: open
wave: 29
deps: [PAR-INFRA-11, PAR-INFRA-08]
size: M
aliases: ["M3-FARM-03"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/StationaryBerryBushBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BerryBushBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/fruit/Lifecycle.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/berry/**"
  - "core/src/test/java/dev/tfp/core/berry/**"
  - "core/src/test/resources/dev/tfp/core/berry/**"
  - "paper/src/main/java/dev/tfp/paper/berry/**"
  - "paper/src/test/java/dev/tfp/paper/berry/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/berry/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/berry/**"
  - "content/generated/content/craftengine/tfp/configuration/berry/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:plant/blueberry_bush[lifecycle=fruiting]"
  - "right-click; screenshot"
plan:
  - "CE bush blocks (lifecycle x stage states), catch-up kind via BerryRules.catchUp, pick fruit by right-click, worldgen placement of bushes (open gap ids moved from PAR-WORLD-13)."
tests:
  - "core: BerryGoldenTest exists"
done_criteria:
  - "A blueberry bush fruits in season and can be picked"
---

# PAR-FARM-06: Stationary berry bushes: lifecycle, picking, propagation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
