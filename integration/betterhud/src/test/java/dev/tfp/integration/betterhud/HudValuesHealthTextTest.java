/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.health.HealthDisplayStyle;
import dev.tfp.paper.hud.HudStyles;
import dev.tfp.paper.hud.HudStylesHolder;
import dev.tfp.paper.integration.HudSnapshot;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** PAR-HUD-17: {@code tfp_health_text} follows {@code hud.health-display-style} through the published styles. */
class HudValuesHealthTextTest {
    /** 17 HP of 17 max on TFC's x 50 scale, as PlayerNeedsHudService publishes it. */
    static final HudSnapshot FULL = HudSnapshot.DEFAULT.withHealth(850, 850);

    static HudStyles health(HealthDisplayStyle style) {
        return new HudStyles(style, ClimateTemperatureStyle.DEFAULT, true, HudStyles.DisabledExperienceBarStyle.HOVER);
    }

    @AfterEach
    void reset() {
        HudStylesHolder.publish(HudStyles.DEFAULTS);
    }

    @Test
    void eachStyleFormatsTheShownHealthAsTfc() {
        assertThat(HudValues.healthText(FULL, health(HealthDisplayStyle.TFC))).isEqualTo("850/850");
        assertThat(HudValues.healthText(FULL, health(HealthDisplayStyle.VANILLA))).isEqualTo("17.0/17.0");
        assertThat(HudValues.healthText(FULL, health(HealthDisplayStyle.TFC_CURRENT))).isEqualTo("850");
        assertThat(HudValues.healthText(FULL, health(HealthDisplayStyle.VANILLA_CURRENT))).isEqualTo("17.0");
        HudSnapshot hurt = HudSnapshot.DEFAULT.withHealth(637.5, 850);
        assertThat(HudValues.healthText(hurt, health(HealthDisplayStyle.TFC))).isEqualTo("638/850");
        assertThat(HudValues.healthText(hurt, health(HealthDisplayStyle.VANILLA))).isEqualTo("12.8/17.0");
    }

    @Test
    void theRegisteredPlaceholderReadsThePublishedStyles() {
        Function<HudSnapshot, String> text = HudValues.TEXTS.get("tfp_health_text");
        assertThat(HudValues.TEXTS.keySet()).containsExactly("tfp_health_text");
        assertThat(text.apply(FULL)).isEqualTo("850/850");
        HudStylesHolder.publish(health(HealthDisplayStyle.VANILLA));
        assertThat(text.apply(FULL)).isEqualTo("17.0/17.0");
        HudStylesHolder.publish(HudStyles.DEFAULTS);
        assertThat(text.apply(FULL)).isEqualTo("850/850");
        // Before the first publish the snapshot default (50/50 shown) is formatted like any other value.
        assertThat(text.apply(HudSnapshot.DEFAULT)).isEqualTo("50/50");
    }

    @Test
    void aMaxThatCannotBeShownIsEmpty() {
        HudStyles s = HudStyles.DEFAULTS;
        assertThat(HudValues.healthText(HudSnapshot.DEFAULT.withHealth(0, 0), s)).isEmpty();
        assertThat(HudValues.healthText(HudSnapshot.DEFAULT.withHealth(10, Double.NaN), s)).isEmpty();
        assertThat(HudValues.healthText(HudSnapshot.DEFAULT.withHealth(Double.NaN, 850), s)).isEmpty();
        assertThat(HudValues.healthText(HudSnapshot.DEFAULT.withHealth(0, 850), s)).isEqualTo("0/850");
    }

    @Test
    void healthTextDoesNotCollideWithTheOtherPlaceholders() {
        assertThat(HudValues.TEXTS.keySet()).doesNotContainAnyElementsOf(HudClimateValues.TEXTS.keySet())
                .doesNotContainAnyElementsOf(HudClockValues.TEXTS.keySet())
                .doesNotContainAnyElementsOf(HudValues.NUMBERS.keySet());
    }
}
