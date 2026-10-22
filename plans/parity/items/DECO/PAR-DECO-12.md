---
id: PAR-DECO-12
title: "Misc blocks: crates, buoys, cakes, golden bamboo, palm mosaic, tree roots, light block"
area: DECO
status: open
wave: 48
deps: [PAR-INFRA-02, PAR-INFRA-13, PAR-INFRA-17]
size: M
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/CrateBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/BuoyBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCCakeBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TreeRootsBlock.java"
  - "src/main/java/net/dries007/tfc/client/CrateOverlays.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/misc/**"
  - "core/src/test/java/dev/tfp/core/misc/**"
  - "core/src/test/resources/dev/tfp/core/misc/**"
  - "paper/src/main/java/dev/tfp/paper/misc/**"
  - "paper/src/test/java/dev/tfp/paper/misc/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/misc/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/misc/**"
  - "content/generated/content/craftengine/tfp/configuration/misc/**"
ui: true
in_game_steps:
  - "tfp debug showcase misc"
plan:
  - "Crates (storage, crateEnableAutomation, content preview on look via actionbar instead of CrateOverlays), buoys floating, TFC cakes, decorative blocks."
  - "Crates and workbenches drawn as renderer cubes (level-1 and up) use PAR-INFRA-17's `tfp:block/rendered_cube` template with destroy_stages, TFC wood sounds and material particles."
tests:
  - "tools/datagen counts"
done_criteria:
  - "Showcase screenshot"
  - "Feel checklist of docs/research/ore-entity-renderer-design.md 6 passes for every renderer-drawn cube of this item (cracks via destroy_stages track progress, the material's particles and sounds, TFC mining time/tool tier, light at noon/midnight/torch, no z-fighting or seams, full-block outline and hitbox, no leftover display after /fill or TNT, pick-block gives the block's item, no ring at distance), screenshots in docs/verification/PAR-DECO-12/"
---

# PAR-DECO-12: Misc blocks: crates, buoys, cakes, golden bamboo, palm mosaic, tree roots, light block

> see_not_parity: NP-UI-01, NP-VIS-02, NP-CLIENT-03 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
