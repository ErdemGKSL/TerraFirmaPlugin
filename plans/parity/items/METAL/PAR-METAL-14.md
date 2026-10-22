---
id: PAR-METAL-14
title: "Bloomery multiblock and bloom"
area: METAL
status: open
wave: 35
deps: [PAR-METAL-11, PAR-INFRA-10, PAR-HEAT-04]
size: M
aliases: ["M4-BLOOM-01"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/devices/BloomeryBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BloomeryBlockEntity.java"
  - "src/main/java/net/dries007/tfc/common/blocks/BloomBlock.java"
  - "src/main/java/net/dries007/tfc/common/blockentities/BloomBlockEntity.java"
  - "src/generated/resources/data/tfc/recipe/bloomery"
files_owned:
  - "core/src/main/java/dev/tfp/core/bloomery/**"
  - "core/src/test/java/dev/tfp/core/bloomery/**"
  - "core/src/test/resources/dev/tfp/core/bloomery/**"
  - "paper/src/main/java/dev/tfp/paper/bloomery/**"
  - "paper/src/test/java/dev/tfp/paper/bloomery/**"
  - "tools/datagen/src/main/java/dev/tfp/datagen/bloomery/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/bloomery/**"
  - "content/generated/content/craftengine/tfp/configuration/bloomery/**"
ui: true
in_game_steps:
  - "build a 3-high bloomery chimney; throw hematite + charcoal; light; tfp time add hours 15; screenshot bloom"
plan:
  - "Bloomery door block; chimney of #bloomery_insulation scanned async; ore + charcoal thrown in (item entities consumed, alternating intake); lit -> lazy phase (14400 ticks) -> bloom block with layers; breaking the bloom with a pickaxe yields raw iron blooms; refine on the anvil."
tests:
  - "core: BloomeryGoldenTest exists"
  - "paper: chimney scan"
done_criteria:
  - "Wrought iron path: bloomery -> raw bloom -> refined bloom (self-test)"
---

# PAR-METAL-14: Bloomery multiblock and bloom

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
