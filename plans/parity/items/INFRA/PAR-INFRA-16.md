---
id: PAR-INFRA-16
title: "Ore overlay runtime: packet-only face overlays, neighbour-driven refresh, Folia-safe, feel checklist"
area: INFRA
status: open
wave: 7
deps: [PAR-INFRA-02, PAR-INFRA-10, PAR-INFRA-15, PAR-INFRA-17, PAR-WORLD-17]
size: M
aliases: ["owner decision 2026-10-22: ores as exposed-face overlays", "research: ce-block-visual-workarounds-2 option 2.5", "block-budget-workarounds 5.1"]
tfc_refs:
  - "src/main/resources/assets/tfc/models/block/ore.json"
  - "src/main/resources/assets/tfc/models/block/ore_column.json"
  - "src/main/java/net/dries007/tfc/common/blocks/rock/Ore.java"
  - "src/main/java/net/dries007/tfc/util/tracker/Collapse.java"
  - "src/generated/resources/data/tfc/recipe/collapse/ore"
files_owned:
  - "core/src/main/java/dev/tfp/core/oreoverlay/**"
  - "core/src/test/java/dev/tfp/core/oreoverlay/**"
  - "paper/src/main/java/dev/tfp/paper/oreoverlay/**"
  - "paper/src/test/java/dev/tfp/paper/oreoverlay/**"
  - "paper/src/main/java/dev/tfp/paper/packet/display/**"
  - "paper/src/test/java/dev/tfp/paper/packet/display/**"
  - "integration/craftengine/src/main/java/dev/tfp/integration/craftengine/oreoverlay/**"
  - "integration/craftengine/src/test/java/dev/tfp/integration/craftengine/oreoverlay/**"
  - "itest/src/main/java/dev/tfp/itest/cases/OreOverlayCase.java"
  - "tools/datagen/src/main/resources/dev/tfp/datagen/oreoverlay/mode.properties"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11, then Paper 26.3 + client 26.3, then Folia 1.21.11 (server only, self-test); survival unless noted; spikes/client/testroom.sh test site"
  - "Setup: tfp time set noon; build a 7x5x7 granite box with `tfp debug place` and put tfp:ore/normal_native_gold/granite, tfp:ore/rich_malachite/granite (marble box), tfp:ore/diamond/gabbro (gabbro box), tfp:ore/normal_hematite/shale (shale box) one block inside each wall; `tfp debug oreoverlay stats` shows 0 overlays for them (buried)"
  - "Exposure, same tick: Bot mines the rock in front of each ore; record 10 frames 50 ms apart around the break: the overlay face must be visible in the first frame where the hole is (no frame with plain rock); 8x crop: specks line up with TFC's ore texture orientation on that face; repeat for up, down, north, south, east, west faces"
  - "Covering, same tick: Bot places granite back into the hole; 10 frames: the overlay vanishes in the same frame the face is covered"
  - "Mining an ore: Bot mines the gold ore with a bronze pickaxe; 10 frames 100 ms apart: cracks advance and are never hidden under the specks (cracked overlay variant or dimmed overlay per PAR-INFRA-17); break particles are rock-coloured and the sound is stone (TFC parity); the drop is the TFC ore item; time-to-break with hand/correct/wrong tool recorded and equal to raw granite's"
  - "Hit/step/place sounds: subtitles while Bot walks on and hits an exposed ore: stone sounds"
  - "Light: screenshots of the exposed gold face at noon, midnight (tfp time set midnight) and with a torch 2 blocks away; the specks are lit like the rock face next to them, never black or full-bright; underwater: flood the hole with water and screenshot"
  - "Z-fighting and seams: screenshots at 1, 16, 32, 48 m; 8x crops of an outer edge (two exposed faces) and an inner corner"
  - "Outline and hitbox: crosshair on the ore, 8x crop of the outline (full cube); stand on an exposed ore top, `data get entity Bot Pos[1]` = floor + 1.0"
  - "Falling/collapse: once PAR-PHYS-01 exists, make a rich ore fall (remove its support or `tfp debug collapse <pos>`); screenshot mid-fall: the falling block shows the overlay; it lands as normal ore (TFC collapse recipe); before PAR-PHYS-01 exists: summon a falling_block of the ore state and check the attached overlays"
  - "Leftovers: `fill` the box interior with air, then refill with granite; TNT next to an exposed vein; a piston pushing an exposed ore (if pistons exist in the world): screenshots after each, no floating overlay, overlays only on the faces that are exposed now (`tfp debug oreoverlay stats` before/after)"
  - "Pick-block: creative pick on an exposed and on a buried ore: the ore's own block item"
  - "Distance and ring: tp Bot to 16, 32, 48, 64, 96 m from an exposed cliff vein (pitch level, flat test row against the sky) and screenshot; no square ring, the spherical cutoff pop on native gold is not noticeable at 1080p; walk Bot along a 200-block cave tunnel lined with ore (worldgen or WorldEdit-free `fill` pattern) and record frames: no overlays popping in at chunk borders"
  - "Budget: `tfp debug oreoverlay stats` near spawn of a fresh world: entities sent to Bot, chunks in radius, table builds and ms; must match the S1 numbers within the gate"
plan:
  - "Current: PAR-INFRA-15 makes every ore look like its host rock and ships `ore_overlay/ores.json`, `occluding.json` and the 53 overlay item models (plus cracked variants if S2 chose them); PAR-WORLD-17 measured the face counts and set the defaults; PAR-INFRA-17 decided the crack handling; the packet layer (paper/packet, PacketEvents 2.14) exists with handler metrics; AsyncDecisionPipeline + RegionCommitQueue exist; PAR-INFRA-10 provides per-position coalescing."
  - "Pure core (`core/oreoverlay`): `ExposureRule` (exposing neighbour = air, cave air, fluid, any non-occluding state; shared with the S1 census), `ExposedFaces.compute(OreSnapshot)` -> immutable `OreOverlayTable` (ore positions, per-ore face mask, overlay id, surface-outcrop flag), `FastPath.decide(table, pos, newStateOccludes, newStateIsOre)` -> provisional face changes for the position and its 6 neighbours, `ViewerSet` diff (sent vs wanted per viewer, nearest-first cap, token bucket 1000 burst / 25 per tick)."
  - "CE adapter (`integration/craftengine/oreoverlay`): copy the ore positions of a chunk on its region thread from CraftEngine's per-chunk custom-block storage as int arrays (no per-block API calls), resolved to ore ids through `ores.json`; fall back to the deterministic vein layout + a per-position check if CE exposes no chunk-level read (decide from the CE jar; document it)."
  - "Paper glue (`paper/oreoverlay`, a FeatureModule): `CHUNK_DATA` send -> append the chunk's overlay spawns for that viewer (within the send radius) in the same bundle, or queue a table build; build = region-thread copy (ore positions + occlusion bits of the chunk and a 1-block border) -> CPU pool `ExposedFaces.compute` -> publish on the chunk's region thread (atomic swap in a concurrent map keyed by world + chunk); `BLOCK_CHANGE`/`MULTI_BLOCK_CHANGE` send -> fast path in the same bundle + enqueue the position (coalesced per position per tick) for the authoritative rebuild, whose difference goes to each viewer; positions on a chunk edge schedule the neighbour chunk on its own region; `UNLOAD_CHUNK`/quit -> one REMOVE_ENTITIES per chunk; player movement -> chunks entering/leaving the radius; chunk unload drops the table. No Bukkit entities, nothing persisted, nothing ticks; packet handlers read only immutable tables (no world access, no I/O)."
  - "Display sender (`paper/packet/display`, the only place importing PacketEvents for this): build ADD_ENTITY + SET_ENTITY_DATA for an item_display (position in the neighbour cell, translation -normal x 0.49, fixed rotation per face, item = overlay item with `custom_model_data` colour = face shade 0xFFFFFF/0xCCCCCC/0x999999/0x808080, `view_range` for the configured cutoff; surface outcrops with the full-range view_range) and REMOVE_ENTITIES; entity ids from the server's id counter; metadata morph for crack stages."
  - "Mining feel: on dig start of an ore (Paper BlockDamageEvent / dig packet) compute the stage from the player's dig speed each tick on the player's entity scheduler and morph that block's face overlays for every viewer to the cracked variant (or dim per S2); restore on abort; remove with the block on break."
  - "Falling ore: `OreOverlays.attachToFalling(entity, oreId)` spawns 6 face overlays as passengers of a falling block entity carrying a tfp ore state (PAR-PHYS-01/02/05 call it; deps added there); removed with the entity."
  - "Pistons: on a piston move involving an exposed ore, remove its overlays at the start and let the BLOCK_CHANGE at the destination add them back."
  - "Config keys from PAR-INFRA-15 (`ore-overlay.cutoff-blocks`, `max-per-player`, `surface-outcrops-full-range`); `/tfp debug oreoverlay stats|reload|show <pos>`; metrics in `/tfp debug packets`."
  - "Last step: set `mode.properties` to HOST_ROCK (the shipped ore look switches from flattened full cubes to host rock + overlays), regenerate content, rerun the in-game steps."
  - "Ledger: close the 'until PAR-INFRA-15/16' wording in the ore rows; residuals (no AO on specks, cutoff) stay as the divergence row."
tests:
  - "core: ExposedFacesTest (6-face cases, chunk-edge borders, fluids, non-occluding neighbours, columnar host), FastPathTest (break in front of an ore, place to cover, ore mined, concurrent change corrected by the rebuild), ViewerSetTest (cap, nearest-first, token bucket), property test: fast path + rebuild converge to `ExposedFaces.compute` of the final world for random edit sequences"
  - "paper: display packet golden bytes per face for 1.21.11 and 26.3 PacketEvents wrappers; MockBukkit: chunk send -> spawns, unload -> one remove, block change -> same-bundle fast path; thread assertions (build decided on the CPU pool, published on the owner region, packet handlers touch no world state)"
  - "itest: `ore.overlay` on Paper and Folia 1.21.11: place/break around an ore converges, no thread-ownership violation, no leftover entity ids after /fill and an explosion (run delegated per PROTOCOL 5.3)"
done_criteria:
  - "In game on 1.21.11 and 26.3 (docs/verification/PAR-INFRA-16/): buried ores look like rock; exposed faces show the ore overlay with TFC's orientation; overlays appear and vanish in the same frame as the face is exposed/covered"
  - "Feel checklist (design doc 6) passes for ores: cracks never hidden under the overlay, rock particles and stone sounds (TFC parity), TFC mining time/tool tier, correct light at noon/midnight/torch and underwater, no z-fighting 1-48 m, no visible seam, full-cube outline and hitbox, falling ore keeps its overlay, no leftover after /fill, TNT and pistons, pick-block gives the ore item, no square ring and no chunk-border pop-in at distance"
  - "Entities per player and table build time within the S1 gate (`tfp debug oreoverlay stats` near spawn recorded in the verdict); self-test `ore.overlay` green on Paper and Folia"
  - "Shipped mode is HOST_ROCK; ./gradlew check and verifyContent green"
---

# PAR-INFRA-16: Ore overlay runtime: packet-only face overlays, neighbour-driven refresh, Folia-safe, feel checklist

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Wave note: added after the owner decisions of 2026-10-22. Wave 7 already holds four items; this is a fifth there on
purpose (files disjoint from PAR-PROC-03, PAR-PROC-04, PAR-PROC-05 and PAR-SURV-04). Items that change ore blocks in the
world got a dep on it: PAR-WORLD-08, PAR-PHYS-01, PAR-PHYS-02, PAR-PHYS-05. Design, data flow and budget:
`docs/research/ore-entity-renderer-design.md` §3-§6.

Note on shared globs: `core/.../oreoverlay/**` contains PAR-WORLD-17's `census/` package (wave 4); this item reuses it and
may move `ExposureRule` out of it.
