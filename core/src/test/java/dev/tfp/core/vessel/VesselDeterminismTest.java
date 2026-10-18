/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/mold/Vessel.java @ c3b4d9536 (the mutations),
 *       src/main/java/net/dries007/tfc/common/component/mold/Mold.java @ c3b4d9536 (drain, fill)
 */
package dev.tfp.core.vessel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.heat.HeatConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The rules are pure: the same inputs give the same states, the order of reads does not matter, and a random script of
 * mutations replayed with the same seed gives the same final state.
 */
class VesselDeterminismTest {
    private static final HeatConfig CFG = HeatConfig.DEFAULT;

    private static VesselState replay(long seed) {
        Random random = new Random(seed);
        VesselState vessel = VesselState.empty(0L);
        long now = 0L;
        for (int step = 0; step < 400; step++) {
            now += random.nextInt(30);
            switch (random.nextInt(4)) {
                case 0 -> {
                    String item = random.nextBoolean() ? "copper_ingot" : "bronze_ingot";
                    Melt melt = new Melt(900f + random.nextInt(400), new FluidAmount("fluid_" + item, 100), 0.5f, 1085f);
                    VesselState.InsertOutcome out = vessel.insert(now, random.nextInt(VesselRules.SLOTS), item,
                        1 + random.nextInt(16), 64, 1.5f, melt, true, CFG);
                    vessel = out.state();
                }
                case 1 -> vessel = vessel.fill(new FluidAmount("copper", 1 + random.nextInt(200)), true, 0.5f, 1085f).state();
                case 2 -> vessel = vessel.heatTo(now, random.nextFloat() * 1500f, CFG);
                default -> vessel = vessel.heatTo(now, 0f, CFG);
            }
        }
        return vessel;
    }

    @Test
    void sameSeedGivesTheSameFinalState() {
        for (long seed = 1; seed <= 25; seed++) {
            VesselState first = replay(seed);
            VesselState second = replay(seed);
            assertThat(second).as("seed %s", seed).isEqualTo(first);
            assertThat(second.temperatureAt(5_000L, CFG)).isEqualTo(first.temperatureAt(5_000L, CFG));
        }
    }

    @Test
    void readOrderDoesNotChangeTemperatures() {
        VesselState vessel = replay(7L);
        List<Long> ticks = new ArrayList<>();
        for (long t = 0; t < 2_000; t += 37) {
            ticks.add(t);
        }
        List<Float> ascending = new ArrayList<>();
        for (long t : ticks) {
            ascending.add(vessel.temperatureAt(t, CFG));
        }
        List<Float> reversed = new ArrayList<>();
        for (int i = ticks.size() - 1; i >= 0; i--) {
            reversed.add(0, vessel.temperatureAt(ticks.get(i), CFG));
        }
        assertThat(reversed).isEqualTo(ascending);
    }

    @Test
    void castDrawAtTheChanceDoesNotBreakAndBelowDoes() {
        MoldState mold = MoldState.empty(MoldKind.INGOT, 100, 0L)
            .fill(0L, new FluidAmount("copper", 100), true, 0.5f, CFG).state();
        FluidAmount required = new FluidAmount("copper", 100);
        assertThat(mold.cast(0L, CFG, required, 1085f, 0.1f, 0.1f).moldBroken()).isFalse();
        assertThat(mold.cast(0L, CFG, required, 1085f, 0.1f, 0.0999f).moldBroken()).isTrue();
        assertThat(mold.cast(0L, CFG, required, 1085f, 0.1f, 0.0999f)).isEqualTo(mold.cast(0L, CFG, required, 1085f, 0.1f, 0.0999f));
    }
}
