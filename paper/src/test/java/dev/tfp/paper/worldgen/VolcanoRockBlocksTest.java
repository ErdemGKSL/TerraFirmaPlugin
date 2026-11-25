/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.volcano.VolcanicBlock;
import dev.tfp.core.worldgen.volcano.VolcanicKind;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-15: the CraftEngine hardened rock and per-rock magma ids of the volcanic blocks. */
class VolcanoRockBlocksTest {
    @Test
    void hardenedRockForEveryRockMagmaForIgneousOnly() {
        int magma = 0;
        for (int rock = 0; rock < PlaceholderRocks.count(); rock++) {
            final String name = PlaceholderRocks.names().get(rock);
            assertThat(VolcanoBlocks.rockBlockId(VolcanicKind.HARDENED_ROCK, rock)).isEqualTo("tfp:rock/hardened/" + name);
            final String id = VolcanoBlocks.rockBlockId(VolcanicKind.MAGMA, rock);
            if (id != null) {
                assertThat(id).isEqualTo("tfp:rock/magma/" + name);
                magma++;
            }
        }
        assertThat(magma).isEqualTo(7);
        final int basalt = PlaceholderRocks.names().indexOf("basalt");
        assertThat(VolcanoBlocks.rockBlockId(new VolcanicBlock(0, 70, 0, VolcanicKind.MAGMA, basalt, null)))
            .isEqualTo("tfp:rock/magma/basalt");
        assertThat(VolcanoBlocks.rockBlockId(VolcanicKind.MAGMA, PlaceholderRocks.names().indexOf("chalk"))).isNull();
        for (VolcanicKind kind : VolcanicKind.values()) {
            if (kind != VolcanicKind.HARDENED_ROCK && kind != VolcanicKind.MAGMA) {
                assertThat(VolcanoBlocks.rockBlockId(kind, basalt)).as("%s", kind).isNull();
            }
        }
    }

    /**
     * PAR-WORLD-60: the non-rock volcanic blocks (air, lava, water, groundcover) carry rock -1 ({@code VolcanoLevel.set}); the
     * census refused every chunk holding one because the rock name was read before the kind. No rock: no CraftEngine rock id,
     * the caller keeps its fallback.
     */
    @Test
    void blocksWithoutRockHaveNoRockBlockAndDoNotThrow() {
        for (VolcanicKind kind : VolcanicKind.values()) {
            assertThat(VolcanoBlocks.rockBlockId(kind, -1)).as("%s", kind).isNull();
            if (!kind.hasRock()) {
                assertThat(VolcanoBlocks.rockBlockId(new VolcanicBlock(0, 70, 0, kind, -1, null))).as("%s", kind).isNull();
            }
        }
    }
}
