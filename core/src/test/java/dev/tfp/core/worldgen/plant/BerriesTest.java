/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/plant/SpreadingBushFeature.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.plant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-FARM-18/19: the berry bush features of the exported data, the bush survival rules and {@code SpreadingBushFeature}. */
class BerriesTest {
    private static final PlantFeature.SpreadingBush BLACKBERRY = new PlantFeature.SpreadingBush(
        PlantState.of("tfp:spreading_bush/blackberry", PlantState.Kind.SPREADING_BUSH, null).withAge(0));

    @Test
    void bushIdsMapToTheirCraftEngineBlocks() {
        assertThat(Berries.of("tfp:plant/bunchberry_bush")).isEqualTo(new Berries.Bush("tfp:berry_bush/bunchberry", PlantState.Kind.BERRY_BUSH));
        assertThat(Berries.of("tfp:plant/wintergreen_berry_bush"))
            .isEqualTo(new Berries.Bush("tfp:berry_bush/wintergreen_berry", PlantState.Kind.BERRY_BUSH));
        assertThat(Berries.of("tfp:plant/raspberry_bush"))
            .isEqualTo(new Berries.Bush("tfp:spreading_bush/raspberry", PlantState.Kind.SPREADING_BUSH));
        assertThat(Berries.of("tfp:plant/cranberry_bush"))
            .isEqualTo(new Berries.Bush("tfp:spreading_bush/cranberry", PlantState.Kind.BERRY_BUSH));
        assertThat(Berries.of("tfp:plant/dead_bush")).isNull();
        assertThat(Berries.of("tfp:plant/allium")).isNull();
        assertThat(Berries.cane("tfp:spreading_bush/blackberry")).isEqualTo("tfp:spreading_bush/blackberry_cane");
    }

    @Test
    void everyBushFeatureOfTheExportedDataLoads() {
        final PlantData data = PlantTestData.data();
        for (String species : List.of("bunchberry", "cloudberry", "gooseberry", "snowberry", "strawberry", "wintergreen_berry",
            "blackberry", "blueberry", "raspberry", "elderberry", "cranberry")) {
            assertThat(data.supported("tfp:plant/" + species + "_bush")).as(species).isNotNull();
        }
        for (String patch : List.of("bunchberry", "cloudberry", "cranberry", "gooseberry", "snowberry", "strawberry", "wintergreen_berry")) {
            assertThat(data.supported("tfp:plant/" + patch + "_bush_patch")).as(patch + " patch").isNotNull();
        }
        assertThat(data.skipped().keySet()).noneMatch(id -> id.contains("berry") && id.endsWith("bush") || id.contains("_bush_patch"));
    }

    @Test
    void theCranberryHoldsFreshWaterAndSurvivesOnSeaBushGround() {
        final PlantState cranberry = PlantTestData.data().supported("tfp:plant/cranberry_bush") == null ? null
            : ((PlantFeature.SimpleBlock) PlantTestData.data().supported("tfp:plant/cranberry_bush").feature()).state();
        assertThat(cranberry).isNotNull();
        assertThat(cranberry.kind()).isEqualTo(PlantState.Kind.BERRY_BUSH);
        assertThat(Berries.cranberry(cranberry)).isTrue();
        assertThat(PlantLevel.fill(cranberry, PlantState.Fluid.WATER).fluid()).isEqualTo(PlantState.Fluid.WATER);
        assertThat(PlantLevel.fill(cranberry, PlantState.Fluid.SALT_WATER)).isNull();
    }

    /** The structure {@code SpreadingBushFeature} makes, over many origins of the synthetic terrain. */
    @Test
    void spreadingBushColumnsCountDownToTheirTopAndCanesPointAtTheirBush() {
        final PlantTestView view = new PlantTestView();
        int columns = 0;
        int canes = 0;
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                final PlantLevel level = new PlantLevel(view, Set.of(), Set.of());
                final XoroshiroRandomSource random = new XoroshiroRandomSource(31L * x + 7L * z);
                final int y = Math.max(PlantTestView.top(x, z), 63) + 1;
                if (!Berries.place(BLACKBERRY, level, random, x, y, z)) {
                    continue;
                }
                for (PlantBlock b : level.blocks()) {
                    final PlantState s = b.state();
                    if (s.kind() == PlantState.Kind.SPREADING_BUSH) {
                        assertThat(s.age()).isBetween(0, 2);
                        final PlantState below = level.placed(b.x(), b.y() - 1, b.z());
                        if (below == null) {
                            // the bottom of a column: on bush plantable ground
                            assertThat(level.view().ground(b.x(), b.y() - 1, b.z()).bushPlantableOn()).isTrue();
                            columns++;
                        } else {
                            assertThat(below.kind()).isEqualTo(PlantState.Kind.SPREADING_BUSH);
                            assertThat(below.age()).isEqualTo(s.age() + 1);
                        }
                    } else if (s.kind() == PlantState.Kind.SPREADING_CANE) {
                        canes++;
                        // a cane stands beside a bush of stage above 0, at the side its facing points to
                        final int d = s.extra();
                        final PlantState bush = level.placed(b.x() - PlantLevel.DX[d], b.y(), b.z() - PlantLevel.DZ[d]);
                        assertThat(bush).isNotNull();
                        assertThat(bush.kind()).isEqualTo(PlantState.Kind.SPREADING_BUSH);
                        assertThat(bush.age()).isGreaterThan(0);
                    }
                }
            }
        }
        assertThat(columns).isGreaterThan(20);
        assertThat(canes).isGreaterThan(5);
    }

    @Test
    void theFeatureIsDeterministic() {
        final PlantTestView view = new PlantTestView();
        final List<Map<Long, PlantState>> runs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            final PlantLevel level = new PlantLevel(view, Set.of(), Set.of());
            final XoroshiroRandomSource random = new XoroshiroRandomSource(99L);
            for (int x = 0; x < 20; x++) {
                Berries.place(BLACKBERRY, level, random, x, Math.max(PlantTestView.top(x, 3), 63) + 1, 3);
            }
            final Map<Long, PlantState> m = new java.util.TreeMap<>();
            level.blocks().forEach(b -> m.put(PlantLevel.key(b.x(), b.y(), b.z()), b.state()));
            runs.add(m);
        }
        assertThat(runs.get(0)).isNotEmpty().isEqualTo(runs.get(1));
    }

    @Test
    void forestBiomesGetBushesOfBothKinds() {
        final Plants plants = new Plants(20260517L, PlantTestData.data());
        final PlantTestView view = new PlantTestView(TfcBiomes.PLAINS);
        final Set<PlantState.Kind> kinds = new java.util.HashSet<>();
        final Set<String> blocks = new java.util.HashSet<>();
        for (int cx = -12; cx < 12; cx++) {
            for (int cz = -12; cz < 12; cz++) {
                plants.place(cx, cz, view, Plants.Step.SURFACE_DECORATION, b -> {
                    if (b.state().kind() == PlantState.Kind.BERRY_BUSH || b.state().kind() == PlantState.Kind.SPREADING_BUSH
                        || b.state().kind() == PlantState.Kind.SPREADING_CANE) {
                        kinds.add(b.state().kind());
                        blocks.add(b.state().block());
                    }
                });
            }
        }
        assertThat(kinds).contains(PlantState.Kind.BERRY_BUSH);
        assertThat(blocks).allMatch(id -> id.startsWith("tfp:berry_bush/") || id.startsWith("tfp:spreading_bush/"));
    }
}
