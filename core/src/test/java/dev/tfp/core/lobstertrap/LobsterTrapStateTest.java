/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.lobstertrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.fishing.LobsterTrap;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-19: the persisted bait and its link to the trap rules. */
class LobsterTrapStateTest {
    @Test
    void baitRoundTripsThroughTheCodec() {
        for (int bait = 0; bait <= LobsterTrap.BAIT_PER_ITEM; bait++) {
            LobsterTrapState s = new LobsterTrapState(bait);
            assertThat(LobsterTrapState.CODEC.decode(LobsterTrapState.CODEC.encode(s))).isEqualTo(s);
        }
    }

    @Test
    void contentsJoinBaitAndOccupiedSlots() {
        assertThat(new LobsterTrapState(2).contents(4)).isEqualTo(new LobsterTrap.Contents(2, 4));
        assertThat(LobsterTrapState.of(new LobsterTrap.Contents(1, 5)).bait()).isEqualTo(1);
        assertThat(LobsterTrap.state(LobsterTrapState.FRESH.contents(0))).isEqualTo(LobsterTrap.State.OPEN);
    }

    @Test
    void negativeBaitIsRefused() {
        assertThatThrownBy(() -> new LobsterTrapState(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
