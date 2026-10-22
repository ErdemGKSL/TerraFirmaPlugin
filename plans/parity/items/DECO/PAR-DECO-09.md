---
id: PAR-DECO-09
title: "Metal decoration blocks: metal blocks (and weathered), bars, chains, grates, trapdoors"
area: DECO
status: open
wave: 47
deps: [PAR-DECO-01, PAR-INFRA-13]
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
tests:
  - "tools/datagen: MetalFamiliesTest"
done_criteria:
  - "Metal showcase screenshot"
---

# PAR-DECO-09: Metal decoration blocks: metal blocks (and weathered), bars, chains, grates, trapdoors

> see_not_parity: NP-VIS-02 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
