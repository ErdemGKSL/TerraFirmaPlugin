---
id: PAR-DECO-01
title: "Wood families loaded in CraftEngine: planks, stairs, slabs, fences, walls with our block_state templates"
area: DECO
status: open
wave: 40
deps: [PAR-WORLD-10, PAR-INFRA-13]
size: M
aliases: ["M6-DECO-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCStairBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCSlabBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/wood/TFCFenceBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/WoodFamilies.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/WoodFamilies*"
  - "content/generated/content/craftengine/tfp/families/wood.yml"
  - "content/generated/content/craftengine/tfp/configuration/woodfamily/**"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/templates/block_state*"
ui: true
in_game_steps:
  - "tfp debug showcase wood"
  - "screenshot"
plan:
  - "Current: families/wood.yml (780 blocks) is generated but not loaded (outside configuration/)."
  - "Our `tfp:block_state/slab|stairs|wall|fence` templates (CE defaults allow only 5 slabs / 4 stairs), visuals via the allocator (auto_state pools for non-full shapes, entity_renderer for fences/walls if pools run out), then move the entries into configuration/woodfamily/; make the generator loud when blockstates are missing."
tests:
  - "tools/datagen: WoodFamiliesTest + allocation report"
  - "VisualBudgetTest"
done_criteria:
  - "All 20 woods' planks/stairs/slabs/fences place with correct shapes (showcase screenshot)"
---

# PAR-DECO-01: Wood families loaded in CraftEngine: planks, stairs, slabs, fences, walls with our block_state templates

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
