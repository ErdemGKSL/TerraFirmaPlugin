---
id: PAR-METAL-08
title: "Charcoal forge block entity: creation from a charcoal pile, lighting, continuous heating of held items"
area: METAL
status: open
wave: 29
deps: [PAR-INFRA-02, PAR-HEAT-05, PAR-HEAT-04, PAR-INFRA-13]
size: M
aliases: ["M4-FORGE-01", "owner: block-entity continuous heating"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/charcoalforge/**"
  - "core/src/test/java/dev/tfp/core/charcoalforge/**"
  - "core/src/test/resources/dev/tfp/core/charcoalforge/**"
  - "paper/src/main/java/dev/tfp/paper/charcoalforge/**"
  - "paper/src/test/java/dev/tfp/paper/charcoalforge/**"
  - "paper/src/main/java/dev/tfp/paper/heat/ForgeHeatListener.java"
  - "paper/src/test/java/dev/tfp/paper/heat/ForgeHeat*"
ui: true
in_game_steps:
  - "tfp debug place tfp:charcoal_forge[heat_level=0]"
  - "add charcoal, light with firestarter"
  - "insert copper ingot; wait 60 s"
  - "tfp debug device; screenshot heat level"
plan:
  - "Current: click catch-up heating verified in-game (spikes/run/catch4); CharcoalForgeRules golden-tested; 8 heat states load."
  - "Device kind `charcoal_forge`: 5 fuel + 5 input + 4 extra slots; created by lighting a 7-layer charcoal pile with a valid structure (TFC isValid); CharcoalForgeState persisted."
  - "Continuous heating without a ticker: each input item keeps its own heat record; the device's `advance(now)` applies the forge temperature curve to every slot item from lastUpdateTick (lazy, closed form per fuel segment), run on GUI open, interaction, chunk load and a slow sweep while a player is within 16 blocks; heat_level state updated from the curve."
  - "Remove the click-heating ForgeHeatListener once the controller exists; update the ledger divergence."
tests:
  - "core: CharcoalForgeRulesTest lazy == stepped for slot items"
  - "paper: MockBukkit advance heats a slot item to the forge temperature"
done_criteria:
  - "An ingot left in the forge for 60 s reaches the forge temperature without clicks (self-test `forge.continuous`)"
---

# PAR-METAL-08: Charcoal forge block entity: creation from a charcoal pile, lighting, continuous heating of held items

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
