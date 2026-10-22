---
id: PAR-POT-06
title: "Large vessel (and glazed): nine slots, sealing, preservation, automation"
area: POTTERY
status: open
wave: 26
deps: [PAR-INFRA-03, PAR-POT-02, PAR-INFRA-13]
size: M
aliases: ["M3-POT-03"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/LargeVesselBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/LargeVesselBlockEntity.java"
  - "src/main/java/net/dries007/tfc/client/screen/LargeVesselScreen.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/largevessel/**"
  - "core/src/test/java/dev/tfp/core/largevessel/**"
  - "core/src/test/resources/dev/tfp/core/largevessel/**"
  - "paper/src/main/java/dev/tfp/paper/largevessel/**"
  - "paper/src/test/java/dev/tfp/paper/largevessel/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/largevessel/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/largevessel/**"
  - "content/generated/content/craftengine/tfp/configuration/largevessel/**"
  - "core/src/main/java/dev/tfp/core/vessel/LargeVessel*"
  - "core/src/test/java/dev/tfp/core/vessel/LargeVessel*"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/large_vessel; place"
  - "add meat, click seal"
  - "hover meat after reopening"
  - "hitbox: look at the vessel (open and sealed, plain and one glazed colour): outline is the heavy_core 8x8x8 box (PAR-INFRA-13; wall post if heavy_core was marked unusable); break it: ceramic sound, vessel drops with contents"
plan:
  - "CE block (17 colours) with a 9-slot GUI + seal button; sealing applies `preserved` via LargeVesselRules; redstone seal; hopper automation only when unsealed (InventoryMoveItemEvent); breaking drops the vessel with contents (item list on the item)."
  - "Visual and hitbox: hitbox-only carrier from PAR-INFRA-13 (owner decision: heavy_core[waterlogged=false], fallback lone cobblestone wall post), the vessel look per colour, facing and sealed state from entity_renderer; never choose a carrier here."
tests:
  - "paper: MockBukkit seal applies trait"
done_criteria:
  - "Sealed meat shows the Preserved trait and a later expiry"
---

# PAR-POT-06: Large vessel (and glazed): nine slots, sealing, preservation, automation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
