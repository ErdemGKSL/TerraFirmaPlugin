# Seasonal packet rewrite: JMH budget and visual checklist (PAR-CLIM-06)

Measured 2026-11-19. Benchmark: `paper/src/jmh/java/dev/tfp/paper/climate/SeasonalRewriteBenchmark.java` (JMH 1.37, 1 fork,
3 x 1 s warm-up, 5 x 1 s measurement, average time). JDK 25.0.5.1 (OpenJDK), cloud container, 4 vCPU Intel Xeon @ 2.30 GHz,
shared with other work (noisy). Raw log: the table below (run once, `EXIT 0`).

## What is timed

`BiomePaletteRewrite` is the library-free core of `SeasonalBiomeHandler` (the Netty-thread work); the PacketEvents
`DataPalette` read and write around it is a plain array copy and is not timed.

| Benchmark | What it does | Score (mean +- 99.9 % error) |
|---|---|---|
| `rewriteSection` | `rewriteSection` over one 4x4x4 biome section (64 cells) plus `distinct` and `bits` (the palette sizing the handler does after a change), 16 random columns over all 3,500 variants | 0.553 +- 0.118 us |
| `rewriteChunk` | The same for a 24-section chunk (height 384) | 13.9 +- 2.5 us |
| `registrySync` | Building the per-user `IdMap` for every base biome and variant, as `SeasonalBiomeHandler.ids` does once per user: one registry name build (`variantIdOf`) and one name lookup each. The client registry is a `HashMap<String,Integer>` of all base and variant names standing in for PacketEvents' `IRegistry.getByName` (also a name map) | 278 +- 69 us |

`VARIANT_COUNT` is `TfcBiomes.COUNT * SeasonalColour.BAND_COUNT` = 125 x 28 = 3,500 variants (PAR-CLIM-05 grew it from the 2,000 of the item title; the numbers below are for 3,500).

## Budget

PERF-SEA-01 in `docs/perf/budgets.md`: the design target was 0.5 s (500,000 us) one-off for the 2,000-variant sync. Measured
0.28 ms for 3,500 variants, about 1,800 times under. The packet rewrite adds 14 us per chunk packet on top of the 219 us per-packet
palette-remap baseline of PERF-PKT-01 (the PacketEvents decode and re-encode dominates, not our array work). Not covered here
(PAR-REL-07 with 50 bots): the real `IRegistry` lookup cost, the async `SeasonalBiomeRemap.compute` table per chunk, and the
registry size of the client (3,500 datapack biomes in the sync packet).

## Re-running

No Gradle source set exists for `paper` yet (`paper/build.gradle.kts` is not owned by this item; see the ledger row). Compile and
run by hand after `./gradlew :paper:classes`:

```
J=<dir with jmh-core-1.37.jar, jmh-generator-annprocess-1.37.jar, jopt-simple-5.0.4.jar, commons-math3-3.6.1.jar>
CP=core/build/classes/java/main:worldgen/build/classes/java/main:paper/build/classes/java/main:$J/jmh-core-1.37.jar:$J/jopt.jar:$J/cm3.jar
javac -proc:full -processorpath $J/jmh-generator-annprocess-1.37.jar:$J/jmh-core-1.37.jar:$J/jopt.jar:$J/cm3.jar -cp $CP \
  -d out paper/src/jmh/java/dev/tfp/paper/climate/SeasonalRewriteBenchmark.java
java -cp out:$CP org.openjdk.jmh.Main dev.tfp.paper.climate.SeasonalRewriteBenchmark
```

## Visual checklist (manual, vanilla 1.21.11 client with the resource pack and the datapack)

Run with a world far from spawn, render distance 12, `/tfp time set month <m>` for each step.

1. January and July at the same spot: the grass colour differs (July greener, January straw or frozen) and matches the
   colour band of that month; the world does not flash on the month change, chunks re-colour nearest first.
2. October and November: dormant grass is yellower than in June; foliage and water follow the same band.
3. Southern hemisphere (z below the equator): the seasons are opposite to the northern ones.
4. Crossing a biome border: the colour step between two 4x4x4 cells is acceptable (quantised, NP-CLIM-01), no one-block seams
   inside a cell.
5. A cave or high mountain: the cells at other heights use their own temperature band (colder bands on peaks).
6. Relog and `/tfp reload`: the view is re-coloured at once; no "biome registry missing" warning in the console; with the
   datapack removed the chunks keep their base biomes and one warning appears.
7. Frame time while 20 chunks stream in (F3): no visible hitch compared with the same run with the remap off.

Pending in-game check (container cannot run a client): all seven steps.
