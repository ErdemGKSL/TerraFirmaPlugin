/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/**
 * M1-TEST-02 sample: the CraftEngine adapter fake on real MockBukkit blocks, the pattern for feature tests
 * that place custom blocks without CraftEngine.
 */
class FakeCustomBlocksMockBukkitTest extends MockServerTest {
    @Test
    void placesReadsAndRemovesPerWorld() {
        World a = server.addSimpleWorld("a");
        World b = server.addSimpleWorld("b");
        FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:rock/raw/granite");
        BlockStateRef granite = BlockStateRef.of("tfp:rock/raw/granite");
        Block at = a.getBlockAt(1, 64, 1);

        assertThat(blocks.place(at, granite, false)).isTrue();
        assertThat(blocks.place(at, BlockStateRef.of("tfp:unknown"), false)).isFalse();
        assertThat(blocks.stateAt(a.getBlockAt(1, 64, 1))).contains(granite);
        assertThat(blocks.stateAt(b.getBlockAt(1, 64, 1))).isEmpty();
        assertThat(blocks.remove(at)).isTrue();
        assertThat(blocks.stateAt(at)).isEmpty();
    }
}
