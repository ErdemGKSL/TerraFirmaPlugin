/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.misc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** PAR-DECO-24: crate and cake rules against TFC's source. */
class MiscRulesTest {
    private static List<String> slots(String... first) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < CrateRules.SLOTS; i++) {
            l.add(i < first.length ? first[i] : null);
        }
        return l;
    }

    @Test
    void doubleClickIsTenTicksInclusiveAndTheInitialStateIsNone() {
        assertThat(CrateRules.isDoubleClick(100, 90)).isTrue();
        assertThat(CrateRules.isDoubleClick(101, 90)).isFalse();
        assertThat(CrateRules.isDoubleClick(0, CrateRules.NO_CLICK)).isFalse();
    }

    @Test
    void aCrateHoldsOneKindOfItem() {
        var same = (java.util.function.BiPredicate<String, String>) String::equals;
        assertThat(CrateRules.accepts("dirt", slots(), s -> true, s -> false, same)).as("empty crate takes anything").isTrue();
        assertThat(CrateRules.accepts("dirt", slots(null, "dirt"), s -> true, s -> false, same)).isTrue();
        assertThat(CrateRules.accepts("stone", slots(null, "dirt"), s -> true, s -> false, same)).as("first non-empty slot decides").isFalse();
        assertThat(CrateRules.accepts("anvil", slots(), s -> !s.equals("anvil"), s -> false, same)).as("chest size").isFalse();
        assertThat(CrateRules.accepts("bundle", slots(), s -> true, s -> s.equals("bundle"), same)).as("containers").isFalse();
        assertThat(CrateRules.SLOTS).isEqualTo(36);
    }

    @Test
    void comparatorSignalFollowsTheFillWithOneForAnyContent() {
        assertThat(CrateRules.comparatorSignal(0, 0)).isZero();
        assertThat(CrateRules.comparatorSignal(0.01, 1)).isEqualTo(1);
        assertThat(CrateRules.comparatorSignal(36, 36)).isEqualTo(15);
        assertThat(CrateRules.comparatorSignal(18, 36)).isEqualTo(8);
    }

    @Test
    void aCakeHasSevenSlices() {
        assertThat(CakeRules.afterBite(0)).isEqualTo(OptionalInt.of(1));
        assertThat(CakeRules.afterBite(5)).isEqualTo(OptionalInt.of(6));
        assertThat(CakeRules.afterBite(6)).isEqualTo(OptionalInt.empty());
        assertThatThrownBy(() -> CakeRules.afterBite(7)).isInstanceOf(IllegalArgumentException.class);
        assertThat(CakeRules.canEat(20, false)).isFalse();
        assertThat(CakeRules.canEat(19, false)).isTrue();
        assertThat(CakeRules.canEat(20, true)).isTrue();
    }
}
