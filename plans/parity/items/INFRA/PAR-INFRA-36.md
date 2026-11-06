---
id: PAR-INFRA-36
title: "Verify falling ores keep their face overlays in game (collapse and falling rich ore)"
area: INFRA
status: open
wave: 17
deps: [PAR-INFRA-16, PAR-PHYS-01]
size: S
aliases: ["deferred in-game check of PAR-INFRA-16 (PROTOCOL 6.5): falling/collapse step"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/util/tracker/Collapse.java"
  - "src/main/java/net/dries007/tfc/common/entities/misc/TFCFallingBlockEntity.java"
  - "src/generated/resources/data/tfc/recipe/collapse/ore"
files_owned:
  - "docs/verification/PAR-INFRA-36/**"
ui: true
in_game_steps:
  - "Headless Paper 1.21.11 + client 1.21.11, then Paper 26.3 + client 26.3 (docs/testing/headless.md); creative; `tfp time set noon`"
  - "Build a 3x3 granite pillar 6 high in the open with `tfp debug oreoverlay place <x> <y> <z> tfp:ore/rich_native_gold/granite` at its top centre (all six faces of that ore exposed once the pillar top is cleared around it); `tfp debug oreoverlay show <x> <y> <z>` lists its exposed faces"
  - "Make the rich ore fall with PAR-PHYS-01's trigger (remove its support or `tfp debug collapse <pos>`); record 10 frames 100 ms apart during the fall: the falling block shows the overlay on every visible face, no face is missing or doubled, no overlay stays at the start position"
  - "After landing: the block is the TFC collapse result (rich -> normal ore, `recipe/collapse/ore`), its exposed faces show the normal-grade overlay; `tfp debug oreoverlay stats` (console: all viewers) shows no leftover entity (entities = exposed faces in range)"
plan:
  - "Current: PAR-INFRA-16 attaches six rider overlays to any falling block entity whose block data is a CraftEngine ore state (EntitySpawnEvent registration, riders sent after the entity's SPAWN_ENTITY, removed with its REMOVE_ENTITIES; seat 0.98 above the base). No falling ores exist until PAR-PHYS-01, so the check moved here (PROTOCOL 6.5). PAR-INFRA-16 does not depend on this item."
  - "Run the in_game_steps (cheap-model agent where available); the main session judges every frame."
tests:
  - "None beyond the in-game steps (the rider packets and their registration are unit-tested in PAR-INFRA-16)"
done_criteria:
  - "Frames in docs/verification/PAR-INFRA-36/ on 1.21.11 and 26.3: the falling ore keeps its overlay while falling, lands as the right grade with overlays on its exposed faces, no leftover overlay; verdict.md per criterion"
---

# PAR-INFRA-36: Verify falling ores keep their face overlays in game (collapse and falling rich ore)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.

Deferred in-game check of PAR-INFRA-16 (PROTOCOL §6.5): falling ores exist only once PAR-PHYS-01 (a dependent of
PAR-INFRA-16) lands.
