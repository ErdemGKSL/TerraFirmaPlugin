/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/StationaryBerryBushBlock.java @ c3b4d9536 (randomTick, tick)
 */
package dev.tfp.core.berry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The catch-up is a pure function of its inputs: the same bush, tick, clock and climate give the same outcome every time, on any thread,
 * and a second catch-up at the same tick changes nothing.
 */
class BerryDeterminismTest {
    private static final long CAL_DAY = CalendarTicks.CALENDAR_TICKS_IN_DAY;
    private static final long NOW = 9_000_000L;

    private static BerryRules.Outcome run(BushSpecies species, BerryBush bush, long now, BerryRules.ClimateSource climate) {
        BerryClock clock = new BerryClock(now, 3L * 12 * CAL_DAY * 8 + 5 * CAL_DAY * 8 + 2 * CAL_DAY, 8, true);
        return BerryRules.catchUp(species, bush, now, clock, climate, BerryRules.Settings.DEFAULT);
    }

    @Test
    void sameInputsGiveTheSameOutcomeOnEveryCallAndThread() {
        BerryRules.ClimateSource climate = t -> new BerryRules.Sample(60, 12f);
        BerryBush bush = new BerryBush(Lifecycle.HEALTHY, 0, 24, NOW - 7 * BerryRules.GROWTH_TICKS, NOW - 300_000);
        BerryRules.Outcome expected = run(BushSpecies.STRAWBERRY, bush, NOW, climate);

        boolean allEqual = IntStream.range(0, 512).parallel()
            .mapToObj(i -> run(BushSpecies.STRAWBERRY, bush, NOW, climate))
            .allMatch(expected::equals);
        assertThat(allEqual).isTrue();
    }

    @Test
    void aSecondCatchUpAtTheSameTickChangesNothing() {
        BerryRules.ClimateSource climate = t -> new BerryRules.Sample(60, 12f);
        BerryBush bush = new BerryBush(Lifecycle.HEALTHY, 0, 24, NOW - 9 * BerryRules.GROWTH_TICKS, NOW - 300_000);
        BerryRules.Alive first = (BerryRules.Alive) run(BushSpecies.STRAWBERRY, bush, NOW, climate);
        BerryRules.Alive second = (BerryRules.Alive) run(BushSpecies.STRAWBERRY, first.bush(), NOW, climate);

        assertThat(second.bush()).isEqualTo(first.bush());
        assertThat(second.propagations()).isEmpty();
    }
}
