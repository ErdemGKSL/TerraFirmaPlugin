---
id: PAR-WORLD-65
title: "Tide pools, sea stack patches and bamboo rules, and the placed-feature coverage guard over every TFC biome tag"
area: WORLD
status: open
wave: 51
deps: [PAR-WORLD-21]
size: M
aliases: ["PAR-WORLD-21 follow-up: the rules of PAR-WORLD-21 that did not fit its session"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/feature/TidePoolFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/SeaStacksFeature.java"
  - "src/main/java/net/dries007/tfc/world/feature/plant/TFCBambooFeature.java"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/feature/tide_pool_decorations.json"
  - "src/main/resources/data/tfc/worldgen/configured_feature/sea_stacks_patch.json"
  - "src/main/resources/data/tfc/worldgen/configured_feature/bamboo.json"
  - "src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/decoration/**"
  - "core/src/test/java/dev/tfp/core/worldgen/decoration/**"
  - "core/src/test/resources/dev/tfp/core/worldgen/decoration/**"
ui: false
plan:
  - "PAR-WORLD-21 ported ForestFloor (the six forest_plants patches, extra_island_guano_patch, atoll_guano) with oracle goldens (DecorationGoldenTest) and the glacial oceanic spring; decoration/open_gaps.txt lists tfc:tide_pool, tfc:big_tide_pool, tfc:sea_stacks_patch, tfc:bamboo, tfc:rare_bamboo, tfc:bamboo_golden, tfc:rare_bamboo_golden."
  - "Port in core/worldgen/decoration with TFC's draws in TFC's order, golden against a scratch oracle (the method bodies on Minecraft 1.21.11's RandomSource from the run-paper server jar, as DecorationGoldenTest describes): TidePoolFeature (tide_pool, big_tide_pool: pool shape, water, the decorations it places), SeaStacksFeature through sea_stacks_patch (random_patch 6 tries, xz 8, y 2), TFCBambooFeature (vanilla bamboo, golden bamboo as a block id the wiring maps; podzol base, probability 0.25). Remove each id from open_gaps.txt (empty at done)."
  - "Coverage guard: a scratch script exports every placed feature id reachable from TFC's in_biome tags (tags expanded) into a test resource; the test asserts each is ruled (a rule list per package: groundcover_deposit.tsv minus its gaps, volcano_features.tsv and VolcanicFeatures, the plant and tree placement goldens, cave/CaveDecoration, ForestFloor, this item's rules) or listed in one of the open-gaps files, so no TFC id is untracked again."
tests:
  - "core: DecorationGoldenTest gains tide pools, sea stacks and bamboo vs the oracle; decoration/open_gaps.txt empty"
  - "core: coverage guard over all TFC biomes"
done_criteria:
  - "All listed ids have golden-tested rules; decoration/open_gaps.txt is empty; the coverage guard passes over all TFC biomes"
---

# PAR-WORLD-65: Tide pools, sea stack patches and bamboo rules, and the coverage guard

> Claimed: session_011osUp5nmwNAYj7g53gEDAm, 2026-11-25

> see_not_parity: NP-WORLD-01 (plans/parity/NOT-PARITY.md): build the listed substitute for those parts, not TFC's exact behaviour.

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
