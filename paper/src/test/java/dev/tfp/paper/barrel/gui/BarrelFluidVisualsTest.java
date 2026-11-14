/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.vessel.FluidAmount;
import org.junit.jupiter.api.Test;

/** PAR-PROC-09: TFC's barrel fluid surface and rain fill numbers. */
class BarrelFluidVisualsTest {
    @Test
    void theSurfaceRisesFromTheBottomToTheRimAndShrinksWhenNearlyEmpty() {
        assertThat(BarrelFluidVisuals.surface(0, 10_000)).isNull();
        BarrelFluidVisuals.Surface half = BarrelFluidVisuals.surface(5000, 10_000);
        assertThat(half.y()).isCloseTo(0.140625f + 0.734375f * 0.5f, within(1e-6f));
        assertThat(half.minX()).isEqualTo(0.1875f);
        assertThat(half.maxZ()).isEqualTo(0.8125f);
        BarrelFluidVisuals.Surface full = BarrelFluidVisuals.surface(10_000, 10_000);
        assertThat(full.y()).isCloseTo(0.875f, within(1e-6f));
        BarrelFluidVisuals.Surface low = BarrelFluidVisuals.surface(100, 10_000); // 1 %: inset grows by 0.02 * 7
        assertThat(low.minX()).isCloseTo(0.1875f + 0.14f, within(1e-5f));
        assertThat(low.maxX()).isCloseTo(0.8125f - 0.14f, within(1e-5f));
    }

    @Test
    void rainChecksAreTheGameTicksDivisibleByFour() {
        assertThat(BarrelFluidVisuals.rainChecks(0, 1)).isEqualTo(1);
        assertThat(BarrelFluidVisuals.rainChecks(1, 4)).isZero();
        assertThat(BarrelFluidVisuals.rainChecks(1, 5)).isEqualTo(1);
        assertThat(BarrelFluidVisuals.rainChecks(0, 400)).isEqualTo(100);
        assertThat(BarrelFluidVisuals.rainChecks(10, 10)).isZero();
        // lazy equals stepped: splitting an interval anywhere gives the same count
        for (long split = 3; split < 97; split += 7) {
            assertThat(BarrelFluidVisuals.rainChecks(3, split) + BarrelFluidVisuals.rainChecks(split, 97))
                .isEqualTo(BarrelFluidVisuals.rainChecks(3, 97));
        }
        long stepped = 0;
        for (long t = 3; t < 97; t++) {
            stepped += t % 4 == 0 ? 1 : 0;
        }
        assertThat(BarrelFluidVisuals.rainChecks(3, 97)).isEqualTo(stepped);
    }

    @Test
    void rainFillsOnlyEmptyOrWaterTanksUpToCapacity() {
        assertThat(BarrelFluidVisuals.rainFilled(FluidAmount.EMPTY, 10_000, 25))
            .isEqualTo(new FluidAmount("minecraft:water", 25));
        assertThat(BarrelFluidVisuals.rainFilled(new FluidAmount("minecraft:water", 9990), 10_000, 25))
            .isEqualTo(new FluidAmount("minecraft:water", 10_000));
        FluidAmount brine = new FluidAmount("tfp:brine", 100);
        assertThat(BarrelFluidVisuals.rainFilled(brine, 10_000, 25)).isSameAs(brine);
        FluidAmount water = new FluidAmount("minecraft:water", 10_000);
        assertThat(BarrelFluidVisuals.rainFilled(water, 10_000, 25)).isSameAs(water);
        assertThat(BarrelFluidVisuals.rainFilled(FluidAmount.EMPTY, 10_000, 0)).isEqualTo(FluidAmount.EMPTY);
    }
}
