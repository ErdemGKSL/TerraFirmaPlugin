---
id: PAR-TOOL-06
title: "Knife, scythe and hoe area behaviours; remaining tool items (saw, chisel, propick, tuyere, shears heads)"
area: TOOL
status: open
wave: 23
deps: [PAR-TOOL-02]
size: M
aliases: ["gaps: remaining metal tools"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/ScytheItem.java"
  - "src/main/java/net/dries007/tfc/common/items/CreativeMiningTool.java"
  - "src/main/java/net/dries007/tfc/common/items/TFCHoeItem.java"
  - "src/main/java/net/dries007/tfc/common/items/HammerItem.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/toolarea/**"
  - "paper/src/test/java/dev/tfp/paper/toolarea/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/toolitems/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/scythe/bronze"
  - "break grass; screenshot"
plan:
  - "Scythe cuts a 3x3 of plants as TFC (decided async, applied sync); generate the remaining metal tool items (saw, chisel, propick, tuyere, shears, fishing rod head) with their data so later items can give them."
tests:
  - "paper: area set test"
done_criteria:
  - "Scythe cuts 3x3 grass in-game"
---

# PAR-TOOL-06: Knife, scythe and hoe area behaviours; remaining tool items (saw, chisel, propick, tuyere, shears heads)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
