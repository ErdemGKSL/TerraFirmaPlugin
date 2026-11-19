/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.barrel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.barrel.BarrelRedstone.Action;
import org.junit.jupiter.api.Test;

/** PAR-PROC-42: golden table of TFC's {@code handleNeighborChanged} (powered x sealed x signal). */
class BarrelRedstoneTest {
    @Test
    void theEightCasesOfTfcsRule() {
        // TFC: if (signal != POWERED) { if (signal != SEALED) {POWERED = SEALED = signal; onSeal/onUnseal} else POWERED = signal }
        assertThat(BarrelRedstone.decide(false, false, false)).isEqualTo(Action.NONE);
        assertThat(BarrelRedstone.decide(false, true, false)).isEqualTo(Action.NONE);
        assertThat(BarrelRedstone.decide(true, false, true)).isEqualTo(Action.NONE);
        assertThat(BarrelRedstone.decide(true, true, true)).isEqualTo(Action.NONE);
        assertThat(BarrelRedstone.decide(false, false, true)).as("signal on, open: seals").isEqualTo(Action.SEAL);
        assertThat(BarrelRedstone.decide(false, true, true)).as("signal on, sealed by hand: records only")
            .isEqualTo(Action.RECORD);
        assertThat(BarrelRedstone.decide(true, true, false)).as("signal off, sealed: unseals").isEqualTo(Action.UNSEAL);
        assertThat(BarrelRedstone.decide(true, false, false)).as("signal off, opened by hand: records only")
            .isEqualTo(Action.RECORD);
    }

    @Test
    void theRecordedSignalRoundTripsThroughTheCodec() {
        BarrelData on = BarrelData.FRESH.withPowered(true);
        assertThat(BarrelData.CODEC.decode(BarrelData.CODEC.encode(on)).powered()).isTrue();
        assertThat(BarrelData.CODEC.decode(BarrelData.CODEC.encode(BarrelData.FRESH)).powered()).isFalse();
    }
}
