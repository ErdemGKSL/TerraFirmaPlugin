---
id: PAR-DATA-14
title: "Vanilla rule hooks no item owns: lava + water hardened rocks, one-layer snow and snow on leaves, vines on TFC leaves, tadpoles, intoxicated chat, predators in boats, TFC mob armour, piglin gold, aqueduct and sluice sources, bamboo growth"
area: DATA
status: open
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
tests:
  - "core: golden tests for the slur transform (seeded) and the lava + water mapping"
  - "paper: MockBukkit test per rule (form event, snow break, vine support, tadpole growth, chat, boat mount, mob equipment, piglin currency, source creation, bamboo growth)"
done_criteria:
  - "Each hook behaves as TFC with a passing test; the lava + water divergence row is updated; screenshots pass"
---

# PAR-DATA-14: Vanilla rule hooks no item owns

> see_not_parity: NP-VIS-03 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
