/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/*.java @ c3b4d9536 (canSurvive, stacking, growth)
 */
package dev.tfp.core.plant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.plant.PlantSurvival.Below;
import dev.tfp.core.worldgen.plant.PlantState;
import dev.tfp.core.worldgen.plant.PlantType;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-FARM-12 core rules: the survival table of the plant shapes (read from the TFC classes), stacking, contact and lazy growth. */
class PlantRulesTest {
    private static Below tags(String... tags) {
        Set<String> set = Set.of(tags);
        return Below.of(set::contains);
    }

    @Test
    void lowerHalvesSurviveOnTheirTagOnly() {
        // type, the tag that supports it (TFCBushBlock / ShortGrassBlock / createDry / TFCCactusBedBlock ... mayPlaceOn)
        Object[][] table = {
            {PlantType.STANDARD, "tfc:bush_plantable_on"}, {PlantType.SHORT_SHRUB, "tfc:bush_plantable_on"},
            {PlantType.SHRUB, "tfc:bush_plantable_on"}, {PlantType.FLOWERBED, "tfc:bush_plantable_on"},
            {PlantType.SHORT_GRASS, "tfc:grass_plantable_on"}, {PlantType.TALL_GRASS, "tfc:grass_plantable_on"},
            {PlantType.TALL_SHRUB, "tfc:grass_plantable_on"}, {PlantType.BEACH_GRASS, "minecraft:sand"},
            {PlantType.BEACH_GRASS, "c:sands"}, {PlantType.DRY, "tfc:dry_plant_plantable_on"},
            {PlantType.CACTUSBED, "tfc:dry_plant_plantable_on"}, {PlantType.PASSABLE_CACTUS, "tfc:dry_plant_plantable_on"},
            {PlantType.PERCHED_EPIPHYTE, "tfc:epiphyte_plantable_on"},
        };
        for (Object[] row : table) {
            PlantType type = (PlantType) row[0];
            assertThat(PlantSurvival.canSurvive(type, false, tags((String) row[1]), false)).as(type + " on " + row[1]).isTrue();
            assertThat(PlantSurvival.canSurvive(type, false, tags("tfc:something_else"), false)).as(type + " on else").isFalse();
            assertThat(PlantSurvival.canSurvive(type, false, tags(), false)).as(type + " on air").isFalse();
        }
    }

    @Test
    void theUpperHalfNeedsTheLowerHalfOfItsOwnBlock() {
        for (PlantType type : new PlantType[] {PlantType.TALL_GRASS, PlantType.TALL_SHRUB, PlantType.PASSABLE_CACTUS}) {
            assertThat(PlantSurvival.canSurvive(type, true, new Below(s -> true, true, false, PlantState.Fluid.EMPTY), false)).isTrue();
            // dirt below an upper half is not enough
            assertThat(PlantSurvival.canSurvive(type, true, tags("tfc:grass_plantable_on", "tfc:bush_plantable_on"), false)).isFalse();
        }
    }

    @Test
    void theCactusBlockAlsoWantsFreeSides() {
        Below sand = tags("tfc:dry_plant_plantable_on");
        assertThat(PlantSurvival.canSurvive(PlantType.CACTUS, false, sand, false)).isTrue();
        assertThat(PlantSurvival.canSurvive(PlantType.CACTUS, false, sand, true)).isFalse();
        // the passable cactus and the bed do not look at the sides
        assertThat(PlantSurvival.canSurvive(PlantType.PASSABLE_CACTUS, false, sand, true)).isTrue();
        assertThat(PlantSurvival.canSurvive(PlantType.CACTUSBED, false, sand, true)).isTrue();
    }

    @Test
    void floatingPlantsNeedTheirFluidBelow() {
        Below fresh = new Below(s -> false, false, false, PlantState.Fluid.WATER);
        Below salt = new Below(s -> false, false, false, PlantState.Fluid.SALT_WATER);
        assertThat(PlantSurvival.canSurvive(PlantType.FLOATING_FRESH, false, fresh, false)).isTrue();
        assertThat(PlantSurvival.canSurvive(PlantType.FLOATING_FRESH, false, salt, false)).isFalse();
        assertThat(PlantSurvival.canSurvive(PlantType.FLOATING, false, salt, false)).isTrue();
        assertThat(PlantSurvival.canSurvive(PlantType.FLOATING, false, tags(), false)).isFalse();
    }

    @Test
    void theCactusFlowerSitsOnABranchingCactus() {
        assertThat(PlantSurvival.canSurvive(PlantType.CACTUS_FLOWER, false, new Below(s -> false, false, true, PlantState.Fluid.EMPTY), false))
            .isTrue();
        assertThat(PlantSurvival.canSurvive(PlantType.CACTUS_FLOWER, false, tags("tfc:dry_plant_plantable_on"), false)).isFalse();
    }

    @Test
    void shapesWithoutAPortedRuleAreNeverRemoved() {
        assertThat(PlantSurvival.ported(PlantType.KELP)).isFalse();
        assertThat(PlantSurvival.canSurvive(PlantType.KELP, false, tags(), false)).isTrue();
    }

    @Test
    void stackingRaisesTheAgeUnlessSneakingOrFull() {
        assertThat(PlantStacking.stackedAge(PlantType.FLOWERBED, 0, false, true)).hasValue(1);
        assertThat(PlantStacking.stackedAge(PlantType.CACTUSBED, 2, false, true)).hasValue(3);
        assertThat(PlantStacking.stackedAge(PlantType.FLOWERBED, 3, false, true)).isEmpty();
        assertThat(PlantStacking.stackedAge(PlantType.FLOWERBED, 1, true, true)).isEmpty();
        assertThat(PlantStacking.stackedAge(PlantType.FLOWERBED, 1, false, false)).isEmpty();
        assertThat(PlantStacking.stackedAge(PlantType.STANDARD, 0, false, true)).isEmpty();
    }

    @Test
    void cactiHurtOnContact() {
        assertThat(CactusContact.DAMAGE).isEqualTo(1.0f);
        for (PlantType t : new PlantType[] {PlantType.CACTUS, PlantType.PASSABLE_CACTUS, PlantType.CACTUSBED, PlantType.BRANCHING_CACTUS,
            PlantType.BRANCHING_CACTUS_TOP}) {
            assertThat(CactusContact.hurts(t)).isTrue();
        }
        assertThat(CactusContact.hurts(PlantType.STANDARD)).isFalse();
        assertThat(CactusContact.hurts(PlantType.CACTUS_FLOWER)).isFalse();
    }

    @Test
    void lazyAgeMatchesSteppedRandomTicksInTheMean() {
        // 1..24 months of 8 days? use ticks: one hour of game time at tick speed 3 is 3600*20*3/4096 ~ capped at lambda 24
        int trials = 4000;
        for (int ticks : new int[] {5, 25, 60, 200}) {
            double stepped = 0;
            double lazy = 0;
            Random a = new Random(11 + ticks);
            Random b = new Random(77 + ticks);
            // elapsed such that lambda == ticks (kept below the cap)
            long elapsed = Math.round(ticks * 4096.0 / 3);
            double lambda = Math.min(24.0, elapsed * 3 / 4096.0);
            for (int i = 0; i < trials; i++) {
                stepped += PlantGrowth.steppedAge(0, 3, Math.round(lambda), PlantGrowth.GROWTH_CHANCE, a);
                lazy += PlantGrowth.lazyAge(0, 3, elapsed, 3, PlantGrowth.GROWTH_CHANCE, b);
            }
            assertThat(lazy / trials).as("ticks " + ticks).isBetween(stepped / trials - 0.12, stepped / trials + 0.12);
        }
    }

    @Test
    void ageNeverExceedsTheMax() {
        Random random = new Random(3);
        for (int i = 0; i < 500; i++) {
            assertThat(PlantGrowth.lazyAge(0, 3, 1_000_000, 3, 0.9, random)).isLessThanOrEqualTo(3);
        }
        assertThat(PlantGrowth.lazyAge(3, 3, 1_000_000, 3, 1.0, random)).isEqualTo(3);
    }

    @Test
    void aColumnGrowsWithinTheCapsAndTheFreeCells() {
        Random random = new Random(5);
        double sum = 0;
        for (int i = 0; i < 2000; i++) {
            int grown = PlantGrowth.lazyTopGrowth(10, 100, 40_000, 3, PlantGrowth.LONG_GROWTH_CHANCE, random);
            assertThat(grown).isBetween(0, PlantGrowth.TOP_MAX_AGE - 10);
            sum += grown;
        }
        // lambda 24 ticks at 3 % per tick: mean 0.72 blocks
        assertThat(sum / 2000).isBetween(0.55, 0.9);
        // a blocked column never grows, an old head neither
        assertThat(PlantGrowth.lazyTopGrowth(10, 0, 40_000, 3, 1.0, random)).isZero();
        assertThat(PlantGrowth.lazyTopGrowth(25, 9, 40_000, 3, 1.0, random)).isZero();
        assertThat(PlantGrowth.steppedTopGrowth(0, 100, 1000, 1.0, random)).isEqualTo(25);
        assertThat(PlantGrowth.steppedTopGrowth(0, 4, 1000, 1.0, random)).isEqualTo(4);
    }

    @Test
    void lazyColumnMatchesSteppedInTheMean() {
        Random a = new Random(21);
        Random b = new Random(22);
        double stepped = 0;
        double lazy = 0;
        int trials = 6000;
        for (int i = 0; i < trials; i++) {
            stepped += PlantGrowth.steppedTopGrowth(0, 100, 24, 0.03, a);
            lazy += PlantGrowth.lazyTopGrowth(0, 100, 32_768, 3, 0.03, b);
        }
        assertThat(lazy / trials).isBetween(stepped / trials - 0.06, stepped / trials + 0.06);
    }

    @Test
    void vinesNeedAboveFreezing() {
        assertThat(PlantGrowth.vineMayGrow(0.01f)).isTrue();
        assertThat(PlantGrowth.vineMayGrow(0f)).isFalse();
        assertThat(PlantGrowth.vineMayGrow(-5f)).isFalse();
    }

    @Test
    void branchingCactusKeepsTfcsComparison() {
        Random random = new Random(9);
        int grows = 0;
        for (int i = 0; i < 10_000; i++) {
            if (PlantGrowth.branchingCactusGrows(PlantGrowth.GROWTH_CHANCE, random)) {
                grows++;
            }
        }
        assertThat(grows / 10_000.0).isBetween(0.94, 0.98);
        assertThat(PlantGrowth.kelpTreeGrows(false, true, 1.0, random)).isFalse();
        assertThat(PlantGrowth.kelpTreeGrows(true, false, 1.0, random)).isFalse();
        assertThat(PlantGrowth.kelpTreeGrows(true, true, 1.0, random)).isTrue();
    }
}
