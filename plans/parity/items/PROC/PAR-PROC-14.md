---
id: PAR-PROC-14
title: "Salads"
area: PROC
status: open
wave: 13
deps: [PAR-INFRA-03, PAR-SURV-01]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/container/SaladContainer.java"
  - "src/main/java/net/dries007/tfc/client/screen/SaladScreen.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/salad/**"
  - "core/src/test/java/dev/tfp/core/salad/**"
  - "core/src/test/resources/dev/tfp/core/salad/**"
  - "paper/src/main/java/dev/tfp/paper/salad/**"
  - "paper/src/test/java/dev/tfp/paper/salad/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/bowl"
  - "right-click; add vegetables; screenshot"
plan:
  - "Right-click with a bowl and vegetables opens the salad GUI; output salad with mixed nutrition (SoupMix-like rules from TFC)."
tests:
  - "core: salad nutrition golden"
done_criteria:
  - "Making a salad in-game"
---

# PAR-PROC-14: Salads

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
