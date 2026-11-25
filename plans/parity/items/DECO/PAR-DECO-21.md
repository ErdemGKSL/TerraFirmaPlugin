---
id: PAR-DECO-21
title: "Metal bars and chains (follow-up of PAR-DECO-09)"
area: DECO
status: open
wave: 49
deps: [PAR-DECO-09]
size: M
aliases: ["split from PAR-DECO-09: bars and chains need shape rows and twin states of iron_bars and the chain, whose vanilla id differs per version"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/Metal.java"
  - "src/main/java/net/dries007/tfc/common/blocks/TFCChainBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/MetalBarsChains.java"
  - "tools/datagen/src/test/java/dev/tfp/datagen/MetalBarsChains*"
  - "content/generated/content/craftengine/tfp/configuration/metalbars/**"
ui: true
in_game_steps:
  - "place tfp:metal/bars/copper and tfp:metal/chain/copper in a row and a column; screenshot"
plan:
  - "Bars: 9 metals (`ALL` part type) x 32 states (north, east, south, west, waterlogged), hitbox-only on one shared `minecraft:iron_bars` carrier per outline, TFC's multipart (post_ends, post, cap, side, side_alt) as item displays; needs TfcShapes rows and vanilla dump rows of iron_bars (ported IronBarsBlock outlines)."
  - "Chains: 9 metals x axis x, y, z (+ TFC's lava-logged light), the vanilla chain block of each version (`chain` up to 1.21.8, `iron_chain` from 1.21.9: check the dump) as the twin; TFC's model turned by the blockstate x / y."
tests:
  - "tools/datagen: MetalBarsChainsTest"
done_criteria:
  - "Bars and chains of the nine metals are CE blocks with TFC strength 5 / 6 (chain 5 / 6, SoundType.CHAIN), models, drops and names; carriers pinned"
---

# PAR-DECO-21: Metal bars and chains (follow-up of PAR-DECO-09)

All fields live in the front matter above. Follow `plans/parity/PROTOCOL.md`.
