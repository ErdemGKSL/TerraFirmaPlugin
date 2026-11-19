---
id: PAR-WORLD-15
title: "Volcanic and spring CE blocks: hardened rock, magma blocks, spring water look"
area: WORLD
status: done
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
ui: false
in_game_steps: []
plan:
  - "Generate rock magma blocks (7 igneous rocks) and hardened rock; VolcanoBlocks writes them; spring water stays vanilla water (divergence, see PAR-FLUID-03)."
  - "Found: RockFamilies already generates `tfp:rock/hardened/<rock>` (21) and `tfp:rock/magma/<rock>` (7, animated textures, light 6) in `configuration/rock/<rock>.yml`; nothing new under `configuration/volcano/`. Since PAR-WORLD-27 the volcanic blocks are written by `TfcTerrain.volcanicData` + `VolcanicFeatureBlocks` (not owned here), so placing them is follow-up PAR-WORLD-45, which also owes TFC magma behaviour on the CE block (step damage, drag-down bubble columns: the CE server state is not vanilla magma); the screenshot check moved there (PROTOCOL 6.5)."
  - "Done: datagen `volcano/VolcanicRockBlocks` (run from `Datagen.generate`, fails datagen when a hardened rock or magma block the world generator relies on is missing, a magma block exists for a non-igneous rock, a magma texture/mcmeta is missing, or TFC's magma rocks differ from the igneous rocks of PlaceholderRocks); paper `VolcanoBlocks.rockBlockId(kind, rock)` maps HARDENED_ROCK / MAGMA to the CE ids. Tests: datagen `VolcanicRockBlocksTest` (2: counts 21 + 7 on the committed content and the pinned checkout; four failure cases), paper `VolcanoRockBlocksTest` (1). Generated content unchanged (verifyContent up to date). Spring water stays vanilla water (NP-FLUID-01)."
tests:
  - "tools/datagen counts"
done_criteria:
  - "Deferred check tracked in PAR-WORLD-45"
---

# PAR-WORLD-15: Volcanic and spring CE blocks: hardened rock, magma blocks, spring water look

> see_not_parity: NP-VIS-02, NP-FLUID-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
