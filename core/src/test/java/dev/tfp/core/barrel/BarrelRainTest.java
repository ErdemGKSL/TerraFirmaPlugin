/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (serverTick: the rain fill)
 */
package dev.tfp.core.barrel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** PAR-PROC-35: the lazy rain fill against TFC's stepped one (1 mB per tick divisible by 4 while it rains). */
class BarrelRainTest {
    static final int CAPACITY = BarrelRules.DEFAULT_CAPACITY;

    /** TFC's serverTick loop: one mB on each raining tick % 4 == 0, unsealed, upright. */
    static int stepped(boolean[] rain, long start, int initial) {
        int amount = initial;
        for (int i = 0; i < rain.length; i++) {
            long tick = start + i;
            if (rain[i] && Math.floorMod(tick, BarrelRain.PERIOD_TICKS) == 0) {
                amount = Math.min(CAPACITY, amount + 1);
            }
        }
        return amount;
    }

    /** Random rain spells: true/false runs of {@code unit} x 1..2000/unit ticks. */
    static boolean[] timeline(Random r, int length, int unit) {
        boolean[] out = new boolean[length];
        boolean raining = r.nextBoolean();
        int i = 0;
        while (i < length) {
            int run = unit * (1 + r.nextInt(2000 / unit));
            for (int j = 0; j < run && i < length; j++, i++) {
                out[i] = raining;
            }
            raining = !raining;
        }
        return out;
    }

    static int lazy(boolean[] rain, long start, int initial, List<Integer> accesses) {
        BarrelData data = BarrelData.FRESH.withFluid(initial == 0 ? FluidAmount.EMPTY : new FluidAmount(TfcFluids.WATER, initial));
        data = BarrelRain.settle(data, start, rain[0], true, CAPACITY);
        for (int at : accesses) {
            boolean now = at < rain.length ? rain[at] : rain[rain.length - 1];
            data = BarrelRain.settle(data, start + at, now, true, CAPACITY);
        }
        return data.fluid().amount();
    }

    @Test
    void checksCountTheTicksDivisibleByFour() {
        assertThat(BarrelRain.checks(0, 4)).isEqualTo(1); // 0
        assertThat(BarrelRain.checks(1, 4)).isZero();
        assertThat(BarrelRain.checks(1, 5)).isEqualTo(1); // 4
        assertThat(BarrelRain.checks(-7, 1)).isEqualTo(2); // -4, 0
        assertThat(BarrelRain.checks(0, 400)).as("the in-game step: 400 ticks give 100 mB").isEqualTo(100);
        assertThat(BarrelRain.checks(5, 5)).isZero();
    }

    @Test
    void accessesAtEveryRainChangeMatchTfcExactly() {
        Random r = new Random(35);
        for (int round = 0; round < 200; round++) {
            // spells start and end on check ticks, so an access one tick before a change sees the old spell and no check is
            // between it and the change
            boolean[] rain = timeline(r, 4 * (1000 + r.nextInt(5000)), BarrelRain.PERIOD_TICKS);
            long start = 4L * (r.nextInt(200) - 100);
            List<Integer> accesses = new ArrayList<>();
            for (int i = 1; i < rain.length; i++) {
                if (rain[i] != rain[i - 1]) {
                    // the access at the first tick of the new spell, and one just before it, seeing the old spell
                    accesses.add(i - 1);
                    accesses.add(i);
                }
                if (r.nextInt(500) == 0) {
                    accesses.add(i);
                }
            }
            accesses.add(rain.length);
            int initial = r.nextInt(3) == 0 ? 0 : r.nextInt(CAPACITY);
            // stepped over [start, start + length): the last access at start + length settles exactly that interval
            int expected = stepped(rain, start, initial);
            assertThat(lazy(rain, start, initial, accesses.stream().distinct().sorted().toList()))
                .as("round %d", round).isEqualTo(expected);
        }
    }

    @Test
    void sparseAccessesOnlyCountIntervalsRainyAtBothEnds() {
        Random r = new Random(9);
        for (int round = 0; round < 200; round++) {
            boolean[] rain = timeline(r, 20_000, 1);
            List<Integer> accesses = new ArrayList<>();
            for (int at = 1 + r.nextInt(3000); at < rain.length; at += 1 + r.nextInt(3000)) {
                accesses.add(at);
            }
            int expected = 0;
            int prev = 0;
            for (int at : accesses) {
                if (rain[prev] && rain[at]) {
                    expected = (int) Math.min(CAPACITY, expected + BarrelRain.checks(prev, at));
                }
                prev = at;
            }
            assertThat(lazy(rain, 0, 0, accesses)).as("round %d", round).isEqualTo(expected);
        }
    }

    @Test
    void sealedSidewaysOrAnotherFluidTakesNoRain() {
        BarrelData open = BarrelData.FRESH.withRain(0, true);
        assertThat(BarrelRain.settle(open, 400, true, true, CAPACITY).fluid()).isEqualTo(new FluidAmount(TfcFluids.WATER, 100));
        assertThat(BarrelRain.settle(open, 400, true, false, CAPACITY).fluid()).as("sideways").isEqualTo(FluidAmount.EMPTY);
        BarrelData sealed = BarrelData.FRESH.sealedAt(0, false).withRain(0, true);
        assertThat(BarrelRain.settle(sealed, 400, true, true, CAPACITY).fluid()).isEqualTo(FluidAmount.EMPTY);
        BarrelData brine = BarrelData.FRESH.withFluid(new FluidAmount("tfp:brine", 10)).withRain(0, true);
        assertThat(BarrelRain.settle(brine, 400, true, true, CAPACITY).fluid()).isEqualTo(new FluidAmount("tfp:brine", 10));
        BarrelData full = BarrelData.FRESH.withFluid(new FluidAmount(TfcFluids.WATER, CAPACITY - 3)).withRain(0, true);
        assertThat(BarrelRain.settle(full, 400, true, true, CAPACITY).fluid().amount()).isEqualTo(CAPACITY);
        assertThat(BarrelRain.settle(BarrelData.FRESH, 400, true, true, CAPACITY).fluid())
            .as("no rain clock yet: the first settle only starts it").isEqualTo(FluidAmount.EMPTY);
    }

    @Test
    void theRainClockRoundTripsAndOldRecordsReadWithoutIt() {
        BarrelData d = BarrelData.FRESH.withFluid(new FluidAmount(TfcFluids.WATER, 7)).withRain(1234, true);
        assertThat(BarrelData.CODEC.fromBytes(BarrelData.CODEC.toBytes(d))).isEqualTo(d);
    }
}
