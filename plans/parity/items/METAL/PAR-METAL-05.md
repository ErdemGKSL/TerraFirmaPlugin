---
id: PAR-METAL-05
title: "Anvil forging GUI: step buttons, target and progress markers, hammering, forging bonus"
area: METAL
status: open
wave: 30
deps: [PAR-METAL-04, PAR-HEAT-02]
size: M
aliases: ["M4-ANV-01", "M4-ANV-02 (forging)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/container/AnvilContainer.java"
  - "src/main/java/net/dries007/tfc/client/screen/AnvilScreen.java"
  - "src/main/java/net/dries007/tfc/common/component/forge/Forging.java"
  - "src/main/java/net/dries007/tfc/common/component/forge/ForgingBonus.java"
  - "src/generated/resources/data/tfc/recipe/anvil"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/anvil/forge/**"
  - "paper/src/test/java/dev/tfp/paper/anvil/forge/**"
  - "core/src/main/java/dev/tfp/core/forge/**"
  - "core/src/test/java/dev/tfp/core/forge/**"
ui: true
in_game_steps:
  - "copper anvil, hot copper double ingot, hammer"
  - "open; select plan; click steps; screenshot"
plan:
  - "GUI with the 8 step buttons as slot items, target/current markers and rule icons as title glyphs; each click needs a hammer and the item above working temperature; damage the hammer; ForgingProgress.checkComplete outputs with the forging bonus stored on the item (forging component PDC); config thresholds (anvil*Threshold)."
tests:
  - "core: ForgingSequenceTest exists"
  - "paper: step click -> offset"
done_criteria:
  - "Forging a copper pickaxe head from a hot double ingot in-game with a bonus"
---

# PAR-METAL-05: Anvil forging GUI: step buttons, target and progress markers, hammering, forging bonus

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
