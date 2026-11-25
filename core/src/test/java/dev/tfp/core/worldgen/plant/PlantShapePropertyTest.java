/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.plant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import dev.tfp.core.worldgen.tree.ForestType;
import dev.tfp.core.worldgen.tree.TreeGround;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The placement parts of PAR-WORLD-13 that {@link PlantShapeGoldenTest} does not run: the new modifiers and the data gate. */
class PlantShapePropertyTest {
    private static final Set<String> NONE = Set.of();

    private static PlantFeature.Configured configured(String id) {
        final PlantFeature.Placed placed = PlantTestData.data().supported(id);
        assertThat(placed).as(id).isNotNull();
        return placed.feature();
    }

    @Test
    void parsesTheNewModifiers() {
        final PlantFeature.Placed arundoForest = PlantTestData.data().supported("tfp:plant/arundo_forest");
        assertThat(arundoForest.modifiers()).anySatisfy(m -> assertThat(m).isInstanceOfSatisfying(PlantFeature.Climate.class,
            c -> assertThat(c.forestTypes()).containsExactlyInAnyOrder(ForestType.DEAD_BAMBOO, ForestType.EDGE_BAMBOO,
                ForestType.SECONDARY_BAMBOO)));
        assertThat(PlantTestData.data().supported("tfp:plant/arundo").modifiers()).contains(
            new PlantFeature.NearFluid(6, EnumSet.of(PlantState.Fluid.WATER, PlantState.Fluid.SALT_WATER)));
        assertThat(PlantTestData.data().supported("tfp:plant/jungle_vines").modifiers()).contains(new PlantFeature.HeightRange(48, 110));
        assertThat(PlantTestData.data().supported("tfp:plant/liana").modifiers()).contains(new PlantFeature.CarvingMask(30, 100));
        assertThat(PlantTestData.data().supported("tfp:atoll_coral_tree").modifiers()).contains(new PlantFeature.Atoll(0.3f, 0.42f));
        assertThat(PlantTestData.data().supported("tfp:plant/dry_phragmite").modifiers()).contains(
            new PlantFeature.Filter(new PlantFeature.DryReplaceable()));
        // dual_noise_provider of one state: the floating plant itself
        assertThat(configured("tfp:plant/green_algae")).isInstanceOfSatisfying(PlantFeature.SimpleBlock.class,
            s -> assertThat(s.state().block()).isEqualTo("tfp:plant/green_algae"));
    }

    @Test
    void heightRangeDrawsInItsBounds() {
        final PlantFeature.Placed placed = new PlantFeature.Placed("t", List.of(new PlantFeature.HeightRange(48, 110)),
            configured("tfp:plant/jungle_vines"));
        final XoroshiroRandomSource random = new XoroshiroRandomSource(7L);
        int any = 0;
        for (int i = 0; i < 400; i++) {
            for (PlantBlock b : Plants.run(placed, new ShapeTestView(), NONE, NONE, random, 3 * i, -64, 5 * i)) {
                assertThat(b.y()).isLessThanOrEqualTo(110);
                any++;
            }
        }
        assertThat(any).isPositive();
    }

    @Test
    void carvingMaskRunsOnCarvedAirOnly() {
        final PlantFeature.Placed placed = new PlantFeature.Placed("t", List.of(new PlantFeature.CarvingMask(30, 100)),
            configured("tfp:plant/liana"));
        final ShapeTestView view = new ShapeTestView();
        final List<PlantBlock> blocks = Plants.run(placed, view, NONE, NONE, new XoroshiroRandomSource(3L), 0, -64, 0);
        assertThat(blocks).isNotEmpty();
        for (PlantBlock b : blocks) {
            assertThat(view.ground(b.x(), b.y(), b.z())).as("%s", b).isEqualTo(TreeGround.AIR);
        }
        // A view without a carving mask has no positions
        assertThat(Plants.run(placed, new PlantTestView(), NONE, NONE, new XoroshiroRandomSource(3L), 0, -64, 0)).isEmpty();
    }

    @Test
    void atollEasingOnlyInAtollBiomes() {
        final Atolls atolls = new Atolls(12345L);
        int inside = 0;
        int total = 0;
        for (int x = -6000; x < 6000; x += 24) {
            for (int z = -6000; z < 6000; z += 24) {
                total++;
                if (atolls.test(TfcBiomes.OCEAN_ATOLLS, x, z, 0.3f, 0.42f)) {
                    inside++;
                    assertThat(atolls.test(TfcBiomes.OCEAN, x, z, 0.3f, 0.42f)).isFalse();
                }
            }
        }
        assertThat(inside).isPositive().isLessThan(total / 4);
    }

    @Test
    void writerGateLeavesOutUnwrittenTypes() {
        final PlantData gated = PlantData.of(PlantTestData.reader(), EnumSet.of(PlantType.STANDARD, PlantType.FLOATING_FRESH));
        assertThat(gated.supported("tfp:plant/allium_patch")).isNotNull();
        assertThat(gated.supported("tfp:plant/green_algae_patch")).isNotNull();
        assertThat(gated.supported("tfp:plant/moss_cover")).isNull();
        assertThat(gated.skipped().get("tfp:plant/moss_cover")).contains("not written yet");
    }

    @Test
    void barrelCactusNeedsFreeSidesAndDryGround() {
        final PlantLevel level = new PlantLevel(new PlantTestView(), NONE, NONE);
        final PlantState lower = PlantTestData.data().blockState("tfp:plant/barrel_cactus").withPart(PlantState.Part.LOWER);
        int survived = 0;
        for (int x = -30; x < 30; x++) {
            for (int z = -30; z < 30; z++) {
                final int y = Math.max(PlantTestView.top(x, z), 63) + 1;
                final boolean expected = PlantType.dryPlantableOn(new PlantTestView().ground(x, y - 1, z))
                    && !level.solid(x + 1, y, z) && !level.solid(x - 1, y, z) && !level.solid(x, y, z + 1) && !level.solid(x, y, z - 1);
                assertThat(level.canSurvive(lower, x, y, z)).isEqualTo(expected);
                survived += expected ? 1 : 0;
            }
        }
        assertThat(survived).isPositive();
    }
}
