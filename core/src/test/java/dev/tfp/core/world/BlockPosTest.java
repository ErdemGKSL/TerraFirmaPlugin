/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BlockPosTest {
    @Test
    void chunkMathHandlesNegatives() {
        BlockPos p = new BlockPos(-1, 64, -17);
        assertThat(p.chunkX()).isEqualTo(-1);
        assertThat(p.chunkZ()).isEqualTo(-2);
        assertThat(ChunkPos.fromKey(p.chunkKey())).isEqualTo(new ChunkPos(-1, -2));
    }

    @Test
    void chunkKeysAreDistinct() {
        assertThat(ChunkPos.key(1, 0)).isNotEqualTo(ChunkPos.key(0, 1));
        assertThat(ChunkPos.key(-1, 0)).isNotEqualTo(ChunkPos.key(0, -1));
        assertThat(ChunkPos.fromKey(ChunkPos.key(Integer.MIN_VALUE, Integer.MAX_VALUE)))
                .isEqualTo(new ChunkPos(Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void oppositeDirections() {
        for (Direction d : Direction.values()) {
            assertThat(d.opposite().opposite()).isEqualTo(d);
            assertThat(new BlockPos(0, 0, 0).offset(d).offset(d.opposite())).isEqualTo(new BlockPos(0, 0, 0));
        }
    }
}
