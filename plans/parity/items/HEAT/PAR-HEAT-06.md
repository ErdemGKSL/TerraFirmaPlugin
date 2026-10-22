---
id: PAR-HEAT-06
title: "Firepit block: placement, fuel, lazy temperature, ash, extinguish by rain and water"
area: HEAT
status: open
wave: 13
deps: [PAR-HEAT-05, PAR-INFRA-08, PAR-HEAT-04, PAR-CLIM-02, PAR-INFRA-13]
size: M
aliases: ["M3-HEAT-03 (firepit)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/FirepitBlockEntity.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/firepit/**"
  - "core/src/test/java/dev/tfp/core/firepit/**"
  - "core/src/test/resources/dev/tfp/core/firepit/**"
  - "paper/src/main/java/dev/tfp/paper/firepit/**"
  - "paper/src/test/java/dev/tfp/paper/firepit/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/Firepit.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/FirepitTest.java"
  - "content/generated/content/craftengine/tfp/devices/firepit.yml"
  - "content/generated/content/craftengine/tfp/configuration/devices/firepit.yml"
  - "core/src/main/java/dev/tfp/core/cooking/Firepit*"
  - "core/src/test/java/dev/tfp/core/cooking/Firepit*"
  - "core/src/main/java/dev/tfp/core/cooking/Cooking*"
  - "core/src/test/java/dev/tfp/core/cooking/Cooking*"
ui: true
in_game_steps:
  - "tfp debug place tfp:firepit[lit=true]"
  - "right-click with ce item give Bot tfp:food/beef"
  - "tfp time add hours 1"
  - "tfp debug device"
plan:
  - "Current: core/cooking FirepitRules (lazy advance) golden-tested; firepit.yml generated outside configuration/ (not loaded)."
  - "Device kind `firepit` (PAR-INFRA-02): fuel slots, input, outputs as DeviceInventory; FirepitState with lastUpdateTick; advance on interaction/GUI open/chunk load."
  - "Lit/unlit visual states with luminance; smoke particle only on interaction; rain and water bucket extinguish (precipitationAt from PAR-CLIM-02)."
  - "Cooking via HeatingService (PAR-HEAT-04); ash and dirtiness from fuel purity as TFC AbstractFirepitBlockEntity."
  - "Move firepit.yml into configuration/devices/ so CE loads it."
tests:
  - "paper: MockBukkit fuel burn catch-up over 2 in-game hours equals FirepitRules"
  - "itest: `firepit.cook` cooks raw meat"
done_criteria:
  - "Lit firepit cooks raw beef in the expected TFC time (self-test + screenshot of lit visual)"
---

# PAR-HEAT-06: Firepit block: placement, fuel, lazy temperature, ash, extinguish by rain and water

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
