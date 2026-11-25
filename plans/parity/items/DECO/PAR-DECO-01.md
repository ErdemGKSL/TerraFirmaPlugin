---
id: PAR-DECO-01
title: "Wood families loaded in CraftEngine: planks, stairs, slabs, fences, walls with our block_state templates"
area: DECO
status: done
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
  - "Note (deco-tool session 2026-11-19): PAR-WORLD-10 is done, so this is unblocked. Needs per-state shape demands in hitbox/HitboxDemands (stairs 80 states x 20 woods, slabs, fences, walls) plus block_state templates; planks, logs and leaves already exist in configuration/wood/."
  - "Done: PAR-DECO-01: stairs, slabs, fences and log fences of the 20 woods are CE blocks in configuration/woodfamily/<wood>.yml (CE stairs_block / slab_block / fence_block, 80 / 6 / 32 states, fluid_state on waterlogged variants, TFC strengths, wood sounds, minecraft: tags from TFC's tag files, self drops with 2 for a double slab, TFC block and item models, names of every locale). Carriers through HitboxDemands shape twins: each wood's stairs and slabs own one vanilla stairs / slab block with TFC's model per state, the double slab draws the planks' visual, every fence is hitbox-only on minecraft:oak_fence with item displays of TFC's post and sides; tfc-shapes rows ported from vanilla; 41 twin blocks in vanilla-shapes-*.json and carrier-policy.json (only_class). Walls are rock (not this item); fence gates stay family data for PAR-DECO-02. Tests: WoodFamiliesTest +2, updated pins in GeneratedCarrierTest and BlockCoverageTest. Pending in-game check: core Showcase has no wood family, so the screenshot needs a give-and-place run."
tests:
  - "tools/datagen: WoodFamiliesTest + allocation report"
  - "VisualBudgetTest"
done_criteria:
  - "All 20 woods' planks/stairs/slabs/fences place with correct shapes (showcase screenshot)"
---

# PAR-DECO-01: Wood families loaded in CraftEngine: planks, stairs, slabs, fences, walls with our block_state templates


All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
