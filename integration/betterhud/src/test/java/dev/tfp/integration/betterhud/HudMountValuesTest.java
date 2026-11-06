/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.player.MountHudService.MountReading;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PAR-HUD-09: the mount placeholders and listener, riding and not riding. */
class HudMountValuesTest {
    @Test
    void namesAreTheCatalogueOnes() {
        assertThat(HudMountValues.NUMBERS.keySet()).containsExactly("tfp_mount_health", "tfp_mount_max_health");
        assertThat(HudMountValues.LISTENERS.keySet()).containsExactly("tfp_mount_health");
        assertThat(HudMountValues.NUMBERS.keySet()).doesNotContainAnyElementsOf(HudValues.NUMBERS.keySet());
    }

    @Test
    void ridingAHurtHorseReadsItsHealthOnTfcScale() {
        MountReading horse = new MountReading(800, 1100);
        assertThat(HudMountValues.NUMBERS.get("tfp_mount_health").applyAsDouble(horse)).isEqualTo(800);
        assertThat(HudMountValues.NUMBERS.get("tfp_mount_max_health").applyAsDouble(horse)).isEqualTo(1100);
        assertThat(HudMountValues.LISTENERS.get("tfp_mount_health").applyAsDouble(horse)).isEqualTo(800.0 / 1100);
        assertThat(HudMountValues.LISTENERS.get("tfp_mount_health").applyAsDouble(new MountReading(1300, 1100)))
            .as("absorption above the max fills the bar").isEqualTo(1);
    }

    @Test
    void notRidingReadsZeroSoTheBarHides() {
        MountReading none = HudMountValues.READINGS.apply(UUID.randomUUID());
        assertThat(none).isEqualTo(MountReading.NONE);
        assertThat(HudMountValues.NUMBERS.get("tfp_mount_health").applyAsDouble(none)).isZero();
        assertThat(HudMountValues.NUMBERS.get("tfp_mount_max_health").applyAsDouble(none)).isZero();
        assertThat(HudMountValues.LISTENERS.get("tfp_mount_health").applyAsDouble(none)).isZero();
    }
}
