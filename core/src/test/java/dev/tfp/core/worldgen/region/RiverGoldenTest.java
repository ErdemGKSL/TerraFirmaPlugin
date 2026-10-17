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
 * and river classes unchanged, with the task list of {@link RegionGeneratorGoldenTest} (the biome altitude task included, since it draws before the rivers;
 * karst and chunk data are removed) plus the river task. It ran with Minecraft 1.21.11's {@code Mth}, {@code XoroshiroRandomSource}
 * and {@code RandomSource}, and the overworld settings. {@link #MTH_ATAN2} and {@link #MTH_TRIG} are Minecraft's own values.
 */
class RiverGoldenTest {
    /** The oracle lines: 3 seeds x 8 grid points = 24 regions. */
    private static final String[] RIVER_GOLDEN = {
        "RIVER seed=42 finite=false grid=(-400,-60) rivers=729 riverPts=527 lakePts=178 edgeHash=-6265006055916850182L flagHash=-3361464813015277765L samples=[20 -5125519756074473553 nnw][40 6787272646477464362 nw][30 -1313140739491035790 nw]",
        "RIVER seed=42 finite=false grid=(-100,25) rivers=328 riverPts=186 lakePts=77 edgeHash=-6193818022069359219L flagHash=4601135280408033293L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=42 finite=false grid=(0,0) rivers=486 riverPts=396 lakePts=140 edgeHash=8432408096995590672L flagHash=-1379693867850620807L samples=[14 -1250576042594772029 n][2 933631229442811319 n][0 3 none]",
        "RIVER seed=42 finite=false grid=(60,200) rivers=376 riverPts=136 lakePts=98 edgeHash=7150000502470311693L flagHash=8529183913661033480L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=42 finite=false grid=(350,-400) rivers=711 riverPts=457 lakePts=190 edgeHash=-4729422256539132998L flagHash=-6591801712229738524L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=42 finite=false grid=(-250,350) rivers=429 riverPts=137 lakePts=85 edgeHash=8935166121644808829L flagHash=1563863222956125049L samples=[0 3 none][1 -3173608438098952002 sse][0 3 none]",
        "RIVER seed=42 finite=false grid=(120,-120) rivers=620 riverPts=506 lakePts=128 edgeHash=-3250921027149942605L flagHash=-5484906741585816675L samples=[16 8341282620911482547 n][0 3 none][28 -9108634695190224577 nne]",
        "RIVER seed=42 finite=false grid=(-5,-5) rivers=486 riverPts=396 lakePts=140 edgeHash=8432408096995590672L flagHash=-1379693867850620807L samples=[1 601790625034206499 nne][0 3 none][0 3 none]",
        "RIVER seed=-7 finite=false grid=(-400,-60) rivers=934 riverPts=899 lakePts=257 edgeHash=7067306515677461423L flagHash=-2504122768160450375L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=-7 finite=false grid=(-100,25) rivers=664 riverPts=459 lakePts=158 edgeHash=7574989328897240238L flagHash=2628487680207385320L samples=[5 7560506398707574785 se][0 3 none][5 7566422033313939032 nww]",
        "RIVER seed=-7 finite=false grid=(0,0) rivers=566 riverPts=366 lakePts=135 edgeHash=541312297411173720L flagHash=-1887678524606789338L samples=[1 -6396152011254886334 se][6 3966132891689612558 ne][9 -9049554840196227939 s]",
        "RIVER seed=-7 finite=false grid=(60,200) rivers=479 riverPts=341 lakePts=135 edgeHash=2711238623276212548L flagHash=6097507782107910655L samples=[23 -6409482619550608387 sse][4 8023616353429744986 nww][27 619355115879273503 e]",
        "RIVER seed=-7 finite=false grid=(350,-400) rivers=540 riverPts=321 lakePts=125 edgeHash=7037130397158014116L flagHash=-2647245068580162596L samples=[16 -7560669935112113108 nnw][7 -5433271881915737044 se][5 5379193772219000444 nne]",
        "RIVER seed=-7 finite=false grid=(-250,350) rivers=630 riverPts=473 lakePts=179 edgeHash=-2332322774619414292L flagHash=7007853031268147288L samples=[5 2685485814774850123 e][23 -4665438404215521568 ne][0 3 none]",
        "RIVER seed=-7 finite=false grid=(120,-120) rivers=578 riverPts=208 lakePts=161 edgeHash=6265836326331333461L flagHash=7150379452006487621L samples=[28 -4088331910055558430 n][16 -352573428453607960 sse][10 7795525894756041489 nw]",
        "RIVER seed=-7 finite=false grid=(-5,-5) rivers=566 riverPts=366 lakePts=135 edgeHash=541312297411173720L flagHash=-1887678524606789338L samples=[14 -2486455949768113085 see][16 -1416096309918261266 nw][19 -6108796597983801705 s]",
        "RIVER seed=20260917 finite=true grid=(-400,-60) rivers=0 riverPts=0 lakePts=0 edgeHash=0L flagHash=1613579465433893511L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(-100,25) rivers=612 riverPts=477 lakePts=126 edgeHash=7794691734052233252L flagHash=-7913431596645380784L samples=[0 3 none][4 -3563864104550757037 sw][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(0,0) rivers=215 riverPts=130 lakePts=48 edgeHash=-3970852045739889119L flagHash=-6444582204926885588L samples=[8 6776094536123866511 nne][0 3 none][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(60,200) rivers=702 riverPts=386 lakePts=157 edgeHash=5699721671361132515L flagHash=6052178728543993954L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(350,-400) rivers=0 riverPts=0 lakePts=0 edgeHash=0L flagHash=-994525036724224920L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(-250,350) rivers=0 riverPts=0 lakePts=0 edgeHash=0L flagHash=-3651927158436471550L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(120,-120) rivers=30 riverPts=0 lakePts=5 edgeHash=2249805086095505990L flagHash=2917470478067607497L samples=[0 3 none][0 3 none][0 3 none]",
        "RIVER seed=20260917 finite=true grid=(-5,-5) rivers=215 riverPts=130 lakePts=48 edgeHash=-3970852045739889119L flagHash=-6444582204926885588L samples=[0 3 none][0 3 none][0 3 none]",
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
