---
id: PAR-PROC-36
title: "Salad completion: salad screen art, eating and tooltip read the salad's own food data, in-game check"
area: PROC
status: open
wave: 14
deps: [PAR-INFRA-03, PAR-SURV-02, PAR-SURV-03]
size: M
aliases: ["follow-up of PAR-PROC-14 (the GUI art, eat path and tooltip files are outside its files_owned; PROTOCOL 3a + 6.5)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/screen/SaladScreen.java"
  - "src/main/resources/assets/tfc/textures/gui/salad.png"
  - "src/main/java/net/dries007/tfc/common/component/food/FoodComponent.java"
  - "src/main/java/net/dries007/tfc/common/component/food/FoodCapability.java"
  - "src/main/java/net/dries007/tfc/common/player/PlayerInfo.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/salad/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/salad/**"
  - "core/src/main/java/dev/tfp/core/gui/GuiImages.java"
  - "core/src/main/java/dev/tfp/core/salad/**"
  - "core/src/test/java/dev/tfp/core/salad/**"
  - "paper/src/main/java/dev/tfp/paper/salad/**"
  - "paper/src/test/java/dev/tfp/paper/salad/**"
  - "paper/src/main/java/dev/tfp/paper/player/NutritionEatListener.java"
  - "paper/src/test/java/dev/tfp/paper/player/NutritionEat*"
  - "paper/src/main/java/dev/tfp/paper/food/FoodTooltipContributor.java"
  - "paper/src/main/java/dev/tfp/paper/food/ItemFoodHooks.java"
  - "paper/src/test/java/dev/tfp/paper/food/**"
  - "docs/verification/PAR-PROC-14/**"
ui: true
in_game_steps:
  - "ce item give Bot tfp:ceramic/bowl 4; ce item give Bot tfp:food/carrot 4; ce item give Bot tfp:food/red_apple 2"
  - "Bot sneaks and right-clicks with the bowls: the salad screen opens with the bowls in the bowl slot; screenshot at GUI scale 2 and 3"
  - "Put the carrots and apples in: the output shows 2 vegetable salads; screenshot; take them: carrots 2, apples 0, bowls 2 left"
  - "Eat one salad: the nutrition tab shows the salad's mixed vegetables/fruit (not zero); a ceramic bowl comes back"
plan:
  - "From PAR-PROC-14 (done: core `salad/SaladMix` + `SaladScreen`, paper `salad/` module: sneaking right-click with a `tfp:salad_bowls` item opens a plain-title 3-row chest with filler panes, the salad output with TFC's mix stored in the stack PDC `tfp:dynamic_food`, `tfp:ingredients`, `tfp:bowl` (`SaladPdc`), taking shrinks the inputs, the bowl comes back after eating, `tfc:empty_bowl` reads the stored bowl)."
  - "Art: datagen `salad/SaladArt` re-lays TFC's `textures/gui/salad.png` onto `SaladScreen` (inputs moved up 6 px to chest row 0, the whole art up 6 px with the bowl and output slot art 4 px further down onto chest row 2) as `tfp:gui/salad`, registered in `GuiImages.all`; then `SaladModule` opens through the shared glyph `GuiService` and drops the filler panes."
  - "Eating: `NutritionEatListener` takes the food of a stack with `tfp:dynamic_food` (SaladPdc.food) instead of the item definition (TFC `FoodComponent.getData()` of a dynamic food), so a salad records its mixed water, saturation and nutrients; `ItemFoodHooks` rot checks use the stack's decay modifier (4.0 for salads, the definition says 4.5)."
  - "Tooltip: `FoodTooltipContributor` reads the stack's dynamic food (nutrition lines of a salad), and lists the ingredients like TFC's tooltip if TFC does; drop the PAR-SURV-02 divergence row about dynamic foods."
  - "In-game check (moved from PAR-PROC-14, PROTOCOL 6.5): run in_game_steps on the headless Paper 1.21.11 server and client; evidence in docs/verification/PAR-PROC-14/."
tests:
  - "paper: eating a stack with tfp:dynamic_food records its nutrients; tooltip shows them; rot uses the stack's decay modifier"
  - "datagen: tfp:gui/salad image entry and PNG size"
done_criteria:
  - "Making a salad in-game: screen with TFC's art, mixed-nutrition salad, inputs shrink, bowl returns after eating (screenshots, docs/verification/PAR-PROC-14/verdict.md)"
  - "Eating a salad adds its mixed nutrients; tests green"
---

# PAR-PROC-36: Salad completion

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
