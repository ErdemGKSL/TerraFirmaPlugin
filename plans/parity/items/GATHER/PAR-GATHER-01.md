---
id: PAR-GATHER-01
title: "Pick up loose rocks and groundcover with a right-click"
area: GATHER
status: open
wave: 21
deps: [PAR-WORLD-09, PAR-INFRA-02, PAR-INFRA-09]
size: M
aliases: ["M3-GATH-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/LooseRockBlock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/gather/**"
  - "core/src/test/java/dev/tfp/core/gather/**"
  - "core/src/test/resources/dev/tfp/core/gather/**"
  - "paper/src/main/java/dev/tfp/paper/gather/**"
  - "paper/src/test/java/dev/tfp/paper/gather/**"
ui: true
in_game_steps:
  - "tfp debug place tfp:rock/loose/granite"
  - "right-click it with an empty hand"
  - "screenshot hotbar"
plan:
  - "Current: core/gather pickup and break rules golden-tested."
  - "Right-click on a loose rock / groundcover gives the item into the hand (stacking into the held stack as TFC), breaks the block; creative yields nothing; placing loose rocks back from the item (loose rock stacking 1-3 per block)."
  - "Explosion decay of the loose rock loot table through PAR-INFRA-09."
tests:
  - "paper: MockBukkit pickup into hand and stacking"
  - "itest: `gather.pickup`"
done_criteria:
  - "In-game pickup of a loose rock and a stick"
---

# PAR-GATHER-01: Pick up loose rocks and groundcover with a right-click

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
