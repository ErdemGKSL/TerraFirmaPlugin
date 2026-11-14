---
id: PAR-PROC-14
title: "Salads"
area: PROC
status: done
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
ui: false
in_game_steps: []
plan:
  - "Right-click with a bowl and vegetables opens the salad GUI; output salad with mixed nutrition (SoupMix-like rules from TFC)."
  - "Built: TFC opens the screen only on a sneaking right-click (InteractionManager SALAD_BOWLS; a plain right-click places the wooden bowl), so ours does too. The screen art (datagen + GuiImages), the eat path (NutritionEatListener reads only the item definition) and the food tooltip are outside files_owned: follow-up PAR-PROC-36 makes eating and the tooltip read the salad's stored food data, adds TFC's salad.png, and holds the in-game check (PROTOCOL 6.5: moved there with the screenshot criterion, ui false here). Until then the screen is a plain-title 3-row chest with filler panes."
  - "Done: core `salad/SaladMix` (TFC setAndUpdateSlots/onSlotTake, bit-exact) and `salad/SaladScreen` (slot layout); paper `salad/` (SaladModule `salad`, SaladMenu, SaladListener, SaladPdc: tfp:dynamic_food / tfp:ingredients / tfp:bowl; bowl back after eating; tfc:empty_bowl extension). Tests: core SaladMixGoldenTest 12, paper SaladMockBukkitTest 14; :core:test :paper:test :tools:datagen:test green. Ledger: feature row + 6 divergences. Verification: deferred to PAR-PROC-36."
tests:
  - "core: salad nutrition golden"
  - "paper: SaladMockBukkitTest (open, output, take, close, bowl after eating)"
done_criteria:
  - "Deferred check tracked in PAR-PROC-36"
---

# PAR-PROC-14: Salads

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
