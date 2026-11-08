---
id: PAR-PROC-08
title: "Barrel block: fluids, sealing, sealed and instant recipes, preservation"
area: PROC
status: done
wave: 9
deps: [PAR-INFRA-02, PAR-INFRA-04, PAR-INFRA-06, PAR-FLUID-01, PAR-INFRA-13]
size: M
aliases: ["M3-PROC-03"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/recipes/SealedBarrelRecipe.java"
  - "src/main/java/net/dries007/tfc/common/recipes/InstantBarrelRecipe.java"
  - "src/main/java/net/dries007/tfc/common/recipes/InstantFluidBarrelRecipe.java"
  - "src/generated/resources/data/tfc/recipe/barrel"
files_owned:
  - "core/src/main/java/dev/tfp/core/barrel/**"
  - "core/src/test/java/dev/tfp/core/barrel/**"
  - "core/src/test/resources/dev/tfp/core/barrel/**"
  - "paper/src/main/java/dev/tfp/paper/barrel/**"
  - "paper/src/test/java/dev/tfp/paper/barrel/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/barrel/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/barrel/**"
  - "content/generated/content/craftengine/tfp/configuration/barrel/**"
ui: false
plan:
  - "Per-wood barrel CE block (sealed state); fill/drain by bucket/jug; item slot; seal toggles via sneak-right-click (GUI in PAR-PROC-09); BarrelProgress.settle on interaction; instant recipes on insert; on_seal food traits (brined, pickled...) via FoodState."
  - "Follow-up PAR-PROC-32 (PROTOCOL 3a + 6.5): the CraftEngine blocks tfp:wood/barrel/<wood> need hitbox carriers from the selector (HardcodedCarrierTest), and tools/datagen/.../hitbox/HitboxDemands.java is outside this item's files_owned; PAR-PROC-32 adds the demands, writes configuration/barrel, adds the itest case and runs the in_game_steps moved there (ui moved with them)."
  - "Done: core/barrel BarrelSimulation (the BarrelProgress rule over any contents model, with the next recipe's on_seal and the instant check after each completion), BarrelAssembly (TFC's counting rules; BarrelRecipe now uses them), BarrelFluidTransfer (TFC transferBetweenBlockHandlerAndItem / transferBetweenItemAndOther over tanks and vanilla buckets), BarrelData (record state + codec); paper/barrel BarrelDevice (kind tfp:wood/barrel: 3 TFC slots + 32 excess, lazy settle in the kind's catch-up, sneak + empty hand seal toggle with on_seal / on_unseal and container drop, fluid by bucket / jug / bottle / vanilla bucket, pre-GUI item slot by click, wooden-rod drum, DROP + splash), BarrelRecipeBook (309 sealed, 20 instant, 2 instant fluid on the recipe runtime; matching, assembly, instant chain), BarrelItemTanks, PaperBarrelEffects, BarrelModule (+ barrel item size huge / heavy). Tests: core BarrelSimulationTest 5, BarrelFluidTransferTest 10; paper BarrelMockBukkitTest 8 (brining adds Brined, creative hand rule: a single container is kept and nothing given, a larger stack loses one and the container is given, the whole brine-pickle-vinegar chain after a long unload, on_seal / on_unseal vinegar, sealed refuses, limewater instant, vinegar + salt water = brine, container fill), BarrelRecipesContentDataMockBukkitTest 1 (all 331). Ledger row + divergences. In-game check: PAR-PROC-32."
  - "Notes (unblock, non-blocking review points): the creative-hand test now uses an explicit empty wooden bucket and counts only storage slots as given. Not pinned yet: TFC's ItemSizeManager modifyWeight on the given container (only matters for content-weighted containers such as sealed barrel items; PAR-PROC-32 can add it). The failed in-game capture was blocked by another agent's server, not by this item; the check lives in PAR-PROC-32 (PROTOCOL 6.5)."
tests:
  - "paper: MockBukkit seal + settle completes a recipe"
  - "core: BarrelGoldenTest exists"
  - "itest: all 331 barrel recipes load (moved to PAR-PROC-32 as BarrelRecipesCase; paper BarrelRecipesContentDataMockBukkitTest checks the 331 on the datagen output)"
done_criteria:
  - "Brining meat in a sealed barrel adds the Brined trait"
  - "Deferred check tracked in PAR-PROC-32"
---

# PAR-PROC-08: Barrel block: fluids, sealing, sealed and instant recipes, preservation

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
