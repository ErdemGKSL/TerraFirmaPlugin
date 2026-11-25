---
id: PAR-DECO-06
title: "Rock families: bricks, cobble, smooth, stairs, slabs, walls, aqueducts, spikes, pressure plates, buttons"
area: DECO
status: done
wave: 46
deps: [PAR-DECO-01, PAR-INFRA-10, PAR-INFRA-13]
size: M
aliases: ["M6-DECO-02 (rock)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Rock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/AqueductBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/RockSpikeBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/RockFamilies.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/RockMudFamilies*"
  - "content/generated/content/craftengine/tfp/families/rock.yml"
  - "content/generated/content/craftengine/tfp/configuration/rockfamily/**"
ui: true
in_game_steps:
  - "tfp debug showcase rock"
  - "screenshot"
plan:
  - "Done 2026-11-25: stairs and slabs of the seven decoration kinds, buttons, pressure plates and spikes of the 21 rocks as CE blocks (357 blocks, 13,314 states, `configuration/rockfamily/<rock>.yml`; shared hitbox-only twins and item displays like PAR-DECO-09; spikes on the best free carrier, water only); tests RockMudFamiliesBlocksTest (3); ledger row and 6 divergence rows. Walls, aqueducts and rope anchors moved to PAR-DECO-22. Pending in-game check (no client in the build container)."
  - "Load the 812 rock family blocks with the `tfp:block_state` templates from PAR-DECO-01; aqueduct connections via the neighbour runner (PAR-INFRA-10); rock spikes as thin spikes; moss-growing variants hook into PAR-GATHER-06."
tests:
  - "tools/datagen: RockMudFamiliesTest, RockMudFamiliesBlocksTest"
  - "VisualBudgetTest"
done_criteria:
  - "Rock showcase with stairs/slabs/walls"
---

# PAR-DECO-06: Rock families: bricks, cobble, smooth, stairs, slabs, walls, aqueducts, spikes, pressure plates, buttons


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
