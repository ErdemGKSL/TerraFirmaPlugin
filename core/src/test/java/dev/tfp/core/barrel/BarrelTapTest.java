/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.barrel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.vessel.FluidAmount;
import org.junit.jupiter.api.Test;

/** PAR-PROC-38: the lazy tap against TFC's 1 mB per tick {@code tickPouring}. */
class BarrelTapTest {
    private static final int CAPACITY = BarrelRules.DEFAULT_CAPACITY;
    private static final String WATER = "minecraft:water";

    private static BarrelData source(int mB) {
        return BarrelData.FRESH.withFluid(mB == 0 ? FluidAmount.EMPTY : new FluidAmount(WATER, mB));
    }

    private static int room(FluidAmount target) {
        return BarrelTap.room(target, WATER, CAPACITY, true);
    }

    /** TFC stepped: every tick, 1 mB when the source has fluid and the target takes it. */
    private static int[] stepped(int source, int target, long ticks, int capacity) {
        for (long t = 0; t < ticks; t++) {
            if (source > 0 && target < capacity) {
                source--;
                target++;
            }
        }
        return new int[] {source, target};
    }

    @Test
    void lazyEqualsTfcsTickByTickPourWhenTheTapStaysOpen() {
        for (int[] c : new int[][] {{1000, 0, 300}, {50, 0, 300}, {1000, CAPACITY - 20, 300}, {7, 3, 1}, {0, 0, 50}}) {
            int[] want = stepped(c[0], c[1], c[2], CAPACITY);
            // one access at the start, one at the end
            BarrelData s = BarrelTap.settle(source(c[0]), 1000, true, tank(c[1]), room(tank(c[1]))).source();
            BarrelTap.Result lazy = BarrelTap.settle(s, 1000 + c[2], true, tank(c[1]), room(tank(c[1])));
            assertThat(lazy.source().fluid().amount()).as("source %s", java.util.Arrays.toString(c)).isEqualTo(want[0]);
            assertThat(amount(lazy.target())).isEqualTo(want[1]);
            assertThat(lazy.moved()).isEqualTo(c[0] - want[0]);
            // an access on every tick gives the same
            BarrelData step = BarrelTap.settle(source(c[0]), 1000, true, tank(c[1]), room(tank(c[1]))).source();
            FluidAmount t = tank(c[1]);
            for (long now = 1001; now <= 1000 + c[2]; now++) {
                BarrelTap.Result r = BarrelTap.settle(step, now, true, t, room(t));
                step = r.source();
                t = r.target();
            }
            assertThat(step.fluid()).isEqualTo(lazy.source().fluid());
            assertThat(t).isEqualTo(lazy.target());
        }
    }

    @Test
    void creditsOnlyWhenTheTapWasOpenAtBothEnds() {
        BarrelData s = BarrelTap.settle(source(500), 0, false, FluidAmount.EMPTY, 0).source();
        assertThat(s.pourTick()).isZero();
        assertThat(s.pourSeen()).isFalse();
        BarrelTap.Result opened = BarrelTap.settle(s, 100, true, FluidAmount.EMPTY, CAPACITY);
        assertThat(opened.moved()).as("closed at the start").isZero();
        assertThat(opened.source().pourSeen()).isTrue();
        BarrelTap.Result closed = BarrelTap.settle(opened.source(), 200, false, FluidAmount.EMPTY, 0);
        assertThat(closed.moved()).as("closed at the end").isZero();
        BarrelTap.Result again = BarrelTap.settle(BarrelTap.settle(closed.source(), 300, true, FluidAmount.EMPTY, CAPACITY)
            .source(), 340, true, FluidAmount.EMPTY, CAPACITY);
        assertThat(again.moved()).isEqualTo(40);
        assertThat(again.source().fluid().amount()).isEqualTo(460);
        assertThat(again.target()).isEqualTo(new FluidAmount(WATER, 40));
    }

    @Test
    void aSealedSourceAnotherFluidOrAFullTargetMoveNothing() {
        BarrelData open = BarrelTap.settle(source(500), 0, true, FluidAmount.EMPTY, CAPACITY).source();
        BarrelData sealed = open.sealedAt(50, false);
        assertThat(BarrelTap.settle(sealed, 100, true, FluidAmount.EMPTY, CAPACITY).moved()).isZero();
        assertThat(BarrelTap.settle(sealed, 100, true, FluidAmount.EMPTY, CAPACITY).source().pourSeen()).isFalse();
        FluidAmount olive = new FluidAmount("tfc:olive_oil", 10);
        assertThat(BarrelTap.room(olive, WATER, CAPACITY, true)).isZero();
        assertThat(BarrelTap.settle(open, 100, true, olive, BarrelTap.room(olive, WATER, CAPACITY, true)).target()).isEqualTo(olive);
        assertThat(BarrelTap.room(FluidAmount.EMPTY, WATER, CAPACITY, false)).as("not usable in a barrel").isZero();
        FluidAmount full = new FluidAmount(WATER, CAPACITY);
        assertThat(BarrelTap.settle(open, 100, true, full, room(full)).moved()).isZero();
        assertThat(BarrelTap.canPour(open, true)).isTrue();
        assertThat(BarrelTap.canPour(open, false)).as("upright").isFalse();
        assertThat(BarrelTap.canPour(source(0), true)).isFalse();
    }

    @Test
    void theTapClockRoundTripsAndOldRecordsReadAsNeverSettled() {
        BarrelData d = BarrelTap.settle(source(500), 77, true, FluidAmount.EMPTY, CAPACITY).source();
        assertThat(BarrelData.CODEC.fromBytes(BarrelData.CODEC.toBytes(d))).isEqualTo(d);
        assertThat(BarrelData.FRESH.pourTick()).isEqualTo(BarrelRules.NO_TICK);
        // the first settle only starts the clock
        assertThat(BarrelTap.settle(source(500), 77, true, FluidAmount.EMPTY, CAPACITY).moved()).isZero();
        // a settle at the same tick with nothing to change returns the same state
        assertThat(BarrelTap.settle(d, 77, true, FluidAmount.EMPTY, CAPACITY).source()).isSameAs(d);
    }

    private static FluidAmount tank(int mB) {
        return mB == 0 ? FluidAmount.EMPTY : new FluidAmount(WATER, mB);
    }

    private static int amount(FluidAmount f) {
        return f.isEmpty() ? 0 : f.amount();
    }
}
