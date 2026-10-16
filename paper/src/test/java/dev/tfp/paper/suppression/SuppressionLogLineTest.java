/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.suppression;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The grep-able INFO line the listener logs for each replaced block. Pure: no Bukkit needed. */
class SuppressionLogLineTest {
    @Test
    void formatIsStableAndGreppable() {
        assertThat(SuppressionListener.suppressLine("BlockFormEvent", "tfc", 12, -3, 407, "minecraft:lava",
                "minecraft:stone"))
                .isEqualTo("suppress: BlockFormEvent at tfc 12,-3,407: minecraft:lava -> minecraft:stone");
    }

    @Test
    void negativeZeroAndWorldNamesWithSlashesArePassedThrough() {
        assertThat(SuppressionListener.suppressLine("BlockFormEvent", "world_nether", 0, 0, 0, "minecraft:water",
                "minecraft:stone"))
                .isEqualTo("suppress: BlockFormEvent at world_nether 0,0,0: minecraft:water -> minecraft:stone");
    }
}
