---
id: PAR-PROC-07
title: "Sewing table GUI"
area: PROC
status: open
wave: 41
deps: [PAR-INFRA-03, PAR-DECO-01]
size: M
aliases: ["M3-PROC-02 (sewing)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/SewingTableBlock.java"
  - "src/main/java/net/dries007/tfc/common/container/SewingTableContainer.java"
  - "src/main/java/net/dries007/tfc/client/screen/SewingTableScreen.java"
  - "src/generated/resources/data/tfc/recipe/sewing"
files_owned:
  - "core/src/main/java/dev/tfp/core/sewing/**"
  - "core/src/test/java/dev/tfp/core/sewing/**"
  - "core/src/test/resources/dev/tfp/core/sewing/**"
  - "paper/src/main/java/dev/tfp/paper/sewing/**"
  - "paper/src/test/java/dev/tfp/paper/sewing/**"
  - "core/src/main/java/dev/tfp/core/craft/Sewing*"
ui: true
in_game_steps:
  - "place a sewing table; right-click; screenshot GUI"
plan:
  - "Textured GUI with the 12-pixel square/stitch grid as clickable button slots (approximation of TFC's free grid: divergence row), yarn/tool/inputs/result slots; SewingRules."
tests:
  - "paper: grid click mapping"
done_criteria:
  - "Sewing a recipe gives the result"
---

# PAR-PROC-07: Sewing table GUI

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
