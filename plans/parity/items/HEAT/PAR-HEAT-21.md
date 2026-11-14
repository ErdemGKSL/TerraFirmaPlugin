---
id: PAR-HEAT-21
title: "Torches loaded in game: hitbox carriers, configuration/light/torch.yml, the dead torch look and the in-game burnout check"
area: HEAT
status: open
wave: 15
deps: [PAR-HEAT-10]
size: S
aliases: ["follow-up of PAR-HEAT-10 (the CraftEngine entries need the hitbox demands and GeneratedCarrierTest; the in-game criterion)"]
tfc_refs:
  - "src/main/resources/assets/tfc/blockstates/torch.json"
  - "src/main/resources/assets/tfc/blockstates/wall_torch.json"
  - "src/main/resources/assets/tfc/blockstates/dead_torch.json"
  - "src/main/resources/assets/tfc/blockstates/dead_wall_torch.json"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/light/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/light/**"
  - "content/generated/content/craftengine/tfp/configuration/light/**"
  - "docs/verification/PAR-HEAT-10/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:torch 4"
  - "place one on the floor and one on a wall; both show the vanilla torch with its flame and light 14 (F3)"
  - "right-click the floor torch with a stick: one stick is taken and a tfp:torch given"
  - "tfp time add days 4 (TFC torchTicks 86,400 = 3.6 days of 24,000 ticks)"
  - "relog / tp away (out of view distance) and back"
  - "screenshot: both are dead torches (torch_off look, no flame, no light), the wall one still facing the wall"
  - "right-click the dead floor torch with a tfp:torch: it is lit again and the held torch is not placed"
  - "place a fresh tfp:torch, right-click it with another tfp:torch (normal and sneaking): no second torch is placed against the lit one"
  - "Screenshots at GUI scale 2 and 3 of the lit and the dead torch, standing and on the wall"
plan:
  - "Current: PAR-HEAT-10 ships the rules (core/light TorchRules), the runtime (paper/light: device kinds tfp:torch and tfp:wall_torch, the burnout pipeline, relight, the held torch's weak start) and the generator tools/datagen light/TorchBlocks.run(tfc, out, carriers), which is not called yet: an explicit appearance state needs a HitboxDemands entry and GeneratedCarrierTest pins the checked block ids."
  - "Add the demands: tfp:torch standing and tfp:wall_torch per facing with the vanilla look (TFC draws vanilla's torch models; the carriers are the vanilla torch and wall torch states, light 14), tfp:dead_torch and tfp:dead_wall_torch per facing with TFC's dead torch models (or hitbox-only with the tfp:render/dead_* item display when the selector has no free torch-shaped carrier, e.g. the unlit redstone torches as CraftEngine's own amethyst torch does). Add TFC's torch shapes to TfcShapes if the selector does not know them."
  - "Call TorchBlocks.run from Datagen.generate (one run line) with carriers from HitboxAllocation.agreed, add the four ids to GeneratedCarrierTest, regenerate content (configuration/light/torch.yml, the dead torch models, torch_off.png, the loot tables blocks/{torch,wall_torch,dead_torch,dead_wall_torch}, tfp_torch lang)."
  - "Check in game that CraftEngine's wall_block_item + block_item order places the wall torch on a side face, that cancelling PlayerInteractEvent stops CraftEngine placing the held torch against a dead torch (TorchListener), that the device useWithItem consumes the click on a lit torch before CraftEngine places the held torch, that a sneak-click with a held torch on a lit torch resets it and places nothing as TFC TorchItem.useOn does (handle it in TorchListener if the device framework skips useWithItem while sneaking), and the rotation of the dead wall torch display (TorchBlocks uses the composter's half turn plus TFC's blockstate y)."
tests:
  - "datagen: GeneratedCarrierTest covers the four torch ids; TorchBlocksTest with the agreed carriers"
done_criteria:
  - "A torch placed and `tfp time add days 4` later is dead after chunk reload (screenshot, docs/verification/PAR-HEAT-10/)"
  - "The dead torch shows TFC's torch_off texture and gives no light; relighting with a held torch works"
---

# PAR-HEAT-21: Torches loaded in game

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
