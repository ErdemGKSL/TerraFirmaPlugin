---
id: PAR-METAL-04
title: "Anvil plan selection GUI"
area: METAL
status: open
wave: 29
deps: [PAR-METAL-03, PAR-INFRA-03, PAR-INFRA-06]
size: S
aliases: ["M4-ANV-02 (plan)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/container/AnvilPlanContainer.java"
  - "src/main/java/net/dries007/tfc/client/screen/AnvilPlanScreen.java"
  - "src/main/java/net/dries007/tfc/network/SelectAnvilPlanPacket.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/anvil/plan/**"
  - "paper/src/test/java/dev/tfp/paper/anvil/plan/**"
  - "core/src/main/java/dev/tfp/core/anvil/AnvilRecipe*"
  - "core/src/test/java/dev/tfp/core/anvil/AnvilRecipe*"
ui: true
in_game_steps:
  - "copper anvil with a hot double ingot; shift-right-click? (open plan button); screenshot"
plan:
  - "Paged GUI of candidate recipes for the input (AnvilRecipes tier gating); a click selects the plan and seeds the work target as TFC (port the seeded target)."
tests:
  - "core: seeded target golden"
  - "paper: paging"
done_criteria:
  - "Selecting 'pickaxe head' for a copper double ingot"
---

# PAR-METAL-04: Anvil plan selection GUI

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
