# Snow catch-up: first-load cost of new chunks (PAR-CLIM-03)

Measured 2026-11-13 on the PAR-CLIM-03 server check (`docs/verification/PAR-CLIM-03/verdict.md`): Paper 1.21.11 build 132,
CraftEngine, BetterModel, BetterHud, PacketEvents as in `docs/testing/headless.md`, the dist jar of the PAR-CLIM-03 commit,
JDK 25, `-Xmx5G`, `level-seed=1234`, 4 vCPU, 15 GB, shared with other agents' builds and servers (noisy).

## What is counted

`SnowCatchUpStats` (paper `weather/`), logged every 30 s with `-Dtfp.snow.stats=true`:

| Counter | Thread | What |
|---|---|---|
| `load` | region | `SnowService.onLoad`: read and write the `tfp:snow_time` stamp, the two surface lookups |
| `plan` | worker pool | `SnowCatchUp.plan` with the mean rainfall (climate walk, at most 48 samples) |
| `hop` | region | one catch-up hop: up to 8 steps, each a surface lookup plus the pipeline capture (the snapshot read) |
| pipeline | worker / region | the decisions and the budgeted commits (1 ms per tick, `CommitBudget`) |

## Result: a fresh cold area in January

Bot (spectator) teleported to 3000 170 -2400, never visited, on January 1 at 10:18 (calendar tick 3.46 M + 1.15 M), view
distance 10. Deltas of the cumulative counters between 19:26:54 and 19:28:06 (the area finished loading before 19:28:06;
the next 30 s logged no new load):

| Quantity | Value |
|---|---|
| Chunk loads | 639 |
| Due (no stamp: TFC's `Integer.MIN_VALUE` default, a full month window) | 557 |
| Plans with steps | 557, each 64 accumulations (the `snowMaxAccumulationOnUpdate` cap) |
| Steps planned | 35,648 |
| `load`, region time per chunk | 5 us |
| `plan`, worker time per plan | 35 us |
| hops | 4,456 (8 per chunk) |
| hop, region time per hop | 265 us (so about 2.1 ms per new chunk, spread over 8 region tasks) |
| Changes applied by the pipeline | 22,664 (catch-up steps and Bot's live samples together; the rest were no-ops or coalesced) |
| Budget deferrals | 21,907 |
| Pipeline latency (cumulative over the run) | avg 5.1 s, max 20.4 s |
| `/mspt` after the area loaded | 1.2 ms avg over 1 min, max 17.1 ms |

The 557 new chunks cost about 1.2 s of region time over the ~50 s of loading, about 1.2 ms per tick on average on top of
world generation (`PERF-GEN-01`: about 30 ms per chunk). The commits stay inside the 1 ms budget per tick, which is why the
snow appears over several seconds (latency) rather than in one tick.

The cumulative maxima `loadMaxUs` 1.59 s and `planMaxUs` 0.97 s come from the first minutes, when the machine ran out of
memory (the kernel killed a process and the server watchdog dumped threads in `LambdaMetafactory` and PacketEvents'
bStats); they are not steady-state numbers. `hopMaxUs` was 20 ms after the January reload and 62 ms after the July one:
a single hop (8 captures) is not under the commit budget, so a spike there lands on the region tick. Not investigated
(GC on the shared host is likely); if it shows up on a quiet machine, lower `STEPS_PER_HOP`.

## Same run, July

Over the whole run up to 19:26:54 (January and July reloads together) 36,032 melt steps were planned; the average hop
cost stayed at 309 to 334 us across the run, so a melting catch-up costs about the same as an accumulating one.

## Not done

- No budget row in `docs/perf/budgets.md` (not this item's file); suggested: `PERF-SNOW-01` region time per new chunk,
  2.1 ms over 8 hops, measured.
- Folia was not run.
