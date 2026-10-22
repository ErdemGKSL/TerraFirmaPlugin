---
id: PAR-WORLD-06
title: "Cave tunnels and canyons (vanilla carvers with TFC carveBlock)"
area: WORLD
status: open
wave: 19
deps: [PAR-WORLD-02]
size: M
aliases: ["M2-GEN-09"]
tfc_refs:
  - "src/main/java/net/dries007/tfc/world/carver/TFCCaveCarver.java"
  - "src/main/java/net/dries007/tfc/world/carver/TFCCanyonCarver.java"
  - "src/main/resources/data/tfc/worldgen/configured_carver"
files_owned:
  - "core/src/main/java/dev/tfp/core/worldgen/carver/**"
  - "core/src/test/java/dev/tfp/core/worldgen/carver/**"
ui: true
in_game_steps:
  - "Fresh world; spectator mode; fly underground near spawn and screenshot a cave"
plan:
  - "Port the vanilla CaveWorldCarver/CanyonWorldCarver tunnel shapes with TFC's configured_carver parameters (probability, y range, radii), feeding the existing CaveCarver carve decision; keep it deterministic per chunk seed."
tests:
  - "core: golden tunnel masks from a vanilla+TFC oracle for 20 chunks"
done_criteria:
  - "Caves and canyons appear in-game; ledger 'caves not ported' row closed"
---

# PAR-WORLD-06: Cave tunnels and canyons (vanilla carvers with TFC carveBlock)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
