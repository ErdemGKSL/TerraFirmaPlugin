/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.bloomery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BloomeryRules} and the records it uses, on the TFC wrought iron recipe shape. */
class BloomeryRulesTest {
    private static final String CAST_IRON = "tfc:metal/cast_iron";
    private static final String CHARCOAL = "minecraft:charcoal";
    private static final BloomeryRecipe IRON = new BloomeryRecipe(CAST_IRON, 100, CHARCOAL, 2, "tfc:raw_iron_bloom", 14400);
    private static final BloomeryItem MELT = new BloomeryItem("test:iron_part", 1, CAST_IRON, 100);

    @Test
    void chimneyLevelsCountLeadingFormedLevelsCappedAtTheMaximum() {
        assertThat(BloomeryRules.chimneyLevels(new boolean[0], 3)).isZero();
        assertThat(BloomeryRules.chimneyLevels(new boolean[] {true, true, false, true}, 3)).isEqualTo(2);
        assertThat(BloomeryRules.chimneyLevels(new boolean[] {true, true, true, true, true}, 3)).isEqualTo(3);
        assertThat(BloomeryRules.chimneyLevels(new boolean[] {false, true}, 3)).isZero();
    }

    @Test
    void capacityNeedsAFormedBaseAndScalesWithChimneyLevels() {
        assertThat(BloomeryRules.capacity(false, 3, 16)).isZero();
        assertThat(BloomeryRules.capacity(true, 0, 16)).isZero();
        assertThat(BloomeryRules.capacity(true, 2, 16)).isEqualTo(32);
        assertThat(BloomeryRules.excess(5, 3)).isEqualTo(2);
        assertThat(BloomeryRules.excess(3, 3)).isZero();
    }

    @Test
    void configRejectsNonPositiveValues() {
        assertThatThrownBy(() -> new BloomeryRules.Config(0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BloomeryRules.Config(16, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(BloomeryRules.Config.DEFAULT).isEqualTo(new BloomeryRules.Config(16, 3));
    }

    @Test
    void recipeRejectsBadCountsAndDuration() {
        assertThatThrownBy(() -> new BloomeryRecipe(CAST_IRON, 0, CHARCOAL, 2, "x", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BloomeryRecipe(CAST_IRON, 100, CHARCOAL, 0, "x", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BloomeryRecipe(CAST_IRON, 100, CHARCOAL, 2, "x", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void catalystNeedsTheItemAndAStackOfTheCatalystCount() {
        assertThat(IRON.matchesCatalyst(CHARCOAL, 2)).isTrue();
        assertThat(IRON.matchesCatalyst(CHARCOAL, 1)).isFalse();
        assertThat(IRON.matchesCatalyst("minecraft:coal", 64)).isFalse();
    }

    @Test
    void recipeIsSelectedByTheFirstContentMeltThenTheFirstRecipe() {
        BloomeryRecipe other = new BloomeryRecipe("tfc:metal/bronze", 100, CHARCOAL, 2, "tfc:other", 10);
        BloomeryItem bronze = new BloomeryItem("test:bronze_part", 1, "tfc:metal/bronze", 100);
        assertThat(BloomeryRules.selectRecipe(List.of(IRON, other), List.of(BloomeryItem.inert(CHARCOAL, 4), bronze, MELT))).isSameAs(other);
        assertThat(BloomeryRules.selectRecipe(List.of(IRON, other), List.of(MELT, bronze))).isSameAs(IRON);
        assertThat(BloomeryRules.selectRecipe(List.of(IRON), List.of(BloomeryItem.inert(CHARCOAL, 4)))).isNull();
        assertThat(BloomeryRules.selectRecipe(List.of(IRON), List.of())).isNull();
    }

    @Test
    void invalidContentsAreDumpedBeforeTheWorldIsScanned() {
        BloomeryItem bronze = new BloomeryItem("test:bronze_part", 1, "tfc:metal/bronze", 100);
        BloomeryRecipe other = new BloomeryRecipe("tfc:metal/bronze", 100, CHARCOAL, 2, "tfc:other", 10);
        assertThat(BloomeryRules.mustDump(List.of(IRON, other), List.of(bronze))).isFalse();
        assertThat(BloomeryRules.mustDump(List.of(IRON), List.of(bronze))).isTrue();
        assertThat(BloomeryRules.mustDump(List.of(IRON), List.of())).isFalse();
        // after the dump the contents are empty, so the world decides the recipe
        assertThat(BloomeryRules.intakeRecipe(List.of(IRON, other), List.of(), List.of(bronze, MELT))).isSameAs(other);
    }

    @Test
    void intakeAlternatesCatalystAndInputUnitsInWorldOrder() {
        List<BloomeryItem> world = List.of(
            BloomeryItem.inert(CHARCOAL, 2),
            MELT,
            new BloomeryItem("test:iron_part", 1, CAST_IRON, 100));
        int[] taken = BloomeryRules.intake(IRON, world, 0, 16);
        assertThat(taken).containsExactly(2, 1, 1);
    }

    @Test
    void intakeTakesNothingWithoutACatalystOrWithAloneCatalystStacks() {
        // a single charcoal is below the catalyst count, so it is not a catalyst and nothing is taken
        assertThat(BloomeryRules.intake(IRON, List.of(BloomeryItem.inert(CHARCOAL, 1), MELT), 0, 16)).containsExactly(0, 0);
        // inputs alone, with nothing in the bloomery yet, are not taken
        assertThat(BloomeryRules.intake(IRON, List.of(MELT), 0, 16)).containsExactly(0);
        // a catalyst alone, with nothing in the bloomery, is not taken either
        assertThat(BloomeryRules.intake(IRON, List.of(BloomeryItem.inert(CHARCOAL, 4)), 0, 16)).containsExactly(0);
    }

    @Test
    void intakeContinuesWhenTheBloomeryAlreadyHoldsInputs() {
        // with inputs already inside, a catalyst arriving alone is taken
        assertThat(BloomeryRules.intake(IRON, List.of(BloomeryItem.inert(CHARCOAL, 4)), 1, 16)).containsExactly(4);
        // but an input arriving alone is refused once the bloomery holds inputs: TFC needs a catalyst then
        assertThat(BloomeryRules.intake(IRON, List.of(MELT), 1, 16)).containsExactly(0);
    }

    @Test
    void intakeStopsAtFreeCapacityAndWhenFull() {
        List<BloomeryItem> world = List.of(BloomeryItem.inert(CHARCOAL, 10), new BloomeryItem("test:iron_part", 10, CAST_IRON, 100));
        assertThat(BloomeryRules.intake(IRON, world, 0, 4)).containsExactly(2, 2);
        assertThat(BloomeryRules.intake(IRON, world, 3, 4)).containsExactly(1, 0);
        assertThat(BloomeryRules.intake(IRON, world, 4, 4)).containsExactly(0, 0);
        assertThat(BloomeryRules.intake(null, world, 0, 16)).containsExactly(0, 0);
    }

    @Test
    void producesBloomsByTheLeastOfFluidAndCatalystBatches() {
        assertThat(BloomeryRules.produce(IRON, List.of(MELT), 1535f)).isNull();
        // 99 mB of fluid is not one batch, even with enough charcoal
        assertThat(BloomeryRules.produce(IRON, List.of(
            unit(99), BloomeryItem.inert(CHARCOAL, 1), BloomeryItem.inert(CHARCOAL, 1)), 1535f)).isNull();
        // 100 mB and one catalyst is short of one batch
        assertThat(BloomeryRules.produce(IRON, List.of(MELT, BloomeryItem.inert(CHARCOAL, 1)), 1535f)).isNull();
        // 200 mB and 4 catalysts make two
        BloomeryRules.Bloom two = BloomeryRules.produce(IRON, List.of(
            MELT, MELT, BloomeryItem.inert(CHARCOAL, 1), BloomeryItem.inert(CHARCOAL, 1), BloomeryItem.inert(CHARCOAL, 1), BloomeryItem.inert(CHARCOAL, 1)), 1535f);
        assertThat(two).isEqualTo(new BloomeryRules.Bloom("tfc:raw_iron_bloom", 2, 1534f));
        // 300 mB and 2 catalysts make one: the catalysts are the limit
        assertThat(BloomeryRules.produce(IRON, List.of(MELT, MELT, MELT, BloomeryItem.inert(CHARCOAL, 1), BloomeryItem.inert(CHARCOAL, 1)), 1535f).count()).isEqualTo(1);
    }

    @Test
    void anyNonPrimaryUnitCountsAsACatalystAsInTfc() {
        // TFC counts every unit that is not a primary input, whatever its item
        BloomeryRules.Bloom bloom = BloomeryRules.produce(IRON, List.of(MELT, BloomeryItem.inert("test:rock", 1), BloomeryItem.inert("test:rock", 1)), 1535f);
        assertThat(bloom).isEqualTo(new BloomeryRules.Bloom("tfc:raw_iron_bloom", 1, 1534f));
    }

    @Test
    void bloomWithoutAHeatingRecipeHasNoTemperature() {
        BloomeryRules.Bloom bloom = BloomeryRules.produce(IRON, List.of(MELT, BloomeryItem.inert(CHARCOAL, 2), BloomeryItem.inert(CHARCOAL, 2)), null);
        assertThat(bloom.temperature()).isNull();
    }

    @Test
    void bloomLayersFollowTfcScaling() {
        assertThat(BloomeryRules.bloomLayers(0, 5)).isZero();
        assertThat(BloomeryRules.bloomLayers(3, 5)).isEqualTo(3);
        assertThat(BloomeryRules.bloomLayers(16, 16)).isEqualTo(8);
        assertThat(BloomeryRules.bloomLayers(8, 16)).isEqualTo(4);
        assertThat(BloomeryRules.bloomLayers(1, 16)).isEqualTo(1);
    }

    @Test
    void lifecycleFiresAtTheFinishTickAndHaltsOnAFailedCheck() {
        long lit = 1000;
        int duration = 14400;
        assertThat(BloomeryRules.phaseAt(lit, lit, duration, BloomeryRules.NEVER)).isEqualTo(BloomeryRules.Phase.LIT);
        assertThat(BloomeryRules.phaseAt(lit + duration - 1, lit, duration, BloomeryRules.NEVER)).isEqualTo(BloomeryRules.Phase.LIT);
        assertThat(BloomeryRules.phaseAt(lit + duration, lit, duration, BloomeryRules.NEVER)).isEqualTo(BloomeryRules.Phase.FIRED);
        assertThat(BloomeryRules.phaseAt(lit + duration + 999, lit, duration, BloomeryRules.NEVER)).isEqualTo(BloomeryRules.Phase.FIRED);

        // a failed check before the finish halts it at that tick, and it stays halted afterwards
        long halt = lit + 500;
        assertThat(BloomeryRules.phaseAt(halt - 1, lit, duration, halt)).isEqualTo(BloomeryRules.Phase.LIT);
        assertThat(BloomeryRules.phaseAt(halt, lit, duration, halt)).isEqualTo(BloomeryRules.Phase.HALTED);
        assertThat(BloomeryRules.phaseAt(lit + duration + 5, lit, duration, halt)).isEqualTo(BloomeryRules.Phase.HALTED);

        // a failed check at or after the finish does not stop the finished recipe
        assertThat(BloomeryRules.phaseAt(lit + duration, lit, duration, lit + duration)).isEqualTo(BloomeryRules.Phase.FIRED);
    }

    @Test
    void remainingTicksAreNotClamped() {
        assertThat(BloomeryRules.remainingTicks(1000, 1000, 14400)).isEqualTo(14400);
        assertThat(BloomeryRules.remainingTicks(1000 + 14400 + 7, 1000, 14400)).isEqualTo(-7);
    }

    private static BloomeryItem unit(int fluidAmount) {
        return new BloomeryItem("test:iron_part", 1, CAST_IRON, fluidAmount);
    }
}
