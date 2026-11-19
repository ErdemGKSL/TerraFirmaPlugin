/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.strip;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.tree.Wood;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-GATHER-08: the strip table against TFC's {@code Wood.BlockType} and {@code BambooBlock}. */
class StripTableTest {
    @Test
    void everyWoodStripsLogAndWood() {
        for (Wood w : Wood.values()) {
            String n = w.serializedName();
            assertThat(StripTable.stripped("tfp:wood/log/" + n)).contains("tfp:wood/stripped_log/" + n);
            assertThat(StripTable.stripped("tfp:wood/wood/" + n)).contains("tfp:wood/stripped_wood/" + n);
            assertThat(StripTable.stripped("tfp:wood/stripped_log/" + n)).isEmpty();
            assertThat(StripTable.stripped("tfp:wood/stripped_wood/" + n)).isEmpty();
        }
        assertThat(Wood.values()).hasSize(20);
        assertThat(StripTable.pairs()).hasSize(41);
    }

    @Test
    void goldenBambooStripsToVanilla() {
        assertThat(StripTable.stripped("tfp:golden_bamboo_block")).contains("minecraft:stripped_bamboo_block");
        assertThat(StripTable.stripped("minecraft:oak_log")).isEmpty();
    }

    @Test
    void propertiesKeepAxisAndBranchDirection() {
        Map<String, String> src = Map.of("axis", "x", "branch_direction", "north", "other", "1");
        assertThat(StripTable.copyProperties("tfp:wood/stripped_log/oak", src))
                .containsOnly(Map.entry("axis", "x"), Map.entry("branch_direction", "north"));
        assertThat(StripTable.copyProperties("minecraft:stripped_bamboo_block", src)).containsOnly(Map.entry("axis", "x"));
    }
}
