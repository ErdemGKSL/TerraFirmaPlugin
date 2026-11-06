---
id: PAR-PROC-25
title: "Workbench crafting in-game verification: 10 sampled CE crafts in a crafting table"
area: PROC
status: done
wave: 7
deps: []
size: S
aliases: ["follow-up of PAR-PROC-02 (its in_game_steps and in-game done-criterion)"]
tfc_refs:
  - "src/generated/resources/data/tfc/recipe/crafting"
files_owned:
  - "docs/verification/PAR-PROC-02/**"
ui: true
in_game_steps:
  - "Place a crafting table in front of Bot (survival) and open it"
  - "Craft each of these 10 emitted recipes (content/generated/content/craftengine/tfp/configuration/crafting/*_recipes.yml) and screenshot the grid with the result shown: tfp:crafting/black_dye (tfp:powder/charcoal or coke), tfp:crafting/blank_disc (tfp:soot + glass pane), tfp:crafting/bricks (minecraft:brick + tfp:mortar), tfp:crafting/glue, tfp:crafting/fire_clay, tfp:crafting/daub, tfp:crafting/handstone, tfp:crafting/jute_net, tfp:crafting/activator_rail (tfp:metal/rod/steel + stick + redstone torch, 12 rails), one tfp:crafting/rock/* recipe (e.g. loose rocks to cobble). If tfc:firestarter is a CE item by then (PAR-HEAT-05), craft tfp:crafting/firestarter from 2 sticks too"
  - "Check that the vanilla material of a CE ingredient alone does not satisfy the recipe (e.g. the material item of tfp:mortar in place of mortar gives no bricks)"
plan:
  - "Deferred screenshot check of PAR-PROC-02 (PROTOCOL §6.5): the PAR-PROC-02 worker session could not start the cheap-model agent that runs the headless client (PROTOCOL §6.1). The CE load half of PAR-PROC-02's criterion was checked there (docs/verification/PAR-PROC-02/verdict.md: CE 26.9.2 on Paper 1.21.11 loads the recipes with 0 issues). Store crops and add the in-game section to docs/verification/PAR-PROC-02/verdict.md."
  - "Done: in-game check on Paper 1.21.11 + CE 26.9.2, vanilla 1.21.11 client (docs/verification/PAR-PROC-02/verdict.md, crafts-gui3.png, crafts/*.png): 9 of 10 sampled recipes craft with TFC's result and count (black dye, blank disc, glue x9, fire clay, daub x2, handstone, jute net, activator rail x12, granite cobble); plain paper in place of a CE ingredient crafts nothing. tfp:crafting/bricks gives nothing: minecraft:bricks is a repurposed (suppressed) visual state; fix and re-check in follow-up PAR-PROC-30, divergence row in the ledger. Firestarter not emitted yet (PAR-HEAT-05)."
tests: []
done_criteria:
  - "The 9 sampled recipes other than bricks craft in a crafting table with the TFC result and count"
  - "tfp:crafting/bricks: Deferred check tracked in PAR-PROC-30 (PROTOCOL §6.4 follow-up; ledger Divergences row `tfc:crafting/bricks`)"
---

# PAR-PROC-25: Workbench crafting in-game verification: 10 sampled CE crafts in a crafting table

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
