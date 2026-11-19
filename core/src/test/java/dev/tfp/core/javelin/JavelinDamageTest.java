/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.javelin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The thrown damage recovered from the generated attack modifier equals TFC's 1.5 x tier bonus for every TFC tier. */
class JavelinDamageTest {
    /** TFCTiers attack bonuses at c3b4d9536. */
    private static final float[] BONUSES = {2.0f, 2.5f, 3.25f, 4.0f, 4.25f, 4.75f, 5.75f, 7.0f, 9.0f};

    @Test
    void everyTfcTierRoundTrips() {
        for (float bonus : BONUSES) {
            double modifier = 0.7f * bonus; // ToolItem.productAttributes: factor x bonus as a float product
            assertThat(JavelinDamage.tierBonus(modifier)).isEqualTo(bonus);
            assertThat(JavelinDamage.thrownDamage(modifier)).isEqualTo(1.5f * bonus);
        }
    }

    @Test
    void copperThrowsFor4875() {
        assertThat(JavelinDamage.thrownDamage(0.7f * 3.25f)).isEqualTo(4.875f);
    }
}
