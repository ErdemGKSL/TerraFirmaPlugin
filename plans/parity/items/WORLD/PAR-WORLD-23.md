---
id: PAR-WORLD-23
title: "TFC rock layers: the rock layer settings walk and per-rock rock settings replace PlaceholderRocks (rock stacking, sand colour, karst surface rocks)"
area: WORLD
status: open
wave: 29
deps: [PAR-WORLD-22, PAR-WORLD-04]
size: M
aliases: ["parity audit 2026-11-08: ledger divergence rows 'TFC maps a rock layer to a rock through the datapack rock settings', 'TFC marks surface rocks that are karst', 'TFC writes veins over ... the TFC rock settings' rocks' and the beach-sand mafic row point to M2-GEN-08, whose parity items (PAR-WORLD-04/05) do not port the rock settings"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/settings/RockLayerSettings.java"
  - "src/main/java/net/dries007/tfc/world/settings/RockSettings.java"
  - "src/main/java/net/dries007/tfc/world/settings/Settings.java"
  - "src/main/java/net/dries007/tfc/world/chunkdata/RegionChunkDataGenerator.java"
  - "src/main/java/net/dries007/tfc/world/chunkdata/RockData.java"
  - "src/main/java/net/dries007/tfc/world/region/KarstSurfaceRocks.java"
  - "src/main/java/net/dries007/tfc/world/surface/SurfaceStates.java"
  - "src/generated/resources/data/tfc/worldgen/world_preset/overworld.json"
  - "src/generated/resources/data/tfc/tfc/worldgen/rock_settings"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/chunk/PlaceholderRocks.java"
  - "core/src/main/java/dev/tfp/core/worldgen/rock/**"
  - "core/src/test/java/dev/tfp/core/worldgen/rock/**"
  - "core/src/test/resources/dev/tfp/core/worldgen/rock/**"
  - "core/src/main/java/dev/tfp/core/worldgen/region/KarstSurfaceRocks*.java"
  - "core/src/main/java/dev/tfp/core/worldgen/region/RegionGenerator.java"
  - "core/src/test/java/dev/tfp/core/worldgen/region/KarstSurfaceRocks*"
  - "paper/src/main/java/dev/tfp/paper/worldgen/RockPalette.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/RockPalette*"
ui: true
in_game_steps:
  - "Fresh world created after this item; tp Bot to the land, uplift and volcanic sample positions printed by `tfp debug worldgen rocks` (or the itest case); spectator mode"
  - "At each position fill a 1x40x8 trench down from the surface (`fill ~ ~-40 ~ ~ ~ ~8 air`), screenshot the exposed rock column from the side"
  - "tp Bot to a karst biome point named by the itest case; screenshot the surface"
plan:
  - "Current (audit 2026-11-08): `core/worldgen/chunk/PlaceholderRocks` maps a region rock id and a layer to a raw rock with our own rule: the supertype (ocean, volcanic, land, uplift) picks one category and `(rock >> 2) + layer` picks a rock inside it. TFC instead walks the world preset's `rock_layer_settings` (`RockLayerSettings.sampleAtLayer`: the supertype list picks a layer id, each layer maps the chosen rock to the next layer id, down to `bottom`), so land is sedimentary three times in four over marble/quartzite/low-grade metamorphic over high-grade over the bottom intrusives. Our columns therefore stack different rocks than TFC's, which changes which ores (rock-restricted veins), loose rocks, sand and gravel a player finds. The per-rock `rock_settings` data (raw, hardened, gravel, cobble, sand, sandstone, loose, mossy_loose, spike, karst, mafic) is not read either: the beach sand colour collapses into one sand kind, `Region.Point.isSurfaceRockKarst` stays false so the karst branches of `BiomeChoice` never run."
  - "Port `RockLayerSettings` (codec field names, the layer graph built and validated as TFC's `decode`: unknown ids, cycles and missing `bottom` refused at load) and `RockSettings` into core/worldgen/rock as immutable records read from the exported `data/tfp/tfp/worldgen/rock_settings/*.json` and from PAR-WORLD-22's `WorldSettings.rockLayerSettings` (defaults = TFC's world preset). Keep `PlaceholderRocks`' public API (`names()`, `count()`, `index(rock, layer)`, `isMafic(index)`) so the volcano, groundcover and terrain callers keep compiling, but make it delegate to the ported sampler; rename later only through the owning items."
  - "Port `KarstSurfaceRocks` as a region task in TFC's order inside `RegionGenerator` (it reads the surface rock's `karst` flag), so `isSurfaceRockKarst` is set and the karst biome variants run; regenerate the region/biome goldens that change and record which."
  - "`RockPalette` takes each rock's blocks from its rock settings (sand colour per rock for beaches and sandstone, gravel, cobble, hardened) instead of name concatenation; the beach-sand mafic test reads `RockSettings.mafic`."
  - "Ledger: close the PlaceholderRocks, karst surface rock, vein-host and beach-sand rows (update them to the new behaviour or delete the divergence)."
tests:
  - "core: RockLayerSettingsTest: the shipped preset decodes; a cycle, an unknown rock and a missing bottom are refused with TFC's messages"
  - "core: RockLayerGoldenTest: 10,000 (pointRock, layer) samples equal TFC's `sampleAtLayer` from a scratch oracle on the pinned checkout"
  - "core: KarstSurfaceRocksGoldenTest and the regenerated region goldens (isSurfaceRockKarst per point vs TFC)"
  - "core: chunk rock column golden (ChunkDataGenerator rock layers + this mapping) for 20 chunks vs TFC"
  - "paper: RockPaletteTest: beach sand and sandstone colour per rock from rock settings"
done_criteria:
  - "PlaceholderRocks no longer contains its own category table; every rock column and karst flag matches TFC's goldens; ledger rows updated"
  - "Screenshots show TFC's layer stacking (sedimentary over metamorphic over intrusive on land) and a karst surface; reviewed"
---

# PAR-WORLD-23: TFC rock layers: the rock layer settings walk and per-rock rock settings replace PlaceholderRocks (rock stacking, sand colour, karst surface rocks)

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
