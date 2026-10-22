---
id: PAR-GATHER-06
title: "Raw rock drops and mossy rock spreading"
area: GATHER
status: open
wave: 18
deps: [PAR-INFRA-09, PAR-INFRA-11]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/rock/RawRockBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/MossSpreadingBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Rock.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/rock/**"
  - "core/src/test/java/dev/tfp/core/rock/**"
  - "core/src/test/resources/dev/tfp/core/rock/**"
  - "paper/src/main/java/dev/tfp/paper/rock/**"
  - "paper/src/test/java/dev/tfp/paper/rock/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/pickaxe/copper"
  - "mine raw granite"
  - "screenshot drops"
plan:
  - "Raw rock loot per TFC tables (loose rocks), mossy cobble/bricks spread lazily near water as TFC."
tests:
  - "core: spread golden"
done_criteria:
  - "Mining raw granite yields TFC drops in-game"
---

# PAR-GATHER-06: Raw rock drops and mossy rock spreading

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
