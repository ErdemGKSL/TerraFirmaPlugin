/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/AddRiversAndLakes.java @ c3b4d9536 (golden values; the oracle also
 * compiled RiverEdge.java, RegionPartition.java and the src/main/java/net/dries007/tfc/world/river/ classes)
 */
package dev.tfp.core.worldgen.region;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.river.Flow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden river maps. Each line is one region, at the same eight grid points as {@link RegionGeneratorGoldenTest}, for
 * three seeds: the seed, finite continents, the grid point, the river edge count and the river and lake point counts,
 * a hash over every edge (its source and drain coordinates as raw double bits, its width, its drain edge index and
 * whether it is a source edge), a hash over every point's river and lake flags and rainfall, and three partition samples.
 * A partition sample is the grid point {@code (x, z)} plus the offsets {@code (0, 0)}, {@code (7, -11)} and
 * {@code (-13, 5)}: the number of river edges in its partition point, a hash of their sorted edge ids (a river id is a
 * hash of the edge's source, drain and width), and the flow of the nearest edge at that point ({@code none} if empty).
 * <p>
 * The values come from TerraFirmaCraft at c3b4d9536 (the pinned commit). An oracle harness compiled TFC's own region
 * and river classes unchanged, with the same trimmed task list as {@link RegionGeneratorGoldenTest} plus the river task
 * (karst, biome choice and chunk data are removed). It ran with Minecraft 1.21.11's {@code Mth}, {@code XoroshiroRandomSource}
 * and {@code RandomSource}, and the overworld settings. {@link #MTH_ATAN2} and {@link #MTH_TRIG} are Minecraft's own values.
 */
class RiverGoldenTest {
    /** The oracle lines: 3 seeds x 8 grid points = 24 regions. */
    private static final String[] RIVER_GOLDEN = {
        "RIVER seed=42 finite=false grid=(-400,-60) rivers=741 riverPts=503 lakePts=199 edgeHash=3369293401887856603L flagHash=7116561773920790446L samples=[48 4026852954824344118 nnw][42 1152561196169133182 n][62 -6379694363557757264 s]",
        "RIVER seed=42 finite=false grid=(-100,25) rivers=338 riverPts=287 lakePts=69 edgeHash=-6139323679556768974L flagHash=3317234964239747552L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=42 finite=false grid=(0,0) rivers=518 riverPts=426 lakePts=129 edgeHash=1771728910322691526L flagHash=-8660639614508070352L samples=[13 -3539578033128558402 nnw][7 4961702899268719027 n][0 3 none]",
        "RIVER seed=42 finite=false grid=(60,200) rivers=387 riverPts=167 lakePts=100 edgeHash=-2796093851242678746L flagHash=-6845562571620097098L samples=[8 1965398312101783320 n][4 -7593802120149759569 see][0 3 none]",
        "RIVER seed=42 finite=false grid=(350,-400) rivers=713 riverPts=537 lakePts=169 edgeHash=-7442090451960872374L flagHash=1630008873519545395L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=42 finite=false grid=(-250,350) rivers=351 riverPts=94 lakePts=76 edgeHash=-2882482488157399501L flagHash=-1567826592245991492L samples=[0 3 none][1 -3953923965155971753 s][0 3 none]",
        "RIVER seed=42 finite=false grid=(120,-120) rivers=638 riverPts=495 lakePts=183 edgeHash=-5277894601035212346L flagHash=-99710927863725805L samples=[9 4117522533435974168 nw][0 3 none][26 -8571472111350418028 nnw]",
        "RIVER seed=42 finite=false grid=(-5,-5) rivers=518 riverPts=426 lakePts=129 edgeHash=1771728910322691526L flagHash=-8660639614508070352L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=-7 finite=false grid=(-400,-60) rivers=1071 riverPts=966 lakePts=288 edgeHash=2338070889683443393L flagHash=5613197530291612125L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=-7 finite=false grid=(-100,25) rivers=560 riverPts=481 lakePts=98 edgeHash=6744828853867670043L flagHash=1234211434792950732L samples=[6 5135367888831462688 nee][3 -7369145454439990226 ne][10 4517532664806197310 se]",
        "RIVER seed=-7 finite=false grid=(0,0) rivers=624 riverPts=427 lakePts=174 edgeHash=8233631706542907186L flagHash=6041962283429845978L samples=[0 3 none][15 2090090763560795608 se][0 3 none]",
        "RIVER seed=-7 finite=false grid=(60,200) rivers=522 riverPts=453 lakePts=135 edgeHash=5394784970689712795L flagHash=-8242928110116673910L samples=[28 6814654089650715135 se][9 -1438029462919086044 n][44 -4163536393262512990 e]",
        "RIVER seed=-7 finite=false grid=(350,-400) rivers=538 riverPts=315 lakePts=113 edgeHash=2711952651642624481L flagHash=5321559582005213984L samples=[7 -8440262245627363529 nww][5 -1549958023606161184 n][8 2517710090213228262 nw]",
        "RIVER seed=-7 finite=false grid=(-250,350) rivers=644 riverPts=518 lakePts=160 edgeHash=7663892031542007520L flagHash=3510182392527790973L samples=[11 1481995839685885767 e][9 239935024772330929 se][0 3 none]",
        "RIVER seed=-7 finite=false grid=(120,-120) rivers=586 riverPts=176 lakePts=150 edgeHash=4341919460593612894L flagHash=-1675410382763450801L samples=[33 -8403420702763919069 nne][12 5377036770646724274 see][12 -391542220230740033 nnw]",
        "RIVER seed=-7 finite=false grid=(-5,-5) rivers=624 riverPts=427 lakePts=174 edgeHash=8233631706542907186L flagHash=6041962283429845978L samples=[10 2616301501402393750 sse][24 -5559953752563050316 sse][11 -3456264345315591916 sw]",
        "RIVER seed=20260917 finite=true grid=(-400,-60) rivers=0 riverPts=0 lakePts=0 edgeHash=0L flagHash=1613579465433893511L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(-100,25) rivers=663 riverPts=458 lakePts=154 edgeHash=1786237926897679895L flagHash=-3587609013068513433L samples=[0 3 none][11 -5855749584201697520 nne][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(0,0) rivers=231 riverPts=132 lakePts=32 edgeHash=-2834032738634231954L flagHash=-274115518008737562L samples=[4 3914317948708577918 sww][0 3 none][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(60,200) rivers=737 riverPts=495 lakePts=195 edgeHash=2120846630643085302L flagHash=-3231172934398209702L samples=[1 8280495117016240520 sse][0 3 none][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(350,-400) rivers=0 riverPts=0 lakePts=0 edgeHash=0L flagHash=-994525036724224920L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(-250,350) rivers=0 riverPts=0 lakePts=0 edgeHash=0L flagHash=-3651927158436471550L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(120,-120) rivers=48 riverPts=4 lakePts=6 edgeHash=-8872492574492127279L flagHash=8331738708493510480L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(-5,-5) rivers=231 riverPts=132 lakePts=32 edgeHash=-2834032738634231954L flagHash=-274115518008737562L samples=[0 3 none][0 3 none][0 3 none]",
    };

    /** Minecraft's {@code Mth.atan2(y, x)}: {y, x, result} as exact doubles. */
    private static final double[][] MTH_ATAN2 = {
        {0.0, 1.0, 0.0},
        {1.0, 0.0, 1.5707963267948966},
        {1.0, 1.0, 0.7853981366411398},
        {-1.0, 2.0, -0.4636472634610012},
        {2.0, -1.0, 2.034443590255898},
        {-3.0, -5.0, -2.601172251133547},
        {0.3, 0.7, 0.4048926939244048},
        {-0.001, -7.0, -3.1414498913544424},
        {5.5, -0.25, 1.6162206921169835},
        {1.0E-9, 3.0, 3.329527851243409E-10},
        {12.5, 40.25, 0.30111601703861957},
        {-80.0, -0.5, -1.5770486714412963},
    };

    /** Minecraft's {@code Mth.sin(double)} and {@code Mth.cos(double)}: {angle, sin, cos}, the results are {@code float}s. */
    private static final double[][] MTH_TRIG = {
        {0.1, 0.09982981, 0.99500453},
        {1.0, 0.8414514, 0.54033285},
        {-2.5, -0.5985448, -0.80108935},
        {3.0, 0.14113283, -0.98999065},
        {100.25, -0.27732244, 0.9607769},
        {-1000.5, -0.99526894, 0.097158395},
        {0.7853981633974483, 0.70710677, 0.70710677},
        {2.356194490192345, 0.70710677, -0.70710677},
    };

    @Test
    void riversMatchTfcAtPinnedCommit() {
        int index = 0;
        for (long seed : new long[] {42L, -7L, 20260917L}) {
            final boolean finite = seed == 20260917L;
            final RegionGenerator generator = new RegionGenerator(new RegionSettings(0.5f, finite, 20_000, 0f, 20_000, 0f), Seed.of(seed));
            for (int[] grid : new int[][] {{-400, -60}, {-100, 25}, {0, 0}, {60, 200}, {350, -400}, {-250, 350}, {120, -120}, {-5, -5}}) {
                assertThat(describe(generator, seed, finite, grid[0], grid[1])).as("line %d", index).isEqualTo(RIVER_GOLDEN[index]);
                index++;
            }
        }
        assertThat(index).isEqualTo(RIVER_GOLDEN.length);
    }

    @Test
    void mthAtan2AndTrigMatchMinecraft() {
        for (double[] row : MTH_ATAN2) {
            assertThat(Mth.atan2(row[0], row[1])).as("atan2(%s, %s)", row[0], row[1]).isEqualTo(row[2]);
        }
        for (double[] row : MTH_TRIG) {
            assertThat(Mth.sin(row[0])).as("sin(%s)", row[0]).isEqualTo((float) row[1]);
            assertThat(Mth.cos(row[0])).as("cos(%s)", row[0]).isEqualTo((float) row[2]);
        }
    }

    /** One golden line for the region that owns the grid point. Same format as the oracle driver. */
    static String describe(RegionGenerator generator, long seed, boolean finite, int gridX, int gridZ) {
        final Region region = generator.getOrCreateRegion(gridX, gridZ);
        final List<RiverEdge> rivers = region.rivers();
        long edgeHash = 0;
        for (RiverEdge edge : rivers) {
            edgeHash = mix(edgeHash, edgeId(edge));
            edgeHash = mix(edgeHash, rivers.indexOf(edge.drainEdge()));
            edgeHash = mix(edgeHash, edge.sourceEdge() ? 1 : 0);
        }
        long flagHash = 0;
        int riverPoints = 0, lakePoints = 0;
        for (Region.Point p : region.points()) {
            if (p.river()) {
                riverPoints++;
            }
            if (p.lake()) {
                lakePoints++;
            }
            flagHash = mix(flagHash, (p.river() ? 1 : 0) | (p.lake() ? 2 : 0));
            flagHash = mix(flagHash, Float.floatToIntBits(p.rainfall));
        }
        final StringBuilder samples = new StringBuilder();
        for (int[] offset : new int[][] {{0, 0}, {7, -11}, {-13, 5}}) {
            final int x = gridX + offset[0], z = gridZ + offset[1];
            final List<RiverEdge> edges = generator.getOrCreatePartitionPoint(x, z).rivers();
            final List<Long> ids = new ArrayList<>();
            RiverEdge nearest = null;
            double best = Double.MAX_VALUE;
            for (RiverEdge edge : edges) {
                ids.add(edgeId(edge));
                final double d = edge.fractal().intersectDistance(x, z);
                if (d < best) {
                    best = d;
                    nearest = edge;
                }
            }
            Collections.sort(ids);
            long listHash = 3;
            for (long id : ids) {
                listHash = mix(listHash, id);
            }
            final Flow flow = nearest == null ? Flow.NONE : nearest.fractal().calculateFlow(x, z);
            samples.append('[').append(ids.size()).append(' ').append(listHash).append(' ').append(flow.serializedName()).append(']');
        }
        return String.format("RIVER seed=%d finite=%b grid=(%d,%d) rivers=%d riverPts=%d lakePts=%d edgeHash=%dL flagHash=%dL samples=%s",
            seed, finite, gridX, gridZ, rivers.size(), riverPoints, lakePoints, edgeHash, flagHash, samples);
    }

    private static long edgeId(RiverEdge edge) {
        long h = 17;
        h = mix(h, Double.doubleToRawLongBits(edge.source().x()));
        h = mix(h, Double.doubleToRawLongBits(edge.source().y()));
        h = mix(h, Double.doubleToRawLongBits(edge.drain().x()));
        h = mix(h, Double.doubleToRawLongBits(edge.drain().y()));
        h = mix(h, edge.width);
        return h;
    }

    private static long mix(long h, long v) {
        return h * 31 + v;
    }
}
