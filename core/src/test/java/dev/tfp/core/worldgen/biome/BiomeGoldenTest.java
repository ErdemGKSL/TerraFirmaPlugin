/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.biome;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.region.Region;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import org.junit.jupiter.api.Test;

/**
 * Golden biome maps: the same 24 regions as {@code RegionGeneratorGoldenTest} (3 seeds x 8 grid points, including negative
 * coordinates). Each line gives the seed, the grid point, the number of points, a hash over every point's biome (its grid
 * coordinates and biome id), a hash over every point's biome altitude and base land height, and eight sample points at the
 * grid point plus the offsets {@code (0, 0), (1, 0), (0, 1), (3, -2), (-5, 4), (9, 9), (-17, -11), (31, 2)}: biome, biome
 * altitude and base land height, or {@code [-]} outside the region.
 * <p>
 * The values come from TerraFirmaCraft at c3b4d9536 (the pinned commit). The oracle compiled TFC's region classes unchanged
 * with the task list of TFC's {@code RegionGenerator}, minus the karst surface rock task (karst is not ported: the rock
 * settings are datapack data, and the rule is off in every point) and the chunk data (not in the region task list). That
 * includes the biome altitude task and the biome choice, which are the last two steps. It ran with Minecraft 1.21.11's
 * {@code Mth} and {@code XoroshiroRandomSource}, and the overworld settings (continentalness 0.5, scales 20000, constants 0).
 */
class BiomeGoldenTest {
    private static final String[] GOLDEN = {
        "BIOME seed=42 grid=(-400,-60) points=10476 biomeHash=-2510331972622945095L altHash=4546684385816608372L samples=[121 8 15][124 8 15][19 8 16][124 8 13][50 8 15][56 8 15][105 0 1][120 7 6]",
        "BIOME seed=42 grid=(-100,25) points=6713 biomeHash=6021100858601279696L altHash=-5702657227351269623L samples=[1 0 0][1 0 0][5 0 0][3 0 0][4 0 0][4 0 0][3 0 0][4 0 0]",
        "BIOME seed=42 grid=(0,0) points=9278 biomeHash=-5303955425287926203L altHash=4071643681724252574L samples=[17 3 0][0 0 0][17 3 0][0 0 0][0 0 0][19 8 8][3 0 0][23 12 6]",
        "BIOME seed=42 grid=(60,200) points=8719 biomeHash=4806079988701834314L altHash=7572294067299685728L samples=[11 0 2][17 0 2][11 0 2][11 0 1][120 0 0][3 0 0][0 0 0][0 0 0]",
        "BIOME seed=42 grid=(350,-400) points=14406 biomeHash=-854299507887078006L altHash=974526777489854673L samples=[3 0 0][3 0 0][3 0 0][3 0 0][3 0 0][17 0 0][-][0 0 0]",
        "BIOME seed=42 grid=(-250,350) points=9012 biomeHash=4380355755445020297L altHash=2819770419525882407L samples=[1 0 0][1 0 0][1 0 0][1 0 0][5 0 0][4 0 0][0 0 0][4 0 0]",
        "BIOME seed=42 grid=(120,-120) points=10809 biomeHash=4876870419068904044L altHash=-2884882397668002794L samples=[0 0 0][12 0 0][12 0 0][0 0 0][13 0 3][16 0 3][3 0 0][0 0 0]",
        "BIOME seed=42 grid=(-5,-5) points=9278 biomeHash=-5303955425287926203L altHash=4071643681724252574L samples=[0 0 0][0 0 0][0 0 0][0 0 0][3 0 0][18 6 3][3 0 0][18 9 3]",
        "BIOME seed=-7 grid=(-400,-60) points=12454 biomeHash=2249299992464628925L altHash=7108950420277761695L samples=[7 0 0][7 0 0][7 0 0][-][3 0 0][7 0 0][-][-]",
        "BIOME seed=-7 grid=(-100,25) points=7986 biomeHash=-3467420189188463858L altHash=-3937646324115832397L samples=[0 0 0][0 0 0][0 0 0][0 0 0][51 0 0][4 0 0][40 4 8][-]",
        "BIOME seed=-7 grid=(0,0) points=11170 biomeHash=7980876382822793016L altHash=1926957718709151831L samples=[0 0 0][0 0 0][13 0 0][0 0 0][12 0 0][-][22 10 7][-]",
        "BIOME seed=-7 grid=(60,200) points=6296 biomeHash=-419390927436570972L altHash=7171992014613464286L samples=[21 11 4][53 11 4][21 11 4][53 11 3][20 9 8][3 0 0][22 11 6][-]",
        "BIOME seed=-7 grid=(350,-400) points=8054 biomeHash=-5983591249770146230L altHash=1449248433027342361L samples=[103 0 1][105 0 1][105 0 1][105 0 2][103 0 1][101 4 5][5 0 0][95 1 3]",
        "BIOME seed=-7 grid=(-250,350) points=12868 biomeHash=-3066685550628410083L altHash=-7081499257953625777L samples=[11 0 0][0 0 0][11 0 0][0 0 0][14 0 0][-][0 0 0][-]",
        "BIOME seed=-7 grid=(120,-120) points=11069 biomeHash=7007128580658410765L altHash=-3495551269804008681L samples=[110 12 4][19 11 4][110 12 5][18 9 2][110 12 5][20 10 7][-][27 4 5]",
        "BIOME seed=-7 grid=(-5,-5) points=11170 biomeHash=7980876382822793016L altHash=1926957718709151831L samples=[12 3 0][0 0 0][0 0 0][0 0 0][17 4 0][0 0 0][23 12 11][-]",
        "BIOME seed=20260917 grid=(-400,-60) points=11942 biomeHash=-6497330944289401672L altHash=8714524782316135534L samples=[3 0 0][3 0 0][3 0 0][3 0 0][3 0 0][-][3 0 0][-]",
        "BIOME seed=20260917 grid=(-100,25) points=11525 biomeHash=380784239601891985L altHash=4487603762742987242L samples=[0 0 0][0 0 0][0 0 0][0 0 0][5 0 0][-][51 0 2][-]",
        "BIOME seed=20260917 grid=(0,0) points=9142 biomeHash=-859915958887075743L altHash=2905765956347506993L samples=[89 10 1][89 9 1][89 10 1][17 8 1][0 0 0][88 9 6][3 0 0][0 0 0]",
        "BIOME seed=20260917 grid=(60,200) points=13440 biomeHash=-1174188330340867533L altHash=-6886089916893412379L samples=[3 0 0][3 0 0][3 0 0][3 0 0][3 0 0][-][12 0 2][-]",
        "BIOME seed=20260917 grid=(350,-400) points=11715 biomeHash=-5222991342257525783L altHash=3044909635101420562L samples=[3 0 0][3 0 0][3 0 0][3 0 0][3 0 0][3 0 0][3 0 0][3 0 0]",
        "BIOME seed=20260917 grid=(-250,350) points=10914 biomeHash=6815634670688025814L altHash=-429248000961859678L samples=[3 0 0][3 0 0][3 0 0][3 0 0][3 0 0][3 0 0][3 0 0][3 0 0]",
        "BIOME seed=20260917 grid=(120,-120) points=6074 biomeHash=-4221278097413574139L altHash=-2446805329217205797L samples=[3 0 0][3 0 0][3 0 0][3 0 0][3 0 0][3 0 0][3 0 0][3 0 0]",
        "BIOME seed=20260917 grid=(-5,-5) points=9142 biomeHash=-859915958887075743L altHash=2905765956347506993L samples=[3 0 0][3 0 0][3 0 0][0 0 0][0 0 0][18 11 3][0 0 0][3 0 0]",
    };

    @Test
    void biomesMatchTfcAtPinnedCommit() {
        int index = 0;
        for (long seed : new long[] {42L, -7L, 20260917L}) {
            final boolean finite = seed == 20260917L;
            final RegionGenerator generator = new RegionGenerator(new RegionSettings(0.5f, finite, 20_000, 0f, 20_000, 0f), Seed.of(seed));
            for (int[] grid : new int[][] {{-400, -60}, {-100, 25}, {0, 0}, {60, 200}, {350, -400}, {-250, 350}, {120, -120}, {-5, -5}}) {
                assertThat(describe(generator, seed, grid[0], grid[1])).as("line %d", index).isEqualTo(GOLDEN[index]);
                index++;
            }
        }
        assertThat(index).isEqualTo(GOLDEN.length);
    }

    static String describe(RegionGenerator generator, long seed, int gridX, int gridZ) {
        final Region region = generator.getOrCreateRegion(gridX, gridZ);
        long biomeHash = 0;
        long altHash = 0;
        int n = 0;
        for (Region.Point p : region.points()) {
            n++;
            biomeHash = mix(biomeHash, p.x);
            biomeHash = mix(biomeHash, p.z);
            biomeHash = mix(biomeHash, p.biome);
            altHash = mix(altHash, p.x);
            altHash = mix(altHash, p.z);
            altHash = mix(altHash, p.biomeAltitude);
            altHash = mix(altHash, p.baseLandHeight);
        }
        final int[][] samples = {{0, 0}, {1, 0}, {0, 1}, {3, -2}, {-5, 4}, {9, 9}, {-17, -11}, {31, 2}};
        final StringBuilder sb = new StringBuilder();
        for (int[] o : samples) {
            final Region.Point p = region.at(gridX + o[0], gridZ + o[1]);
            sb.append(p == null ? "[-]" : "[" + p.biome + " " + p.biomeAltitude + " " + p.baseLandHeight + "]");
        }
        return String.format("BIOME seed=%d grid=(%d,%d) points=%d biomeHash=%dL altHash=%dL samples=%s",
            seed, gridX, gridZ, n, biomeHash, altHash, sb);
    }

    private static long mix(long h, long v) {
        return h * 31 + v;
    }
}
