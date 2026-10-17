/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.groundcover;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.deposit.DepositKind;
import dev.tfp.core.worldgen.deposit.DepositMaterial;
import dev.tfp.core.worldgen.deposit.Deposits;
import dev.tfp.core.worldgen.ore.Indicator;
import dev.tfp.core.worldgen.ore.Weighted;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Golden values of TFC's loose rocks, shore groundcover, soil discs (ore deposits, clay, peat), vein indicators and climate
 * filter at the pinned commit (c3b4d9536).
 *
 * <p>The expected values came from TFC's own classes, compiled in a scratch oracle against Minecraft 1.21.11's
 * {@code RandomSource}, {@code XoroshiroRandomSource}, {@code Xoroshiro128PlusPlus} and {@code RandomSupport}:
 * {@code LooseRockFeature}, {@code SoilDiscFeature}, {@code SoilDiscConfig} (codec removed), {@code Metaballs2D},
 * {@code ClimatePlacement} and {@code IntertidalPlacement} (codecs, {@code type} and {@code getPositions} removed),
 * {@code EnvironmentHelpers.adjustAvgTempForElev}, {@code Helpers.fork/uniform/triangle}, and the above-ground indicator block
 * of {@code VeinFeature.place} and {@code BlockWithFluidFeature.place} copied into the harness. The vanilla steps around them
 * (rarity_filter, in_square, random_patch, heightmap, height_range uniform) were written from the 1.21 code; the rarity filter
 * and {@code Mth.randomBetweenInclusive} were checked against the server bytecode. Stubs: {@code BlockPos}, {@code BlockState}
 * (block, fluid, properties; groundcover survives on the harness's full blocks), {@code WorldGenLevel} (the terrain of
 * {@link SyntheticView}), {@code ChunkData} (the climate of {@link SyntheticView}), {@code RockSettings}, {@code FluidHelpers}
 * (fills a groundcover block with the fluid there) and {@code isWorldgenReplaceable} (air or a fluid block). Nothing was run on
 * a server.
 *
 * <p>Each case seeds {@code new XoroshiroRandomSource(seed ^ a * 341873128712L ^ b * 132897987541L ^ f * 0x9E3779B97F4A7C15L)}
 * for a chunk or column {@code (a, b)} and a feature number {@code f}, runs one feature, and hashes (FNV-1a 64) the line
 * {@code "a b|x,y,z,block,count,fluid;...|<next long of the random>"}, so the placements and the number of draws both match.
 * Chunks x, z in [-4, 3] (discs [-8, 7]), columns x in [-40, 39] step 3 and z step 5, four level seeds.
 */
class GroundcoverGoldenTest {
    record Golden(String name, long seed, int placed, int count, long hash) {}

    private static final GroundView VIEW = new SyntheticView();

    static final List<Golden> LOOSE = List.of(
        new Golden("loose", 0L, 0, 461, 3270409649936140184L),
        new Golden("loose", 42L, 0, 460, 2394298900238616648L),
        new Golden("loose", -7654321L, 0, 464, -8539810983921929628L),
        new Golden("loose", 123456789012345L, 0, 468, 6796780999009964832L));

    static final List<Golden> SHORE = List.of(
        new Golden("driftwood", 0L, 0, 14, 3465153675779995943L),
        new Golden("driftwood", 42L, 0, 15, -3549161955543465641L),
        new Golden("driftwood", -7654321L, 0, 14, -354254893041553765L),
        new Golden("driftwood", 123456789012345L, 0, 15, -7701526016511853772L),
        new Golden("clam", 0L, 0, 6, -2299083908045998556L),
        new Golden("clam", 42L, 0, 13, 6205102314796428032L),
        new Golden("clam", -7654321L, 0, 10, 3458543516988938764L),
        new Golden("clam", 123456789012345L, 0, 19, 31170351565121117L),
        new Golden("mollusk", 0L, 0, 9, 4738933540323203272L),
        new Golden("mollusk", 42L, 0, 12, -878425996645271297L),
        new Golden("mollusk", -7654321L, 0, 11, 7537167352730808830L),
        new Golden("mollusk", 123456789012345L, 0, 7, -6804684020742131989L),
        new Golden("mussel", 0L, 0, 6, 2212729981102129171L),
        new Golden("mussel", 42L, 0, 6, -4781530469068552520L),
        new Golden("mussel", -7654321L, 0, 13, 2886374408199406977L),
        new Golden("mussel", 123456789012345L, 0, 7, -2947629130149378171L),
        new Golden("seaweed", 0L, 0, 47, 2972092972457767135L),
        new Golden("seaweed", 42L, 0, 88, 1439224026818210524L),
        new Golden("seaweed", -7654321L, 0, 87, -7996493968182473234L),
        new Golden("seaweed", 123456789012345L, 0, 71, 8927113281254098715L),
        new Golden("sticks_shore", 0L, 0, 127, 7945317226668283334L),
        new Golden("sticks_shore", 42L, 0, 165, 4758214904078377863L),
        new Golden("sticks_shore", -7654321L, 0, 218, -8408784215679262042L),
        new Golden("sticks_shore", 123456789012345L, 0, 142, -1140250330575750019L));

    static final List<Golden> DISC = List.of(
        new Golden("native_copper_deposit", 0L, 18, 98, 7114856354275070216L),
        new Golden("native_copper_deposit", 42L, 20, 107, -5660548057822573300L),
        new Golden("native_copper_deposit", -7654321L, 18, 87, -1074683179539804545L),
        new Golden("native_copper_deposit", 123456789012345L, 27, 184, 4216849684558947247L),
        new Golden("native_copper_deep_deposit", 0L, 7, 1173, 4631069838301494520L),
        new Golden("native_copper_deep_deposit", 42L, 15, 1689, -2327151925842614480L),
        new Golden("native_copper_deep_deposit", -7654321L, 4, 293, -1461855432168032764L),
        new Golden("native_copper_deep_deposit", 123456789012345L, 11, 1439, -1100718215462942009L),
        new Golden("cassiterite_deposit", 0L, 26, 178, 7452153648295225117L),
        new Golden("cassiterite_deposit", 42L, 26, 165, 6643044990744753585L),
        new Golden("cassiterite_deposit", -7654321L, 27, 215, -2410046027113125463L),
        new Golden("cassiterite_deposit", 123456789012345L, 22, 106, 5229776511201984249L),
        new Golden("cassiterite_deep_deposit", 0L, 11, 1213, 8271113374252987044L),
        new Golden("cassiterite_deep_deposit", 42L, 11, 1353, -1168412750888365831L),
        new Golden("cassiterite_deep_deposit", -7654321L, 16, 1886, -6418409108233042215L),
        new Golden("cassiterite_deep_deposit", 123456789012345L, 11, 1627, -4211954078964182859L),
        new Golden("native_silver_deposit", 0L, 22, 156, 2293048656166993609L),
        new Golden("native_silver_deposit", 42L, 24, 161, 7601251199833908684L),
        new Golden("native_silver_deposit", -7654321L, 21, 125, 79935980808036960L),
        new Golden("native_silver_deposit", 123456789012345L, 25, 151, 2564825716983259788L),
        new Golden("native_silver_deep_deposit", 0L, 8, 852, -4416887499257919392L),
        new Golden("native_silver_deep_deposit", 42L, 6, 690, -6837947278853197700L),
        new Golden("native_silver_deep_deposit", -7654321L, 13, 2157, -8061564392236626691L),
        new Golden("native_silver_deep_deposit", 123456789012345L, 6, 861, -4944167335741495911L),
        new Golden("native_gold_deposit", 0L, 21, 122, 5035562318581096294L),
        new Golden("native_gold_deposit", 42L, 25, 215, 2809209678941433270L),
        new Golden("native_gold_deposit", -7654321L, 26, 180, -2259270135836970132L),
        new Golden("native_gold_deposit", 123456789012345L, 17, 132, -8116703186135805994L),
        new Golden("native_gold_deep_deposit", 0L, 7, 987, 3786821657347817806L),
        new Golden("native_gold_deep_deposit", 42L, 12, 1499, 6125322607033123182L),
        new Golden("native_gold_deep_deposit", -7654321L, 11, 1470, 6148267329930204253L),
        new Golden("native_gold_deep_deposit", 123456789012345L, 19, 2413, 7390346976195190302L),
        new Golden("clay_disc_with_indicator", 0L, 7, 571, 8177780375746638011L),
        new Golden("clay_disc_with_indicator", 42L, 7, 447, -2509880687198088545L),
        new Golden("clay_disc_with_indicator", -7654321L, 4, 281, -155536962394286316L),
        new Golden("clay_disc_with_indicator", 123456789012345L, 5, 550, 6939453267980459231L),
        new Golden("peat_disc", 0L, 0, 0, -1256860806344277358L),
        new Golden("peat_disc", 42L, 0, 0, 8119865016535622385L),
        new Golden("peat_disc", -7654321L, 1, 1193, -2718742206458819483L),
        new Golden("peat_disc", 123456789012345L, 2, 2095, 6636293994858702904L));

    static final List<Golden> INDICATOR = List.of(
        new Golden("12", 0L, 0, 130, -3000266116796602819L),
        new Golden("12", 42L, 0, 126, 868726518568526947L),
        new Golden("12", -7654321L, 0, 126, -3009467780773115070L),
        new Golden("12", 123456789012345L, 0, 141, -824170692009133890L),
        new Golden("25", 0L, 0, 69, -5032342771408162774L),
        new Golden("25", 42L, 0, 68, -6326383190653584491L),
        new Golden("25", -7654321L, 0, 55, 7541221029807996826L),
        new Golden("25", 123456789012345L, 0, 72, 2760521731229326124L),
        new Golden("40", 0L, 0, 40, -4618472948122510973L),
        new Golden("40", 42L, 0, 39, -5106537305133935987L),
        new Golden("40", -7654321L, 0, 36, 8048068764323099098L),
        new Golden("40", 123456789012345L, 0, 37, -5760955311910027507L));

    static final List<Golden> CLIMATE = List.of(
        new Golden("0", 0L, 0, 89, -7235353872379254093L),
        new Golden("0", 42L, 0, 98, 2443851536329952455L),
        new Golden("0", -7654321L, 0, 98, -7410512296640572836L),
        new Golden("0", 123456789012345L, 0, 103, 8460337424345548724L),
        new Golden("1", 0L, 0, 113, 2083774451142645850L),
        new Golden("1", 42L, 0, 101, -6727018226189484359L),
        new Golden("1", -7654321L, 0, 92, 8628711157617523751L),
        new Golden("1", 123456789012345L, 0, 114, 8355872980157384587L),
        new Golden("2", 0L, 0, 363, 2046300698530702133L),
        new Golden("2", 42L, 0, 363, 1142916288303346206L),
        new Golden("2", -7654321L, 0, 363, -3444015115312837488L),
        new Golden("2", 123456789012345L, 0, 363, -3305112848985307232L));

    static XoroshiroRandomSource rnd(long seed, int a, int b, int f) {
        return new XoroshiroRandomSource(seed ^ (a * 341873128712L) ^ (b * 132897987541L) ^ (f * 0x9E3779B97F4A7C15L));
    }

    static long fnv(long h, String s) {
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    static String log(Groundcover g) {
        return g.x() + "," + g.y() + "," + g.z() + "," + g.block() + "," + g.count() + "," + g.waterlogged() + ";";
    }

    @Test
    void looseRocksMatchTfc() {
        for (Golden golden : LOOSE) {
            long hash = 0xcbf29ce484222325L;
            int total = 0;
            for (int cx = -4; cx < 4; cx++) {
                for (int cz = -4; cz < 4; cz++) {
                    final XoroshiroRandomSource r = rnd(golden.seed(), cx, cz, 0);
                    final StringBuilder log = new StringBuilder();
                    final int[] n = {0};
                    LooseRocks.patch(r, cx << 4, cz << 4, VIEW, g -> {
                        log.append(log(g));
                        n[0]++;
                    });
                    hash = fnv(hash, cx + " " + cz + "|" + log + "|" + r.nextLong() + "\n");
                    total += n[0];
                }
            }
            assertThat(total).as("loose rocks, seed %d", golden.seed()).isEqualTo(golden.count());
            assertThat(hash).as("loose rocks, seed %d", golden.seed()).isEqualTo(golden.hash());
        }
    }

    @Test
    void shoreGroundcoverMatchesTfc() {
        for (Golden golden : SHORE) {
            final int p = indexOf(golden.name());
            final ShoreGroundcover.Patch patch = ShoreGroundcover.PATCHES.get(p);
            long hash = 0xcbf29ce484222325L;
            int total = 0;
            for (int cx = -4; cx < 4; cx++) {
                for (int cz = -4; cz < 4; cz++) {
                    final XoroshiroRandomSource r = rnd(golden.seed(), cx, cz, 10 + p);
                    final StringBuilder log = new StringBuilder();
                    final int[] n = {0};
                    ShoreGroundcover.patch(r, patch, cx << 4, cz << 4, VIEW, g -> {
                        log.append(log(g));
                        n[0]++;
                    });
                    hash = fnv(hash, cx + " " + cz + "|" + log + "|" + r.nextLong() + "\n");
                    total += n[0];
                }
            }
            assertThat(total).as("%s, seed %d", golden.name(), golden.seed()).isEqualTo(golden.count());
            assertThat(hash).as("%s, seed %d", golden.name(), golden.seed()).isEqualTo(golden.hash());
        }
    }

    @Test
    void soilDiscsMatchTfc() {
        for (Golden golden : DISC) {
            int f = -1;
            for (int i = 0; i < Deposits.FEATURES.size(); i++) {
                if (Deposits.FEATURES.get(i).feature().equals("tfc:" + golden.name())) {
                    f = i;
                }
            }
            assertThat(f).as(golden.name()).isNotNegative();
            final Deposits.Feature feature = Deposits.FEATURES.get(f);
            long hash = 0xcbf29ce484222325L;
            int total = 0;
            int placed = 0;
            for (int cx = -8; cx < 8; cx++) {
                for (int cz = -8; cz < 8; cz++) {
                    final XoroshiroRandomSource r = rnd(golden.seed(), cx, cz, 30 + f);
                    final StringBuilder log = new StringBuilder();
                    final int[] n = {0};
                    if (Deposits.place(r, feature, cx << 4, cz << 4, VIEW, (x, y, z) -> {
                        final DepositMaterial material = SyntheticView.material(x, y, z);
                        final DepositKind kind = feature.disc().replacement(material);
                        if (kind != null) {
                            log.append(x).append(',').append(y).append(',').append(z).append(',')
                                .append(kind.name().toLowerCase(Locale.ROOT)).append(",1,false;");
                            n[0]++;
                        }
                    })) {
                        placed++;
                    }
                    hash = fnv(hash, cx + " " + cz + "|" + log + "|" + r.nextLong() + "\n");
                    total += n[0];
                }
            }
            assertThat(placed).as("%s placed, seed %d", golden.name(), golden.seed()).isEqualTo(golden.placed());
            assertThat(total).as("%s, seed %d", golden.name(), golden.seed()).isEqualTo(golden.count());
            assertThat(hash).as("%s, seed %d", golden.name(), golden.seed()).isEqualTo(golden.hash());
        }
    }

    @Test
    void veinIndicatorsMatchTfc() {
        final int[] rarities = {12, 25, 40};
        for (Golden golden : INDICATOR) {
            final int rarity = Integer.parseInt(golden.name());
            int k = 0;
            while (rarities[k] != rarity) {
                k++;
            }
            final Indicator indicator = new Indicator(35, rarity, 1, 0, Weighted.of(List.of("tfp:ore/small_malachite"), List.of(1.0)));
            long hash = 0xcbf29ce484222325L;
            int total = 0;
            for (int x = -40; x < 40; x += 3) {
                for (int z = -40; z < 40; z += 5) {
                    for (int maxVeinY : new int[] {30, 50, 70, 90}) {
                        final XoroshiroRandomSource r = rnd(golden.seed(), x, z, 100 + k * 7 + maxVeinY);
                        final Groundcover g = VeinIndicators.indicator(r, indicator, x, z, maxVeinY, VIEW);
                        hash = fnv(hash, x + " " + z + " " + maxVeinY + "|" + (g == null ? "" : log(g)) + "|" + r.nextLong() + "\n");
                        total += g == null ? 0 : 1;
                    }
                }
            }
            assertThat(total).as("indicator %d, seed %d", rarity, golden.seed()).isEqualTo(golden.count());
            assertThat(hash).as("indicator %d, seed %d", rarity, golden.seed()).isEqualTo(golden.hash());
        }
    }

    @Test
    void climateFilterMatchesTfc() {
        final ClimateFilter[] filters = {
            new ClimateFilter(0, 30, 100, 400, -0.5f, 0.5f, false, -64, 320, true, false),
            new ClimateFilter(-5, 25, 50, 300, 0.2f, 0.8f, true, 0, 120, true, true),
            new ClimateFilter(12, Float.POSITIVE_INFINITY, 350, Float.POSITIVE_INFINITY, -1f, 1f, false, -64, 320, false, false),
        };
        for (Golden golden : CLIMATE) {
            final int k = Integer.parseInt(golden.name());
            long hash = 0xcbf29ce484222325L;
            int valid = 0;
            for (int x = -50; x < 50; x += 7) {
                for (int z = -50; z < 50; z += 3) {
                    for (int y : new int[] {-64, 63, 64, 100, 150, 200}) {
                        final XoroshiroRandomSource r = rnd(golden.seed(), x, z, 300 + k * 1000 + y);
                        final boolean v = filters[k].isValid(VIEW, x, y, z, r);
                        if (v) {
                            valid++;
                        }
                        hash = fnv(hash, x + " " + y + " " + z + " " + v + " " + r.nextLong() + "\n");
                    }
                }
            }
            assertThat(valid).as("climate %d, seed %d", k, golden.seed()).isEqualTo(golden.count());
            assertThat(hash).as("climate %d, seed %d", k, golden.seed()).isEqualTo(golden.hash());
        }
    }

    @Test
    void goldenCount() {
        assertThat(LOOSE.size() + SHORE.size() + DISC.size() + INDICATOR.size() + CLIMATE.size()).isEqualTo(92);
    }

    private static int indexOf(String name) {
        for (int i = 0; i < ShoreGroundcover.PATCHES.size(); i++) {
            if (ShoreGroundcover.PATCHES.get(i).feature().equals("tfc:" + name + "_patch")) {
                return i;
            }
        }
        throw new AssertionError(name);
    }
}
