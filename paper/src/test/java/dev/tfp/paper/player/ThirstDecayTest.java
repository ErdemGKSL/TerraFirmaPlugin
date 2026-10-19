/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.VersionedCodec;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The lazy thirst decay on the stored data: one jump, stepped calls, the join restart and the codec migration. */
class ThirstDecayTest {
    /** A full player whose clock starts at tick 0 with no exhaustion left over. */
    private static final PlayerData START = PlayerData.NEW.withThirstClock(100f, 0L, 0L);

    @Test
    void oneCalendarDayAtMildTemperatureLosesEightPoints() {
        // The core rule: 8 points x 5 at 22 degrees (no heat share) is 40 lost, as ThirstLazyVsSteppedTest checks.
        PlayerData out = ThirstDecay.evaluate(START, 24_001, 22f);
        assertThat(out.thirst()).isEqualTo(60f);
        assertThat(out.thirstTick()).isEqualTo(24_001L);
    }

    @Test
    void sameElapsedTimeGivesTheSameValue() {
        PlayerData first = ThirstDecay.evaluate(START, 24_001, 22f);
        PlayerData second = ThirstDecay.evaluate(START, 24_001, 22f);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void steppedCallsEqualOneLazyJump() {
        // Lazy equals stepped for one case: 0 -> 12000 -> 24001 must give the same thirst as 0 -> 24001, because the
        // leftover exhaustion carries between the calls.
        PlayerData jump = ThirstDecay.evaluate(START, 24_001, 22f);
        PlayerData stepped = ThirstDecay.evaluate(ThirstDecay.evaluate(START, 12_000, 22f), 24_001, 22f);
        assertThat(stepped.thirst()).isCloseTo(jump.thirst(), within(1e-3f));
        assertThat(stepped.passiveExhaustion()).isEqualTo(jump.passiveExhaustion());
    }

    @Test
    void heatTakesItsShareOfEachPoint() {
        // At 34 degrees each point costs 5 x (1 + 0.4) = 7: 8 points give 44 left.
        assertThat(ThirstDecay.evaluate(START, 24_001, 34f).thirst()).isEqualTo(44f);
    }

    @Test
    void unknownTemperatureGivesNoHeatShare() {
        // NaN is the ambient reading before the first climate reading: it counts as the low temperature.
        assertThat(ThirstDecay.evaluate(START, 24_001, Float.NaN).thirst())
                .isEqualTo(ThirstDecay.evaluate(START, 24_001, 22f).thirst());
    }

    @Test
    void firstEvaluationStartsTheClockWithoutLoss() {
        PlayerData unset = PlayerData.NEW;
        PlayerData out = ThirstDecay.evaluate(unset, 500_000, 30f);
        assertThat(out.thirst()).isEqualTo(100f);
        assertThat(out.thirstTick()).isEqualTo(500_000L);
    }

    @Test
    void restartKeepsThirstAndMovesTheClock() {
        PlayerData restarted = ThirstDecay.restart(ThirstDecay.evaluate(START, 24_001, 22f), 900_000L);
        assertThat(restarted.thirst()).isEqualTo(60f);
        assertThat(restarted.thirstTick()).isEqualTo(900_000L);
    }

    @Test
    void v1DataMigratesToAnUnsetClock() {
        DataMap v1 = new DataMap()
                .putInt(VersionedCodec.VERSION_KEY, 1)
                .putFloat("thirst", 42f)
                .putLong("lastDrinkTick", 5L)
                .putLong("intoxication", 6L)
                .putString("chiselMode", "tfp:smooth")
                .putList("nutrition", List.of());
        PlayerData migrated = PlayerDataCodec.CODEC.decode(v1);
        assertThat(migrated.thirst()).isEqualTo(42f);
        assertThat(migrated.thirstTick()).isEqualTo(Long.MIN_VALUE);
        assertThat(migrated.passiveExhaustion()).isZero();
    }
}
