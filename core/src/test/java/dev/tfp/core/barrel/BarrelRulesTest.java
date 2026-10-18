/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (canModify gates fill and drain),
 * src/main/java/net/dries007/tfc/common/capabilities/InventoryFluidTank.java @ c3b4d9536
 */
package dev.tfp.core.barrel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.vessel.FluidAmount;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BarrelRulesTest {

    private static final int CAP = BarrelRules.DEFAULT_CAPACITY;

    @Test
    void capacityDefaultIsTfcs() {
        assertThat(BarrelRules.DEFAULT_CAPACITY).isEqualTo(10_000);
    }

    /** Random fills never exceed the offer or the room, and a sealed or refused fill takes nothing. */
    @Test
    void fillNeverExceedsOfferOrRoom() {
        final Random random = new Random(42L);
        for (int i = 0; i < 2000; i++) {
            final int stored = random.nextInt(CAP + 1);
            final String fluid = random.nextBoolean() ? "tfc:water" : "tfc:milk";
            final FluidAmount held = stored == 0 ? FluidAmount.EMPTY : new FluidAmount("tfc:water", stored);
            final FluidAmount offered = new FluidAmount(fluid, random.nextInt(CAP * 2));
            final int accepted = BarrelRules.fillAccepted(held, false, true, offered, CAP);
            assertThat(accepted).isBetween(0, offered.amount());
            assertThat(stored + accepted).isLessThanOrEqualTo(CAP);
            if (held.isEmpty() || fluid.equals("tfc:water")) {
                continue;
            }
            assertThat(accepted).isZero();
        }
        assertThat(BarrelRules.fillAccepted(FluidAmount.EMPTY, true, true, new FluidAmount("tfc:water", 5), CAP)).isZero();
        assertThat(BarrelRules.fillAccepted(FluidAmount.EMPTY, false, false, new FluidAmount("tfc:water", 5), CAP)).isZero();
    }

    @Test
    void drainGivesNothingWhileSealed() {
        assertThat(BarrelRules.drainAccepted(new FluidAmount("tfc:water", 300), true, 100)).isZero();
        assertThat(BarrelRules.drainAccepted(new FluidAmount("tfc:water", 300), false, 100)).isEqualTo(100);
        assertThatThrownBy(() -> BarrelRules.drainAccepted(FluidAmount.EMPTY, false, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fillAndDrainThroughTheState() {
        final BarrelState open = BarrelState.unsealed(FluidAmount.EMPTY, BarrelStack.EMPTY);
        final BarrelState.FillOutcome first = open.fill(new FluidAmount("tfc:water", CAP + 5), true, CAP);
        assertThat(first.accepted()).isEqualTo(CAP);
        assertThat(first.state().fluid()).isEqualTo(new FluidAmount("tfc:water", CAP));
        final BarrelState.DrainOutcome out = first.state().drain(CAP);
        assertThat(out.drained()).isEqualTo(CAP);
        assertThat(out.state().fluid().isEmpty()).isTrue();
        final BarrelState sealed = first.state().seal(1, BarrelFixtures.CHAIN);
        assertThat(sealed.drain(10).drained()).isZero();
        assertThat(sealed.fill(new FluidAmount("tfc:water", 10), true, CAP).accepted()).isZero();
    }
}
