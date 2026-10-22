---
id: PAR-TOOL-07
title: "Shields per metal"
area: TOOL
status: open
wave: 24
deps: [PAR-TOOL-03]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/items/TFCShieldItem.java"
  - "src/main/java/net/dries007/tfc/ForgeEventHandler.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/shield/**"
  - "paper/src/main/java/dev/tfp/paper/shield/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:metal/shield/copper"
  - "block a zombie; screenshot"
plan:
  - "Shield items per metal on the vanilla shield (blocks_attacks component on 1.21.5+, banner pattern texture); TFC onShieldBlock damage rule."
tests:
  - "tools/datagen counts"
done_criteria:
  - "Copper shield blocks a zombie hit"
---

# PAR-TOOL-07: Shields per metal

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
