---
id: PAR-WORLD-63
title: "Plant stage packet handler: CHUNK_DATA / BLOCK_CHANGE / MULTI_BLOCK_CHANGE rewrite through PlantStageRemap, CE state id resolution and the module wiring"
area: WORLD
status: open
wave: 30
deps: [PAR-WORLD-61]
size: M
aliases: ["PAR-WORLD-61 follow-up: the PacketEvents side lives in dev.tfp.paper.packet (ArchitectureTest), outside PAR-WORLD-61's files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/client/model/PlantBlockModel.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/packet/plantstage/**"
  - "paper/src/test/java/dev/tfp/paper/packet/plantstage/**"
  - "paper/src/main/java/dev/tfp/paper/plant/lifecycle/**"
  - "paper/src/test/java/dev/tfp/paper/plant/lifecycle/**"
ui: true
in_game_steps:
  - "Same meadow with canna, water canna and goldenrod (the plants that got a dormant look) in spring, summer, autumn, winter (tfp debug calendar): the dormant look (canna and water canna vanish, goldenrod shows goldenrod_2) in the dormant/dying weeks, the blooming look otherwise; screenshot each season"
plan:
  - "PAR-WORLD-61 built the data and the pure rule: datagen puts the dormant looks on tfp:plant/dormant_look (look 0..n-1, spare tripwire states) and the table base CE state -> {plant, state} into data/tfp/data_maps/plant_lifecycle.json (dormant_states); paper/plant/lifecycle has PlantStageTable (reads it), PlantStageRemap (resolve the CE states to server ids, block(), section() over 4096 ids) and PlantStageClimate (chunk climate and TFC's average temperature from the seed)."
  - "Module (paper/plant/lifecycle, FeatureModule + one service-file line): load PlantStageTable on the CPU pool; once CraftEngine has loaded, resolve every CE state to its server id (BlockVisuals.blockData(BlockStateRef) then the packet layer's id of that BlockData) into a volatile PlantStageRemap; re-resolve on /tfp reload and CE reload."
  - "Handler (paper/packet/plantstage): on CHUNK_DATA decode only sections whose palette holds an id PlantStageRemap.rewrites, call section() with the viewer's View (calendar fraction of year and day time from the ClockSnapshot, northern hemisphere from the viewer's z as HudHemisphere) and the world's PlantStageClimate; write the section back (palette may grow: fresh palette as SeasonalBiomeHandler does). BLOCK_CHANGE / MULTI_BLOCK_CHANGE through block(). Netty thread, no world reads. Check first (log one chunk) that PacketEvents sees the server ids before CraftEngine's encoder maps them (docs/spikes/M0-NET-01.md pipeline order)."
  - "Season change: when a plant crosses dormant/blooming the client keeps its old look until the chunk is resent; resend the chunks in view at each month change through the seasonal biome resend (ResendLimiter) or a weekly resend of chunks that hold plants."
tests:
  - "paper: handler rewrites a CHUNK_DATA section holding a base plant id by season and leaves every other packet byte-identical; BLOCK_CHANGE likewise"
done_criteria:
  - "Plants with a dormant look change look with the seasons in game as in TFC's dormant/blooming split (screenshots per season in docs/verification/PAR-WORLD-63/)"
---

# PAR-WORLD-63: Plant stage packet handler

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
