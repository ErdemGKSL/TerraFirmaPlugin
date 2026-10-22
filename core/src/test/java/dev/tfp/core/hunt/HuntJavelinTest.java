/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/loot/AnimalYieldProvider.java @ c3b4d9536, NotPredatedCondition.java and the animal loot tables
 * (hand-checked values)
 */
package dev.tfp.core.hunt;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.tool.ToolTier;
import org.junit.jupiter.api.Test;

/** Unit tests for the javelin rules, with the stone sedimentary tier as the javelin material. */
class HuntJavelinTest {
    private static final int USES = ToolTier.SEDIMENTARY.uses();

    @Test
    void aJavelinNeedsOneSpareUseToStartAThrow() {
        assertThat(USES).isEqualTo(50);
        assertThat(HuntJavelin.canStartThrow(USES - 2, USES)).isTrue();
        assertThat(HuntJavelin.canStartThrow(USES - 1, USES)).isFalse();
        assertThat(HuntJavelin.canStartThrow(0, USES)).isTrue();
    }

    @Test
    void aThrowNeedsTenTicksOfCharge() {
        assertThat(HuntJavelin.releasesThrow(9)).isFalse();
        assertThat(HuntJavelin.releasesThrow(10)).isTrue();
        assertThat(HuntJavelin.MIN_CHARGE_TICKS).isEqualTo(10);
    }

    @Test
    void aThrowCostsOneDurabilityAndNeverBreaksTheStack() {
        assertThat(HuntJavelin.DURABILITY_PER_THROW).isEqualTo(1);
        for (int damage = 0; damage <= USES - 2; damage++) {
            assertThat(HuntJavelin.canStartThrow(damage, USES)).isTrue();
            assertThat(HuntJavelin.breaksOnThrow(damage, USES)).as("damage %d", damage).isFalse();
        }
    }

    @Test
    void thrownDamageIsOneAndAHalfTimesTheTierBonus() {
        // Sedimentary stone: attack damage bonus 2.0, so 3 damage, as the TFC tooltip shows.
        assertThat(HuntJavelin.thrownDamage(ToolTier.SEDIMENTARY)).isEqualTo(3f);
        assertThat(HuntJavelin.thrownDamage(4f)).isEqualTo(6f);
        assertThat(HuntJavelin.NOT_A_JAVELIN_DAMAGE).isEqualTo(8f);
    }
}
