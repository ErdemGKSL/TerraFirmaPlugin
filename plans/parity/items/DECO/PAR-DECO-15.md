---
id: PAR-DECO-15
title: "Visual budget re-check after all block families"
area: DECO
status: open
wave: 49
deps: [PAR-DECO-01, PAR-DECO-06, PAR-DECO-07, PAR-DECO-09, PAR-WORLD-12]
size: S
aliases: ["X-VIS-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
files_owned:
  - "tools/datagen/src/test/java/dev/tfp/datagen/VisualBudget*"
  - "docs/visuals/budget.md"
ui: false
in_game_steps: []
plan:
  - "Regenerate the allocation report with every family loaded; confirm >= 500 free solid-pool slots per default profile and version; document any profile change."
tests:
  - "VisualBudgetTest"
done_criteria:
  - "Budget report committed; profiles unchanged or decision recorded"
---

# PAR-DECO-15: Visual budget re-check after all block families

> see_not_parity: NP-VIS-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
