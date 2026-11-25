---
id: PAR-DECO-09
title: "Metal decoration blocks: metal blocks (and weathered), bars, chains, grates, trapdoors"
area: DECO
status: done
wave: 47
deps: [PAR-DECO-01, PAR-INFRA-13, PAR-INFRA-17]
size: M
aliases: ["M6-DECO-03"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/Metal.java"
  - "src/main/java/net/dries007/tfc/common/blocks/GrateBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCChainBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/MetalFamilies.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/MetalFamilies*"
  - "content/generated/content/craftengine/tfp/families/metal.yml"
  - "content/generated/content/craftengine/tfp/configuration/metalfamily/**"
ui: true
in_game_steps:
  - "tfp debug showcase metal"
plan:
  - "Load the 177 metal family blocks (lamps in PAR-METAL-19); chains as vanilla chain carriers, bars as iron bars carriers where pools allow, otherwise entity_renderer."
  - "Renderer cubes (metal blocks) use PAR-INFRA-17's `tfp:block/rendered_cube` template with destroy_stages, TFC metal sounds and material particles."
  - "Done: 61 renderer cubes (metal block x4 ages, grate x4 ages), 40 stairs, 40 slabs and 9 trapdoors as CE blocks (configuration/metalfamily/<metal>.yml, MetalFamilies.writeBlocks; slabs, stairs and trapdoors hitbox-only on shared twins stone_brick_slab / stone_brick_stairs / oak_trapdoor); bars and chains moved to PAR-DECO-21, lamps stay PAR-METAL-19. MetalFamiliesTest (+1) passes. Pending in-game check (showcase metal not written; container cannot run the client)."
tests:
  - "tools/datagen: MetalFamiliesTest"
done_criteria:
  - "Metal showcase screenshot"
  - "Feel checklist of docs/research/ore-entity-renderer-design.md 6 passes for every renderer-drawn cube of this item (cracks via destroy_stages track progress, the material's particles and sounds, TFC mining time/tool tier, light at noon/midnight/torch, no z-fighting or seams, full-block outline and hitbox, no leftover display after /fill or TNT, pick-block gives the block's item, no ring at distance), screenshots in docs/verification/PAR-DECO-09/"
---

# PAR-DECO-09: Metal decoration blocks: metal blocks (and weathered), bars, chains, grates, trapdoors

> see_not_parity: NP-VIS-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
