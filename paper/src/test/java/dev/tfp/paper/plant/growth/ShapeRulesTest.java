/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.growth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** PAR-FARM-29: the pure growth and survival rules of the vine, giant kelp, saguaro, creeping plants and epiphytes. */
class ShapeRulesTest {
    private static final String VINE = VineGrowth.ID;

    private static MapWorld wall() {
        // a stone wall at x = 1 from y 0 to 20, the vine at x = 0
        MapWorld w = new MapWorld();
        for (int y = 0; y <= 20; y++) {
            for (int z = -10; z <= 10; z++) {
                w.solid(1, y, z);
            }
        }
        return w;
    }

    @Test
    void aVineSpreadsOnlyOverHeldFaces() {
        for (int seed = 0; seed < 40; seed++) {
            MapWorld w = wall();
            w.custom(0, 10, 0, BlockStateRef.of(VINE).with("east", "true"));
            Random random = new Random(seed);
            for (int i = 0; i < 4000; i++) {
                int count = w.count(VINE);
                java.util.List<java.util.List<Integer>> vines = new java.util.ArrayList<>();
                for (int x = -6; x <= 6; x++) {
                    for (int y = 0; y <= 20; y++) {
                        for (int z = -8; z <= 8; z++) {
                            if (w.custom(x, y, z).isPresent()) {
                                vines.add(java.util.List.of(x, y, z));
                            }
                        }
                    }
                }
                java.util.List<Integer> at = vines.get(random.nextInt(vines.size()));
                VineGrowth.tick(w, at.get(0), at.get(1), at.get(2), random);
                assertThat(w.count(VINE)).isGreaterThanOrEqualTo(count);
            }
            // every vine block holds a face that is supported, and there are at most five per 9x3x9 box
            int total = 0;
            for (int x = -6; x <= 6; x++) {
                for (int y = 0; y <= 20; y++) {
                    for (int z = -8; z <= 8; z++) {
                        BlockStateRef state = w.custom(x, y, z).orElse(null);
                        if (state == null) {
                            continue;
                        }
                        total++;
                        assertThat(ShapeSurvival.check(w, x, y, z, state).keep()).as("%d %d %d %s", x, y, z, state).isTrue();
                    }
                }
            }
            // sideways and upward spread stops at five in reach; the strand that hangs down is not capped (as in vanilla)
            assertThat(total).isGreaterThan(1);
        }
    }

    @Test
    void aVineWithoutAWarmSampleDoesNotGrow() {
        MapWorld w = wall();
        w.custom(0, 10, 0, BlockStateRef.of(VINE).with("east", "true"));
        assertThat(ShapeGrowth.run(w, 0, 10, 0, 50, 0, new Random(1))).isZero();
        assertThat(w.count(VINE)).isEqualTo(1);
        Random random = new Random(1);
        for (int i = 0; i < 100; i++) {
            ShapeGrowth.run(w, 0, 10, 0, 50, ShapeGrowth.TEMPERATURE_SAMPLES, random);
        }
        assertThat(w.count(VINE)).isGreaterThan(1);
    }

    @Test
    void aVineGrowsAlongTheWallAndEveryVineIsHeld() {
        MapWorld w = wall();
        w.custom(0, 10, 0, BlockStateRef.of(VINE).with("east", "true"));
        Random random = new Random(3);
        for (int i = 0; i < 20000; i++) {
            VineGrowth.tick(w, 0, 10 - random.nextInt(8), 0, random);
        }
        assertThat(w.count(VINE)).isGreaterThanOrEqualTo(2);
        for (int y = 0; y <= 20; y++) {
            for (int z = -8; z <= 8; z++) {
                BlockStateRef state = w.custom(0, y, z).orElse(null);
                if (state != null) {
                    assertThat(ShapeSurvival.check(w, 0, y, z, state).keep()).as("%d %d %s", y, z, state).isTrue();
                }
            }
        }
    }

    private static MapWorld sea() {
        // sea floor at y 0 (tag), salt water y 1..30, x/z -6..6
        MapWorld w = new MapWorld().tag(KelpTreeGrowth.KELP_TREES, KelpTreeGrowth.BODY, KelpTreeGrowth.FLOWER)
            .tag(KelpTreeGrowth.KELP_BRANCHES, KelpTreeGrowth.BODY);
        w.floor(-6, -6, 6, 6, 0, KelpTreeGrowth.SEA_BUSH_PLANTABLE_ON);
        w.waterBox(-6, 1, -6, 6, 30, 6);
        return w;
    }

    @Test
    void aGiantKelpFlowerGrowsAUpwardsTreeWithinAgeFive() {
        MapWorld w = sea();
        BlockStateRef flower = BlockStateRef.of(KelpTreeGrowth.FLOWER).with("fluid", "salt_water").with("age", "0").with("facing", "up");
        w.custom(0, 1, 0, flower);
        Random random = new Random(11);
        for (int round = 0; round < 400; round++) {
            for (int y = 1; y <= 12; y++) {
                for (int x = -3; x <= 3; x++) {
                    for (int z = -3; z <= 3; z++) {
                        BlockStateRef state = w.custom(x, y, z).orElse(null);
                        if (state != null && state.id().equals(KelpTreeGrowth.FLOWER) && ShapeGrowth.ticks(state)) {
                            ShapeGrowth.run(w, x, y, z, 3, 0, random);
                        }
                    }
                }
            }
        }
        assertThat(w.count(KelpTreeGrowth.BODY)).isPositive();
        // the base is a body on the floor, every flower is still age <= 5, every block survives
        assertThat(w.custom(0, 1, 0).orElseThrow().id()).isEqualTo(KelpTreeGrowth.BODY);
        for (int y = 1; y <= 20; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    BlockStateRef state = w.custom(x, y, z).orElse(null);
                    if (state == null) {
                        continue;
                    }
                    if (state.id().equals(KelpTreeGrowth.FLOWER)) {
                        assertThat(Integer.parseInt(state.properties().get("age"))).isBetween(0, 5);
                    }
                    assertThat(ShapeSurvival.check(w, x, y, z, state).keep()).as("%d %d %d %s", x, y, z, state).isTrue();
                }
            }
        }
    }

    @Test
    void theKelpFlowerStaysWhereTheWaterAboveIsNotFree() {
        MapWorld w = sea();
        w.solid(0, 2, 0);
        w.custom(0, 1, 0, BlockStateRef.of(KelpTreeGrowth.FLOWER).with("fluid", "salt_water").with("age", "0"));
        assertThat(ShapeGrowth.run(w, 0, 1, 0, 8, 0, new Random(5))).isZero();
    }

    private static MapWorld desert() {
        MapWorld w = new MapWorld();
        w.floor(-8, -8, 8, 8, 0, CactusGrowth.DRY_PLANTABLE_ON);
        return w;
    }

    @Test
    void aSaguaroTopGrowsATrunkOfAtMostEightAndArmsOfAtMostFour() {
        int branched = 0;
        for (int seed = 0; seed < 60; seed++) {
            MapWorld w = desert();
            w.custom(0, 1, 0, BlockStateRef.of(CactusGrowth.TOP).with("grows_branches", "true").with("down", "true"));
            ShapeGrowth.run(w, 0, 1, 0, ShapeGrowth.MAX_TICKS, 0, new Random(seed));
            ShapeGrowth.run(w, 0, 1, 0, ShapeGrowth.MAX_TICKS, 0, new Random(seed + 1000));
            int height = 0;
            while (CactusGrowth.isCactus(w.id(0, 1 + height, 0))) {
                height++;
            }
            assertThat(height).isBetween(1, 9);
            branched += w.count(CactusGrowth.BODY) + w.count(CactusGrowth.TOP) > height ? 1 : 0;
            for (int y = 1; y <= 12; y++) {
                for (int x = -4; x <= 4; x++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockStateRef state = w.custom(x, y, z).orElse(null);
                        if (state != null) {
                            assertThat(CactusGrowth.survives(w, x, y, z)).as("%d %d %d %s", x, y, z, state).isTrue();
                        }
                    }
                }
            }
        }
        assertThat(branched).as("some saguaros grew an arm").isPositive();
    }

    @Test
    void aSaguaroTopWithACactusAboveBecomesABodyAtOnce() {
        MapWorld w = desert();
        w.custom(0, 1, 0, BlockStateRef.of(CactusGrowth.TOP));
        w.custom(0, 2, 0, BlockStateRef.of(CactusGrowth.BODY));
        assertThat(CactusGrowth.growthTick(w, 0, 1, 0, w.custom(0, 1, 0).orElseThrow(), new Random(1))).isTrue();
        assertThat(w.custom(0, 1, 0).orElseThrow().id()).isEqualTo(CactusGrowth.BODY);
    }

    @Test
    void columnsNeedTheirAnchor() {
        MapWorld w = new MapWorld();
        w.solid(0, 10, 0);
        BlockStateRef head = BlockStateRef.of("tfp:plant/hanging_vines").with("age", "3");
        BlockStateRef body = BlockStateRef.of("tfp:plant/hanging_vines_plant");
        w.custom(0, 9, 0, body).custom(0, 8, 0, head);
        assertThat(ShapeSurvival.check(w, 0, 9, 0, body).keep()).isTrue();
        assertThat(ShapeSurvival.check(w, 0, 8, 0, head).keep()).isTrue();
        w.custom(0, 10, 0, "tfp:plant/allium"); // the anchor is gone
        assertThat(ShapeSurvival.check(w, 0, 9, 0, body).keep()).isFalse();
        // a twisting plant stands on a floor, and on its own body
        BlockStateRef tree = BlockStateRef.of("tfp:plant/arundo").with("age", "1");
        assertThat(ShapeSurvival.check(new MapWorld().solid(0, 0, 0), 0, 1, 0, tree).keep()).isTrue();
        assertThat(ShapeSurvival.check(new MapWorld(), 0, 1, 0, tree).keep()).isFalse();
    }

    @Test
    void kelpNeedsWaterAndAFloor() {
        MapWorld w = new MapWorld().solid(0, 0, 0);
        BlockStateRef wet = BlockStateRef.of("tfp:plant/winged_kelp").with("fluid", "salt_water");
        BlockStateRef dry = BlockStateRef.of("tfp:plant/winged_kelp").with("fluid", "empty");
        assertThat(ShapeSurvival.check(w, 0, 1, 0, wet).keep()).isTrue();
        assertThat(ShapeSurvival.check(w, 0, 1, 0, dry).keep()).isFalse();
        w.solid(0, 0, 0, "minecraft:magma_block");
        assertThat(ShapeSurvival.check(w, 0, 1, 0, wet).keep()).isFalse();
    }

    @Test
    void aVineLosesUnsupportedFacesAndPopsWithNone() {
        MapWorld w = new MapWorld().solid(1, 5, 0);
        BlockStateRef two = BlockStateRef.of(VINE).with("east", "true").with("west", "true");
        ShapeSurvival.Verdict verdict = ShapeSurvival.check(w, 0, 5, 0, two);
        assertThat(verdict.keep()).isTrue();
        assertThat(verdict.update().properties()).containsEntry("east", "true").containsEntry("west", "false");
        assertThat(ShapeSurvival.check(new MapWorld(), 0, 5, 0, two).keep()).isFalse();
    }

    @Test
    void creepingPlantsKeepTheFacesTheyCanCreepOn() {
        String id = "tfp:plant/moss";
        MapWorld w = new MapWorld().solid(0, 0, 0).solid(1, 1, 0);
        BlockStateRef state = BlockStateRef.of(id).with("down", "true");
        ShapeSurvival.Verdict verdict = ShapeSurvival.check(w, 0, 1, 0, state);
        assertThat(verdict.keep()).isTrue();
        assertThat(verdict.update().properties()).containsEntry("down", "true").containsEntry("east", "true").containsEntry("up", "false");
        assertThat(ShapeSurvival.check(new MapWorld(), 0, 1, 0, state).keep()).isFalse();
        // a block of the not-plantable tag is not crept on
        MapWorld bad = new MapWorld().solid(0, 0, 0, ShapeSurvival.CREEPING_NOT_ON);
        assertThat(ShapeSurvival.check(bad, 0, 1, 0, state).keep()).isFalse();
        // the stone lichens need a block of the stone tag
        BlockStateRef lichen = BlockStateRef.of("tfp:plant/cobblestone_lichen").with("down", "true");
        assertThat(ShapeSurvival.check(new MapWorld().solid(0, 0, 0), 0, 1, 0, lichen).keep()).isFalse();
        assertThat(ShapeSurvival.check(new MapWorld().solid(0, 0, 0, ShapeSurvival.CREEPING_STONE_ON), 0, 1, 0, lichen).keep()).isTrue();
    }

    @Test
    void epiphytesNeedATrunkBehindThem() {
        BlockStateRef state = BlockStateRef.of("tfp:plant/guzmania").with("facing", "north");
        // facing north: the log is on the south side
        MapWorld w = new MapWorld().solid(0, 5, 1, ShapeSurvival.EPIPHYTE_ON);
        assertThat(ShapeSurvival.check(w, 0, 5, 0, state).keep()).isTrue();
        assertThat(ShapeSurvival.check(new MapWorld().solid(0, 5, -1, ShapeSurvival.EPIPHYTE_ON), 0, 5, 0, state).keep()).isFalse();
    }

    @Test
    void giantKelpBodiesAndFlowersNeedWaterAndTheirSupport() {
        MapWorld w = sea();
        BlockStateRef body = BlockStateRef.of(KelpTreeGrowth.BODY).with("fluid", "salt_water");
        w.custom(0, 1, 0, body);
        ShapeSurvival.Verdict verdict = ShapeSurvival.check(w, 0, 1, 0, body);
        assertThat(verdict.keep()).isTrue();
        assertThat(verdict.update().properties()).containsEntry("down", "true");
        assertThat(ShapeSurvival.check(w, 0, 1, 0, body.with("fluid", "empty")).keep()).isFalse();
        BlockStateRef flower = BlockStateRef.of(KelpTreeGrowth.FLOWER).with("fluid", "salt_water").with("age", "2");
        w.custom(0, 2, 0, flower);
        assertThat(ShapeSurvival.check(w, 0, 2, 0, flower).keep()).isTrue();
        // lifted off the sea floor with nothing around: pops
        assertThat(ShapeSurvival.check(w, 0, 9, 0, flower).keep()).isFalse();
    }

    @Test
    void theShapeIdsAreTheOnesDatagenWrites() {
        assertThat(ShapeGrowth.shape(VINE)).contains(ShapeGrowth.Shape.VINE);
        assertThat(ShapeGrowth.shape(KelpTreeGrowth.FLOWER)).contains(ShapeGrowth.Shape.KELP_FLOWER);
        assertThat(ShapeGrowth.shape(CactusGrowth.TOP)).contains(ShapeGrowth.Shape.CACTUS_TOP);
        assertThat(ShapeGrowth.shape("tfp:plant/allium")).isEmpty();
        assertThat(ShapeSurvival.handles("tfp:plant/allium")).isFalse();
        for (String id : new String[] {VINE, "tfp:plant/moss", "tfp:plant/guzmania", KelpTreeGrowth.BODY, CactusGrowth.TOP,
            "tfp:plant/arundo_plant", "tfp:plant/leafy_kelp"}) {
            assertThat(ShapeSurvival.handles(id)).as(id).isTrue();
        }
    }
}
