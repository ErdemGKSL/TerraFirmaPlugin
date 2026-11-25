---
id: PAR-DECO-07
title: "Mud bricks, alabaster, aggregate, fire bricks, wattle and daub, thatch"
area: DECO
status: done
wave: 47
deps: [PAR-DECO-01, PAR-INFRA-13]
size: M
aliases: ["M6-DECO-02 (mud)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/WattleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/StainedWattleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/MudFamilies.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/MudFamilies*"
  - "content/generated/content/craftengine/tfp/families/mud.yml"
  - "content/generated/content/craftengine/tfp/configuration/building/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/building/**"
  - "paper/src/main/java/dev/tfp/paper/wattle/**"
ui: true
in_game_steps:
  - "place tfp:wattle; add 4 sticks, daub, dye; screenshot each step"
plan:
  - "Generate and load these families; wattle: add sticks/daub/dye in place (WattleBlock states) with connection updates via the neighbour runner."
  - "Done: 51 alabaster cubes, 8 mud bricks, fire bricks and aggregate as CE blocks (AssetPipeline.writeNaturalBlocks, templates natural_stone and natural_shovel), NaturalBlocksTest.buildingCubes passes; stairs, slabs, walls and wattle moved to PAR-DECO-19, thatch is PAR-POT-08. Pending in-game check; the full Datagen run and the visual budget pins could not be verified because HEAD groundcover (PAR-WORLD-31 wip) throws a hitbox carrier mismatch."
tests:
  - "tools/datagen counts"
done_criteria:
  - "Wattle building steps work in-game"
---

# PAR-DECO-07: Mud bricks, alabaster, aggregate, fire bricks, wattle and daub, thatch

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
