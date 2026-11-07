---
id: PAR-PROC-30
title: "minecraft:bricks is craftable in TFC: stop repurposing it as a visual state; bricks craft in-game re-check"
area: PROC
status: done
wave: 8
deps: []
size: S
aliases: ["follow-up of PAR-PROC-25 (in-game check of PAR-PROC-02's workbench recipes)"]
tfc_refs:
  - "src/generated/resources/data/tfc/recipe/crafting/bricks.json"
files_owned:
  - "tools/datagen/src/main/resources/dev/tfp/datagen/visuals/fullcube-states.csv"
  - "docs/research/vanilla-fullcube-supply.csv"
  - "tools/datagen/scripts/export_fullcube_states.py"
  - "tools/datagen/src/test/java/dev/tfp/datagen/visuals/**"
  - "docs/verification/PAR-PROC-30/**"
ui: true
in_game_steps:
  - "Place a crafting table in front of Bot (survival, TFC world) and open it"
  - "Craft tfp:crafting/bricks (minecraft:brick in X, tfp:mortar in Y of XYX/YXY/XYX): the result slot shows 4 Bricks; shift-take them; data get entity Bot Inventory shows minecraft:bricks count 4"
  - "Place one of the crafted bricks: it shows the vanilla brick texture (not a TFC rock or other repurposed look) and drops itself when mined"
  - "The same grid with the material item of tfp:mortar (minecraft:paper) in place of mortar gives no result"
  - "Vanilla's 2x2 bricks recipe (4 minecraft:brick) still gives no result: TFC disables it (data/minecraft/recipe/bricks.json is neoforge:false), so un-repurposing minecraft:bricks must not bring it back (KeptVanillaRecipes/CraftListener)"
  - "Capture the bricks grid at GUI scale 2 and 3 (PROTOCOL §6.1); PAR-PROC-25 stored scale 3 only"
  - "When done: point the bricks row of docs/verification/PAR-PROC-02/verdict.md to docs/verification/PAR-PROC-30/, update the \"Done\" plan line of plans/parity/items/PROC/PAR-PROC-25.md (10 of 10), and mark the ledger divergence row resolved"
plan:
  - "Found 2026-11-06 by PAR-PROC-25 (docs/verification/PAR-PROC-02/verdict.md, crafts/bricks.png): tfp:crafting/bricks loads in CraftEngine but the result slot stays empty in a TFC world. minecraft:bricks is classified `absent` in the full-cube supply (docs/research/vanilla-fullcube-supply.csv row `bricks,absent,...`), so every visual profile repurposes it (visual_mappings `minecraft:bricks: minecraft:stone`, suppressed_blocks.json lists it with creation path `item`) and SuppressionListener.onCraft clears any crafting result that is a repurposed block. TFC crafts minecraft:bricks from 5 brick + 4 mortar (count 4), so the block is player-made in a TFC world and must not be repurposed."
  - "Fix: reclassify bricks as tfc_present in the research CSV, the exporter and fullcube-states.csv (the supply test checks they agree), regenerate content (./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc), check the profiles still fit their pools. Add a datagen test that no `id:` result of the emitted configuration/crafting/*_recipes.yml is a suppressed block in any profile (at commit 5eb498c minecraft:bricks is the only one in all 12 profiles)."
  - "Done: minecraft:bricks reclassified tfc_present in docs/research/vanilla-fullcube-supply.csv, the exporter and fullcube-states.csv; content regenerated (no profile suppresses or maps it; terrain-grade supply 663/667); guard CraftedResultsNotRepurposedTest (2 tests: no emitted workbench result is a suppressed block in any of the 12 profiles; bricks never a candidate state); in-game check on Paper 1.21.11 + CE 26.9.2, TFC world, GUI scale 2 and 3: 4 bricks craft, place as vanilla bricks and drop themselves; paper for mortar and vanilla 2x2 give nothing (docs/verification/PAR-PROC-30/verdict.md)"
tests:
  - "tools/datagen visuals: no emitted workbench recipe result is a suppressed (repurposed) block in any profile"
done_criteria:
  - "minecraft:bricks is not repurposed in any visual profile, and the datagen guard test passes"
  - "tfp:crafting/bricks crafts 4 Bricks in a crafting table in a TFC world (screenshot), placed bricks look like vanilla bricks"
---

# PAR-PROC-30: minecraft:bricks is craftable in TFC: stop repurposing it as a visual state; bricks craft in-game re-check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
