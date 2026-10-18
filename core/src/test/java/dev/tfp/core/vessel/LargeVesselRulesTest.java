/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.vessel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.item.ItemSize;
import dev.tfp.core.item.ItemSizing;
import dev.tfp.core.item.ItemWeight;
import org.junit.jupiter.api.Test;

/**
 * The large vessel's item acceptance grid, against TFC's {@code isItemValid}: sealed vessels take nothing, unsealed vessels
 * take items strictly smaller than {@code LARGE}. The rows are the TFC size enum in order, which the rule reads by value.
 */
class LargeVesselRulesTest {
    @Test
    void slotCountIsTfc() {
        assertThat(LargeVesselRules.SLOTS).isEqualTo(9);
    }

    @Test
    void acceptanceGridMatchesTfc() {
        // from TFC at c3b4d9536: size|unsealedAccepts
        boolean[] unsealed = {true, true, true, true, false, false, false};
        ItemSize[] sizes = ItemSize.values();
        for (int i = 0; i < sizes.length; i++) {
            ItemSizing item = new ItemSizing(sizes[i], ItemWeight.LIGHT);
            assertThat(LargeVesselRules.acceptsItem(false, item)).as("unsealed %s", sizes[i]).isEqualTo(unsealed[i]);
            assertThat(LargeVesselRules.acceptsItem(true, item)).as("sealed %s", sizes[i]).isFalse();
        }
    }

    @Test
    void extractionNeedsUnsealed() {
        assertThat(LargeVesselRules.canExtract(false)).isTrue();
        assertThat(LargeVesselRules.canExtract(true)).isFalse();
    }
}
