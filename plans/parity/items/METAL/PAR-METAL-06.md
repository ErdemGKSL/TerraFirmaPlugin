---
id: PAR-METAL-06
title: "Welding on the anvil (flux)"
area: METAL
status: open
wave: 31
deps: [PAR-METAL-05]
size: S
aliases: ["M4-ANV-02 (welding)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/recipes/WeldingRecipe.java"
  - "src/generated/resources/data/tfc/recipe/welding"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/anvil/weld/**"
  - "paper/src/test/java/dev/tfp/paper/anvil/weld/**"
  - "core/src/main/java/dev/tfp/core/anvil/Welding*"
  - "core/src/test/java/dev/tfp/core/anvil/Welding*"
ui: true
in_game_steps:
  - "anvil with two hot wrought iron ingots + flux; click weld"
plan:
  - "Weld button with flux in the catalyst slot and both inputs above welding temperature; WeldingRecipes copy-best/worst bonus."
tests:
  - "paper: weld consumes flux"
done_criteria:
  - "Welding two iron ingots into a double ingot in-game"
---

# PAR-METAL-06: Welding on the anvil (flux)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
