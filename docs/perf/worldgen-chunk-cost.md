# Worldgen chunk cost (M2-GEN-15, partial)

## Full Paper fill path (in-server)

Measured 2026-10-18 with `/tfp debug worldgen 300` (console, RCON) on the install-fix server
(`spikes/run/install-fix/paper-1.21.11`: Paper 1.21.11-132, CraftEngine 26.9.2, BetterModel,
BetterHud, PacketEvents, the dist jar), JDK 25, `-Xmx3G`. Machine: cloud container, 4 vCPU
Intel Xeon @ 2.80GHz, 15 GB RAM, shared with other work (noisy).

What is timed: `TfcChunkGenerator.timeFill`, the same `TfcTerrain.fill` that `generateNoise`
runs (core stages, every block write with the CraftEngine rock/ore palette, deposits, vein
indicators, trees, volcanoes, corals, plants, groundcover, loose rocks), into a throwaway
`Bukkit.createChunkData(world)`. One chunk at a time, on the command's thread (the server
thread), `System.nanoTime` around the fill. 20 untimed warm-up chunks at chunk -6250,-6250, then
a contiguous 18 x 18 grid from chunk 6250,6250 (block 100000,100000; never loaded). The world is
not touched, so Paper's own chunk-system cost (status steps, light, saving) is NOT included.

| Run | Count | Mean | p95 | Min | Max |
|---|---|---|---|---|---|
| 1 (first after start) | 300 | 29.85 ms | 41.43 ms | 17.86 ms | 847.22 ms |
| 2 (same area again) | 300 | 26.86 ms | 39.08 ms | 18.34 ms | 88.42 ms |

0 chunks refused, no worldgen errors in the log. The 847 ms max of run 1 is a single outlier
right after startup (JIT/GC or the server's own startup work; not explained). Run 1 kept the
server thread busy for over 10 s, so Paper's watchdog printed its "not responded for 10 seconds"
thread dump (not a crash); its stack was in `TfcTerrain$Ground.columnsOf` -> `TfcTerrain.columns`,
the neighbour columns the trees and plants read. That fits the full fill costing about 2x the
core stages (13.4 ms below): the decoration recomputes the columns of neighbouring chunks.

## Core stages only (single thread, earlier)

Measured 2026-10-17 on the core stages only (single thread, JDK 25, seed -5793902121713522665).

| Measurement | Value |
|---|---|
| Chunks (spread grid, 4-chunk spacing, 352 sea / 148 land) | 500, after 50 warm-up |
| Mean per chunk | 13.40 ms |
| p95 per chunk | 18.23 ms |
| Min / max | 8.80 / 165.9 ms (max not explained) |
| Stage means: heights / climate+slopes / surface+rock+veins / volcano | 0.24 / 0.51 / 12.46 / 0.19 ms |
| Contiguous grid at (0,0), all sea | mean 12.5-12.9 ms, p95 16.0-17.1 ms |

## Throughput by thread count (PAR-WORLD-03, 2026-11-19)

`worldgen/src/jmh/java/dev/tfp/worldgen/bench/ChunkCostBench.java`: a plain `main` (the worldgen build file is not owned by this
item, so there is no JMH source set; same discipline: fixed seed, JIT warm-up, a fresh grid origin per round, wall time over
a 32 x 32 grid, median of 3 rounds). It times the platform-free part of the fill: `ChunkDataGenerator.generate` plus
`SurfaceHeights.chunk` (heights, climate, rivers, cones). The Bukkit-bound part (`TfcTerrain.fill`: palette writes, veins,
trees, plants) cannot run outside a server, so its cost stays the in-server figures above.
Machine: 4 vCPU cloud container, JDK 25, `-Xmx3g`.

| Threads | chunks/s (median) | min / max |
|---|---|---|
| 1 | 2 904 | 2 093 / 3 361 |
| 4 | 10 675 | 10 599 / 12 762 |
| 8 (only 4 cores) | 9 758 | 9 510 / 13 128 |

The core stages are about 0.3 ms per chunk single threaded and scale 3.7x on 4 cores (no shared lock), so the generator itself
is not the bottleneck: the 13 to 27 ms per chunk are the surface/rock/vein writes and the decoration in `TfcTerrain.fill`.
8 threads on 4 cores gain nothing, as expected.

## Budget and vanilla baseline (pending in-game run)

PRD.md:68 names the target "Worldgen throughput within 1.5x of vanilla at equal thread count". The vanilla baseline is NOT
measured: this container cannot run a pre-generation comparison with a result that means anything (shared 4 vCPU, the
chunk system's own cost needs a real server). Pending in-game check, to be run by the owner: pre-generate the same 10 000
chunks (100 x 100 from chunk 6250,6250) on a vanilla world and on a TFC world with equal `worker-threads`
(`world.getChunkAtAsync` over the grid, or Chunky), under spark, and compare chunks/s. No ratio is claimed.

## Exposed gems (decision)

Superseded by the ore overlay (PAR-WORLD-17, `docs/perf/ore-overlay-budget.md`, `docs/spikes/S1-ore-overlay.md`): every ore,
gems included, is a packet overlay on the host rock, so there is no separate "level-2 exposed gem" decision. Earlier count
(core stages only): 0 of 48 volcano gem positions touch air or water.
