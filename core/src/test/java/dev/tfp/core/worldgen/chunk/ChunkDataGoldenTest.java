/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/chunkdata/RegionChunkDataGenerator.java @ c3b4d9536 (golden values)
 */
package dev.tfp.core.worldgen.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Golden chunk data. Each line is one block position: the seed, the chunk, the block position, the sea-level temperature,
 * the average rainfall, the rain variance and the base groundwater (floats as {@link Float#toString}, which round-trips
 * exactly), then the rock point and layer at {@code (y 60, surface 72)}, at {@code (y -30, surface 120)} and at the surface.
 * <p>
 * The values come from TerraFirmaCraft at c3b4d9536 (the pinned commit). An oracle harness compiled TFC's
 * {@code RegionChunkDataGenerator} and {@code LerpFloatLayer} unchanged, with the region generator of
 * {@link dev.tfp.core.worldgen.region.RegionGeneratorGoldenTest} (the same rivers and lakes; the biome, karst and forest
 * stages removed). Its {@code ChunkData} is a stub that keeps the getters verbatim, and the datapack rock settings are
 * replaced by the rock point and layer. It ran against Minecraft 1.21.11's {@code Mth}, {@code RandomSource} and
 * {@code XoroshiroRandomSource} classes, with the registries bootstrapped and no server started. Seeds 42 and -7 are
 * infinite continents, seed 20260917 has finite continents on, as in the region golden test. Every chunk is three block
 * positions, at local {@code (0, 0)}, {@code (15, 15)} and {@code (7, 9)}; the eight fixed chunks include negative
 * coordinates, and three river-influenced chunks per seed come from a fixed scan of chunks in [-60, 60] step 3.
 */
class ChunkDataGoldenTest {
    private static final Pattern LINE = Pattern.compile(
        "seed=(-?\\d+) chunk=\\((-?\\d+),(-?\\d+)\\) pos=\\((-?\\d+),(-?\\d+)\\) temp=(\\S+) rain=(\\S+) var=(\\S+) gw=(\\S+)"
            + " rock=(-?\\d+)/(-?\\d+) surface=(-?\\d+)/(-?\\d+) deep=(-?\\d+)/(-?\\d+)");

    private static final String[] GOLDEN = {
        "seed=42 chunk=(-25,-3) pos=(-400,-48) temp=6.255013 rain=233.05118 var=-0.7999046 gw=0.0 rock=-51489288/0 surface=-51489288/0 deep=-51489286/2",
        "seed=42 chunk=(-25,-3) pos=(-385,-33) temp=6.3130345 rain=232.90851 var=-0.8056842 gw=0.0 rock=-51489288/0 surface=-51489288/0 deep=-51489286/2",
        "seed=42 chunk=(-25,-3) pos=(-393,-39) temp=6.2846127 rain=233.00276 var=-0.8028772 gw=0.0 rock=-51489288/0 surface=-51489288/0 deep=-51489286/2",
        "seed=42 chunk=(0,0) pos=(0,0) temp=5.0 rain=263.75 var=-0.65 gw=0.0 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(0,0) pos=(15,15) temp=4.9033613 rain=266.44238 var=-0.6454945 gw=0.0 rock=-51489286/0 surface=-51489286/0 deep=-51489288/2",
        "seed=42 chunk=(0,0) pos=(7,9) temp=4.955455 rain=265.04382 var=-0.647628 gw=0.0 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(-1,-1) pos=(-16,-16) temp=5.1051264 rain=260.87753 var=-0.66331303 gw=0.0 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(-1,-1) pos=(-1,-1) temp=5.0066104 rain=263.56952 var=-0.65083325 gw=0.0 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(-1,-1) pos=(-9,-7) temp=5.060007 rain=262.16492 var=-0.65710807 gw=0.0 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(7,-12) pos=(112,-192) temp=4.27525 rain=275.312 var=-0.59317887 gw=0.0 rock=-51489288/0 surface=-51489288/0 deep=-51489288/2",
        "seed=42 chunk=(7,-12) pos=(127,-177) temp=4.174769 rain=277.90277 var=-0.5793099 gw=0.0 rock=-51489288/0 surface=-51489288/0 deep=-51489288/2",
        "seed=42 chunk=(7,-12) pos=(119,-183) temp=4.22741 rain=276.58893 var=-0.5864204 gw=0.0 rock=-51489288/0 surface=-51489288/0 deep=-51489288/2",
        "seed=42 chunk=(-14,9) pos=(-224,144) temp=6.154756 rain=241.25282 var=-0.7493788 gw=0.0 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(-14,9) pos=(-209,159) temp=6.0919223 rain=243.05006 var=-0.7411466 gw=0.0 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(-14,9) pos=(-217,153) temp=6.1232066 rain=242.2094 var=-0.7453711 gw=0.0 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(40,30) pos=(640,480) temp=6.99688 rain=271.6882 var=-0.46896148 gw=96.043335 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(40,30) pos=(655,495) temp=7.116812 rain=271.20288 var=-0.46961278 gw=56.551193 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(40,30) pos=(647,489) temp=7.0643463 rain=271.38754 var=-0.4691996 gw=79.63693 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(-100,55) pos=(-1600,880) temp=7.3671374 rain=214.97617 var=-0.4839968 gw=0.0 rock=-1340730515/0 surface=-1340730515/0 deep=-1340730515/2",
        "seed=42 chunk=(-100,55) pos=(-1585,895) temp=7.373145 rain=214.45169 var=-0.48207012 gw=0.0 rock=-1340730515/0 surface=-1340730515/0 deep=-1340730515/2",
        "seed=42 chunk=(-100,55) pos=(-1593,889) temp=7.364909 rain=214.7523 var=-0.48250958 gw=0.0 rock=-1340730515/0 surface=-1340730515/0 deep=-1340730515/2",
        "seed=42 chunk=(3,-300) pos=(48,-4800) temp=-6.058498 rain=307.69107 var=-0.6234193 gw=0.0 rock=998838160/0 surface=998838160/0 deep=998838160/2",
        "seed=42 chunk=(3,-300) pos=(63,-4785) temp=-5.9959173 rain=309.29718 var=-0.62981105 gw=0.0 rock=998838160/0 surface=998838160/0 deep=998838160/2",
        "seed=42 chunk=(3,-300) pos=(55,-4791) temp=-6.0261974 rain=308.557 var=-0.62777096 gw=0.0 rock=998838160/0 surface=998838160/0 deep=998838160/2",
        "seed=42 chunk=(18,-6) pos=(288,-96) temp=3.7003052 rain=289.77448 var=-0.5649182 gw=0.0 rock=-51489286/0 surface=-51489286/0 deep=-51489288/2",
        "seed=42 chunk=(18,-6) pos=(303,-81) temp=3.7261183 rain=290.40283 var=-0.5620035 gw=124.21875 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(18,-6) pos=(295,-87) temp=3.7132714 rain=290.166 var=-0.56351644 gw=53.78125 rock=-51489286/0 surface=-51489286/0 deep=-51489288/2",
        "seed=42 chunk=(18,-3) pos=(288,-48) temp=3.7329028 rain=292.1416 var=-0.56252784 gw=44.95496 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(18,-3) pos=(303,-33) temp=3.766228 rain=292.7761 var=-0.5586195 gw=106.6618 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(18,-3) pos=(295,-39) temp=3.7493746 rain=292.53598 var=-0.5606623 gw=67.24087 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(21,-6) pos=(336,-96) temp=3.7427974 rain=289.41193 var=-0.5589753 gw=39.999996 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(21,-6) pos=(351,-81) temp=3.7761228 rain=290.04636 var=-0.55506694 gw=43.28125 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=42 chunk=(21,-6) pos=(343,-87) temp=3.7602708 rain=289.8071 var=-0.5569773 gw=62.71875 rock=-51489286/0 surface=-51489286/0 deep=-51489286/2",
        "seed=-7 chunk=(-25,-3) pos=(-400,-48) temp=4.2284255 rain=241.89392 var=0.18318611 gw=0.0 rock=-1506881344/0 surface=-1506881344/0 deep=-1506881344/2",
        "seed=-7 chunk=(-25,-3) pos=(-385,-33) temp=4.3360972 rain=240.49792 var=0.16829836 gw=0.0 rock=-1506881344/0 surface=-1506881344/0 deep=-1506881344/2",
        "seed=-7 chunk=(-25,-3) pos=(-393,-39) temp=4.288932 rain=241.10724 var=0.17488018 gw=0.0 rock=-1506881344/0 surface=-1506881344/0 deep=-1506881344/2",
        "seed=-7 chunk=(0,0) pos=(0,0) temp=5.0 rain=263.75 var=0.029166637 gw=0.0 rock=-1506881344/0 surface=-1506881344/0 deep=-1506881344/2",
        "seed=-7 chunk=(0,0) pos=(15,15) temp=5.0690737 rain=263.6351 var=0.038275443 gw=0.0 rock=-1506881344/0 surface=-1506881344/0 deep=-1506881344/2",
        "seed=-7 chunk=(0,0) pos=(7,9) temp=5.0395193 rain=263.47345 var=0.035133667 gw=0.0 rock=-1506881344/0 surface=-1506881344/0 deep=-1506881344/2",
        "seed=-7 chunk=(-1,-1) pos=(-16,-16) temp=4.925291 rain=263.84784 var=0.039736282 gw=0.0 rock=-1506881344/0 surface=-1506881344/0 deep=-1506881344/2",
        "seed=-7 chunk=(-1,-1) pos=(-1,-1) temp=4.995349 rain=263.75528 var=0.029820984 gw=0.0 rock=-1506881344/0 surface=-1506881344/0 deep=-1506881344/2",
        "seed=-7 chunk=(-1,-1) pos=(-9,-7) temp=4.965616 rain=263.58078 var=0.03427975 gw=0.0 rock=-1506881344/0 surface=-1506881344/0 deep=-1506881344/2",
        "seed=-7 chunk=(7,-12) pos=(112,-192) temp=4.6032276 rain=288.40826 var=0.077226914 gw=0.0 rock=-1506881344/0 surface=-1506881344/0 deep=-1506881344/2",
        "seed=-7 chunk=(7,-12) pos=(127,-177) temp=4.6376944 rain=289.3193 var=0.06411549 gw=0.0 rock=-1506881344/0 surface=-1506881344/0 deep=-1506881344/2",
        "seed=-7 chunk=(7,-12) pos=(119,-183) temp=4.6211686 rain=288.7965 var=0.070025794 gw=0.0 rock=-1506881344/0 surface=-1506881344/0 deep=-1506881344/2",
        "seed=-7 chunk=(-14,9) pos=(-224,144) temp=5.220486 rain=231.92496 var=0.2178392 gw=0.0 rock=-1506881342/0 surface=-1506881342/0 deep=-1506881342/2",
        "seed=-7 chunk=(-14,9) pos=(-209,159) temp=5.2442274 rain=232.63608 var=0.21489257 gw=0.0 rock=-1506881342/0 surface=-1506881342/0 deep=-1506881342/2",
        "seed=-7 chunk=(-14,9) pos=(-217,153) temp=5.233118 rain=232.24397 var=0.2165053 gw=0.0 rock=-1506881342/0 surface=-1506881342/0 deep=-1506881342/2",
        "seed=-7 chunk=(40,30) pos=(640,480) temp=7.340231 rain=301.83844 var=0.02100899 gw=0.0 rock=420776060/0 surface=420776060/0 deep=420776060/2",
        "seed=-7 chunk=(40,30) pos=(655,495) temp=7.3663416 rain=302.46835 var=0.019053988 gw=0.0 rock=420776060/0 surface=420776060/0 deep=420776060/2",
        "seed=-7 chunk=(40,30) pos=(647,489) temp=7.350038 rain=302.15854 var=0.020008232 gw=0.0 rock=420776060/0 surface=420776060/0 deep=420776060/2",
        "seed=-7 chunk=(-100,55) pos=(-1600,880) temp=7.078374 rain=204.49379 var=0.18887721 gw=0.0 rock=-259478124/0 surface=-259478124/0 deep=-259478124/2",
        "seed=-7 chunk=(-100,55) pos=(-1585,895) temp=7.186887 rain=204.96333 var=0.18005532 gw=0.0 rock=-259478124/0 surface=-259478124/0 deep=-259478124/2",
        "seed=-7 chunk=(-100,55) pos=(-1593,889) temp=7.1371536 rain=204.71033 var=0.18376788 gw=0.0 rock=-259478124/0 surface=-259478124/0 deep=-259478124/2",
        "seed=-7 chunk=(3,-300) pos=(48,-4800) temp=-5.119334 rain=290.55542 var=0.8570064 gw=0.0 rock=-1497150211/0 surface=-1497150211/0 deep=-1497150211/2",
        "seed=-7 chunk=(3,-300) pos=(63,-4785) temp=-5.0347834 rain=291.08484 var=0.8586336 gw=0.0 rock=-1497150211/0 surface=-1497150211/0 deep=-1497150211/2",
        "seed=-7 chunk=(3,-300) pos=(55,-4791) temp=-5.070506 rain=290.85306 var=0.8584628 gw=0.0 rock=-1497150211/0 surface=-1497150211/0 deep=-1497150211/2",
        "seed=-7 chunk=(-45,-60) pos=(-720,-960) temp=2.8536742 rain=228.39476 var=0.84539163 gw=91.51318 rock=1019255830/0 surface=1019255830/0 deep=-690196682/2",
        "seed=-7 chunk=(-45,-60) pos=(-705,-945) temp=2.8738873 rain=227.97192 var=0.85598814 gw=84.32638 rock=-690196682/0 surface=-690196682/0 deep=-690196682/2",
        "seed=-7 chunk=(-45,-60) pos=(-713,-951) temp=2.860092 rain=228.1182 var=0.85040027 gw=84.96384 rock=-690196682/0 surface=1019255830/0 deep=-690196682/2",
        "seed=-7 chunk=(-45,-57) pos=(-720,-912) temp=2.7864497 rain=226.45177 var=0.84709096 gw=0.0 rock=-690196682/0 surface=-690196682/0 deep=-690196682/2",
        "seed=-7 chunk=(-45,-57) pos=(-705,-897) temp=2.8103316 rain=225.99933 var=0.8578112 gw=0.2764991 rock=-690196682/0 surface=-690196682/0 deep=-690196682/2",
        "seed=-7 chunk=(-45,-57) pos=(-713,-903) temp=2.7945795 rain=226.16139 var=0.85215735 gw=0.90323037 rock=-690196682/0 surface=-690196682/0 deep=-690196682/2",
        "seed=-7 chunk=(-42,-60) pos=(-672,-960) temp=2.9819121 rain=229.01428 var=0.8774775 gw=85.77288 rock=-690196682/0 surface=-690196682/0 deep=-690196682/2",
        "seed=-7 chunk=(-42,-60) pos=(-657,-945) temp=3.0057938 rain=228.56183 var=0.8881977 gw=121.621475 rock=-690196682/0 surface=-690196682/0 deep=-690196682/2",
        "seed=-7 chunk=(-42,-60) pos=(-665,-951) temp=2.9905312 rain=228.71996 var=0.8825603 gw=103.130165 rock=-690196682/0 surface=-690196682/0 deep=-690196682/2",
        "seed=20260917 chunk=(-25,-3) pos=(-400,-48) temp=5.330193 rain=252.96585 var=-0.6993968 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-1318825455/2",
        "seed=20260917 chunk=(-25,-3) pos=(-385,-33) temp=5.375626 rain=253.9958 var=-0.7039485 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-1318825455/2",
        "seed=20260917 chunk=(-25,-3) pos=(-393,-39) temp=5.3558717 rain=253.65811 var=-0.70141757 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-1318825455/2",
        "seed=20260917 chunk=(0,0) pos=(0,0) temp=5.0 rain=263.75 var=-0.675 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-1318825455/2",
        "seed=20260917 chunk=(0,0) pos=(15,15) temp=4.947756 rain=266.18616 var=-0.6703727 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-1318825455/2",
        "seed=20260917 chunk=(0,0) pos=(7,9) temp=4.9768677 rain=265.04883 var=-0.67247415 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-1318825455/2",
        "seed=20260917 chunk=(-1,-1) pos=(-16,-16) temp=5.050705 rain=261.18274 var=-0.6854304 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-1318825455/2",
        "seed=20260917 chunk=(-1,-1) pos=(-1,-1) temp=5.0032415 rain=263.58926 var=-0.67567813 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-1318825455/2",
        "seed=20260917 chunk=(-1,-1) pos=(-9,-7) temp=5.030376 rain=262.46585 var=-0.6801961 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-1318825455/2",
        "seed=20260917 chunk=(7,-12) pos=(112,-192) temp=4.6648602 rain=255.08566 var=-0.7189361 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-73928819/2",
        "seed=20260917 chunk=(7,-12) pos=(127,-177) temp=4.5760016 rain=257.55487 var=-0.71205604 gw=0.0 rock=-73928819/0 surface=-73928819/0 deep=-73928819/2",
        "seed=20260917 chunk=(7,-12) pos=(119,-183) temp=4.6182966 rain=256.43335 var=-0.7151673 gw=0.0 rock=-73928819/0 surface=-73928819/0 deep=-73928819/2",
        "seed=20260917 chunk=(-14,9) pos=(-224,144) temp=5.484337 rain=265.9762 var=-0.70268345 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-1318825455/3",
        "seed=20260917 chunk=(-14,9) pos=(-209,159) temp=5.4454083 rain=267.31555 var=-0.69628686 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-1318825455/3",
        "seed=20260917 chunk=(-14,9) pos=(-217,153) temp=5.461116 rain=266.7679 var=-0.6993363 gw=0.0 rock=-1318825455/0 surface=-1318825455/0 deep=-1318825455/3",
        "seed=20260917 chunk=(40,30) pos=(640,480) temp=6.7232485 rain=266.16724 var=-0.59889007 gw=0.0 rock=-1318825453/0 surface=-73928817/0 deep=-1318825453/2",
        "seed=20260917 chunk=(40,30) pos=(655,495) temp=6.76862 rain=265.33566 var=-0.59561646 gw=0.0 rock=-1318825453/0 surface=-1318825453/0 deep=-1318825453/3",
        "seed=20260917 chunk=(40,30) pos=(647,489) temp=6.7494273 rain=265.65567 var=-0.59748024 gw=0.0 rock=-1318825453/0 surface=-1318825453/0 deep=-1318825453/2",
        "seed=20260917 chunk=(-100,55) pos=(-1600,880) temp=5.39555 rain=206.15991 var=-0.49517924 gw=0.0 rock=-1559392939/0 surface=-1559392939/0 deep=-1559392939/2",
        "seed=20260917 chunk=(-100,55) pos=(-1585,895) temp=5.40416 rain=206.3775 var=-0.4925396 gw=0.0 rock=-1559392939/0 surface=-1559392939/0 deep=-1559392939/2",
        "seed=20260917 chunk=(-100,55) pos=(-1593,889) temp=5.398994 rain=206.2406 var=-0.49348384 gw=0.0 rock=-1559392939/0 surface=-1559392939/0 deep=-1559392939/2",
        "seed=20260917 chunk=(3,-300) pos=(48,-4800) temp=-4.4147573 rain=292.41785 var=-0.07570676 gw=0.0 rock=-450652060/0 surface=-450652060/0 deep=-450652060/2",
        "seed=20260917 chunk=(3,-300) pos=(63,-4785) temp=-4.3363857 rain=292.7305 var=-0.07846591 gw=0.0 rock=-450652060/0 surface=-450652060/0 deep=-450652060/2",
        "seed=20260917 chunk=(3,-300) pos=(55,-4791) temp=-4.3672314 rain=292.59995 var=-0.07730262 gw=0.0 rock=-450652060/0 surface=-450652060/0 deep=-450652060/2",
        "seed=20260917 chunk=(57,54) pos=(912,864) temp=7.453676 rain=263.9389 var=-0.48188955 gw=0.0 rock=-73928819/0 surface=-73928819/0 deep=-421487343/2",
        "seed=20260917 chunk=(57,54) pos=(927,879) temp=7.4666624 rain=261.66727 var=-0.47459134 gw=21.32946 rock=-73928819/0 surface=-73928819/0 deep=-421487343/2",
        "seed=20260917 chunk=(57,54) pos=(919,873) temp=7.46015 rain=262.34384 var=-0.47823074 gw=5.972249 rock=-73928819/0 surface=-73928819/0 deep=-421487343/2",
        "seed=20260917 chunk=(57,57) pos=(912,912) temp=7.4543705 rain=253.85289 var=-0.48047206 gw=27.327253 rock=-421487343/0 surface=-421487343/0 deep=-421487343/2",
        "seed=20260917 chunk=(57,57) pos=(927,927) temp=7.45646 rain=254.01877 var=-0.47636497 gw=162.65773 rock=-421487343/0 surface=-421487343/0 deep=-421487343/2",
        "seed=20260917 chunk=(57,57) pos=(919,921) temp=7.4543414 rain=253.72661 var=-0.4787482 gw=101.61218 rock=-421487343/0 surface=-421487343/0 deep=-421487343/2",
        "seed=20260917 chunk=(57,60) pos=(912,960) temp=7.430172 rain=248.91719 var=-0.48503053 gw=116.59125 rock=-421487343/0 surface=-421487343/0 deep=-421487343/2",
        "seed=20260917 chunk=(57,60) pos=(927,975) temp=7.43219 rain=249.04933 var=-0.48087475 gw=148.40553 rock=-421487343/0 surface=-421487343/0 deep=-421487343/2",
        "seed=20260917 chunk=(57,60) pos=(919,969) temp=7.4301095 rain=248.77516 var=-0.48328394 gw=134.00215 rock=-421487343/0 surface=-421487343/0 deep=-421487343/2"
    };

    @Test
    void chunkDataMatchesTfcAtPinnedCommit() {
        final Map<Long, ChunkDataGenerator> generators = new HashMap<>();
        int riverPositions = 0;
        for (String line : GOLDEN) {
            final Matcher m = LINE.matcher(line);
            assertThat(m.matches()).as(line).isTrue();
            final long seed = Long.parseLong(m.group(1));
            final ChunkDataGenerator generator = generators.computeIfAbsent(seed, ChunkDataGoldenTest::generatorFor);

            final int chunkX = Integer.parseInt(m.group(2));
            final int chunkZ = Integer.parseInt(m.group(3));
            final int x = Integer.parseInt(m.group(4));
            final int z = Integer.parseInt(m.group(5));
            final ChunkData data = generator.generate(chunkX, chunkZ);

            assertThat(data.chunkX()).as(line).isEqualTo(chunkX);
            assertThat(data.chunkZ()).as(line).isEqualTo(chunkZ);
            assertThat(data.averageSeaLevelTemperature(x, z)).as(line).isEqualTo(Float.parseFloat(m.group(6)));
            assertThat(data.averageRainfall(x, z)).as(line).isEqualTo(Float.parseFloat(m.group(7)));
            assertThat(data.rainVariance(x, z)).as(line).isEqualTo(Float.parseFloat(m.group(8)));
            assertThat(data.baseGroundwater(x, z)).as(line).isEqualTo(Float.parseFloat(m.group(9)));
            if (data.baseGroundwater(x, z) > 0) {
                riverPositions++;
            }

            final ChunkDataGenerator.RockSample rock = generator.generateRock(x, 60, z, 72);
            assertThat(rock).as(line).isEqualTo(new ChunkDataGenerator.RockSample(Integer.parseInt(m.group(10)), Integer.parseInt(m.group(11))));
            final ChunkDataGenerator.RockSample deep = generator.generateRock(x, -30, z, 120);
            assertThat(deep).as(line).isEqualTo(new ChunkDataGenerator.RockSample(Integer.parseInt(m.group(14)), Integer.parseInt(m.group(15))));
            final ChunkDataGenerator.RockSample surface = generator.generateSurfaceRock(x, z);
            assertThat(surface).as(line).isEqualTo(new ChunkDataGenerator.RockSample(Integer.parseInt(m.group(12)), Integer.parseInt(m.group(13))));
        }
        assertThat(GOLDEN).hasSize(99);
        // The river path is exercised: nine chunks with groundwater, three positions each
        assertThat(riverPositions).isEqualTo(27);
    }

    private static ChunkDataGenerator generatorFor(long seed) {
        final boolean finite = seed == 20260917L;
        final Seed levelSeed = Seed.of(seed);
        final RegionGenerator regionGenerator = new RegionGenerator(new RegionSettings(0.5f, finite, 20_000, 0f, 20_000, 0f), levelSeed);
        return new ChunkDataGenerator(regionGenerator, levelSeed);
    }
}
