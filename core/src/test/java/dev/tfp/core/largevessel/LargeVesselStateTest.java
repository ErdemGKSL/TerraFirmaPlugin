/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.largevessel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.automation.Face;
import org.junit.jupiter.api.Test;

/** PAR-POT-06: the state machine of TFC's {@code toggleSeal} and {@code handleNeighborChanged}, and the side table. */
class LargeVesselStateTest {
    @Test
    void toggleSealFlipsSealedAndKeepsPowered() {
        LargeVesselState.Change seal = LargeVesselState.FRESH.toggleSeal();
        assertThat(seal.state()).isEqualTo(new LargeVesselState(true, false));
        assertThat(seal.seal()).isEqualTo(LargeVesselState.Seal.SEAL);
        assertThat(seal.state().canModify()).isFalse();

        LargeVesselState.Change open = new LargeVesselState(true, true).toggleSeal();
        assertThat(open.state()).isEqualTo(new LargeVesselState(false, true));
        assertThat(open.seal()).isEqualTo(LargeVesselState.Seal.UNSEAL);
        assertThat(open.state().canModify()).isTrue();
    }

    @Test
    void aSignalSealsAnOpenVesselAndTheEndOfItOpensASealedOne() {
        LargeVesselState.Change on = LargeVesselState.FRESH.neighbourSignal(true);
        assertThat(on.state()).isEqualTo(new LargeVesselState(true, true));
        assertThat(on.seal()).isEqualTo(LargeVesselState.Seal.SEAL);

        LargeVesselState.Change off = on.state().neighbourSignal(false);
        assertThat(off.state()).isEqualTo(LargeVesselState.FRESH);
        assertThat(off.seal()).isEqualTo(LargeVesselState.Seal.UNSEAL);
    }

    @Test
    void aSignalThatAgreesWithTheSealOnlyRecordsPowered() {
        // sealed by hand, then a signal arrives: powered flips, the seal stays and nothing is applied to the food
        LargeVesselState.Change on = new LargeVesselState(true, false).neighbourSignal(true);
        assertThat(on.state()).isEqualTo(new LargeVesselState(true, true));
        assertThat(on.seal()).isEqualTo(LargeVesselState.Seal.NONE);
        // opened by hand while powered, then the signal ends: powered flips, still open
        LargeVesselState.Change off = new LargeVesselState(false, true).neighbourSignal(false);
        assertThat(off.state()).isEqualTo(LargeVesselState.FRESH);
        assertThat(off.seal()).isEqualTo(LargeVesselState.Seal.NONE);
    }

    @Test
    void noChangeWhenTheSignalIsTheRecordedOne() {
        assertThat(LargeVesselState.FRESH.neighbourSignal(false)).isNull();
        assertThat(new LargeVesselState(true, true).neighbourSignal(true)).isNull();
    }

    @Test
    void placingFollowsTheSignalAndTheItemSeal() {
        assertThat(LargeVesselState.placed(false, true)).isEqualTo(new LargeVesselState(false, true));
        assertThat(LargeVesselState.placed(true, false)).isEqualTo(new LargeVesselState(true, false));
    }

    @Test
    void codecRoundTrips() {
        for (boolean sealed : new boolean[] {false, true}) {
            for (boolean powered : new boolean[] {false, true}) {
                LargeVesselState s = new LargeVesselState(sealed, powered);
                assertThat(LargeVesselState.CODEC.decode(LargeVesselState.CODEC.encode(s))).isEqualTo(s);
            }
        }
    }

    @Test
    void sidesAreTfcsEveryFaceButDownInAndDownOut() {
        var table = LargeVesselSides.table();
        for (Face f : Face.values()) {
            if (f == Face.DOWN) {
                assertThat(table.insertSlots(f)).isEmpty();
                assertThat(table.extractSlots(f)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8);
            } else {
                assertThat(table.insertSlots(f)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8);
                assertThat(table.extractSlots(f)).isEmpty();
            }
        }
    }
}
