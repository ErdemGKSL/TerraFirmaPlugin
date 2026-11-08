---
id: PAR-PROC-32
title: "Barrel CE blocks: tfp:wood/barrel/<wood> hitbox demands, configuration/barrel, in-game brining check"
area: PROC
status: open
wave: 10
deps: [PAR-PROC-08, PAR-INFRA-13]
size: M
aliases: ["follow-up of PAR-PROC-08 (its CraftEngine blocks, its in_game_steps and its itest case)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BarrelBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/resources/assets/tfc/blockstates/wood/barrel"
  - "src/main/resources/assets/tfc/models/block/wood/barrel"
  - "src/main/resources/data/tfc/loot_table/blocks/wood/barrel"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/barrel/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/barrel/**"
  - "content/generated/content/craftengine/tfp/configuration/barrel/**"
  - "itest/src/main/java/dev/tfp/itest/cases/BarrelRecipesCase.java"
  - "docs/verification/PAR-PROC-08/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:wood/barrel/oak"
  - "fill with brine (bucket), add meat, seal"
  - "tfp time add hours 5; unseal; hover meat"
plan:
  - "Deferred from PAR-PROC-08 (PROTOCOL 3a + 6.5): the barrel runtime is done and tested (core/barrel BarrelSimulation, BarrelAssembly, BarrelFluidTransfer, BarrelData; paper/barrel BarrelDevice kind tfp:wood/barrel covering tfp:wood/barrel/<wood>, BarrelRecipeBook over the 331 recipes, BarrelItemTanks, PaperBarrelEffects, BarrelModule). The CraftEngine blocks are missing because their hitbox carriers must come from the selector (HardcodedCarrierTest), and HitboxDemands.java was outside PAR-PROC-08's files_owned."
  - "Hitbox: the tfc-shapes.json rows exist (tfc:wood/barrel[axis=y,sealed=true|false], tfc:wood/barrel[axis=x|z,rack=false|true]). Add HitboxDemands for tfp:wood/barrel (upright sealed / unsealed; the side and rack states may wait for PAR-PROC-09's rack). Per wood the look differs, so either one demand per wood x state (model-conflict rule: one model per vanilla state) or one hitbox-only demand per state with the per-wood look drawn by entity_renderer; pick what the allocator can serve and record the choice. Pin tfp:wood/barrel/* in GeneratedCarrierTest."
  - "Block entries: datagen/barrel writes configuration/barrel/barrel.yml through DeviceBlockSpec + DeviceYaml.templated, one block per TFC wood (WoodFamilies' list): the tfp:device behaviour, the property sealed (boolean, default false; BarrelDevice.SEALED, the device writes it), TFC's strength / sound / axe tool read from TFCBlocks, the loot from TFC's loot table (the item itself), the block item with TFC's item model, TFC's models and textures copied (sealed and unsealed upright models). Add the section to CeYamlCheck if it is new. Placement: TFC needs a sturdy block below (getStateForPlacement) and FACING (not down) for barrels on their side; the side states come with PAR-PROC-09's rack."
  - "itest: BarrelRecipesCase (SelfTestSuite hub line): on the running server all 331 barrel recipes (309 sealed, 20 instant, 2 instant fluid) decode through BarrelRecipeBook (the MockBukkit BarrelRecipesContentDataMockBukkitTest already checks the datagen output)."
  - "In-game check (PROTOCOL 6): run the steps with the bot; screenshot the sealed and unsealed barrel and the meat's tooltip showing Brined; store docs/verification/PAR-PROC-08/. Also check that BucketListener (PAR-FLUID-02) does not place the bucket's fluid in the world when the bucket is used on the barrel (the device consumes the click first)."
tests:
  - "datagen: BarrelBlockTest (one entry per wood, sealed property, carriers from the allocation)"
  - "itest: BarrelRecipesCase"
done_criteria:
  - "Brining meat in a sealed barrel adds the Brined trait, in game (screenshot of the tooltip)"
---

# PAR-PROC-32: Barrel CE blocks: tfp:wood/barrel/<wood> hitbox demands, configuration/barrel, in-game brining check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
