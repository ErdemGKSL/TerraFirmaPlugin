/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.mount;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.mount.MountInteract.Decision;
import org.junit.jupiter.api.Test;

/** Golden decisions of {@code TFCHorse.mobInteract} at c3b4d9536: baby passes, untamed is angry, tamed is mounted. */
class MountInteractGoldenTest {

    @Test
    void aBabyIsLeftToVanillaWhateverItsFamiliarity() {
        assertThat(MountInteract.decide(LivestockAge.CHILD, 0.0f)).isEqualTo(Decision.PASS);
        assertThat(MountInteract.decide(LivestockAge.CHILD, 1.0f)).isEqualTo(Decision.PASS);
    }

    @Test
    void anUntamedAdultIsAngryUpToAndIncludingFifteenPercent() {
        assertThat(MountInteract.decide(LivestockAge.ADULT, 0.15f)).isEqualTo(Decision.ANGRY);
        assertThat(MountInteract.decide(LivestockAge.OLD, 0.0f)).isEqualTo(Decision.ANGRY);
    }

    @Test
    void aTamedAdultOrOldAnimalIsMounted() {
        assertThat(MountInteract.decide(LivestockAge.ADULT, 0.1500001f)).isEqualTo(Decision.MOUNT);
        assertThat(MountInteract.decide(LivestockAge.OLD, 1.0f)).isEqualTo(Decision.MOUNT);
    }

    @Test
    void anOverburdenedPassengerIsThrownOff() {
        assertThat(MountInteract.rejectsPassenger(true)).isTrue();
        assertThat(MountInteract.rejectsPassenger(false)).isFalse();
    }
}
