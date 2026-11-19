/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.prospect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PropickTiersTest {
    @Test
    void levelsAndKeys() {
        assertThat(PropickTiers.level("copper")).isEqualTo(1);
        assertThat(PropickTiers.level("red_steel")).isEqualTo(6);
        assertThat(PropickTiers.level("x")).isZero();
        assertThat(PropickTiers.metalOf("tfp:metal/propick/steel")).isEqualTo("steel");
        assertThat(PropickTiers.metalOf("tfp:metal/propick_head/steel")).isNull();
        assertThat(PropickTiers.prospectedKey("tfp:ore/normal_native_copper/granite"))
            .isEqualTo("block.tfc.ore.normal_native_copper.granite.prospected");
        assertThat(PropickTiers.prospectedKey("tfp:halite")).isEqualTo("block.tfc.halite.prospected");
        assertThat(PropickTiers.messageKey(ProspectResult.NOTHING)).isEqualTo("tfc.tooltip.propick.nothing");
        assertThat(PropickTiers.messageKey(ProspectResult.VERY_LARGE)).isEqualTo("tfc.tooltip.propick.found_very_large");
    }
}
