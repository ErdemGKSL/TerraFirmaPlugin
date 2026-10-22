---
id: PAR-WORLD-15
title: "Volcanic and spring CE blocks: hardened rock, magma blocks, spring water look"
area: WORLD
status: open
wave: 26
deps: [PAR-WORLD-14, PAR-INFRA-08]
size: S
aliases: []
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/TFCMagmaBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/HotWaterBlock.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/VolcanoBlocks.java"
  - "tools/datagen/src/main/java/dev/tfp/datagen/volcano/**"
  - "content/generated/content/craftengine/tfp/configuration/volcano/**"
ui: true
in_game_steps:
  - "Locate a volcano with tfp debug chunk; screenshot"
plan:
  - "Generate rock magma blocks (7 igneous rocks) and hardened rock; VolcanoBlocks writes them; spring water stays vanilla water (divergence, see PAR-FLUID-03)."
tests:
  - "tools/datagen counts"
done_criteria:
  - "A caldera shows TFC magma textures (screenshot)"
---

# PAR-WORLD-15: Volcanic and spring CE blocks: hardened rock, magma blocks, spring water look

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
