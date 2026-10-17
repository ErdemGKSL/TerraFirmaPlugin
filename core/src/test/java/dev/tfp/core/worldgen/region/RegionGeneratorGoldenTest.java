/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/region/RegionGenerator.java @ c3b4d9536 (golden values)
 */
package dev.tfp.core.worldgen.region;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.noise.Cellular2D;
import org.junit.jupiter.api.Test;

/**
 * Golden region maps. Each line is one region: the seed, whether finite continents are on, the grid point, the continent
 * cell {@code (cx, cy)}, the region bounds, the point count, the land, island, mountain and rock-id sums, and a hash over
 * every point's annotations (land, island, mountain, coastal mountain, volcanic and barrier flags; the distances,
 * ocean depth, hotspot age, rock id, rainfall, rainfall variance, temperature and divergence, floats as raw bits).
 * <p>
 * The values come from TerraFirmaCraft at c3b4d9536 (the pinned commit). An oracle harness compiled TFC's own region
 * classes unchanged, with TFC's task order: the karst surface rock task and the chunk data are removed from the task list,
 * and the biome altitude and biome choice tasks are kept (the altitude draws from the random stream before the rivers, so
 * the river values depend on it; the biome choice is last and changes no value here, see BiomeGoldenTest in the biome package). The river task is kept, so the rainfall in the hash includes the
 * river and lake increase; {@link RiverGoldenTest} checks the river edges. It ran with Minecraft 1.21.11's {@code Mth} and
 * {@code XoroshiroRandomSource}, and the overworld world preset settings (continentalness 0.5, scales 20000, constants 0).
 * Seeds 42 and -7 use the default finite-continents setting; seed 20260917 has finite continents on.
 */
class RegionGeneratorGoldenTest {
    private static final String[] GOLDEN = {
        "seed=42 finite=false grid=(-400,-60) cell=(-4,-1) bounds=[-471,-334]x[-106,-6] points=10476 land=5434 island=104 mountain=260 rockSum=-1563044422 hash=-6821514151650546440L",
        "seed=42 finite=false grid=(-100,25) cell=(-1,0) bounds=[-141,-41]x[-98,41] points=6713 land=2696 island=124 mountain=185 rockSum=1627483405 hash=3810872859259668207L",
        "seed=42 finite=false grid=(0,0) cell=(0,0) bounds=[-52,131]x[-23,54] points=9278 land=3542 island=147 mountain=317 rockSum=-934038577 hash=530461416795234184L",
        "seed=42 finite=false grid=(60,200) cell=(1,2) bounds=[16,134]x[112,236] points=8719 land=3423 island=113 mountain=359 rockSum=-1022587526 hash=-5337563375846627827L",
        "seed=42 finite=false grid=(350,-400) cell=(4,-4) bounds=[337,481]x[-479,-318] points=14406 land=5978 island=74 mountain=244 rockSum=-493199565 hash=-1064857219367162108L",
        "seed=42 finite=false grid=(-250,350) cell=(-3,3) bounds=[-319,-187]x[237,366] points=9012 land=3789 island=90 mountain=267 rockSum=-1379697138 hash=-8935778349910029072L",
        "seed=42 finite=false grid=(120,-120) cell=(1,-1) bounds=[79,180]x[-150,24] points=10809 land=4503 island=123 mountain=330 rockSum=831273691 hash=8649900095296074574L",
        "seed=42 finite=false grid=(-5,-5) cell=(0,0) bounds=[-52,131]x[-23,54] points=9278 land=3542 island=147 mountain=317 rockSum=-934038577 hash=530461416795234184L",
        "seed=-7 finite=false grid=(-400,-60) cell=(-5,0) bounds=[-516,-384]x[-70,46] points=12454 land=6371 island=67 mountain=300 rockSum=1311566488 hash=1880800660172801993L",
        "seed=-7 finite=false grid=(-100,25) cell=(-1,0) bounds=[-191,-75]x[-42,47] points=7986 land=4510 island=103 mountain=260 rockSum=-1410558689 hash=567545100931732360L",
        "seed=-7 finite=false grid=(0,0) cell=(0,0) bounds=[-87,62]x[-77,33] points=11170 land=5125 island=116 mountain=320 rockSum=1792418966 hash=1335084519670296339L",
        "seed=-7 finite=false grid=(60,200) cell=(0,2) bounds=[-15,79]x[154,249] points=6296 land=3514 island=73 mountain=258 rockSum=2050608179 hash=-465942313997016028L",
        "seed=-7 finite=false grid=(350,-400) cell=(4,-4) bounds=[268,398]x[-419,-336] points=8054 land=4128 island=103 mountain=250 rockSum=1970381680 hash=-8892994103314062377L",
        "seed=-7 finite=false grid=(-250,350) cell=(-3,3) bounds=[-347,-205]x[225,365] points=12868 land=5311 island=123 mountain=270 rockSum=1351255360 hash=4764256633981539653L",
        "seed=-7 finite=false grid=(120,-120) cell=(1,-1) bounds=[48,194]x[-134,-38] points=11069 land=4843 island=89 mountain=306 rockSum=-507493347 hash=-6256330016966923922L",
        "seed=-7 finite=false grid=(-5,-5) cell=(0,0) bounds=[-87,62]x[-77,33] points=11170 land=5125 island=116 mountain=320 rockSum=1792418966 hash=1335084519670296339L",
        "seed=20260917 finite=true grid=(-400,-60) cell=(-4,-1) bounds=[-480,-354]x[-151,11] points=11942 land=0 island=0 mountain=0 rockSum=1600964492 hash=2293348428464881058L",
        "seed=20260917 finite=true grid=(-100,25) cell=(-1,0) bounds=[-164,-21]x[-93,48] points=11525 land=5461 island=87 mountain=397 rockSum=1582516892 hash=850125229299383930L",
        "seed=20260917 finite=true grid=(0,0) cell=(0,0) bounds=[-56,72]x[-47,89] points=9142 land=2368 island=102 mountain=272 rockSum=-1705006723 hash=-4275248343918212923L",
        "seed=20260917 finite=true grid=(60,200) cell=(0,2) bounds=[-36,100]x[93,241] points=13440 land=6221 island=110 mountain=281 rockSum=-490751352 hash=7157597257631638077L",
        "seed=20260917 finite=true grid=(350,-400) cell=(4,-4) bounds=[291,436]x[-452,-313] points=11715 land=0 island=0 mountain=0 rockSum=170143112 hash=6774669283781185499L",
        "seed=20260917 finite=true grid=(-250,350) cell=(-2,4) bounds=[-294,-144]x[291,410] points=10914 land=0 island=0 mountain=0 rockSum=-1851883796 hash=-8915170942019985142L",
        "seed=20260917 finite=true grid=(120,-120) cell=(1,-1) bounds=[72,182]x[-169,-61] points=6074 land=555 island=40 mountain=200 rockSum=2095684297 hash=-5658868336505276508L",
        "seed=20260917 finite=true grid=(-5,-5) cell=(0,0) bounds=[-56,72]x[-47,89] points=9142 land=2368 island=102 mountain=272 rockSum=-1705006723 hash=-4275248343918212923L",
    };

    @Test
    void regionsMatchTfcAtPinnedCommit() {
        int index = 0;
        for (long seed : new long[] {42L, -7L, 20260917L}) {
            final boolean finite = seed == 20260917L;
            final RegionGenerator generator = new RegionGenerator(new RegionSettings(0.5f, finite, 20_000, 0f, 20_000, 0f), Seed.of(seed));
            for (int[] grid : new int[][] {{-400, -60}, {-100, 25}, {0, 0}, {60, 200}, {350, -400}, {-250, 350}, {120, -120}, {-5, -5}}) {
                assertThat(describe(generator, seed, finite, grid[0], grid[1])).as("line %d", index).isEqualTo(GOLDEN[index]);
                index++;
            }
        }
        assertThat(index).isEqualTo(GOLDEN.length);
    }

    /** One golden line for the region that owns the grid point. Same format as the oracle driver. */
    static String describe(RegionGenerator generator, long seed, boolean finite, int gridX, int gridZ) {
        final Cellular2D.Cell cell = generator.sampleCell(gridX, gridZ);
        final Region region = generator.getOrCreateRegion(gridX, gridZ);
        long h = 0;
        int n = 0, land = 0, island = 0, mountain = 0, rockSum = 0;
        for (Region.Point p : region.points()) {
            n++;
            final int flags = (p.land() ? 1 : 0) | (p.island() ? 2 : 0) | (p.mountain() ? 4 : 0)
                | (p.coastalMountain() ? 8 : 0) | (p.volcanic() ? 16 : 0) | (p.barrierIsland() ? 32 : 0);
            if (p.land()) {
                land++;
            }
            if (p.island()) {
                island++;
            }
            if (p.mountain()) {
                mountain++;
            }
            rockSum += p.rock;
            h = mix(h, p.x);
            h = mix(h, p.z);
            h = mix(h, flags);
            h = mix(h, p.distanceToOcean);
            h = mix(h, p.distanceToDeepOcean);
            h = mix(h, p.distanceToEdge);
            h = mix(h, p.distanceToWestCoast);
            h = mix(h, p.distanceToLand);
            h = mix(h, p.baseLandHeight);
            h = mix(h, p.oceanDepth);
            h = mix(h, p.hotSpotAge);
            h = mix(h, p.rock);
            h = mix(h, Float.floatToIntBits(p.rainfall));
            h = mix(h, Float.floatToIntBits(p.rainfallVariance));
            h = mix(h, Float.floatToIntBits(p.temperature));
            h = mix(h, Float.floatToIntBits(p.divergence));
        }
        return String.format("seed=%d finite=%b grid=(%d,%d) cell=(%d,%d) bounds=[%d,%d]x[%d,%d] points=%d land=%d island=%d mountain=%d rockSum=%d hash=%dL",
            seed, finite, gridX, gridZ, cell.cx(), cell.cy(), region.minX(), region.maxX(), region.minZ(), region.maxZ(),
            n, land, island, mountain, rockSum, h);
    }

    private static long mix(long h, long v) {
        return h * 31 + v;
    }
}
