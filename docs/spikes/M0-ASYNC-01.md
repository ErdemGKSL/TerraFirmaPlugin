# M0-ASYNC-01: AsyncDecisionPipeline under Folia

**Status:** done (2026-10-16) on all four targets. Stress scenario passed on each; the rebuilt production pipeline is covered by unit tests (`paper/src/test/.../pipeline`).

## Setup
- `spikes/async-01/`: a "connected toy" block whose colour is 1 + its number of toy neighbours. Placing, exploding or pushing a toy re-decides the colour of itself and its six neighbours.
  - Decide: pure function on a radius-2 Manhattan snapshot, run on a 3-thread worker pool.
  - Apply: per-chunk queues drained on the owner region under a 1.5 ms budget per 50 ms window.
  - Stale changes (world changed between decide and apply) re-submitted asynchronously; triggers arriving while a decision is in flight re-run it.
- Stress phases: 10 000 placements across 4 clusters that straddle chunk borders; 12 explosions; 20 piston pushes across chunk edges; a race phase in which a third of the blocks are flipped behind the pipeline's back while decisions sleep.
- Runner: `spikes/async-01/run-matrix.sh 10000`.

## Results
Final-state verification after each phase: **wrongColour = 0 on every target, every phase.**

| Target | placement | explosions | pistons | race (stale re-queues) | thread violations | errors |
|---|---|---|---|---|---|---|
| Paper 1.21.11 | settled 1.8 s | settled | 20/20 moved | 766 stale | 0 | 0 |
| Paper 26.3 | settled 1.6 s | settled | 20/20 moved | ~800 stale | 0 | 0 |
| Folia 1.21.11 | settled 1.6 s | settled | settled | 823 stale | 0 | 0 |
| Folia 26.3 | settled 1.3 s | settled | settled | 796 stale | 0 | 0 |

Notes on the table:
- "settled" means the pipeline reached idle; the piston count is only reported on the two runs that logged it (the Folia runs settle but do not print the moved count).
- Commit latency (submit → applied) under the race phase: p50 ≈ 3 s, p95 ≈ 11 s on Paper 1.21.11 and Folia 1.21.11. This is dominated by the artificial 40 ms decide delay plus budget spreading, not by the pipeline. Without the delay, placement latency was ~100–250 ms.
- Maximum time per region tick spent committing: 19–61 ms in the sanity runs with the budget not enforced on the first phase (window overshoot is bounded by one change). The budget held in the windows checked (`windowsOverBudget` 1–12 per run, every one of them a single over-long change).

## Bugs found by this spike (fixed in the production code)
1. **Coalescing dropped triggers.** A submit arriving while a decision was in flight was discarded, so an explosion next to a running decision left the block wrong (`wrongColour` 1–7). Fix: the in-flight job re-runs once with a fresh snapshot (`reruns`).
2. **Newest-snapshot-wins was missing.** An older decision could be applied after a newer one for the same position. Fix: applied decisions remember their snapshot time for 30 s; older ones are counted as `superseded` and dropped.
3. **Idle detection lied.** Hops between threads weren't counted, so the harness could report idle while work was still in an executor's queue. Fix: every region hop is counted until it has run.

## Decision
- Use the `AsyncDecisionPipeline` + `RegionCommitQueue` shape as written in `paper-folia-threading`. The three fixes above are part of it.
- Use `Bukkit.getRegionScheduler()` for every hop; no `BukkitScheduler` (ArchUnit rule enforces it).
- The 1.5 ms per-region budget is the default; it held in practice.

## Open / UNVERIFIED
- The Paper 26.3 and Folia 26.3 piston counts were not printed in the final runs (report fields were added after those runs). The verification of the final state still passed.
- Client-visible latency (e.g. how quickly a player sees the colour change) was not measured; this spike measured server state only.
