/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.spawn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.tfp.core.calendar.Month;

/**
 * M5-ENT-03 targeted rules: the weighted pick, each gate step in TFC's order, and the elevation temperature. Each test is one
 * rule, traced by hand from TFC at c3b4d9536 ({@code Faunas}, {@code ClimatePlacement}, {@code EnvironmentHelpers}).
 */
class SpawnRulesTest {

    private static final FaunaRule OPEN = new FaunaRule(1, -1, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0, 4, false, false, -1, List.of());

    private static SpawnSite site(int y, Month month, int brightness) {
        return new SpawnSite(y, 63, SpawnTsv.chunk(20f, 250f, 0f, 0f), 2, true, brightness, month);
    }

    private static SpawnEntry entry(String type, int weight) {
        return new SpawnEntry("plains", SpawnCategory.CREATURE, type, weight, 1, 4);
    }

    @Test
    void weightedPickWalksTheListInOrder() {
        List<SpawnEntry> list = List.of(entry("tfc:a", 1), entry("tfc:b", 3));
        // total 4: draws 0 picks a, 1..3 pick b
        assertThat(SpawnRules.pick(list, new FixedRandom(0, 0f)).get().type()).isEqualTo("tfc:a");
        assertThat(SpawnRules.pick(list, new FixedRandom(1, 0f)).get().type()).isEqualTo("tfc:b");
        assertThat(SpawnRules.pick(list, new FixedRandom(3, 0f)).get().type()).isEqualTo("tfc:b");
        assertThat(SpawnRules.pick(list, new FixedRandom(4, 0f)).get().type()).isEqualTo("tfc:a");
    }

    @Test
    void weightlessListPicksNothing() {
        assertThat(SpawnRules.pick(List.of(entry("tfc:a", 0)), new FixedRandom(0, 0f))).isEmpty();
        assertThat(SpawnRules.pick(List.of(), new FixedRandom(0, 0f))).isEmpty();
    }

    @Test
    void chanceRefusesUnlessTheDrawIsZero() {
        FaunaRule dolphin = new FaunaRule(10, 6, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, 0, 4, false, false, -1, List.of());
        assertThat(SpawnRules.accepts(dolphin, site(40, Month.JULY, 15), new FixedRandom(0, 0f))).isTrue();
        assertThat(SpawnRules.accepts(dolphin, site(40, Month.JULY, 15), new FixedRandom(3, 0f))).isFalse();
    }

    @Test
    void depthRuleIsSeaLevelMinusDistance() {
        FaunaRule dolphin = new FaunaRule(1, 6, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, 0, 4, false, false, -1, List.of());
        // sea level 63, distance 6: y 57 is allowed, 58 is not
        assertThat(SpawnRules.accepts(dolphin, site(57, Month.JULY, 15), new FixedRandom(0, 0f))).isTrue();
        assertThat(SpawnRules.accepts(dolphin, site(58, Month.JULY, 15), new FixedRandom(0, 0f))).isFalse();
    }

    @Test
    void monthsAndBrightnessGateTheSpawn() {
        FaunaRule salmon = new FaunaRule(1, -1, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, 0, 4, false, true, -1, List.of(Month.MAY, Month.JUNE));
        assertThat(SpawnRules.accepts(salmon, site(60, Month.JUNE, 15), new FixedRandom(0, 0f))).isTrue();
        assertThat(SpawnRules.accepts(salmon, site(60, Month.DECEMBER, 15), new FixedRandom(0, 0f))).isFalse();

        FaunaRule octopus = new FaunaRule(1, 33, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, 0, 4, false, false, 0, List.of());
        assertThat(SpawnRules.accepts(octopus, site(30, Month.JULY, 0), new FixedRandom(0, 0f))).isTrue();
        assertThat(SpawnRules.accepts(octopus, site(30, Month.JULY, 1), new FixedRandom(0, 0f))).isFalse();
    }

    @Test
    void groundwaterIgnoresRiversWhenFlagged() {
        // base groundwater 400 and rain 250: the river term caps the total at 500, so 650 is not allowed
        SpawnSite wet = new SpawnSite(60, 63, SpawnTsv.chunk(20f, 250f, 0f, 400f), 2, true, 15, Month.JULY);
        FaunaRule withRivers = new FaunaRule(1, -1, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 500f,
                Float.POSITIVE_INFINITY, 0, 4, false, false, -1, List.of());
        FaunaRule rainOnly = new FaunaRule(1, -1, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 500f,
                Float.POSITIVE_INFINITY, 0, 4, false, true, -1, List.of());
        assertThat(SpawnRules.accepts(withRivers, wet, new FixedRandom(0, 0f))).isTrue();
        assertThat(SpawnRules.accepts(rainOnly, wet, new FixedRandom(0, 0f))).isFalse();
    }

    @Test
    void forestDensityIsInclusive() {
        FaunaRule panther = new FaunaRule(1, -1, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.POSITIVE_INFINITY, 2, 3, false, false, -1, List.of());
        SpawnSite forest2 = new SpawnSite(60, 63, SpawnTsv.chunk(20f, 250f, 0f, 0f), 2, true, 15, Month.JULY);
        SpawnSite forest4 = new SpawnSite(60, 63, SpawnTsv.chunk(20f, 250f, 0f, 0f), 4, true, 15, Month.JULY);
        assertThat(SpawnRules.accepts(panther, forest2, new FixedRandom(0, 0f))).isTrue();
        assertThat(SpawnRules.accepts(panther, forest4, new FixedRandom(0, 0f))).isFalse();
    }

    @Test
    void temperatureIsLoweredOnlyAboveSeaLevel() {
        assertThat(SpawnRules.adjustAverageTemperatureForElevation(63, 20f)).isEqualTo(20f);
        assertThat(SpawnRules.adjustAverageTemperatureForElevation(0, 20f)).isEqualTo(20f);
        // 100 - 63 = 37 blocks above sea level: 37 * 0.16225 = 6.00325 degrees
        assertThat(SpawnRules.adjustAverageTemperatureForElevation(100, 20f)).isCloseTo(13.99675f, org.assertj.core.api.Assertions.within(1e-4f));
        // the drop is capped at 17.822 degrees
        assertThat(SpawnRules.adjustAverageTemperatureForElevation(400, 20f)).isCloseTo(2.178f, org.assertj.core.api.Assertions.within(1e-4f));
    }

    @Test
    void fuzzyDrawDecidesInsideTheWindow() {
        // Panda-like window: temperature 18 to 28, groundwater 300 to 500, forest 3 to 4. Site: 23, 350, 0 rain variance.
        FaunaRule panda = new FaunaRule(1, -1, 18f, 28f, 300f, 500f, 3, 4, true, false, -1, List.of());
        SpawnSite site = new SpawnSite(60, 63, SpawnTsv.chunk(23f, 250f, 0f, 100f), 3, true, 15, Month.JULY);
        // normalised deltas: temperature 0, groundwater 50 / 200 = 0.25, rain variance 0; the draw product must exceed 0.25
        assertThat(SpawnRules.accepts(panda, site, new FixedRandom(0, 0.9f))).isTrue();
        assertThat(SpawnRules.accepts(panda, site, new FixedRandom(0, 0.1f))).isFalse();
    }

    @Test
    void attemptDrawsGroupSizeAndRefusesTfcGate() {
        FaunaRule refuse = new FaunaRule(1, -1, 30f, 40f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0, 4, false, false,
                -1, List.of());
        SpawnEntry cow = new SpawnEntry("plains", SpawnCategory.CREATURE, "tfc:cow", 1, 2, 5);
        SpawnSite cold = site(60, Month.JULY, 15);

        // the gate refuses at 20 C: no pick is returned, even though the entry was chosen
        assertThat(SpawnRules.attempt(List.of(cow), Map.of("tfc:cow", refuse), cold, new FixedRandom(0, 0f))).isEmpty();

        FaunaRule open = OPEN;
        // group size 2 + (3 % 4) = 5
        Optional<SpawnPick> pick = SpawnRules.attempt(List.of(cow), Map.of("tfc:cow", open), cold, new FixedRandom(3, 0f));
        assertThat(pick).isPresent();
        assertThat(pick.get().groupSize()).isEqualTo(5);
        assertThat(pick.get().entry().isTfc()).isTrue();
    }

    @Test
    void vanillaEntryIsNotGatedHere() {
        SpawnEntry spider = new SpawnEntry("plains", SpawnCategory.MONSTER, "minecraft:spider", 100, 4, 4);
        Optional<SpawnPick> pick = SpawnRules.attempt(List.of(spider), Map.of(), site(60, Month.JULY, 15), new FixedRandom(0, 0f));
        assertThat(pick).isPresent();
        assertThat(pick.get().groupSize()).isEqualTo(4);
        assertThat(pick.get().entry().isTfc()).isFalse();
    }

    @Test
    void missingGateForTfcSpeciesIsAnError() {
        SpawnEntry cow = entry("tfc:cow", 1);
        assertThatThrownBy(() -> SpawnRules.attempt(List.of(cow), Map.of(), site(60, Month.JULY, 15), new FixedRandom(0, 0f)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidDataIsRejected() {
        assertThatThrownBy(() -> new FaunaRule(0, -1, 0f, 1f, 0f, 1f, 0, 4, false, false, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpawnEntry("plains", SpawnCategory.CREATURE, "tfc:cow", 1, 5, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
