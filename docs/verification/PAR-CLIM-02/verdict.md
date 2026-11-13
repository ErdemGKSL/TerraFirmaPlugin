# PAR-CLIM-02 in-game check

Status: **passed** (run 3, 2026-11-13, HEAD bef3af3; the Java code under test is unchanged since e665d20/bef3af3).

## Run 3 (2026-11-13): pass

- Server: Paper 1.21.11 build 132 (`spikes/client/server.sh`, `TFP_WORLD=1`, `level-seed=1234`, `EXTRA_JVM=-Xmx5G`),
  CraftEngine 26.9.2, BetterModel, BetterHud, PacketEvents as in `docs/testing/headless.md`. Client: vanilla 1.21.11
  under Xvfb, pack applied.
- Calendar: `tfp time add months 7` (January 1, 1001), then `tfp time add hours 109` to January 5, 19:06, inside the
  climate rain window that runs from January 5 16:00 to January 6 14:00 for this seed (found offline with
  `OverworldClimate.rainIntensity` and `ChunkDataGenerator.climateAt`, then confirmed by the readouts below).
  `weather rain 1000000` only makes the vanilla client draw a storm; it is not an input of the decision.
- Readouts are Bot's own `/tfp debug chunk`, typed in the client and copied from the `[CHAT]` lines of the client log.

| Step | Position | Readout (`climate now:` / `precipitation here:`) |
|---|---|---|
| Cold site, before the window (calendar January 1) | 0 125 -2000 | `temperature -19.3°C, average -9.3°C, rainfall 418.3 mm (calendar tick 2310683)` / `NONE` |
| Cold site, in the window | 8 125 -1976 | `temperature -18.9°C, average -7.9°C, rainfall 390.0 mm (calendar tick 2419500)` / `SNOW` |
| Cold site, after 6 h away | 8 125 -1976 | `temperature -17.3°C, average -9.2°C, rainfall 388.1 mm (calendar tick 2426966)` / `SNOW` |
| Warm site, same window | -256 67 9600 | `temperature 29.8°C, average 28.9°C, rainfall 248.9 mm (calendar tick 2429333)` / `RAIN` |

Cleared patch: `fill 4 112 -1989 12 123 -1981 air replace snow` removed 14 world-generation snow layers in the window
(`run3-cold-patch-before.png`, taken right after the clear). Bot then went to 8 150 -1500 (30 chunks away) for 35 s,
`tfp time add hours 6`, and came back: the chunk's stamp moved to 2426583 and the patch held **29** snow blocks
(`clone 4 112 -1989 12 123 -1981 4 260 -1989 filtered minecraft:snow`, a count without changing the patch;
`run3-cold-patch-after.png`, same camera from a barrier block at 8 124 -1976, yaw 180, pitch 45, night vision). Those
layers can only come from the SNOW decision (`SnowService` catch-up over the window), not from world generation.

| Criterion | Result |
|---|---|
| Server: `precipitation here: SNOW` at a sub-zero January site | Pass (`run3-cold-snow-readout.png`, -17.3 C, January 6) |
| Server: snow built on the cleared patch from the SNOW decision | Pass (0 after the clear, 29 snow blocks after the 6 h catch-up; `run3-cold-patch-before.png`, `run3-cold-patch-after.png`) |
| Server: `precipitation here: RAIN` at a warm site in the same window | Pass (`run3-warm-rain-readout.png`, 29.8 C, January 6) |
| The decision follows the climate's rain window, not `/weather` | Pass (NONE on January 1 under the same vanilla storm) |
| Client falling particles show snow at the cold site | Not reachable; accepted substitute NOT-PARITY NP-CLIM-02 (ledger Divergences row "The client's snow or rain follows the biome temperature ...", revisit PAR-CLIM-05) |
| Checklist | `docs/visual-checklists/PAR-CLIM-02.md` |

Findings outside this item, recorded only:
- With `-Xmx3G` the first attempt of run 3 ran out of heap after a few far teleports. The class histogram showed 12.5
  million `PoiRecord`s: the terrain repurposes `bee_nest` / `beehive` states (`content/generated`, 2,970 + 1,638 uses),
  and every placed bee nest or beehive is a vanilla point of interest. Not PrecipitationService. Reported to the
  orchestrator as a follow-up for the visual allocator.
- The suggested warm site 0 74 9000 is ocean for seed 1234 (Bot sinks, so the readout is correctly NONE: rain does not
  reach under water). The item's steps now use a land column found with the heightmap.

## Run 2 (commit 1942d21): fail

Cold site with only world-generation snow and no per-position readout (the console readout over RCON is PAR-CLIM-17).
Superseded by run 3.

## Run 1 (working tree of e665d20): withdrawn

Its readouts could not be reproduced and nothing in it separated snow built by `SnowService` from world-generation snow.
Its screenshots were removed in run 3.

## Notes for screenshot reviewers

- **Rain particles at a sub-zero site are expected until PAR-CLIM-05.** The seasonal variant biomes keep the TFC base
  biome's climate (temperature 0.5, precipitation on; see `SeasonalVariantRainTest`), so the vanilla client draws rain
  there at any server temperature (vanilla's height adjustment would need y above about 360 to turn it to snow). That
  is the substitute in NOT-PARITY NP-CLIM-02. The cold-site proof is the readout `precipitation here: SNOW` plus the snow
  built on the cleared patch.
- **`precipitation here: NONE` under `/weather rain` is TFC-correct** outside a climate rain window:
  `WeatherHelpers.getPrecipitationAt` reads only `getRain` at the calendar time, the instant temperature and the
  rainfall. Coupling the vanilla weather to the climate is PAR-CLIM-12.
