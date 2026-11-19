---
id: PAR-WORLD-28
title: "Wire the cave and canyon tunnel masks into the terrain fill, and the in-game cave check"
area: WORLD
status: done
wave: 20
deps: [PAR-WORLD-06]
size: S
aliases: ["follow-up of PAR-WORLD-06 (2026-11-19): the tunnel shapes are ported in core, but TfcTerrain is outside PAR-WORLD-06's files_owned"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/TFCChunkGenerator.java"
  - "src/main/java/net/dries007/tfc/world/carver/CarverHelpers.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/CaveWiringTest.java"
ui: true
in_game_steps:
  - "Fresh world; spectator mode; fly underground near spawn and screenshot a cave"
  - "Find a canyon (tp along a few chunks at y 30-60 until one opens), screenshot it"
plan:
  - "Current (2026-11-19): `core/worldgen/carver/TunnelCarvers.mask(fork, chunkX, chunkZ, minY, height)` returns the chunk's `TunnelMask` (TFC's cave, deep cave and canyon carvers, golden-exact against vanilla + TFC configs, PAR-WORLD-06). `TfcTerrain` still has `IN_CAVE = false` and `inCave(x, y, z)` returns it, so no block is carved."
  - "In `TfcTerrain.fill`, build the mask once per chunk with the world seed's fork (`PositionalRandom.fork(new XoroshiroRandomSource(seed))`, the same fork the aquifer uses), the world's min y and height, and pass `mask.contains(lx, y, lz)` to `CaveCarver.carve`. Keep the existing rule that only rock positions (TFC's `canReplaceBlock` on `overworld_carver_replaceables`) are carved; decide whether soil/sand surface kinds the tag lists (dirt, grass, sand, gravel) must be carved too, as TFC's tag includes them, and record a divergence if not."
  - "The decorations view (`TfcTerrain.Ground`) should see the opened blocks where it matters (ledger divergence 'TFC's decorations read the carved terrain'); at least keep loose rocks off cave ceilings that are now open."
  - "Cost: the mask is ~0.2 ms per chunk on one core (17 x 17 start chunks x 3 carvers); check the PAR-WORLD-03 chunk budget bench."
  - "After this lands, PAR-WORLD-19 (ore census rerun with caves) can run; it lists PAR-WORLD-06 in deps but needs this item too."
  - "Done: `TfcTerrain.fill` carves the tunnel mask per chunk through `TfcTerrain.CaveChunk` (replaceable = rock and every surface kind but snow blocks, as TFC's `overworld_carver_replaceables`; density-opened water is not recarved, as TFC removes water from the tag); carved air is `cave_air`; `Ground` reads the carves of the chunk and its neighbours (carved column tops, so loose rocks and plants do not float over a cave that breaks the surface). Tests: paper `CaveWiringTest` (2). Budget: one mask per chunk (~0.2 ms) plus neighbour masks only when the decorations read a neighbour column; bench not rerun (no server)."
  - "Pending in-game check (container cannot run client): the two in_game_steps (cave and canyon screenshots) into docs/verification/PAR-WORLD-28/, and the PAR-WORLD-03 chunk budget on a server."
tests:
  - "paper: CaveWiringTest: a filled chunk with a non-empty mask has air/water/lava exactly at the masked rock positions the carve decision opens, lava at or below min y + 8, and the rest rock"
done_criteria:
  - "Caves and canyons appear in-game (screenshots in docs/verification/PAR-WORLD-28/); ledger 'caves not ported' divergence row closed; chunk budget kept"
---

# PAR-WORLD-28: Wire the cave and canyon tunnel masks into the terrain fill, and the in-game cave check

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
