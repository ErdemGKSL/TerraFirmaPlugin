---
id: PAR-PROC-31
title: "Vanilla full-cube blocks made by TFC barrel, loom, heating and glassworking recipes: stop repurposing them; guard over every recipe type"
area: PROC
status: open
wave: 9
deps: [PAR-PROC-30]
size: S
aliases: ["follow-up of PAR-PROC-30 (review note: 'workbench recipes (other recipe types pending)' had no owner)"]
tfc_refs:
  - "src/generated/resources/data/tfc/recipe/barrel/"
  - "src/generated/resources/data/tfc/recipe/loom/white_wool.json"
  - "src/generated/resources/data/tfc/recipe/heating/"
  - "src/generated/resources/data/tfc/recipe/glassworking/"
files_owned:
  - "tools/datagen/src/main/resources/dev/tfp/datagen/visuals/fullcube-states.csv"
  - "docs/research/vanilla-fullcube-supply.csv"
  - "docs/research/block-budget-workarounds.md"
  - "tools/datagen/scripts/export_fullcube_states.py"
  - "tools/datagen/src/test/java/dev/tfp/datagen/visuals/**"
ui: false
plan:
  - "Found 2026-11-07 by the PAR-PROC-30 review. PAR-PROC-30 reclassified only the workbench result minecraft:bricks as tfc_present. TFC also makes vanilla full-cube blocks through other recipe types: barrel (dye/bleach to wool, terracotta, glazed terracotta, concrete powder), loom (loom/white_wool.json), heating (glazed terracotta) and glassworking (stained glass). They are classified `absent` in docs/research/vanilla-fullcube-supply.csv, so profiles suppress some of them (at 71de43e: nether-end-off profiles 16, 8 glazed terracotta and 8 wool colours; nether-end-on profiles 8). Nothing is reachable yet because those devices are not ported (barrel PAR-PROC-08 open)."
  - "Fix: list every vanilla full-cube result of TFC's barrel/loom/heating/glassworking/other block-making recipes (read the TFC JSON, not by hand), reclassify them tfc_present in the research CSV, the exporter's crafted set (TFC_CRAFTED) and fullcube-states.csv, regenerate content (./gradlew :tools:datagen:generateContent -Ptfc.checkout=/opt/tfc), check every profile still fits its pool (PAR-REL-12 guard), and record any profile that no longer fits as a divergence."
  - "Widen CraftedResultsNotRepurposedTest beyond configuration/crafting/*_recipes.yml so it reads the result ids of every recipe type the TFC tree defines (or that datagen emits), so a new device port cannot bring back a repurposed result."
  - "Update docs/research/block-budget-workarounds.md: it still quotes the terrain-grade supply 664/668 (663/667 after PAR-PROC-30, lower again after this item)."
tests:
  - "tools/datagen visuals: no vanilla block produced by any TFC recipe type is a suppressed (repurposed) block in any profile"
done_criteria:
  - "No vanilla block that any TFC recipe produces is repurposed in any visual profile; the widened guard test passes; all profiles still fit their pools"
---

# PAR-PROC-31: Vanilla full-cube blocks made by TFC barrel, loom, heating and glassworking recipes

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
