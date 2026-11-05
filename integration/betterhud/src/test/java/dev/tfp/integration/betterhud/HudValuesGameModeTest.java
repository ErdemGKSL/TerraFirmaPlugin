/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.HudSnapshot;
import org.junit.jupiter.api.Test;

/** PAR-HUD-12: the game-mode gate the generated bars and popups read. */
class HudValuesGameModeTest {
    private static final HudSnapshot HOT = new HudSnapshot(45, 50, 30, 10, 1200);

    @Test
    void withNeedsTheGateIsTheMaxHealthAndThePopupShowsTheTemperature() {
        assertThat(HudValues.NEEDS_GATE).isEqualTo("tfp_max_health");
        assertThat(HudValues.NUMBERS.get(HudValues.NEEDS_GATE).applyAsDouble(HOT)).isEqualTo(50);
        assertThat(HudValues.NUMBERS.get("tfp_held_temp").applyAsDouble(HOT)).isEqualTo(1200);
    }

    /** Creative and spectator: the gate and the popup value read 0, so the layouts' {@code > 0} conditions hide them. */
    @Test
    void withoutNeedsTheGateAndThePopupReadZero() {
        HudSnapshot off = HOT.withNeedsActive(false);
        assertThat(HudValues.NUMBERS.get(HudValues.NEEDS_GATE).applyAsDouble(off)).isZero();
        assertThat(HudValues.NUMBERS.get("tfp_held_temp").applyAsDouble(off)).isZero();
        // the other values are kept, so a switch back shows them unchanged
        assertThat(HudValues.NUMBERS.get("tfp_health").applyAsDouble(off)).isEqualTo(45);
        assertThat(HudValues.NUMBERS.get("tfp_thirst").applyAsDouble(off)).isEqualTo(30);
        assertThat(HudValues.LISTENERS.get("tfp_health").applyAsDouble(off)).isEqualTo(0.9);
        assertThat(HudValues.NUMBERS.get(HudValues.NEEDS_GATE).applyAsDouble(off.withNeedsActive(true))).isEqualTo(50);
    }
}
