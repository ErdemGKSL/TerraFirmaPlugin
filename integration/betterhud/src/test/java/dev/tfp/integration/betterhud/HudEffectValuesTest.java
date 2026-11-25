/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PAR-HUD-20: the ink placeholder. */
class HudEffectValuesTest {
    @Test
    void nameIsTheCatalogueOneAndNewToTheOtherTables() {
        assertThat(HudEffectValues.NUMBERS.keySet()).containsExactly("tfp_ink");
        assertThat(HudEffectValues.NUMBERS.keySet()).doesNotContainAnyElementsOf(HudValues.NUMBERS.keySet());
        assertThat(HudEffectValues.NUMBERS.keySet()).doesNotContainAnyElementsOf(HudMountValues.NUMBERS.keySet());
    }

    @Test
    void aPlayerWithoutInkReadsZero() {
        assertThat(HudEffectValues.NUMBERS.get("tfp_ink").applyAsInt(UUID.randomUUID())).isZero();
    }
}
