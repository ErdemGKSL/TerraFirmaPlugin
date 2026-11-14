---
id: PAR-CLIM-04
title: "Snow piles, ice piles, icicles and sea ice as CraftEngine blocks"
area: CLIMATE
status: blocked
wave: 13
deps: [PAR-CLIM-03, PAR-INFRA-08, PAR-INFRA-13]
size: M
aliases: ["M2-WTH-03", "M2-SEA-02 (sea ice)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/common/blocks/SnowPileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/IcePileBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/IcicleBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/SeaIceBlock.java"
  - "src/main/java/net/dries007/tfc/common/blocks/ThinSpikeBlock.java"
files_owned:
  - "tools/datagen/src/main/java/dev/tfp/datagen/weather/**"
  - "tools/datagen/src/test/java/dev/tfp/datagen/weather/**"
  - "content/generated/content/craftengine/tfp/configuration/weather/**"
  - "paper/src/main/java/dev/tfp/paper/weather/WorldBlocks.java"
  - "paper/src/main/java/dev/tfp/paper/weather/SnowBlock.java"
ui: true
in_game_steps:
  - "tfp debug place tfp:snow_pile[layers=3]"
  - "tfp debug place tfp:icicle"
  - "tfp debug block"
  - "Screenshot each"
plan:
  - "Blocked (2026-11-14): files_owned is too narrow for PAR-INFRA-13's carrier rule. Every explicit appearance state in generated CE YAML must come from the hitbox selector (HardcodedCarrierTest bans `\"minecraft:<block>[` literals outside datagen/hitbox and datagen/visuals; GeneratedCarrierTest checks each state equals HitboxAllocation.agreed of a HitboxDemands entry, and pins the exact block ids and block_state_mappings). So the four blocks need rows and demands in files this item does not own: tools/datagen/src/main/java/dev/tfp/datagen/hitbox/HitboxDemands.java, hitbox/TfcShapes.java + src/main/resources/dev/tfp/datagen/hitbox/tfc-shapes.json (tfc:snow_pile[layers=1..8] = vanilla snow shapes, tfc:ice_pile and tfc:sea_ice full cubes with ice friction, tfc:icicle[tip=true|false] = ThinSpikeBlock TIP_SHAPE / PILLAR_SHAPE) and tools/datagen/src/test/java/dev/tfp/datagen/hitbox/GeneratedCarrierTest.java (pin the 4 ids and any hide mappings), as PAR-PROC-34 had. Also: carrier-policy.json rejects pointed_dripstone (random XZ offset), so the icicle carrier must be the selector's choice. Unblock: add those four paths to files_owned (owner/manager). Prepared and tested in the worker's worktree, not published: datagen/weather/WeatherBlocks.java (TFC blockstates/loot/tags/strengths checked, TFC's icicle/thin_spike/sea_ice models and texture copied, sturdy_base_block up+stackable as ThinSpikeBlock.canSurvive, snow pile on the state real snow of that height shows), WeatherBlocksTest (7), WorldBlocks write/read swap through CustomBlocks with placeholder fallback, WorldBlocksCraftEngineMockBukkitTest (pile over fern placed as tfp:snow_pile and melted back to the fern + absorbed grass; ice pile, sea ice, icicle tip/pillar mapping)."
  - "Current: SnowService writes vanilla placeholders and remembers the covered block in the chunk PDC."
  - "Generate CE blocks: `tfp:snow_pile` (layers 1-8, carrier repurposed snow layers + covered-block entity_renderer only where needed), `tfp:ice_pile`, `tfp:icicle` (thin spike, tip/middle/base), `tfp:sea_ice`."
  - "Swap SnowBlock/WorldBlocks writers to the CE ids through CustomBlocks; melting restores the remembered block."
  - "Icicles drip/fall (ThinSpikeBlock break when unsupported, damage on fall)."
tests:
  - "tools/datagen: state counts vs TFC blockstates"
  - "paper: MockBukkit pile place/melt restores block"
done_criteria:
  - "Server check: a pile over a plant melts back to the plant; icicles under an overhang at -10 C"
---

# PAR-CLIM-04: Snow piles, ice piles, icicles and sea ice as CraftEngine blocks

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
