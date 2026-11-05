---
id: PAR-PROC-02
title: "Workbench crafting: the plain 2,255 shaped/shapeless recipes as CE recipe YAML"
area: PROC
status: done
wave: 6
deps: []
size: M
aliases: ["M3-GUI-02"]
tfc_refs:
  - "src/generated/resources/data/tfc/recipe/crafting"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/crafting/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/crafting/**"
  - "content/generated/content/craftengine/tfp/configuration/crafting/**"
ui: false
in_game_steps: []
plan:
  - "Datagen converts the 1686 shaped + 569 shapeless TFC recipes to CE recipe YAML (tags -> CE tag ingredients; vanilla items kept); report the ones CE cannot express and route them to PAR-PROC-03."
  - "Built (2026-11-05): tools/datagen crafting/CraftingRecipes + ItemTags write craftengine/tfp/configuration/crafting/<group>_recipes.yml and reports/crafting-recipes.json. Every plain recipe lands in one bucket: emitted (114 today), routed to PAR-PROC-03 (183: 137 tfc:fluid_content, 46 tfc:and/not_rotten), pending (1958: the result or a whole slot is not a CE item yet; the report names each missing id and its owner item or family file; tfc:gem_saw waits for PAR-PROC-24). Pending recipes are emitted automatically by the generation that adds their items. Tags are expanded to item lists at datagen (our CE items carry no TFC tags; CE resolves only vanilla and CE settings tags): c: = NeoForge members + TFC additions, minecraft: = the server's vanilla tag + TFC additions, optional vanilla references dropped."
  - "In-game screenshot check deferred to PAR-PROC-25 (PROTOCOL 6.5): this worker session could not start the cheap-model agent for the headless client. CE load checked here on Paper 1.21.11 + CE 26.9.2: docs/verification/PAR-PROC-02/verdict.md."
  - "Done: PAR-PROC-02 commit (datagen crafting/ generator, 6 CraftingRecipesTest cases on the real checkout, CeYamlCheck allows recipes); CE load verdict docs/verification/PAR-PROC-02/verdict.md; follow-ups PAR-PROC-24 (tool block tags), PAR-PROC-25 (in-game crafts)."
tests:
  - "tools/datagen: count of emitted + routed = 2255 (routed = PAR-PROC-03 + pending on missing items; CraftingRecipesTest)"
done_criteria:
  - "CE loads the recipes with 0 issues (docs/verification/PAR-PROC-02/verdict.md)"
  - "Deferred check tracked in PAR-PROC-25 (a sample of 10 crafts in-game)"
---

# PAR-PROC-02: Workbench crafting: the plain 2,255 shaped/shapeless recipes as CE recipe YAML

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
