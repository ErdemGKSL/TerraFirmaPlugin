---
id: PAR-WORLD-06
title: "Cave tunnels and canyons (vanilla carvers with TFC carveBlock)"
area: WORLD
status: done
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
ui: false
in_game_steps: []
plan:
  - "Port the vanilla CaveWorldCarver/CanyonWorldCarver tunnel shapes with TFC's configured_carver parameters (probability, y range, radii), feeding the existing CaveCarver carve decision; keep it deterministic per chunk seed."
  - "Ore overlay (2026-10-22): once caves and canyons generate, rerun PAR-WORLD-17's `ore.census` (exposure was measured on a vanilla proxy before caves existed) and update docs/spikes/S1-ore-overlay.md; if the p95 exceeds the gate, raise an owner question about the overlay defaults."
  - "Ore overlay rerun moved: it needs caves in a real world, so it runs in PAR-WORLD-19 after PAR-WORLD-28 wires the mask (PAR-WORLD-19 lists only PAR-WORLD-06 in deps; it also needs PAR-WORLD-28)."
  - "Done (2026-11-19): core/worldgen/carver `TunnelCarvers.mask` (TFC applyCarvers loop: 17 x 17 start chunks, carvers cave, deep_cave, canyon at positional y 1..3), `CaveTunnels` (vanilla 1.21.1 CaveWorldCarver with cave.json/deep_cave.json), `CanyonTunnels` (CanyonWorldCarver with canyon.json), `CarverMath` (1.21.1 float sin table, carveEllipsoid, canReach, samplers), `TunnelMask`. Tests: TunnelGoldenTest (20 chunks x 4 masks vs an oracle running Minecraft's own carver classes with TFC's configs; port matched on 1764 chunks), TunnelPropertyTest (9: determinism, union, vertical range, world height, rates per carver vs oracle, start-chunk rates, sine table, concurrency, carve decision). ~0.2 ms per chunk. Wiring into TfcTerrain and the in-game check: PAR-WORLD-28."
tests:
  - "core: golden tunnel masks from a vanilla+TFC oracle for 20 chunks"
done_criteria:
  - "Deferred check tracked in PAR-WORLD-28 (caves and canyons appear in-game once the mask is wired into TfcTerrain, which is outside this item's files_owned); ledger rows updated: the tunnel shapes are ported, the 'caves not ported' divergence now points at PAR-WORLD-28"
---

# PAR-WORLD-06: Cave tunnels and canyons (vanilla carvers with TFC carveBlock)

All fields live in the front matter above; the loop parses them. Paths in `tfc_refs` are relative to the TFC checkout (`/opt/tfc`, pinned commit in `docs/porting/ledger.md`). Follow `plans/parity/PROTOCOL.md`.
