/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pluck;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.paper.pluck.PluckRules.Outcome;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-20: TFC's {@code Pluckable.pluck} decision, in TFC's order. */
class PluckRulesTest {
    private static final long HOUR = CalendarTicks.CALENDAR_TICKS_IN_HOUR;

    @Test
    void healthAtOrBelowTfcsThresholdIsIgnoredBeforeAnythingElse() {
        assertThat(PluckRules.decide(0.15001f, 0, 0, false, true)).isEqualTo(Outcome.IGNORE);
        assertThat(PluckRules.decide(0.15f, 0, 0, false, true)).isEqualTo(Outcome.IGNORE);
        assertThat(PluckRules.decide(0.1f, 1_000, 1_001, true, false)).isEqualTo(Outcome.IGNORE); // not the cooldown, not worn out
        assertThat(PluckRules.decide(0.1501f, 0, 0, false, true)).isEqualTo(Outcome.PLUCK);
        assertThat(PluckRules.decide(1f, 0, 0, false, true)).isEqualTo(Outcome.PLUCK);
    }

    @Test
    void theCooldownIsOneCalendarHourFromTheLastPlucking() {
        assertThat(PluckRules.decide(1f, 5_000, 5_000 + HOUR - 1, false, true)).isEqualTo(Outcome.COOLDOWN);
        assertThat(PluckRules.decide(1f, 5_000, 5_000 + HOUR, false, true)).isEqualTo(Outcome.PLUCK);
        assertThat(PluckRules.decide(1f, 0, 10, false, true)).as("never plucked").isEqualTo(Outcome.PLUCK);
        assertThat(PluckRules.remaining(1_000, 1_500)).isEqualTo(HOUR - 500);
        assertThat(PluckRules.remaining(0, 99)).isZero();
    }

    @Test
    void livestockMustBeAnAdultWithUsesLeftAndWildBirdsNeedNothingMore() {
        assertThat(PluckRules.decide(1f, 0, 0, true, false)).isEqualTo(Outcome.WORN_OUT);
        assertThat(PluckRules.decide(1f, 0, 0, true, true)).isEqualTo(Outcome.PLUCK);
        assertThat(PluckRules.decide(1f, 0, 0, false, false)).isEqualTo(Outcome.PLUCK);
        assertThat(PluckRules.decide(1f, 100, 101, true, false)).as("cooldown first").isEqualTo(Outcome.COOLDOWN);
    }

    @Test
    void aPluckingCostsAFifteenthOfTheMaximumHealth() {
        assertThat(PluckRules.DAMAGE_FRACTION).isEqualTo(0.15f);
        // a bird at full health can be plucked six times before it is under the threshold: 1 - 6 * 0.15 = 0.1
        float health = 1f;
        int plucks = 0;
        while (PluckRules.decide(health, 0, 0, false, true) == Outcome.PLUCK) {
            health -= PluckRules.DAMAGE_FRACTION;
            plucks++;
        }
        assertThat(plucks).isEqualTo(6);
    }
}
