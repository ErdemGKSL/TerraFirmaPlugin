---
id: PAR-DECO-12
title: "Misc blocks: crates, buoys, cakes, golden bamboo, palm mosaic, tree roots, light block"
area: DECO
status: open
wave: 48
deps: [PAR-INFRA-02]
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
tests:
  - "tools/datagen counts"
done_criteria:
  - "Showcase screenshot"
---

# PAR-DECO-12: Misc blocks: crates, buoys, cakes, golden bamboo, palm mosaic, tree roots, light block

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
