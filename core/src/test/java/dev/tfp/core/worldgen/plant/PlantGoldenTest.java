/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.plant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.noise.BiomeInfoNoise;
import dev.tfp.core.worldgen.random.LegacyRandomSource;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Golden values of TFC's plant, wild crop and coral features at the pinned commit (c3b4d9536).
 *
 * <p>The expected values came from TFC's own classes, compiled in a scratch oracle against Minecraft 1.21.11's
 * {@code RandomSource}, {@code XoroshiroRandomSource}, {@code LegacyRandomSource}, {@code WorldgenRandom},
 * {@code PerlinSimplexNoise} and {@code SimplexNoise} (the biome info noise of {@code noise_based_count}), and vanilla's own
 * {@code CoralFeature}, {@code CoralTreeFeature}, {@code CoralMushroomFeature} and {@code CoralClawFeature} bytecode, with TFC's
 * {@code CoralHelpers} and {@code TFCCoral*Feature} copied in; {@code DynamicDensityRandomPatchFeature}, {@code NoisyMultipleFeature},
 * {@code BlockWithFluidFeature}, {@code EmergentPlantFeature}, {@code SubmergedTallPlantFeature}, {@code TallPlantFeature},
 * {@code TallWildCropFeature} and {@code SpreadingCropFeature}, {@code ClimatePlacement.isValid}, {@code IntertidalPlacement},
 * {@code NoSolidNeighborsPlacement}, {@code ShallowWaterPlacement}, {@code WouldSurviveWithFluidPredicate},
 * {@code RandomPropertyProvider}, {@code FluidHelpers.fillWithFluid}, {@code EnvironmentHelpers.isWorldgenReplaceable} and the
 * {@code canSurvive}/{@code placeTwoHalves} of the plant and wild crop block classes (TFCBushBlock, PlantBlock.createDry,
 * ShortGrassBlock, TFCTallGrassBlock, TFCPassableCactusBlock, TFCCactusBedBlock, FloatingWaterPlantBlock, WaterPlantBlock,
 * TallWaterPlantBlock, WildCropBlock, WildDoubleCropBlock, FloodedWildCropBlock) copied in, each plant's block class taken from
 * TFC's Plant.java, and every tag test reading the real tags (vanilla 1.21.11's merged with TFC's). The feature JSON is TFC's
 * own (src/main/resources/data/tfc/worldgen). The vanilla steps around them (placed feature position stream, rarity_filter,
 * in_square, heightmap, count, noise_based_count, block_predicate_filter, would_survive, random_patch, simple_block) were
 * written from the 1.21 code; Util.shuffle/getRandom and Direction.Plane follow the 1.21.11 bytecode. Stubs: BlockPos,
 * Direction, BlockState (block, properties, fluid), the level (the terrain of {@link PlantTestView}, writes recorded in first-set
 * order with the last state), ChunkData (the climate and forest density of {@link PlantTestView}). Nothing was run on a server.
 *
 * <p>Each case seeds {@code new XoroshiroRandomSource(seed ^ a * 341873128712L ^ b * 132897987541L ^ f * 0x9E3779B97F4A7C15L)}
 * for a chunk or column {@code (a, b)} and a feature number {@code f}, runs one placed feature, and hashes (FNV-1a 64) the line
 * {@code "a b|x,y,z,block,part,age,fluid,extra;...|<next long of the random>"}, so the placements and the number of draws both
 * match. Patch features: chunks x, z in [-8, 7] from the chunk's corner at y -64 (f = 20 + index); the per-try features: columns
 * x in [-40, 39], z in [-40, 39] step 3 from y 64 (f = 60 + index); four level seeds.
 */
class PlantGoldenTest {
    record Golden(String feature, long seed, int placed, long hash) {}

    static final List<Golden> PATCHES = List.of(
        new Golden("tfp:plant/bluegrass_patch", 0L, 37, 2715235402149284292L),
        new Golden("tfp:plant/bluegrass_patch", 42L, 39, 6866063570812751866L),
        new Golden("tfp:plant/bluegrass_patch", -7654321L, 53, 7863259259801922673L),
        new Golden("tfp:plant/bluegrass_patch", 123456789012345L, 44, -6993177808840182422L),
        new Golden("tfp:plant/allium_patch", 0L, 4, 6153617350787898009L),
        new Golden("tfp:plant/allium_patch", 42L, 10, 3813294212145059130L),
        new Golden("tfp:plant/allium_patch", -7654321L, 0, 5879366431342828412L),
        new Golden("tfp:plant/allium_patch", 123456789012345L, 3, 6505876884252721821L),
        new Golden("tfp:plant/foxglove_patch", 0L, 34, 526076860182853383L),
        new Golden("tfp:plant/foxglove_patch", 42L, 36, 4601702547452075920L),
        new Golden("tfp:plant/foxglove_patch", -7654321L, 28, 2203019929887068469L),
        new Golden("tfp:plant/foxglove_patch", 123456789012345L, 38, -8514619785259536724L),
        new Golden("tfp:plant/cattail_patch", 0L, 90, -7114541914234333780L),
        new Golden("tfp:plant/cattail_patch", 42L, 80, 3336910175519264419L),
        new Golden("tfp:plant/cattail_patch", -7654321L, 56, 3773807900456075982L),
        new Golden("tfp:plant/cattail_patch", 123456789012345L, 78, 2229085806613476476L),
        new Golden("tfp:plant/manatee_grass_patch", 0L, 62, 1578780990382773227L),
        new Golden("tfp:plant/manatee_grass_patch", 42L, 69, 5345219185503417005L),
        new Golden("tfp:plant/manatee_grass_patch", -7654321L, 72, -1422655438047578058L),
        new Golden("tfp:plant/manatee_grass_patch", 123456789012345L, 63, -8500492767699705641L),
        new Golden("tfp:plant/duckweed_patch", 0L, 291, 134045640104278864L),
        new Golden("tfp:plant/duckweed_patch", 42L, 214, 1252104871792881360L),
        new Golden("tfp:plant/duckweed_patch", -7654321L, 229, 8312974636589929818L),
        new Golden("tfp:plant/duckweed_patch", 123456789012345L, 220, 7672573334254371742L),
        new Golden("tfp:plant/rose_patch", 0L, 2, -927966166110211594L),
        new Golden("tfp:plant/rose_patch", 42L, 0, 1999394526639749626L),
        new Golden("tfp:plant/rose_patch", -7654321L, 2, 1601746368833813784L),
        new Golden("tfp:plant/rose_patch", 123456789012345L, 4, 3683669273510623491L),
        new Golden("tfp:plant/dead_bush_patch", 0L, 31, 4476452726426305688L),
        new Golden("tfp:plant/dead_bush_patch", 42L, 18, -568630373645156252L),
        new Golden("tfp:plant/dead_bush_patch", -7654321L, 18, -52184915176898124L),
        new Golden("tfp:plant/dead_bush_patch", 123456789012345L, 41, 188033706226852780L),
        new Golden("tfp:plant/beachgrass_patch", 0L, 47, -1853218499135116423L),
        new Golden("tfp:plant/beachgrass_patch", 42L, 38, 8257732393316720334L),
        new Golden("tfp:plant/beachgrass_patch", -7654321L, 38, -1591714401395781926L),
        new Golden("tfp:plant/beachgrass_patch", 123456789012345L, 51, 6558481439332608690L),
        new Golden("tfp:plant/badderlocks_patch", 0L, 174, -5085704022064457042L),
        new Golden("tfp:plant/badderlocks_patch", 42L, 228, 8926174675996485455L),
        new Golden("tfp:plant/badderlocks_patch", -7654321L, 330, -1725506959663219555L),
        new Golden("tfp:plant/badderlocks_patch", 123456789012345L, 298, -4160166725018470352L),
        new Golden("tfp:plant/sea_lavender_patch", 0L, 60, -5789981044807433819L),
        new Golden("tfp:plant/sea_lavender_patch", 42L, 36, 678663275577127841L),
        new Golden("tfp:plant/sea_lavender_patch", -7654321L, 48, -7828109845592614418L),
        new Golden("tfp:plant/sea_lavender_patch", 123456789012345L, 74, 1136413775967745714L),
        new Golden("tfp:plant/prickly_pear_patch", 0L, 2, -2333379168244133506L),
        new Golden("tfp:plant/prickly_pear_patch", 42L, 0, -2112552280017865683L),
        new Golden("tfp:plant/prickly_pear_patch", -7654321L, 0, -7654193645042499902L),
        new Golden("tfp:plant/prickly_pear_patch", 123456789012345L, 6, 4021077902033819349L),
        new Golden("tfp:plant/silken_pincushion_cactus_patch", 0L, 7, -3067938213532715670L),
        new Golden("tfp:plant/silken_pincushion_cactus_patch", 42L, 39, 2046252914427139000L),
        new Golden("tfp:plant/silken_pincushion_cactus_patch", -7654321L, 32, -8316281753480435560L),
        new Golden("tfp:plant/silken_pincushion_cactus_patch", 123456789012345L, 19, 541469764807973987L),
        new Golden("tfp:plant/sea_palm_patch", 0L, 21, 7524117499160484353L),
        new Golden("tfp:plant/sea_palm_patch", 42L, 16, 2451686941424147387L),
        new Golden("tfp:plant/sea_palm_patch", -7654321L, 9, -560850474388710376L),
        new Golden("tfp:plant/sea_palm_patch", 123456789012345L, 3, 5563262860329748493L),
        new Golden("tfp:plant/wild_crop/wheat_patch", 0L, 0, 4007012089532712540L),
        new Golden("tfp:plant/wild_crop/wheat_patch", 42L, 0, 2727613002572815349L),
        new Golden("tfp:plant/wild_crop/wheat_patch", -7654321L, 0, 1217067829094294081L),
        new Golden("tfp:plant/wild_crop/wheat_patch", 123456789012345L, 0, -8168043098821284192L),
        new Golden("tfp:plant/wild_crop/maize_patch", 0L, 0, -2824087008060843497L),
        new Golden("tfp:plant/wild_crop/maize_patch", 42L, 6, 2499406155514697990L),
        new Golden("tfp:plant/wild_crop/maize_patch", -7654321L, 0, 1611210538189577678L),
        new Golden("tfp:plant/wild_crop/maize_patch", 123456789012345L, 0, -5844387536821387813L),
        new Golden("tfp:plant/wild_crop/pumpkin_patch", 0L, 0, 2277736146823197522L),
        new Golden("tfp:plant/wild_crop/pumpkin_patch", 42L, 0, 6748774535809102078L),
        new Golden("tfp:plant/wild_crop/pumpkin_patch", -7654321L, 2, 2546719371290738533L),
        new Golden("tfp:plant/wild_crop/pumpkin_patch", 123456789012345L, 0, 2099208034284041722L),
        new Golden("tfp:plant/wild_crop/rice_patch", 0L, 0, 5396449331621810028L),
        new Golden("tfp:plant/wild_crop/rice_patch", 42L, 0, 8187089800256573204L),
        new Golden("tfp:plant/wild_crop/rice_patch", -7654321L, 0, 7437758238515945156L),
        new Golden("tfp:plant/wild_crop/rice_patch", 123456789012345L, 2, -7340790027092055520L),
        new Golden("tfp:surface_grasses", 0L, 557, -2531123506162579579L),
        new Golden("tfp:surface_grasses", 42L, 549, -9187444900504451116L),
        new Golden("tfp:surface_grasses", -7654321L, 553, -8187574309298764109L),
        new Golden("tfp:surface_grasses", 123456789012345L, 565, 2128704341928107680L),
        new Golden("tfp:coral_tree", 0L, 712, -8488166051160998967L),
        new Golden("tfp:coral_tree", 42L, 708, 5635301747840261618L),
        new Golden("tfp:coral_tree", -7654321L, 698, -3995294162836520145L),
        new Golden("tfp:coral_tree", 123456789012345L, 747, -7865130206861543007L),
        new Golden("tfp:coral_mushroom", 0L, 3089, 6459464610017812074L),
        new Golden("tfp:coral_mushroom", 42L, 3045, -3058164457195107689L),
        new Golden("tfp:coral_mushroom", -7654321L, 3245, -8197964196909566026L),
        new Golden("tfp:coral_mushroom", 123456789012345L, 2847, 6949240071546069529L),
        new Golden("tfp:coral_claw", 0L, 678, 431820380026103596L),
        new Golden("tfp:coral_claw", 42L, 697, 2564229881028717732L),
        new Golden("tfp:coral_claw", -7654321L, 588, 4591115134215773649L),
        new Golden("tfp:coral_claw", 123456789012345L, 742, -8262576969575371896L));

    static final List<Golden> TRIES = List.of(
        new Golden("tfp:plant/bluegrass", 0L, 659, -3423607434782690639L),
        new Golden("tfp:plant/bluegrass", 42L, 659, -1043134865101603225L),
        new Golden("tfp:plant/bluegrass", -7654321L, 659, -9008889714595097488L),
        new Golden("tfp:plant/bluegrass", 123456789012345L, 659, -8515981468334709400L),
        new Golden("tfp:plant/allium", 0L, 659, 3542571793066190293L),
        new Golden("tfp:plant/allium", 42L, 659, -4038132405192260860L),
        new Golden("tfp:plant/allium", -7654321L, 659, -3448095862632485463L),
        new Golden("tfp:plant/allium", 123456789012345L, 659, 8837087127641752788L),
        new Golden("tfp:plant/foxglove", 0L, 1318, -214670340748823887L),
        new Golden("tfp:plant/foxglove", 42L, 1318, -5242468945868337235L),
        new Golden("tfp:plant/foxglove", -7654321L, 1318, -6898007757279134157L),
        new Golden("tfp:plant/foxglove", 123456789012345L, 1318, 4490471381826982534L),
        new Golden("tfp:plant/cattail", 0L, 88, 7817561772987878544L),
        new Golden("tfp:plant/cattail", 42L, 88, 352715478389891003L),
        new Golden("tfp:plant/cattail", -7654321L, 88, -8816601899860131412L),
        new Golden("tfp:plant/cattail", 123456789012345L, 88, -574746587944038675L),
        new Golden("tfp:plant/manatee_grass", 0L, 566, -8936005243354717981L),
        new Golden("tfp:plant/manatee_grass", 42L, 566, 5456774448597984131L),
        new Golden("tfp:plant/manatee_grass", -7654321L, 566, 459939479293385221L),
        new Golden("tfp:plant/manatee_grass", 123456789012345L, 566, -7046230024790048520L),
        new Golden("tfp:plant/duckweed", 0L, 299, 8988596419539091722L),
        new Golden("tfp:plant/duckweed", 42L, 299, -9154605710637455733L),
        new Golden("tfp:plant/duckweed", -7654321L, 299, 6361134780905468080L),
        new Golden("tfp:plant/duckweed", 123456789012345L, 299, 6751835967902550364L),
        new Golden("tfp:plant/rose", 0L, 720, 1441858535519058723L),
        new Golden("tfp:plant/rose", 42L, 720, -2646722900162031681L),
        new Golden("tfp:plant/rose", -7654321L, 720, -533439879643626288L),
        new Golden("tfp:plant/rose", 123456789012345L, 720, -2663184893262119361L),
        new Golden("tfp:plant/dead_bush", 0L, 846, -5696257774216018291L),
        new Golden("tfp:plant/dead_bush", 42L, 846, -8339020864942629106L),
        new Golden("tfp:plant/dead_bush", -7654321L, 846, -5682647122993217290L),
        new Golden("tfp:plant/dead_bush", 123456789012345L, 846, 8857462498902765283L),
        new Golden("tfp:plant/beachgrass", 0L, 115, 8288187114546572446L),
        new Golden("tfp:plant/beachgrass", 42L, 115, -8699348757279790311L),
        new Golden("tfp:plant/beachgrass", -7654321L, 115, 3117919464246625849L),
        new Golden("tfp:plant/beachgrass", 123456789012345L, 115, 5654426272678862028L),
        new Golden("tfp:plant/badderlocks", 0L, 916, 5899456201466691139L),
        new Golden("tfp:plant/badderlocks", 42L, 916, 7129414250801951203L),
        new Golden("tfp:plant/badderlocks", -7654321L, 916, 6850985149662200238L),
        new Golden("tfp:plant/badderlocks", 123456789012345L, 916, -7016933330602344950L),
        new Golden("tfp:plant/sea_lavender", 0L, 164, -7306329526830268493L),
        new Golden("tfp:plant/sea_lavender", 42L, 164, -8212042104624306631L),
        new Golden("tfp:plant/sea_lavender", -7654321L, 164, 6548533435694145578L),
        new Golden("tfp:plant/sea_lavender", 123456789012345L, 164, -3120632215672423486L),
        new Golden("tfp:plant/prickly_pear", 0L, 1254, -2418875460851776769L),
        new Golden("tfp:plant/prickly_pear", 42L, 1254, 5689350394171435623L),
        new Golden("tfp:plant/prickly_pear", -7654321L, 1254, 2306014655428303490L),
        new Golden("tfp:plant/prickly_pear", 123456789012345L, 1254, 1908463643275507960L),
        new Golden("tfp:plant/silken_pincushion_cactus", 0L, 1079, -8467561088728724057L),
        new Golden("tfp:plant/silken_pincushion_cactus", 42L, 1079, -1935493619465003739L),
        new Golden("tfp:plant/silken_pincushion_cactus", -7654321L, 1079, -4309154091375686368L),
        new Golden("tfp:plant/silken_pincushion_cactus", 123456789012345L, 1079, -3115442677256074246L),
        new Golden("tfp:plant/sea_palm", 0L, 846, 6375328106659460726L),
        new Golden("tfp:plant/sea_palm", 42L, 846, -6388812208161354701L),
        new Golden("tfp:plant/sea_palm", -7654321L, 846, -5858120214207966400L),
        new Golden("tfp:plant/sea_palm", 123456789012345L, 846, -7248939789580110669L),
        new Golden("tfp:plant/wild_crop/wheat", 0L, 659, 6119992323289536721L),
        new Golden("tfp:plant/wild_crop/wheat", 42L, 659, -7194243117628107064L),
        new Golden("tfp:plant/wild_crop/wheat", -7654321L, 659, -2254207051269084208L),
        new Golden("tfp:plant/wild_crop/wheat", 123456789012345L, 659, -6628822326355872815L),
        new Golden("tfp:plant/wild_crop/maize", 0L, 1318, 5507601313588474211L),
        new Golden("tfp:plant/wild_crop/maize", 42L, 1318, 5808010908703822260L),
        new Golden("tfp:plant/wild_crop/maize", -7654321L, 1318, 1510913607225762960L),
        new Golden("tfp:plant/wild_crop/maize", 123456789012345L, 1318, -3200113615738211859L),
        new Golden("tfp:plant/wild_crop/pumpkin", 0L, 326, 5619645369636861653L),
        new Golden("tfp:plant/wild_crop/pumpkin", 42L, 332, -1444923769432270297L),
        new Golden("tfp:plant/wild_crop/pumpkin", -7654321L, 304, -7596800815701106045L),
        new Golden("tfp:plant/wild_crop/pumpkin", 123456789012345L, 304, -1122714163693274863L),
        new Golden("tfp:plant/wild_crop/rice", 0L, 681, 4632912136049285746L),
        new Golden("tfp:plant/wild_crop/rice", 42L, 681, -3413787031735864692L),
        new Golden("tfp:plant/wild_crop/rice", -7654321L, 681, -6667561115987062480L),
        new Golden("tfp:plant/wild_crop/rice", 123456789012345L, 681, -2820206587853026608L));

    private static final PlantTestView VIEW = new PlantTestView();

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

    static String log(List<PlantBlock> blocks) {
        final StringBuilder sb = new StringBuilder();
        for (PlantBlock b : blocks) {
            final PlantState s = b.state();
            sb.append(b.x()).append(',').append(b.y()).append(',').append(b.z()).append(',').append(s.block()).append(',')
                .append(s.part()).append(',').append(s.age()).append(',').append(s.fluid()).append(',').append(s.extra()).append(';');
        }
        return sb.toString();
    }

    private static Set<String> replaceable() {
        return PlantTestData.data().blockTag(PlantState.NS + "single_block_replaceable");
    }

    private static Set<String> snowPileable() {
        return PlantTestData.data().blockTag(PlantState.NS + "can_be_snow_piled");
    }

    private static List<String> order(List<Golden> goldens) {
        return goldens.stream().map(Golden::feature).distinct().toList();
    }

    @Test
    void patchFeaturesMatchTfc() {
        final List<String> features = order(PATCHES);
        final Set<String> replaceable = replaceable();
        final Set<String> snow = snowPileable();
        for (Golden golden : PATCHES) {
            final int f = features.indexOf(golden.feature());
            final PlantFeature.Placed feature = PlantTestData.data().supported(golden.feature());
            assertThat(feature).as("%s is supported", golden.feature()).isNotNull();
            long hash = 0xcbf29ce484222325L;
            int total = 0;
            for (int cx = -8; cx < 8; cx++) {
                for (int cz = -8; cz < 8; cz++) {
                    final XoroshiroRandomSource r = rnd(golden.seed(), cx, cz, 20 + f);
                    final List<PlantBlock> blocks = Plants.run(feature, VIEW, replaceable, snow, r, cx << 4, -64, cz << 4);
                    hash = fnv(hash, cx + " " + cz + "|" + log(blocks) + "|" + r.nextLong() + "\n");
                    total += blocks.size();
                }
            }
            assertThat(total).as("%s, seed %d", golden.feature(), golden.seed()).isEqualTo(golden.placed());
            assertThat(hash).as("%s, seed %d", golden.feature(), golden.seed()).isEqualTo(golden.hash());
        }
    }

    @Test
    void perTryFeaturesMatchTfc() {
        final List<String> features = order(TRIES);
        final Set<String> replaceable = replaceable();
        final Set<String> snow = snowPileable();
        for (Golden golden : TRIES) {
            final int f = features.indexOf(golden.feature());
            final PlantFeature.Placed feature = PlantTestData.data().supported(golden.feature());
            assertThat(feature).as("%s is supported", golden.feature()).isNotNull();
            long hash = 0xcbf29ce484222325L;
            int total = 0;
            for (int x = -40; x < 40; x++) {
                for (int z = -40; z < 40; z += 3) {
                    final XoroshiroRandomSource r = rnd(golden.seed(), x, z, 60 + f);
                    final List<PlantBlock> blocks = Plants.run(feature, VIEW, replaceable, snow, r, x, 64, z);
                    hash = fnv(hash, x + " " + z + "|" + log(blocks) + "|" + r.nextLong() + "\n");
                    total += blocks.size();
                }
            }
            assertThat(total).as("%s, seed %d", golden.feature(), golden.seed()).isEqualTo(golden.placed());
            assertThat(hash).as("%s, seed %d", golden.feature(), golden.seed()).isEqualTo(golden.hash());
        }
    }

    @Test
    void biomeInfoNoiseMatchesVanilla() {
        long h = 0xcbf29ce484222325L;
        for (int x = -50; x < 50; x += 7) {
            for (int z = -50; z < 50; z += 9) {
                h = fnv(h, Double.doubleToLongBits(BiomeInfoNoise.INSTANCE.getValue(x / 160.0 * 37.3, z / 160.0 * 41.1)) + ";");
            }
        }
        assertThat(h).isEqualTo(NOISE_HASH);
        assertThat(BiomeInfoNoise.INSTANCE.getValue(1.5, -2.25)).isEqualTo(NOISE_A);
        assertThat(BiomeInfoNoise.INSTANCE.getValue(-100.125, 77.5)).isEqualTo(NOISE_B);
    }

    @Test
    void legacyRandomMatchesVanilla() {
        final LegacyRandomSource r = new LegacyRandomSource(2345L);
        assertThat(r.nextInt(256)).isEqualTo(LEGACY_INTS[0]);
        assertThat(r.nextInt(100)).isEqualTo(LEGACY_INTS[1]);
        assertThat(r.nextDouble()).isEqualTo(LEGACY_DOUBLE);
        assertThat(r.nextFloat()).isEqualTo(LEGACY_FLOAT);
        assertThat(r.nextInt(1 << 20)).isEqualTo(LEGACY_INTS[2]);
    }

    // vanilla 1.21.11 Biome.BIOME_INFO_NOISE and LegacyRandomSource(2345), printed by the oracle
    private static final long NOISE_HASH = -599807260556071607L;
    private static final double NOISE_A = 0.6686439070816858;
    private static final double NOISE_B = 0.30785666362544883;
    private static final int[] LEGACY_INTS = {239, 34, 723322};
    private static final double LEGACY_DOUBLE = 0.5615676641464233;
    private static final float LEGACY_FLOAT = 0.05435258f;
}
