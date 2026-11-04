# PAR-WORLD-17 verification

- Date: 2026-11-04. Census on tree `1b912b5` plus the PAR-WORLD-17 changes; client cost re-measured on `71fbeb0` plus the
  review fix (quads on real exposed faces, frame time from the F3 + 2 chart, BetterHud panel off).
- Server: Paper 1.21.11 build 132, with CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449 and
  PacketEvents 2.14.0. The census also ran on Folia 1.21.11.
- Client: 1.21.11 (`spikes/client`) on Xvfb + Mesa llvmpipe at 1280x720, render distance 6, AFK frame cap off
  (`inactivityFpsLimit:minimized`), vsync off.
- Steps:
  - `spikes/ore-overlay/run-all.sh` ran the census: vanilla seeds 1-3 and TFC seeds 1-3 at 32x32 chunks, then Folia TFC
    seed 1 at 8x8.
  - `spikes/ore-overlay/run-client-cost.sh` ran the client cost: TFC seed 2, a 64x64 stepped stone quarry built at spawn
    (floor y 64), Bot in spectator hovering south of the rim, F3 and F3 + 2 on, `/hud turn off`; fields of 0/2k/8k/20k
    quads on real exposed faces, 27 s each, then a 30 s flight at 10 blocks/s, 20 blocks above the surface.
  - `spikes/ore-overlay/run-vein-shots.sh` took the vein reference (exposed example 336, 61, 547 of seed 2).

| Criterion | Verdict | Evidence |
|---|---|---|
| Census tables in `docs/spikes/S1-ore-overlay.md`: 3 seeds, p50/p95/max, gated radius and VD 6/8/10, vanilla proxy | pass | §2; reports in `spikes/ore-overlay/out/` |
| Client-cost table for 2k/8k/20k quads and a pass/fail verdict | pass (the verdict is FAIL, owner question recorded) | §3 and §4: frame time avg 63 / 65 / 69 / 78 ms at 0 / 2k / 8k / 20k (+6 ms at 8k), `frame-time-3x.png` |
| Screenshots of the synthetic field with F3 visible | pass | `field-0-f3.png`, `field-2000-f3.png`, `field-8000-f3.png`, `field-20000-f3.png`: F3 line 1 and the F3 + 2 chart readable, no HUD panel over them, the gold overlay quads lie flush on the quarry's floor, terrace tops and walls (`quads-8x.png`, `quads-edge-8x.png`). Flight: `field-8000-flying-10s-f3.png` and `-20s-` show the field on the terrain ahead; at 30 s Bot is over the sea |
| Screenshots of the exposed-vein reference | deferred check tracked in PAR-WORLD-19 | Our world has no tunnels yet: the only exposed ores lie on sea beds at water level and cannot be seen (`vein-seabed-4m.png`, `vein-seabed-64m.png`). PAR-WORLD-19 depends on PAR-WORLD-06 and PAR-WORLD-08, holds the steps and the criterion, and does not list PAR-WORLD-17 in its deps. A vanilla report's `exposed_example` is a stone block, not an ore (spike doc §5) |
| ARCHITECTURE.md §9.1 and `docs/research/ore-entity-renderer-design.md` §4 updated | pass | Measured numbers, their two biases and the owner question |
| itest `ore.census` on Paper and Folia 1.21.11 without thread-ownership violations | pass | Paper: 21 cases, 0 failing, ×6 runs. Folia: 21 cases, 0 failing, thread-assert log case passed |
