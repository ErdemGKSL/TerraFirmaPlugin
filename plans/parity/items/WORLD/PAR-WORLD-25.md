---
id: PAR-WORLD-25
title: "TFC corals and sea pickle as CE blocks: 5 colours x (coral, coral fan, wall fan) live and dead, the salt-water sea pickle, dying out of water, drops; replace the vanilla coral placeholders in generation"
area: WORLD
status: open
wave: 25
deps: [PAR-WORLD-12, PAR-INFRA-13, PAR-FLUID-03]
size: M
aliases: ["parity audit 2026-11-08: TFCBlocks.CORAL (Coral x Coral.BlockType) and TFCBlocks.SEA_PICKLE named by no item; PlantBlocks writes vanilla corals and sea pickles as placeholders"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/plant/coral/Coral.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/coral/TFCCoralPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/coral/LivingCoralPlantBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/coral/CoralWallFanBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/coral/LivingCoralWallFanBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/plant/coral/TFCSeaPickleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/resources/data/tfc/loot_table/blocks/coral"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/coral/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/coral/**"
  - "content/generated/content/craftengine/tfp/configuration/coral/**"
  - "core/src/main/java/dev/tfp/core/coral/**"
  - "core/src/test/java/dev/tfp/core/coral/**"
  - "paper/src/main/java/dev/tfp/paper/coral/**"
  - "paper/src/test/java/dev/tfp/paper/coral/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/PlantBlocks.java"
ui: true
in_game_steps:
  - "Fresh world; tp Bot to a warm coral reef (tfp debug worldgen finds one); screenshot under water: TFC coral plants, fans, wall fans and sea pickles"
  - "Break a tube coral with an empty hand and with a knife; screenshot the drops (TFC loot)"
  - "Place a living tube coral on dry land; wait 120 ticks; screenshot: it turned into dead tube coral; place a sea pickle out of water (no light) and in water (lit)"
plan:
  - "Current (audit 2026-11-08): `paper/worldgen/PlantBlocks` writes vanilla coral blocks, fans, wall fans and sea pickles where TFC places its own `tfc:coral/<colour>_coral|_coral_fan|_coral_wall_fan` and `_dead_*` blocks (30) and `tfc:sea_pickle`; their items (`tfc:coral/*` live and dead coral and fans, the sea pickle) are not obtainable, and the vanilla blocks' loot and survival rules are vanilla's."
  - "Datagen: 30 coral blocks + sea pickle as CE blocks from TFC's blockstates/models/loot (living: wet-grass sound, instabreak, no collision; dead: requires the correct tool for drops; wall fans drop the fan: lootFrom), a fluid-state property for salt water (waterlogged carrier per the visuals skill; the carrier must not create a real vanilla coral, NP-VIS-03), hitbox carriers from PAR-INFRA-13 (BIG 2..14 x 0..15, SMALL 0..4, wall fan shapes); items for coral and dead coral as TFC (needsItem)."
  - "Rules in core/coral: scanForWater (any infinite water on the block's fluid property or an adjacent water source, as TFC/vanilla BaseCoralPlantTypeBlock), die to the dead block keeping facing and dropping the fluid; survival (sturdy face below or behind); sea pickle survival (non-empty collision top or sturdy top) and light by count when in salt water; decide async, apply sync through the neighbour runner (PAR-INFRA-10) with the 60..100 tick delay TFC schedules."
  - "Generation: PlantBlocks writes the CE ids instead of the vanilla placeholders (shared with PAR-WORLD-12, a different wave); vanilla coral blocks (TFC uses vanilla's full coral blocks) stay vanilla."
  - "Ledger: close the coral/sea pickle placeholder divergence; feature row."
tests:
  - "tools/datagen: 30 coral blocks + sea pickle, items only where TFC registers them, loot matches TFC's tables"
  - "core: coral survival/die golden vs TFC's rules (fluid property x neighbour water x support)"
  - "paper: MockBukkit: a living coral without water becomes dead after the delay; a wall fan falls without support"
done_criteria:
  - "Reefs generate TFC coral blocks and sea pickles; living corals die out of water; drops follow TFC loot; tests green; screenshots pass"
---

# PAR-WORLD-25: TFC corals and sea pickle as CE blocks

> see_not_parity: NP-FLUID-01, NP-VIS-03 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
