---
id: PAR-HEAT-29
title: "Dead torch outlines closer to TFC: widen the hitbox candidate dump to every small no-collision vanilla block and re-pick the dead torch carriers"
area: HEAT
status: done
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
  - "OWNER REQUEST (2026-11-17): the pressed button does not satisfy the owner; find a better outline, ideally the real torch shape. New idea to evaluate FIRST: CraftEngine ships a light system (block.light-system.enable, default true, Config.enableBlockLightSystem; BukkitCEWorld.updateLight -> LightUtils.updateChunkLight recomputes server light with the CE block luminance and sends light packets), so a torch-shaped carrier that emits light client-side (vanilla soul_torch / soul_wall_torch light 10, copper_torch / copper_wall_torch where the version has them, or minecraft:torch itself) with luminance: 0 on the dead torch may read 0 in the client. The carrier must be a state no TFC world and no player can produce (check carrier-policy: TFC has no nether, vanilla torch recipe disabled; check soul/copper torch recipes and worldgen in /opt/tfc), and its explicit state gets the torch_off model (an explicit state + model rewrites that state for every client, so no other CE block may use it). That also removes the item-display look and may fix PAR-HEAT-28. Spike it in game on 1.21.11 and 26.3: place, break, relog, chunk reload, F3 client light = 0, a dark cave, no flicker when a neighbour changes; the wiki says client light issues are normal and partial, so keep the pressed-button fallback if any check fails and record the result in docs/verification/PAR-HEAT-29/verdict.md and the ledger divergence row."
  - "Why (PAR-HEAT-21 unblock, 2026-11-17): the manager's side check asked for the dead wall torch selection box to match TFC (tfc:dead_wall_torch[facing=north] selection 5.5,3,11 to 10.5,13,16; tfc:dead_torch 6,0,6 to 10,10,10; no collision). PAR-HEAT-21 kept the pressed oak wall button (outline 6 x 4 x 1, IoU about 0.08) because no closer free carrier exists among the 59 blocks of tools/datagen/src/main/resources/dev/tfp/datagen/hitbox/vanilla-shapes-*.json. That dump never listed most small no-collision wall blocks, so TorchCarrierTest.noFreeCarrierHasACloserDeadTorchOutline only proves the claim for that subset."
  - "Re-dump (scratch dumper outside the repo, each Paper jar 1.21.4, 1.21.11, 26.1.2, 26.3) and add to carrier-policy.json with a reasoned presence every no-collision, no-light wall or floor block with a small outline, at least: the ten coral wall fans and coral fans (live and dead, waterlogged both ways; the live ones die out of water by a scheduled tick), lever, tripwire_hook, the wall signs and wall hanging signs, wall banners, vine, glow_lichen (light), cocoa (collision: reference), sculk_vein, hanging_roots, dead_bush, the small flowers. Check TFC's presence of each in /opt/tfc (recipes disabled or replaced, worldgen) before marking it absent; rough IoU against the TFC dead wall torch: lever wall about 0.69 (player-placed in TFC unless its recipe is disabled), tripwire_hook wall about 0.40, a dead coral wall fan about 0.29, the pressed button 0.08."
  - "Let HitboxAllocation pick again (weights unchanged unless needed; addingTheTorchesMovesNoOtherCarrier must stay true or the moved carriers are re-pinned in their own tests) and keep noFreeCarrierHasACloserDeadTorchOutline as the guard over the wider dump. If the new carrier is not a button, check the render yaw of the dead wall torch item display in TorchBlocks for its facing convention and its client prediction on right-click (coral fans and signs predict nothing; a sign opens no editor when the click is consumed)."
  - "carrier-policy.json is also touched by PAR-INFRA-51 (blocked): edit only the new rows and the PAR-HEAT-21 rows, keep theirs on rebase. Regenerate content (./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc), update the ledger's dead torch outlines divergence row with the new outline and IoU."
  - "Done (2026-11-17): owner idea spiked first on Paper 1.21.11 (spike CE pack on soul_torch / soul_wall_torch, luminance 0, TFC models): F3 block light 0 in the carrier, but the client draws the soul flame and smoke particles whatever the model (TorchBlock/WallTorchBlock.animateTick, same bytecode in 26.3) and lit the tunnel walls around it: rejected, copper torch and minecraft:torch likewise. Widened dump (scratch dumper on the 1.21.4, 1.21.11, 26.1.2, 26.3 Paper jars; reproduces the old rows exactly) adds every state of soul/copper torches, lever, tripwire hook, cocoa, vine, glow lichen, sculk vein, hanging roots, dead bush, small flowers, coral fans and wall fans, wall signs, wall hanging signs, wall banners, big dripleaf, its stem and the torchflower crop, each with a reasoned policy (new usable blocks only_class torch/wall_torch). Selector result: tfp:dead_torch on torchflower_crop[age=1] (6x10x6, score 0.67, was red_mushroom 0.53), tfp:dead_wall_torch with TFC model on big_dripleaf[facing,tilt=full,waterlogged=false] (6x13x6 by the wall, 0.55, was the pressed button hitbox-only 0.39); the wall lever (0.73) is crafted in TFC. Tests: hitbox TorchCarrierTest 6 (new theDumpHoldsTheWidenedCandidates, guard over the wider dump), TorchBlocksTest updated; check passes. In game 1.21.11: outlines vs vanilla references front/left/right GUI 2 and 3, chunk-load burnout after 4 days, F3 0 block light at once, tunnel passable, relight, no flicker on neighbour change, break, relog; Paper 26.3 loads the content with no issue, 26.3 client blocked by GLX here (pending with PAR-INFRA-23). Evidence docs/verification/PAR-HEAT-29/. Ledger: dead torch outlines divergence row rewritten."
tests:
  - "datagen: TorchCarrierTest over the wider dump (chosen carrier light 0, no collision, best IoU among free states); VanillaCandidatesTest and CarrierPolicyRecipeTest/CarrierPolicyWorldgenTest pass with the new blocks; TorchBlocksTest pins the yaw for the new carrier"
done_criteria:
  - "The dead wall torch and dead floor torch carriers are the closest free outlines to TFC's in the widened dump (test), with no collision and no light"
  - "In game the dead torches show TFC's dead look with the new outline next to a vanilla torch reference, and a one-wide tunnel lined with them is passable (screenshots GUI 2 and 3, docs/verification/PAR-HEAT-29/)"
---

# PAR-HEAT-29: Dead torch outlines closer to TFC

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
