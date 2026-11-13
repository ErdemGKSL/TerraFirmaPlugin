---
id: PAR-WORLD-26
title: "Amethyst geodes: tfc:geode (hardened basalt shell, quartzite layers, amethyst ore) in the vein step"
area: WORLD
status: open
wave: 22
deps: [PAR-WORLD-08, PAR-WORLD-24]
size: M
aliases: ["parity audit 2026-11-13: tfc:geode is in the in_biome/veins tag of every TFC biome but VeinData skips it and no item names it"]
tfc_refs:
  - "src/main/resources/data/tfc/worldgen/configured_feature/geode.json"
  - "src/main/resources/data/tfc/worldgen/placed_feature/geode.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/veins.json"
  - "src/main/java/net/dries007/tfc/world/feature/TFCFeatures.java"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/geode/**"
  - "core/src/test/java/dev/tfp/core/worldgen/geode/**"
  - "core/src/test/resources/dev/tfp/core/worldgen/geode/**"
  - "paper/src/main/java/dev/tfp/paper/worldgen/Geode*.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/Geode*"
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
ui: true
in_game_steps:
  - "Fresh world generated after this item; the itest case prints the position of one geode between y -48 and 30; tp Bot next to it; fill the shell's near half with air via the commit queue debug (or tp inside); screenshot at GUI scale 2: hardened basalt outside, quartzite layer, quartzite with amethyst ore on about 30% of the inner face, hollow centre"
  - "tfp debug block on an inner block: tfp:ore/amethyst/quartzite; on the shell: tfp:rock/hardened/basalt"
plan:
  - "Current (audit 2026-11-13): `in_biome/veins` lists `tfc:geode` after the 54 veins in every TFC biome. `core/worldgen/ore/VeinData.tagOrder` drops it on purpose (comment: 'other features of the tag (the geode) are left out') and no item, ledger row or open-gaps file names it, so TFC worlds here have no geodes."
  - "TFC config: a vanilla `minecraft:geode` feature with filling air, outer layer `tfc:rock/hardened/basalt`, middle and inner layer `tfc:rock/hardened/quartzite`, alternate inner layer `tfc:ore/amethyst/quartzite`, inner placements air, use_alternate_layer0_chance 0.3, invalid_blocks_threshold 1, invalid blocks `#minecraft:geode_invalid_blocks`, cannot_replace `#minecraft:features_cannot_replace`, crack, layers and every other omitted key at the vanilla codec defaults (take them from the 1.21.1 GeodeConfiguration, GeodeCrackSettings and GeodeLayerSettings codecs, not from memory). Placement: rarity_filter 1 in 50, in_square, height uniform -48..30."
  - "Port vanilla GeodeFeature (net.minecraft.world.level.levelgen.feature.GeodeFeature, MC 1.21.1 as TFC runs it) platform-free in core/worldgen/geode with the same order of random draws (WorldgenRandom seeded per feature index like the vein step), the normal noise for the shell distortion, the crack plane and the 'invalid blocks' abort rule; read the config from the shipped placed/configured JSON through datagen output, never a hand copy."
  - "Place it in TfcTerrain's vein step at its tag position (after the veins) under our chunk-boundary rule (NP-WORLD-01): the geode only writes inside the chunk being filled, decided from the 3x3 origin chunks deterministically, so a geode crossing chunk borders is identical whichever chunk generates first. Blocks are the CE ids of the hardened rocks and the amethyst ore (palette ids in TfcTerrain)."
  - "Amethyst ore here is the CE ore block `tfp:ore/amethyst/quartzite` with its overlay (PAR-INFRA-15/16); no budding amethyst and no vanilla amethyst clusters exist in TFC's config (inner placements air), so none are placed."
  - "Ledger: feature row; a divergence row only if the chunk-boundary rule changes a geode compared with TFC's 3x3 feature region."
tests:
  - "core: GeodeGoldenTest: block-for-block equality with a vanilla GeodeFeature oracle run on TFC's config (scratch export from a 1.21.1 dev run kept outside the repo) for 20 seeds/origins, including one aborted by invalid blocks"
  - "core: chunk-order independence: a geode spanning 4 chunks is identical when the chunks are filled in any order"
  - "core: frequency: about 1 geode per 50 chunks over 2000 sampled chunks (binomial 99% bound), all origins within y -48..30"
  - "itest: worldgen.geode finds at least one geode in a 40x40 chunk sample and checks its three layers"
done_criteria:
  - "Geodes generate as TFC's: hardened basalt shell, quartzite layers, amethyst ore at the 0.3 alternate chance, hollow inside, about 1 per 50 chunks between y -48 and 30"
  - "Golden and order-independence tests green; the geode is read from the veins tag by the geode code (VeinData stays PAR-WORLD-08's); screenshot passes review"
---

# PAR-WORLD-26: Amethyst geodes: tfc:geode (hardened basalt shell, quartzite layers, amethyst ore) in the vein step

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
