/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.plant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Golden values of TFC's plant shape features (PAR-WORLD-13) at the pinned commit (c3b4d9536): the configured features alone,
 * run at a grid of origins of {@link ShapeTestView}.
 *
 * <p>The expected values came from a scratch oracle (kept outside the repository) holding TFC's own code of
 * {@code CreepingPlantFeature}, {@code CreepingOceanPlantFeature}, {@code RotatableWaterPlantFeature},
 * {@code EpiphytePlantFeature}, {@code TFCTwistingVinesFeature}, {@code TFCWeepingVinesFeature}, {@code TFCKelpFeature},
 * {@code TFCVinesFeature}, {@code GiantKelpFeature} with {@code KelpTreeFlowerBlock.generatePlant/growTreeRecursive} and
 * {@code KelpTreeBlock}/{@code PipePlantBlock.getStateForPlacement}, and {@code GrowingBranchingCactusBlock.growRecursively},
 * with the {@code canSurvive}/{@code updateStateFromSides} of the blocks they place, transliterated against stub Minecraft
 * types (BlockPos, Direction with {@code allShuffled} and {@code Plane.getRandomDirection} as in 1.21.11, BlockState, a level
 * that records writes in first-set order and drops a write of the terrain's own block). No Minecraft jar was available to
 * the worker (PROTOCOL: goldens built from TFC source by hand), so the random is the port's {@link XoroshiroRandomSource},
 * which other golden tests check against vanilla; the tags are those of the generated data, read independently of the port's
 * helpers. Each case seeds {@code rnd(seed, x, z, 100 + f)} for feature number {@code f} and the region random of the
 * saguaro {@code rnd(seed, x, z, 500 + f)}, runs at column {@code (x, z)} for x in [-48, 48) step 7 and z in [-48, 48) step 5
 * from the ocean floor (ocean plants) or one above {@code max(top, 63)} (the others; the epiphyte 6 lower, the vines 3
 * higher), and hashes (FNV-1a 64) {@code "x z|<blocks>|<next long>|<next region long>"} per column.
 */
class PlantShapeGoldenTest {
    record Golden(String feature, long seed, int placed, long hash) {}

    static final List<Golden> GOLDENS = List.of(
        new Golden("tfp:plant/moss_cover", 0L, 24887, 1259039062885672281L),
        new Golden("tfp:plant/moss_cover", 42L, 24894, 5817626914885098237L),
        new Golden("tfp:plant/moss_cover", -7654321L, 24923, 978576741659545927L),
        new Golden("tfp:plant/cobblestone_lichen_patch", 0L, 3089, 1012346453668924921L),
        new Golden("tfp:plant/cobblestone_lichen_patch", 42L, 3075, -7168576515143621295L),
        new Golden("tfp:plant/cobblestone_lichen_patch", -7654321L, 3172, 496150048982691928L),
        new Golden("tfp:plant/anemone_green_patch", 0L, 665, -8950780944899926876L),
        new Golden("tfp:plant/anemone_green_patch", 42L, 693, -3653840122746274649L),
        new Golden("tfp:plant/anemone_green_patch", -7654321L, 705, -7890795082320144999L),
        new Golden("tfp:plant/barnacles_patch", 0L, 878, -6851855920150789520L),
        new Golden("tfp:plant/barnacles_patch", 42L, 875, -9157775605906311768L),
        new Golden("tfp:plant/barnacles_patch", -7654321L, 884, 4892234135201515776L),
        new Golden("tfp:plant/starfish_patch", 0L, 42, 771296323339722275L),
        new Golden("tfp:plant/starfish_patch", 42L, 48, -908807537248732322L),
        new Golden("tfp:plant/starfish_patch", -7654321L, 51, 1746200498903262925L),
        new Golden("tfp:plant/guzmania", 0L, 48, -8435741818239356459L),
        new Golden("tfp:plant/guzmania", 42L, 54, -6480362905686030023L),
        new Golden("tfp:plant/guzmania", -7654321L, 51, -4839742886329882955L),
        new Golden("tfp:plant/arundo", 0L, 121, -5932034416368422178L),
        new Golden("tfp:plant/arundo", 42L, 82, -7059245877545654057L),
        new Golden("tfp:plant/arundo", -7654321L, 78, 4763124146783680637L),
        new Golden("tfp:plant/tree_fern", 0L, 55, -2009742248274889168L),
        new Golden("tfp:plant/tree_fern", 42L, 84, 4074102751867223767L),
        new Golden("tfp:plant/tree_fern", -7654321L, 101, 7444474250005329418L),
        new Golden("tfp:plant/hanging_vines", 0L, 3436, 1135215984838150121L),
        new Golden("tfp:plant/hanging_vines", 42L, 3498, 7723437452739101026L),
        new Golden("tfp:plant/hanging_vines", -7654321L, 3274, 6756457114571228359L),
        new Golden("tfp:plant/winged_kelp", 0L, 17068, 4134485381799965524L),
        new Golden("tfp:plant/winged_kelp", 42L, 16455, -4704683655841587070L),
        new Golden("tfp:plant/winged_kelp", -7654321L, 16425, 603980016137648669L),
        new Golden("tfp:plant/jungle_vines", 0L, 454, 8067110532675693810L),
        new Golden("tfp:plant/jungle_vines", 42L, 438, 249647793840314706L),
        new Golden("tfp:plant/jungle_vines", -7654321L, 441, 8029789543925029939L),
        new Golden("tfp:plant/giant_kelp", 0L, 447, 951001523242963660L),
        new Golden("tfp:plant/giant_kelp", 42L, 414, 627493274219878392L),
        new Golden("tfp:plant/giant_kelp", -7654321L, 479, -7857310458079873912L),
        new Golden("tfp:plant/saguaro", 0L, 4441, -5057208265545258743L),
        new Golden("tfp:plant/saguaro", 42L, 4493, 6058839777494677971L),
        new Golden("tfp:plant/saguaro", -7654321L, 4509, -2613987435311572472L));

    /** The cases run from the ocean floor; the giant kelp also in the all-salt ocean view. */
    private static final Set<String> FLOOR = Set.of("tfp:plant/anemone_green_patch", "tfp:plant/barnacles_patch",
        "tfp:plant/starfish_patch", "tfp:plant/winged_kelp", "tfp:plant/giant_kelp");

    private static int offset(String feature) {
        return switch (feature) {
            case "tfp:plant/guzmania" -> -6;
            case "tfp:plant/jungle_vines" -> 3;
            default -> 0;
        };
    }

    @Test
    void shapeFeaturesMatchTfc() {
        final List<String> features = GOLDENS.stream().map(Golden::feature).distinct().toList();
        final Set<String> replaceable = PlantTestData.data().blockTag(PlantState.NS + "single_block_replaceable");
        final Set<String> snow = PlantTestData.data().blockTag(PlantState.NS + "can_be_snow_piled");
        for (Golden golden : GOLDENS) {
            final int f = features.indexOf(golden.feature());
            final PlantFeature.Placed placed = PlantTestData.data().supported(golden.feature());
            assertThat(placed).as("%s is supported", golden.feature()).isNotNull();
            final PlantFeature.Placed configured = new PlantFeature.Placed(golden.feature(), List.of(), placed.feature());
            final ShapeTestView view = new ShapeTestView(golden.feature().equals("tfp:plant/giant_kelp"));
            final boolean floor = FLOOR.contains(golden.feature());
            long hash = 0xcbf29ce484222325L;
            int total = 0;
            for (int x = -48; x < 48; x += 7) {
                for (int z = -48; z < 48; z += 5) {
                    final XoroshiroRandomSource r = PlantGoldenTest.rnd(golden.seed(), x, z, 100 + f);
                    final XoroshiroRandomSource region = PlantGoldenTest.rnd(golden.seed(), x, z, 500 + f);
                    final int y = (floor ? view.oceanFloor(x, z) : Math.max(PlantTestView.top(x, z), 63) + 1) + offset(golden.feature());
                    final List<PlantBlock> blocks = Plants.run(configured, view, replaceable, snow, r, region, null, x, y, z);
                    hash = PlantGoldenTest.fnv(hash, x + " " + z + "|" + PlantGoldenTest.log(blocks) + "|" + r.nextLong() + "|"
                        + region.nextLong() + "\n");
                    total += blocks.size();
                }
            }
            assertThat(total).as("%s, seed %d", golden.feature(), golden.seed()).isEqualTo(golden.placed());
            assertThat(hash).as("%s, seed %d", golden.feature(), golden.seed()).isEqualTo(golden.hash());
        }
    }
}
