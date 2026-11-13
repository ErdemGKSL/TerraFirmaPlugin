/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.hud.HudStyles;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshot.BarStates;
import org.junit.jupiter.api.Test;

/**
 * PAR-HUD-19: the placeholders of the health and thirst bar states (TFC {@code IngameOverlays.renderHealthBar} and
 * {@code renderThirst}): absorption in the fill and the text, the surplus listener, the glow flag and the overheat level.
 */
class HudValuesBarStatesTest {
    /** 17 of 17 HP shown as 850/850, with 4 absorption hearts (200 shown) and a full surplus fill of 0.2. */
    static final HudSnapshot ABSORBING = HudSnapshot.DEFAULT.withHealth(850, 850)
            .withBarStates(new BarStates(200, 0.2, false, 0L, 0));

    @Test
    void theStatePlaceholdersAreRegisteredApartFromTheBaseNumbers() {
        assertThat(HudValues.STATES.keySet()).containsExactly("tfp_absorption", "tfp_health_glow", "tfp_thirst_overheat");
        assertThat(HudValues.STATE_LISTENERS.keySet()).containsExactly("tfp_health_surplus");
        assertThat(HudValues.STATES.keySet()).doesNotContainAnyElementsOf(HudValues.NUMBERS.keySet())
                .doesNotContainAnyElementsOf(HudMountValues.NUMBERS.keySet());
        assertThat(HudValues.STATE_LISTENERS.keySet()).doesNotContainAnyElementsOf(HudValues.LISTENERS.keySet());
    }

    @Test
    void absorptionIsInTheFillTheNumberAndTheText() {
        assertThat(HudValues.NUMBERS.get("tfp_health").applyAsDouble(ABSORBING)).isEqualTo(1050);
        assertThat(HudValues.STATES.get("tfp_absorption").applyAsDouble(ABSORBING)).isEqualTo(200);
        // TFC clamps the fill to the bar.
        assertThat(HudValues.LISTENERS.get("tfp_health").applyAsDouble(ABSORBING)).isEqualTo(1);
        assertThat(HudValues.healthText(ABSORBING, HudStyles.DEFAULTS)).isEqualTo("1050/850");
        HudSnapshot hurt = HudSnapshot.DEFAULT.withHealth(425, 850).withBarStates(new BarStates(100, 0, true, 0L, 0));
        assertThat(HudValues.LISTENERS.get("tfp_health").applyAsDouble(hurt)).isEqualTo(525.0 / 850);
        assertThat(HudValues.healthText(hurt, HudStyles.DEFAULTS)).isEqualTo("525/850");
        assertThat(HudValues.STATE_LISTENERS.get("tfp_health_surplus").applyAsDouble(ABSORBING)).isEqualTo(0.2);
        assertThat(HudValues.STATE_LISTENERS.get("tfp_health_surplus").applyAsDouble(hurt)).isZero();
    }

    @Test
    void glowReadsTheSteadyFlagAndTheHurtStampAgainstTheClock() {
        assertThat(HudValues.STATES.get("tfp_health_glow").applyAsDouble(HudSnapshot.DEFAULT)).isZero();
        HudSnapshot steady = HudSnapshot.DEFAULT.withBarStates(new BarStates(0, 0, true, 0L, 0));
        assertThat(HudValues.STATES.get("tfp_health_glow").applyAsDouble(steady)).isEqualTo(1);
        long now = System.nanoTime();
        HudSnapshot hit = HudSnapshot.DEFAULT.withBarStates(BarStates.NONE.withHurtUntil(now + 60_000_000_000L));
        assertThat(HudValues.STATES.get("tfp_health_glow").applyAsDouble(hit)).isEqualTo(1);
        HudSnapshot over = HudSnapshot.DEFAULT.withBarStates(BarStates.NONE.withHurtUntil(now - 1));
        assertThat(HudValues.STATES.get("tfp_health_glow").applyAsDouble(over)).isZero();
    }

    @Test
    void overheatLevelIsPublishedAsIs() {
        HudSnapshot hot = HudSnapshot.DEFAULT.withBarStates(new BarStates(0, 0, false, 0L, 3));
        assertThat(HudValues.STATES.get("tfp_thirst_overheat").applyAsDouble(hot)).isEqualTo(3);
        assertThat(HudValues.STATES.get("tfp_thirst_overheat").applyAsDouble(HudSnapshot.DEFAULT)).isZero();
    }

    /** PAR-HUD-12: without needs (creative, spectator) every state reads 0; the bars are gated off there anyway. */
    @Test
    void statesReadZeroWithoutNeeds() {
        HudSnapshot off = HudSnapshot.DEFAULT.withBarStates(new BarStates(200, 0.5, true, 0L, 4)).withNeedsActive(false);
        for (var e : HudValues.STATES.entrySet()) {
            assertThat(e.getValue().applyAsDouble(off)).as(e.getKey()).isZero();
        }
    }
}
