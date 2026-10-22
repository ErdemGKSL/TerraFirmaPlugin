---
id: PAR-PROC-15
title: "Scribing table"
area: PROC
status: open
wave: 41
deps: [PAR-INFRA-03, PAR-DECO-01]
size: S
aliases: ["M6-DECO-01 (scribing GUI)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/ScribingTableBlock.java"
  - "src/main/java/net/dries007/tfc/common/container/ScribingTableContainer.java"
  - "src/main/java/net/dries007/tfc/client/screen/ScribingTableScreen.java"
  - "src/main/java/net/dries007/tfc/network/ScribingTablePacket.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/scribing/**"
  - "core/src/test/java/dev/tfp/core/scribing/**"
  - "core/src/test/resources/dev/tfp/core/scribing/**"
  - "paper/src/main/java/dev/tfp/paper/scribing/**"
  - "paper/src/test/java/dev/tfp/paper/scribing/**"
ui: true
in_game_steps:
  - "place a scribing table; right-click; screenshot"
plan:
  - "Item rename with ink (black dye) as TFC's anvil-like screen; the name is typed via an anvil inventory (vanilla text field) with our background."
tests:
  - "paper: rename consumes ink"
done_criteria:
  - "Renaming an item at a scribing table"
---

# PAR-PROC-15: Scribing table

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
