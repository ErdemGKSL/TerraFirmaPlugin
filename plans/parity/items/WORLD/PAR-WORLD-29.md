---
id: PAR-WORLD-29
title: "Wire the aquifer barrier noise and the fluid support rule into the terrain fill, and the floating-fluid scan"
area: WORLD
status: done
wave: 21
deps: [PAR-WORLD-07, PAR-WORLD-28]
size: S
aliases: ["follow-up of PAR-WORLD-07 (2026-11-19): the barrier noise and FluidSupport are in core, but TfcTerrain is outside PAR-WORLD-07's files_owned (and owned by PAR-WORLD-28)"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/TFCAquifer.java"
  - "src/main/java/net/dries007/tfc/world/noise/NoiseSampler.java"
files_owned:
  - "paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"
  - "paper/src/test/java/dev/tfp/paper/worldgen/AquiferWiringTest.java"
ui: true
in_game_steps:
  - "Fresh world; spectator mode; scan 100 chunks around spawn (RCON script counting water/lava blocks with air directly below or beside, below y 63, outside rivers, lakes and oceans); expect 0"
  - "Screenshot one flooded cave next to a dry cave at a different level"
plan:
  - "Current (2026-11-19): `core/worldgen/aquifer/AquiferBarrierNoise` (TFC's `NoiseSampler.barrierNoise`, golden-exact against Minecraft's `NormalNoise`) and `FluidSupport.settle` (dam a carved fluid with air below or beside it) exist; `TfcTerrain` still passes `NO_BARRIER` and applies no support rule."
  - "In `TfcTerrain`, build one `AquiferBarrierNoise.create(aquiferFork)` per world (thread-safe, immutable) and pass it to every chunk's `Aquifer` instead of `NO_BARRIER`."
  - "After the carve decisions of a chunk (with the PAR-WORLD-28 tunnel mask), apply `FluidSupport.settle` with a lookup over the chunk's own decisions; positions in the neighbour chunks count as their own carve decision (same aquifer, same mask function) or, if that is too costly, as solid with a note in the ledger."
  - "Check the PAR-WORLD-03 chunk budget (barrier noise is sampled only near aquifer borders)."
  - "Done: `TfcTerrain` builds one `AquiferBarrierNoise.create(aquiferFork)` per world and passes it to every chunk's `Aquifer`; `TfcTerrain.settledCave` applies `FluidSupport.settle` to every cave carve, reading neighbour chunks' own carve decisions (their own mask and aquifer, built lazily only when a border fluid needs them). The decoration view reads the settled carves. Tests: paper `AquiferWiringTest` (2; the 100-chunk scan finds 2695 cave fluids, 3 dammed, none with air below or beside). Budget: no extra cost away from fluids; bench not rerun (no server)."
  - "Pending in-game check (container cannot run client): the 100-chunk floating-fluid RCON scan and the flooded/dry cave screenshot into docs/verification/PAR-WORLD-29/."
tests:
  - "paper: AquiferWiringTest: a filled chunk with caves has no water or lava with air directly below or beside it; the barrier noise is the world's (not zero)"
done_criteria:
  - "No floating water in a 100-chunk scan (in-game, docs/verification/PAR-WORLD-29/); ledger barrier divergence row closed; chunk budget kept"
---

# PAR-WORLD-29: Wire the aquifer barrier noise and the fluid support rule into the terrain fill, and the floating-fluid scan

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
