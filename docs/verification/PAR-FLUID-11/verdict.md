# PAR-FLUID-11 in-game check: hot spring healing (PAR-FLUID-04)

Status: **pass** (2026-11-13, HEAD bef3af3, jar built from that tree). Committed on top of 96830d6; between bef3af3 and
96830d6 only `paper/src/main/java/dev/tfp/paper/bucket/BucketListener.java` (PAR-FLUID-10) and PAR-CLIM-02 docs changed, so the hot spring code
(`paper/src/main/java/dev/tfp/paper/worldwater/hot/**`) under test is the same in both trees.

## Setup

- Server: Paper 1.21.11 through `spikes/client/server.sh` with `TFP_WORLD=1`, `level-seed=12345`, CraftEngine, BetterModel,
  BetterHud and PacketEvents as in `docs/testing/headless.md` (priming start, world deleted, second start). Client: vanilla
  1.21.11 under Xvfb (`spikes/client/session.sh`, `MC_OPTIONS="guiScale:2"`), pack applied. Difficulty normal, noon,
  clear weather. The run was done by the worker session itself (no subagent tool in that session).
- Finding the spring: the seed's `tfc:random_active_hot_spring` placements were listed offline with a throwaway test
  (`WaterTerrain` centre biome of the chunk = `ChunkWater.biome(8, 8)`, the feature's rarity 1/50, origin and the
  selector's pick, water configs only; not committed). The first old_mountains candidate, origin (1427, -1535), holds a
  gravel-bedded pool at y 98 (`pool-view.png`, seen from above in spectator first). Water-on-gravel cells were listed with
  `execute ... positioned over world_surface if block ~ ~-1 ~ water if block ~ ~-2 ~ gravel`.
- `tfp debug water Bot` (typed in the client; the RCON reply is empty) at the pool: `Bot at 1421 98 -1536: fresh water,
  river flow none, biome old_mountains, surface height 98.`

## Steps and readings

| Step | Health (`data get entity Bot Health`) | HUD | Crop |
|---|---|---|---|
| In the pool (1421.3, 98, -1536.0), survival, `damage Bot 10 minecraft:generic` | 8.498 | 350/850 | `pool-0s-health-2x.png` |
| Same place, 10 s later | 11.765 (+3.27) | 500/850 | `pool-10s-health-2x.png` |
| Control: dry grass at (1441, 84, -1519), `effect give Bot instant_health` to 20, then `damage Bot 10 minecraft:generic` | 8.247 | 350/850 | `dry-0s-health-2x.png` |
| Same place, 10 s later | 8.483 (+0.24, natural regeneration) | 360/850 | `dry-10s-health-2x.png` |

Bot stood on the corner of two or more pool cells, so +3.27 is what about two touched spring blocks give (TFC: 1 in 10
per tick per block, 0.08 each, 1.6 per block over 200 ticks).

The health numbers come from `data get entity`; the HUD column (TFC-style health x 42.5, 850 max) is only a cross-check.
The HUD crops were taken a moment before each `data get` read, not at the same instant: 8.498 x 42.5 = 361 shows as 350
(the crop caught the health before the first heal rolls), while 11.765 shows as 500 and the dry readings match (350, 360).
Next time, capture the crop and the reading together. The pass result rests on the `data get` values.

## Criteria

| Criterion | Result |
|---|---|
| Health after 10 s in the pool is higher than right after the damage (about +1.6 per touched spring block over 10 s; compare with 10 s on dry land) | **Pass**: +3.27 in the pool against +0.24 on dry land; the HUD rises from 350/850 to 500/850 |

## Notes

- `tfp debug water` names the pool `fresh water`: its kind comes from `WorldWaterRules.classify`, while the healing reads
  the `SpringWater` shape rule (PAR-FLUID-04). Healing is not affected; the readout is follow-up PAR-FLUID-14 (the spring
  bucket is PAR-FLUID-10).
- The debug biome is the column's TFC local biome (the one the volcano features read); the vanilla stand-in biome at the
  spot is `windswept_hills`.
