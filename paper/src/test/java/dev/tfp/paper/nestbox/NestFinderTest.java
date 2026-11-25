/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.nestbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-07: the hen's search for the nearest known nest box. */
class NestFinderTest {
    private static final UUID W = UUID.randomUUID();

    @Test
    void takesTheNearestWithinTheReachInTheSameWorld() {
        NestFinder f = new NestFinder();
        f.add(W, new BlockPos(10, 64, 0));
        f.add(W, new BlockPos(3, 64, 0));
        f.add(UUID.randomUUID(), new BlockPos(1, 64, 0));
        assertThat(f.nearest(W, 0.5, 64, 0.5, 16, 4)).contains(new BlockPos(3, 64, 0));
        assertThat(f.nearest(W, 0.5, 64, 0.5, 2, 4)).isEmpty();
        assertThat(f.nearest(W, 0.5, 80, 0.5, 16, 4)).as("too far above").isEmpty();
        assertThat(f.nearest(UUID.randomUUID(), 0.5, 64, 0.5, 16, 4)).isEmpty();
    }

    @Test
    void removedNestsAreGoneAndTiesAreStable() {
        NestFinder f = new NestFinder();
        f.add(W, new BlockPos(2, 64, 0));
        f.add(W, new BlockPos(-2, 64, 0));
        assertThat(f.nearest(W, 0.5, 64, 0.5, 16, 4)).contains(new BlockPos(-2, 64, 0));
        f.remove(W, new BlockPos(-2, 64, 0));
        assertThat(f.nearest(W, 0.5, 64, 0.5, 16, 4)).contains(new BlockPos(2, 64, 0));
        assertThat(f.size()).isEqualTo(1);
        f.remove(W, new BlockPos(2, 64, 0));
        assertThat(f.nearest(W, 0.5, 64, 0.5, 16, 4)).isEmpty();
    }
}
