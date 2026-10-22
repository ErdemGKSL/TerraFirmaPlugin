---
id: PAR-WORLD-09
title: "Groundcover, loose rocks, deposits and boulders as CE blocks in generation"
area: WORLD
status: open
wave: 20
deps: [PAR-INFRA-08, PAR-INFRA-13, PAR-INFRA-15]
size: M
aliases: ["M2-GEN-11"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlockType.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/LooseRockBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/OreDeposit.java"
  - "src/main/java/net/dries007/tfc/world/feature/LooseRockFeature.java"
  - "src/generated/resources/data/tfc/tfc/deposit"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/groundcover/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/groundcover/**"
  - "content/generated/content/craftengine/tfp/configuration/groundcover/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/GroundcoverBlocks.java"
  - "core/src/main/java/dev/tfp/core/worldgen/groundcover/**"
  - "core/src/main/java/dev/tfp/core/worldgen/deposit/**"
  - "core/src/test/java/dev/tfp/core/worldgen/groundcover/**"
  - "core/src/test/java/dev/tfp/core/worldgen/deposit/**"
ui: true
in_game_steps:
  - "Fresh world; look at the ground near spawn; tfp debug block on a loose rock"
plan:
  - "Generate CE blocks: loose rocks (21 rocks, plain/mossy, button-hitbox carrier), small ores (graded), groundcover (sticks, bones, clam, feather, flint, guano, driftwood, mollusk, seaweed, pumice, rotten flesh, salt lick, podzol...), ore deposits per rock and ore (gravel-like)."
  - "Wire GroundcoverBlocks to write CE ids; add the missing rules: underground loose rocks, the 6 boulders, guano, water clay, rooted dirt and mollisol discs, powder snow."
  - "Ore overlay (2026-10-22): small ores stay button-carrier groundcover with their own model (not overlays); deposits stay full-block models (per rock at level-0/1, rock-agnostic at level-2); ore blocks themselves are drawn by PAR-INFRA-15/16 and must not get visuals here."
tests:
  - "tools/datagen: counts vs TFC registries"
  - "core: GroundcoverDepositGoldenTest open-gap list empty"
done_criteria:
  - "Fresh world shows loose rocks and indicators above veins (screenshot)"
---

# PAR-WORLD-09: Groundcover, loose rocks, deposits and boulders as CE blocks in generation

> see_not_parity: NP-VIS-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
