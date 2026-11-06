---
id: PAR-PROC-24
title: "Our tfp:mineable/* block tags as server tags, so CE can build tools whose tool component names them (gem saw, knife, hammer, scythe)"
area: PROC
status: done
wave: 7
deps: [PAR-PROC-02]
size: S
aliases: ["follow-up of PAR-PROC-02 (found by the CE load of the workbench recipes)"]
tfc_refs:
  - "src/generated/resources/data/tfc/tags/block/mineable/glass_saw.json"
  - "src/generated/resources/data/tfc/tags/block/mineable/knife.json"
  - "src/generated/resources/data/tfc/tags/block/mineable/hammer.json"
  - "src/generated/resources/data/tfc/tags/block/mineable/scythe.json"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/toolblocktags/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/toolblocktags/**"
ui: false
in_game_steps: []
plan:
  - "Found 2026-11-05 on Paper 1.21.11 + CraftEngine 26.9.2 (PAR-PROC-02): CE builds every recipe result at load, and building tfp:gem_saw throws 'Cannot parse component minecraft:tool ... Missing tag: tfp:mineable/glass_saw in minecraft:block', which made CE fail its whole recipe load ('Failed to load recipes'). The same holds for every CE item whose tool template argument mineable_tag is a tfp: block tag (configuration/material/tool.yml: gem saw; metal.yml: knives, hammers, scythes). Our tfp:mineable/* tags exist only in the plugin's own data/ (tag runtime), not as server tags."
  - "Ship the tfp:mineable/* block tags (members: the vanilla blocks of TFC's tags plus the CE blocks' vanilla carriers where CE needs them, or whatever form the tool component accepts) in the bootstrap datapack, or switch those items' tool rules to an equivalent that needs no server tag. Then CraftingRecipes.unbuildable finds nothing and tfc:crafting/gem_saw is emitted (PAR-PROC-02 regenerates it with no change)."
  - "Done: datagen toolblocktags/ToolBlockTags ships the 7 tfp:mineable/* block tags in the bootstrap datapack tfp_seasonal_biomes (vanilla members only: #minecraft: refs kept, #c:/#tfp: expanded, entries required:false, CE blocks such as poured glass left out and listed in reports/tool-block-tags.json); CraftingRecipes.unbuildable accepts a shipped tag (gem saw recipe emitted, 115 emitted) and its report order is now deterministic (Set.copyOf ordering made verifyContent flaky); ToolBlockTagsTest 4, CraftingRecipesTest updated; server check docs/verification/PAR-PROC-24/verdict.md (CE 0 issues, 160 recipes, ce item give of gem saw, knife, hammer, scythe pass)."
tests:
  - "tools/datagen: CraftingRecipes.unbuildable(tree) is empty; tfp:crafting/gem_saw is in the emitted recipes"
done_criteria:
  - "ce item give of tfp:gem_saw and a metal knife works; CE loads recipes with 0 issues and no 'Failed to load recipes'"
---

# PAR-PROC-24: Our tfp:mineable/* block tags as server tags, so CE can build tools whose tool component names them (gem saw, knife, hammer, scythe)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
