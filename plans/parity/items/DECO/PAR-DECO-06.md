---
id: PAR-DECO-06
title: "Rock families: bricks, cobble, smooth, stairs, slabs, walls, aqueducts, spikes, pressure plates, buttons"
area: DECO
status: open
wave: 46
deps: [PAR-DECO-01, PAR-INFRA-10]
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
  - "Load the 812 rock family blocks with the `tfp:block_state` templates from PAR-DECO-01; aqueduct connections via the neighbour runner (PAR-INFRA-10); rock spikes as thin spikes; moss-growing variants hook into PAR-GATHER-06."
tests:
  - "tools/datagen: RockMudFamiliesTest"
  - "VisualBudgetTest"
done_criteria:
  - "Rock showcase with stairs/slabs/walls"
---

# PAR-DECO-06: Rock families: bricks, cobble, smooth, stairs, slabs, walls, aqueducts, spikes, pressure plates, buttons

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
