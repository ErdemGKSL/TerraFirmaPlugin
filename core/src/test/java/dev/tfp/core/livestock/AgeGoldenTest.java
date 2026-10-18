/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.livestock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * M5-LIV-01 golden age values, hand-traced from TFC at c3b4d9536: {@code TFCAnimalProperties.getAgeType} (old once
 * {@code oldTick} has passed, adult once {@code birthTick + adulthoodDays * PLAYER_TICKS_IN_DEFAULT_DAY} has passed) and
 * {@code TFCAnimalProperties.tickAnimalData} (sets {@code oldTick = now + (1 + nextInt(5)) * TICKS_IN_DAY} once, for an adult
 * with {@code uses > usesToElderly}).
 *
 * <p>TFC compares the calendar's player ticks with {@code PLAYER_TICKS_IN_DEFAULT_DAY} (28 800) for adulthood, but
 * with {@code TICKS_IN_DAY} (24 000) for the old age offset. That mix is kept as is.
 */
class AgeGoldenTest {

    private static AnimalState born(long birthTick, int uses, long oldTick) {
        return new AnimalState(false, 0f, -1L, birthTick, oldTick, AnimalState.NEVER, AnimalState.NEVER, uses, false,
            AnimalState.NOT_PREGNANT);
    }

    @Test
    void pigBecomesAdultAfterEightyPlayerDays() {
        // TFC: adultTick = 0 + 80 * 28 800 = 2 304 000; ADULT only when now > adultTick (strict)
        final AnimalState pig = born(0L, 0, -1L);
        assertThat(LivestockRules.ageAt(pig, LivestockSpecies.PIG, 2_304_000L)).isEqualTo(LivestockAge.CHILD);
        assertThat(LivestockRules.ageAt(pig, LivestockSpecies.PIG, 2_304_001L)).isEqualTo(LivestockAge.ADULT);
    }

    @Test
    void chickenBecomesAdultAfterTwentyFourPlayerDays() {
        // TFC: adultTick = 24 * 28 800 = 691 200
        final AnimalState chicken = born(0L, 0, -1L);
        assertThat(LivestockRules.ageAt(chicken, LivestockSpecies.CHICKEN, 691_200L)).isEqualTo(LivestockAge.CHILD);
        assertThat(LivestockRules.ageAt(chicken, LivestockSpecies.CHICKEN, 691_201L)).isEqualTo(LivestockAge.ADULT);
    }

    @Test
    void oldAgeSetsAtUsesAboveTheLimitWithADayOffset() {
        // TFC: pig uses limit 60. uses 61 > 60, adult, oldTick unset -> oldTick = now + (1 + 2) * 24 000 = 3 072 000
        final AnimalState pig = born(0L, 61, -1L);
        final AnimalState marked = LivestockRules.markElderlyIfDue(pig, LivestockSpecies.PIG, 3_000_000L, 2);
        assertThat(marked.oldTick()).isEqualTo(3_072_000L);
        assertThat(LivestockRules.ageAt(marked, LivestockSpecies.PIG, 3_072_000L)).isEqualTo(LivestockAge.ADULT);
        assertThat(LivestockRules.ageAt(marked, LivestockSpecies.PIG, 3_072_001L)).isEqualTo(LivestockAge.OLD);
    }

    @Test
    void usesEqualToTheLimitDoNotTriggerOldAge() {
        // TFC: getUses() > getUsesToElderly() is strict; pig limit 60
        final AnimalState pig = born(0L, 60, -1L);
        assertThat(LivestockRules.markElderlyIfDue(pig, LivestockSpecies.PIG, 3_000_000L, 0)).isEqualTo(pig);
    }

    @Test
    void childrenAreNotMarkedOld() {
        // TFC: only Age.ADULT is marked. A child with uses over the limit is left alone
        final AnimalState child = born(2_999_000L, 200, -1L);
        assertThat(LivestockRules.markElderlyIfDue(child, LivestockSpecies.PIG, 3_000_000L, 0)).isEqualTo(child);
    }

    @Test
    void oldTickIsWrittenOnlyOnce() {
        // TFC: condition oldTick == -1 in tickAnimalData, so a later check does not reset the day
        final AnimalState marked = born(0L, 61, 3_072_000L);
        assertThat(LivestockRules.markElderlyIfDue(marked, LivestockSpecies.PIG, 4_000_000L, 4)).isEqualTo(marked);
    }

    @Test
    void oldDayDrawFiveGivesFiveDaysOffset() {
        // TFC: 1L + random.nextInt(5) ranges 1 to 5 days; draw 4 gives 5 * 24 000 = 120 000
        // pig is adult after 2 304 000, so the check runs at 3 000 000
        final AnimalState pig = born(0L, 61, -1L);
        assertThat(LivestockRules.markElderlyIfDue(pig, LivestockSpecies.PIG, 3_000_000L, 4).oldTick())
            .isEqualTo(3_120_000L);
    }
}
