# PAR-WORLD-17 verification

- Date: 2026-11-04. Tree: `1b912b5` plus the PAR-WORLD-17 changes.
- Server: Paper 1.21.11 build 132, with CraftEngine 26.9.2, BetterModel 3.5.1, BetterHud 2.2.0-SNAPSHOT-449 and
  PacketEvents 2.14.0. The census also ran on Folia 1.21.11.
- Client: 1.21.11 (`spikes/client`) on Xvfb + Mesa llvmpipe at 1280x720.
- Steps:
  - `spikes/ore-overlay/run-all.sh` ran the census: vanilla seeds 1-3 and TFC seeds 1-3 at 32x32 chunks, then Folia TFC
    seed 1 at 8x8.
  - `spikes/ore-overlay/run-client-cost.sh` ran the client cost: TFC seed 2, fields of 0/2k/8k/20k quads, then flying.
  - `spikes/ore-overlay/run-vein-shots.sh` took the vein reference (exposed example 336, 61, 547 of seed 2).

| Criterion | Verdict | Evidence |
|---|---|---|
| Census tables in `docs/spikes/S1-ore-overlay.md`: 3 seeds, p50/p95/max, gated radius and VD 6/8/10, vanilla proxy | pass | §2; reports in `spikes/ore-overlay/out/` |
| Client-cost table for 2k/8k/20k quads and a pass/fail verdict | pass (the verdict is FAIL, owner question recorded) | §3 and §4; `field-*-f3.png`, `fps-counter-8x.png` |
| Screenshots of the synthetic field with F3 visible | pass | `field-0-f3.png`, `field-2000-f3.png`, `field-8000-f3.png`, `field-20000-f3.png`, `field-8000-flying-f3.png`. The gold overlay quads render in the world light (`overlay-quads-8x.png`) |
| Screenshots of the exposed-vein reference | deferred check tracked in PAR-WORLD-19 | Our world has no tunnels yet: the only exposed ores lie on sea beds at water level and cannot be seen (`vein-seabed-4m.png`, `vein-seabed-64m.png`). PAR-WORLD-19 depends on PAR-WORLD-06, holds the steps and the criterion, and does not list PAR-WORLD-17 in its deps |
| ARCHITECTURE.md §9.1 and `docs/research/ore-entity-renderer-design.md` §4 updated | pass | Measured numbers and the owner question |
| itest `ore.census` on Paper and Folia 1.21.11 without thread-ownership violations | pass | Paper: 21 cases, 0 failing, ×6 runs. Folia: 21 cases, 0 failing, thread-assert log case passed |
