---
id: PAR-DATA-14
title: "Vanilla rule hooks no item owns: lava + water hardened rocks, one-layer snow and snow on leaves, vines on TFC leaves, tadpoles, intoxicated chat, predators in boats, TFC mob armour, piglin gold, aqueduct and sluice sources, bamboo growth"
area: DATA
status: done
wave: 51
deps: [PAR-DATA-10, PAR-SURV-08, PAR-WORLD-15, PAR-GATHER-04, PAR-CLIM-04, PAR-DECO-06, PAR-PROC-13, PAR-FAUNA-10, PAR-FAUNA-12]
size: M
aliases: ["parity audit 2026-11-06: mixins and ForgeEventHandler handlers named by no item"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
  - "src/main/java/net/dries007/tfc/mixin/SnowLayerBlockMixin.java"
  - "src/main/java/net/dries007/tfc/mixin/VineBlockMixin.java"
  - "src/main/java/net/dries007/tfc/mixin/TadpoleMixin.java"
  - "src/main/java/net/dries007/tfc/mixin/MobMixin.java"
  - "src/main/java/net/dries007/tfc/mixin/HeightmapMixin.java"
  - "src/main/java/net/dries007/tfc/common/items/BarteringItem.java"
  - "src/main/java/net/dries007/tfc/util/Metal.java"
  - "src/main/java/net/dries007/tfc/config/ServerConfig.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldrules/**"
  - "core/src/test/java/dev/tfp/core/worldrules/**"
  - "core/src/test/resources/dev/tfp/core/worldrules/**"
  - "paper/src/main/java/dev/tfp/paper/worldrules/**"
  - "paper/src/test/java/dev/tfp/paper/worldrules/**"
ui: true
in_game_steps:
  - "Pour a water bucket next to a lava source on a TFC world; screenshot the formed block (hardened gabbro / rhyolite / basalt as TFC)"
  - "Fill a 4-layer snow pile, break it once with a shovel; screenshot that one layer is left; let snow fall on TFC leaves (tfp weather snow); screenshot the single layer on the leaves"
  - "Drink 2 jugs of beer as Bot (tfp debug intoxication Bot max if PAR-SURV-08 adds it), write 'hello world' in chat; screenshot the slurred line"
plan:
  - "Current (audit 2026-11-06): these TFC hooks of vanilla rules are in no item (PAR-DATA-10 covers dispensers, bonemeal and the enableVanilla* spawn keys; PAR-HEAT-13 fire arrows and dousing; PAR-WORLD-16 portals and gamerules):"
  - "onFluidPlaceBlock: lava + water makes TFC hardened gabbro (stone), hardened rhyolite (cobblestone) and hardened basalt (basalt) instead of the vanilla block; today the ledger row 'Lava + water ... make vanilla stone' says revisit once TFC's result is ported: port it with BlockFormEvent writing the CE hardened rock of PAR-WORLD-15 and update that divergence row (NP-VIS-03 stays: the vanilla block is still never created)."
  - "SnowLayerBlockMixin: breaking a snow layer block removes one layer; a single layer survives on TFC leaves (non-solid top) and placement falls back to one layer there; HeightmapMixin: IBlockRain blocks are motion-blocking for rain and snow placement."
  - "VineBlockMixin: vanilla vines accept any TFC leaves block (ILeavesBlock) as a support; TadpoleMixin: tadpoles are not fed by slime balls and grow into the TFC frog (PAR-FAUNA-12), keeping name, no-AI and persistence."
  - "onServerChat: intoxication above 20 % slurs chat (chance = clampedMap(intoxication, 0.2, 1, 0, 0.7); swap, repeat and add letters in TFC's order of random draws) using PAR-SURV-08's intoxication value; onMount: predators cannot ride boats; onCropsGrow: vanilla bamboo grows only with plantLongGrowthChance."
  - "MobMixin: unless enableVanillaMobsSpawningWithVanillaEquipment, mobs get their armour from the TFC tags mob_head/chest/leg/feet_armor (random member, empty when the tag is empty) and never vanilla equipment; BarteringItem: gold ingots (isPiglinCurrency metals) are piglin currency through the vanilla piglin_loved/bartering rules."
  - "onFluidCreateSource: waterlogged aqueducts never count as sources and a block fed by a sluice outlet cannot become a source (BlockFromToEvent / FluidLevelChangeEvent on Paper; decide async, apply sync)."
  - "One feature module (`paper/worldrules`) with one listener per rule, pure decisions in core/worldrules, config keys read through TfpConfig; every rule respects game mode and peaceful as TFC."
  - "Built (2026-11-25): module `world_rules` (paper/worldrules) with HardenedRockListener (BlockFormEvent: decision read at LOW before the suppression listener, CE hardened gabbro/rhyolite/basalt server data written at HIGHEST; vanilla or suppressed result while CE is not loaded), SnowLayerListener (a survival/adventure break of a vanilla snow layer of 2+ layers leaves one layer less, restored by the region thread into the empty hole after vanilla's break and drops; a 2+ layer placement on TFC leaves is refused), EntityRulesListener (slime ball does not feed a tadpole; METAMORPHOSIS cancelled and TFC's frog spawned through AnimalService with no-AI, name and persistence, grow-up sound 0.15/1.0; predators cannot mount boats), BambooListener (stalk growth via BlockSpreadEvent/BlockGrowEvent kept only if nextFloat() <= plantLongGrowthChance 0.03), FluidSourceListener (FluidLevelChangeEvent / BlockFromToEvent re-run vanilla getNewLiquid on a neighbour snapshot with waterlogged aqueducts and sluice-fed sources not counted; TFC's answer replaces the new data, or is placed by the region thread). Pure decisions in core/worldrules/WorldRules."
  - "Already owned elsewhere, nothing added: intoxicated chat (PAR-SURV-08, IntoxicationModule + ChatSlur), TFC mob armour from the mob_*_armor tags (PAR-DATA-18, VanillaRulesModule.equip), single snow layers on leaves from the weather (SnowService.snowSurvivesOn). Vines on leaves and HeightmapMixin: TFC leaves are full solid CE server cubes here (auto_state solid), so vanilla vines already accept them and they already block motion for rain/snow; thatch (PAR-POT-08) is a colliding leaves-pool cube, so it blocks motion as well."
  - "Follow-up PAR-DATA-22: piglin currency (CE gold ingot is a paper item; Paper's barter API is per Material)."
  - "Pending in-game check (container cannot run client)"
  - "Done: PAR-DATA-14 commit; core WorldRulesTest (7), paper WorldRulesMockBukkitTest (7); FeatureModulesTest, BootstrapTest, VanillaRulesModuleMockBukkitTest still pass; ledger feature row + divergence rows (lava + water row updated)."
tests:
  - "core: golden tests for the slur transform (seeded) and the lava + water mapping"
  - "paper: MockBukkit test per rule (form event, snow break, vine support, tadpole growth, chat, boat mount, mob equipment, piglin currency, source creation, bamboo growth)"
done_criteria:
  - "Each hook behaves as TFC with a passing test; the lava + water divergence row is updated; screenshots pass"
---

# PAR-DATA-14: Vanilla rule hooks no item owns

> see_not_parity: NP-VIS-03 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
