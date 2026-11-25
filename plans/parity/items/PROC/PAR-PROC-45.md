---
id: PAR-PROC-45
title: "Newer vanilla recipe removal in-game verification: copper tools/armour and spears do not craft, kept recipes still do (the check PAR-PROC-43 closed without)"
area: PROC
status: done
wave: 19
deps: []
size: S
aliases: ["follow-up of PAR-PROC-43 (its in_game_steps; PROTOCOL 6.5 deferred check: the datagen work is done and tested, a headless client run was not available in the worker's container)"]
tfc_refs: []
files_owned:
  - "docs/verification/PAR-PROC-43/**"
ui: true
in_game_steps:
  - "give Bot minecraft:copper_ingot 8 and minecraft:stick 2; open a crafting table; lay out a copper pickaxe: no result; screenshot at GUI scale 2"
  - "lay out a copper chestplate and a wooden spear (vanilla planks): no result; screenshot"
  - "lay out minecraft:stick from two vanilla planks (a kept recipe): result shows (control)"
plan:
  - "PAR-PROC-43 ships `NewerVanillaRecipes` (datagen writes data/minecraft/recipe/<id>.json with TFC's false condition for copper tools/armour and the spears; CraftListener blocks disabled ids). Run the steps on Paper 1.21.11 with the generated content installed; store crops and verdict.md in docs/verification/PAR-PROC-43/. Fix failures through PAR-PROC-43's files or a follow-up."
  - "Done: in-game check run 2026-11-25 on Paper 1.21.11 with the headless client (GUI scale 2): copper pickaxe and chestplate layouts and the wooden spear layout give no result, two planks still give 4 sticks. Evidence and verdict: docs/verification/PAR-PROC-43/ (verdict.md). No code change."
tests: []
done_criteria:
  - "Copper tools/armour and spears do not craft on 1.21.11; kept vanilla recipes still craft (screenshots reviewed, verdict.md)"
---

# PAR-PROC-45: Newer vanilla recipe removal in-game verification

All fields live in the front matter above; the loop parses them. Follow `plans/parity/PROTOCOL.md`.
