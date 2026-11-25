---
id: PAR-DECO-18
title: "Sandstone stairs, slabs and walls and the calcite spike"
area: DECO
status: open
wave: 49
deps: [PAR-DECO-08, PAR-DECO-06]
size: M
aliases: ["split from PAR-DECO-08: 63 shape blocks (7 colours x raw/smooth/cut x stairs/slab/wall) need vanilla twin states that DECO-01 and DECO-06 also need"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/SandstoneBlockType.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java"
  - "src/main/java/net/dries007/tfc/common/blocks/ThinSpikeBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/sandstone/**"
  - "content/generated/content/craftengine/tfp/configuration/sandstone/**"
ui: false
plan:
  - "Reuse the shape strategy DECO-06 settles for rock stairs, slabs and walls (shared hitbox-only twin with item displays, or a per-block twin if enough vanilla blocks remain); the DECO-01 twin approach used 20 stairs and 20 slab blocks."
  - "tfc:calcite is a ThinSpikeBlock (tip true/false, no loot table): a non-cube spike, own behaviour."
tests:
  - "tools/datagen counts"
done_criteria:
  - "63 sandstone shape blocks and calcite emitted, carriers allocated, ledger updated"
---

# PAR-DECO-18: Sandstone stairs, slabs and walls and the calcite spike

All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`.
