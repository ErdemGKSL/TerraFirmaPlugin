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

## Budget and vanilla baseline (open)

PRD.md:68 names the target "Worldgen throughput within 1.5x of vanilla at equal thread count".
The vanilla baseline is still NOT measured, so no ratio is claimed. The command cannot time
vanilla the same way: vanilla's noise generator is not a Bukkit `ChunkGenerator`, so there is
no `generateNoise` to call on throwaway data. A fair comparison needs the same end-to-end path
for both: pre-generate the same N chunks far from spawn (for example `world.getChunkAtAsync`
over a grid, or a pre-generation plugin) on a vanilla world and on a TFC world with equal worker
threads, and compare chunks per second (ideally under spark). That also adds the chunk-system
cost that this fill timing leaves out.

## Exposed gems (decision open)

0 of 48 gem positions touch air or water (all volcano GEM_ORE in the core-stage chunks; no vein
gems). Caveat: measured on the core stages before caves, trees and decoration were counted, so
this is not a decision about the overlay yet.

Open: the vanilla baseline (and the ratio against the PRD budget), throughput at N worker
threads, and the gem overlay decision.
