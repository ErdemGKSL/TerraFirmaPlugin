---
id: PAR-HEAT-29
title: "Dead torch outlines closer to TFC: widen the hitbox candidate dump to every small no-collision vanilla block and re-pick the dead torch carriers"
area: HEAT
status: open
wave: 16
deps: [PAR-HEAT-21]
size: M
aliases: ["follow-up of PAR-HEAT-21 (manager side check 2026-11-17: the dead wall torch selection box is much smaller than TFC's)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/DeadTorchBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/DeadWallTorchBlock.java"
  - "src/main/resources/assets/tfc/blockstates/dead_torch.json"
  - "src/main/resources/assets/tfc/blockstates/dead_wall_torch.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/TfcShapes.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/VanillaCandidates.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/light/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/light/**"
  - "content/generated/content/craftengine/tfp/configuration/light/**"
  - "docs/verification/PAR-HEAT-29/**"
ui: true
in_game_steps:
  - "weather clear 1000000; gamemode survival Bot; tfp debug thirst Bot 100; tfp debug hunger Bot 20"
  - "build a stone pillar after its chunks have loaded; place a tfp:torch on the floor and one on each face of the pillar; gamemode creative Bot; tfp time add days 4; tp Bot 300 blocks away, wait 8 s, tp back; gamemode survival Bot; tfp debug thirst Bot 100; tfp debug hunger Bot 20"
  - "next to them set a real minecraft:wall_torch and minecraft:torch for reference; from 1.5 blocks in front and from the side, look at each dead torch and the reference: screenshots of the selection outline at GUI scale 2 and 3"
  - "walk through a one-wide stone tunnel lined with dead wall torches at head and foot height: nothing blocks movement"
  - "right-click each dead torch with a tfp:torch: it is lit again and the held torch is not placed"
plan:
  - "Why (PAR-HEAT-21 unblock, 2026-11-17): the manager's side check asked for the dead wall torch selection box to match TFC (tfc:dead_wall_torch[facing=north] selection 5.5,3,11 to 10.5,13,16; tfc:dead_torch 6,0,6 to 10,10,10; no collision). PAR-HEAT-21 kept the pressed oak wall button (outline 6 x 4 x 1, IoU about 0.08) because no closer free carrier exists among the 59 blocks of tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/vanilla-shapes-*.json. That dump never listed most small no-collision wall blocks, so TorchCarrierTest.noFreeCarrierHasACloserDeadTorchOutline only proves the claim for that subset."
  - "Re-dump (scratch dumper outside the repo, each Paper jar 1.21.4, 1.21.11, 26.1.2, 26.3) and add to carrier-policy.json with a reasoned presence every no-collision, no-light wall or floor block with a small outline, at least: the ten coral wall fans and coral fans (live and dead, waterlogged both ways; the live ones die out of water by a scheduled tick), lever, tripwire_hook, the wall signs and wall hanging signs, wall banners, vine, glow_lichen (light), cocoa (collision: reference), sculk_vein, hanging_roots, dead_bush, the small flowers. Check TFC's presence of each in /opt/tfc (recipes disabled or replaced, worldgen) before marking it absent; rough IoU against the TFC dead wall torch: lever wall about 0.69 (player-placed in TFC unless its recipe is disabled), tripwire_hook wall about 0.40, a dead coral wall fan about 0.29, the pressed button 0.08."
  - "Let HitboxAllocation pick again (weights unchanged unless needed; addingTheTorchesMovesNoOtherCarrier must stay true or the moved carriers are re-pinned in their own tests) and keep noFreeCarrierHasACloserDeadTorchOutline as the guard over the wider dump. If the new carrier is not a button, check the render yaw of the dead wall torch item display in TorchBlocks for its facing convention and its client prediction on right-click (coral fans and signs predict nothing; a sign opens no editor when the click is consumed)."
  - "carrier-policy.json is also touched by PAR-INFRA-51 (blocked): edit only the new rows and the PAR-HEAT-21 rows, keep theirs on rebase. Regenerate content (./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc), update the ledger's dead torch outlines divergence row with the new outline and IoU."
tests:
  - "datagen: TorchCarrierTest over the wider dump (chosen carrier light 0, no collision, best IoU among free states); VanillaCandidatesTest and CarrierPolicyRecipeTest/CarrierPolicyWorldgenTest pass with the new blocks; TorchBlocksTest pins the yaw for the new carrier"
done_criteria:
  - "The dead wall torch and dead floor torch carriers are the closest free outlines to TFC's in the widened dump (test), with no collision and no light"
  - "In game the dead torches show TFC's dead look with the new outline next to a vanilla torch reference, and a one-wide tunnel lined with them is passable (screenshots GUI 2 and 3, docs/verification/PAR-HEAT-29/)"
---

# PAR-HEAT-29: Dead torch outlines closer to TFC

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
