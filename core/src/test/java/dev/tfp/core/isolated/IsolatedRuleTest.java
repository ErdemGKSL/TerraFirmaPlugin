/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.isolated;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.Direction;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** PAR-PHYS-06: TFC's {@code WorldTracker.isIsolated} and the queued-position check around it. */
class IsolatedRuleTest {
    private static Function<Direction, IsolatedCell> all(IsolatedCell cell) {
        return d -> cell;
    }

    private static Function<Direction, IsolatedCell> allEmptyBut(Direction side, IsolatedCell cell) {
        Map<Direction, IsolatedCell> m = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) {
            m.put(d, d == side ? cell : IsolatedCell.EMPTY);
        }
        return m::get;
    }

    @Test
    void allSixNeighboursEmptyBreaks() {
        assertThat(IsolatedRule.isIsolated(all(IsolatedCell.EMPTY))).isTrue();
        assertThat(IsolatedRule.shouldBreak(IsolatedCell.RAW, all(IsolatedCell.EMPTY))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void anyNeighbourWithCollisionKeeps(Direction side) {
        assertThat(IsolatedRule.shouldBreak(IsolatedCell.RAW, allEmptyBut(side, IsolatedCell.SOLID))).isFalse();
        // another raw rock is support too (TFC only looks at the collision shape)
        assertThat(IsolatedRule.shouldBreak(IsolatedCell.RAW, allEmptyBut(side, IsolatedCell.RAW))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void aTagMemberWithoutCollisionIsNoSupport(Direction side) {
        assertThat(IsolatedRule.shouldBreak(IsolatedCell.RAW, allEmptyBut(side, IsolatedCell.of(true, false))))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void anUnknownNeighbourKeeps(Direction side) {
        assertThat(IsolatedRule.shouldBreak(IsolatedCell.RAW, allEmptyBut(side, null))).isFalse();
    }

    @Test
    void blockThatLeftTheTagKeeps() {
        assertThat(IsolatedRule.shouldBreak(IsolatedCell.SOLID, all(IsolatedCell.EMPTY))).isFalse();
        assertThat(IsolatedRule.shouldBreak(IsolatedCell.EMPTY, all(IsolatedCell.EMPTY))).isFalse();
        assertThat(IsolatedRule.shouldBreak(null, all(IsolatedCell.EMPTY))).isFalse();
    }

    @Test
    void cellFactorySharesTheConstants() {
        assertThat(IsolatedCell.of(true, true)).isSameAs(IsolatedCell.RAW);
        assertThat(IsolatedCell.of(false, true)).isSameAs(IsolatedCell.SOLID);
        assertThat(IsolatedCell.of(false, false)).isSameAs(IsolatedCell.EMPTY);
        assertThat(IsolatedCell.of(true, false)).isEqualTo(new IsolatedCell(true, false));
        assertThat(IsolatedRule.BREAKS_WHEN_ISOLATED).isEqualTo("tfp:breaks_when_isolated");
    }
}
