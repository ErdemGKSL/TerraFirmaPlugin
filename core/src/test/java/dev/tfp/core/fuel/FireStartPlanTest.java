/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/FirestarterItem.java @ c3b4d9536, FlintAndPyriteItem.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536
 */
package dev.tfp.core.fuel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.fuel.FireStart.Outcome;
import dev.tfp.core.fuel.FireStart.Strength;
import dev.tfp.core.fuel.FirepitPile.Entry;
import dev.tfp.core.fuel.FirepitPile.Kind;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** PAR-HEAT-05: the pre-drawn plan of a fire start (draw order, chances) and TFC's ground item scan. */
class FireStartPlanTest {
    /** Returns the given floats in order. */
    private static RandomGenerator draws(float... values) {
        return new RandomGenerator() {
            int i;

            @Override
            public long nextLong() {
                throw new UnsupportedOperationException();
            }

            @Override
            public float nextFloat() {
                return values[i++];
            }
        };
    }

    @Test
    void firestarterIsStrongAndRainScalesTheFirepitChance() {
        FireStartPlan dry = FireStartPlan.firestarter(FireStartPlan.FIRESTARTER_CHANCE, false, draws(0.42f));
        assertThat(dry.strength()).isEqualTo(Strength.STRONG);
        assertThat(dry.firepitBase()).isEqualTo(0.5);
        assertThat(dry.firepitRoll()).isEqualTo(0.42f);
        assertThat(FireStartPlan.firestarter(0.5, true, draws(0f)).firepitBase()).isEqualTo(0.15, within(1e-12));
    }

    @Test
    void flintAndPyriteDrawsStrengthFirstThenTheFirepitRoll() {
        FireStartPlan strong = FireStartPlan.flintAndPyrite(0.25, false, draws(0.1f, 0.9f));
        assertThat(strong.strength()).isEqualTo(Strength.STRONG);
        assertThat(strong.firepitRoll()).isEqualTo(0.9f);
        assertThat(strong.firepitBase()).isEqualTo(0.25);
        FireStartPlan weak = FireStartPlan.flintAndPyrite(0.25, true, draws(0.25f, 0.0f));
        assertThat(weak.strength()).isEqualTo(Strength.WEAK);
        // TFC: raining ? 0.3 : 1 * chance
        assertThat(weak.firepitBase()).isEqualTo(0.3);
    }

    @Test
    void resolveUsesThePlanRoll() {
        FireStartPlan plan = FireStartPlan.firestarter(0.5, false, draws(0.55f));
        assertThat(plan.resolve(false, true, 3, 0, true)).isEqualTo(Outcome.FIREPIT_MISSED);
        assertThat(plan.resolve(false, true, 3, 1, true)).isEqualTo(Outcome.FIREPIT); // 0.55 < 0.6
        assertThat(plan.resolve(false, true, 2, 5, true)).isEqualTo(Outcome.FIRE);
        assertThat(plan.resolve(false, false, 3, 5, true)).isEqualTo(Outcome.FIRE);
        assertThat(plan.resolve(true, true, 3, 5, true)).isEqualTo(Outcome.CANCELLED);
        assertThat(FireStartPlan.fixed(Strength.WEAK, FireStart.NO_FIREPIT, draws(0f)).resolve(false, true, 9, 0, true))
            .isEqualTo(Outcome.NONE);
        assertThat(FireStartPlan.fixed(Strength.STRONG, FireStart.NO_FIREPIT, draws(0f)).resolve(false, true, 9, 0, true))
            .isEqualTo(Outcome.FIRE);
    }

    @Test
    void scanCountsInTfcOrderAndCapsLogsAtFour() {
        FirepitPile.Scan scan = FirepitPile.scan(List.of(new Entry(Kind.STICK, 2), new Entry(Kind.OTHER, 5),
            new Entry(Kind.LOG, 3), new Entry(Kind.KINDLING, 2), new Entry(Kind.STICK, 1), new Entry(Kind.LOG, 3),
            new Entry(Kind.LOG, 1)));
        assertThat(scan.sticks()).isEqualTo(3);
        assertThat(scan.kindling()).isEqualTo(2);
        // three logs from entry 2, one from entry 5 (partly taken, still removed), entry 6 is not used: four were taken
        assertThat(scan.logs()).containsExactly(2, 2, 2, 5);
        assertThat(scan.used()).containsExactly(0, 2, 3, 4, 5);
        assertThat(scan.hasLogs()).isTrue();
        assertThat(FirepitPile.scan(List.of(new Entry(Kind.STICK, 3))).hasLogs()).isFalse();
    }
}
