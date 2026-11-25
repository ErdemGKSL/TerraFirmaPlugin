/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.products;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.nestbox.NestFinder;
import dev.tfp.paper.nestbox.NestHandOff;
import dev.tfp.paper.nestbox.NestSeeker;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-27: the ground laying of PAR-FAUNA-06 skips a seated hen and a hen with a free nest in reach. */
class GroundLayingTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID HEN = UUID.randomUUID();

    /** The hand-off the nest box module provides, over a finder and a set of seated hens. */
    private static NestHandOff handOff(NestFinder finder, Set<UUID> seated) {
        return (hen, world, x, y, z) -> seated.contains(hen)
            || finder.hasOpen(world, x, y, z, NestSeeker.RADIUS, NestSeeker.VERTICAL);
    }

    private static boolean allowed(NestHandOff nests) {
        return ProductsModule.groundLayingAllowed(nests, HEN, WORLD, 0.5, 64, 0.5);
    }

    @Test
    void withoutTheNestBoxModuleTheHenLaysOnTheGround() {
        assertThat(allowed(null)).isTrue();
    }

    @Test
    void aSeatedHenDoesNotLayOnTheGround() {
        assertThat(allowed(handOff(new NestFinder(), Set.of(HEN)))).isFalse();
        assertThat(allowed(handOff(new NestFinder(), Set.of(UUID.randomUUID())))).isTrue();
    }

    @Test
    void aHenWithAFreeNestInReachDoesNotLayOnTheGround() {
        NestFinder finder = new NestFinder();
        finder.add(WORLD, new BlockPos(5, 64, 0));
        assertThat(allowed(handOff(finder, Set.of()))).isFalse();
    }

    @Test
    void aFullNestOrOneOutOfReachDoesNotHoldHerBack() {
        NestFinder finder = new NestFinder();
        finder.add(WORLD, new BlockPos(5, 64, 0));
        finder.add(WORLD, new BlockPos(40, 64, 0)); // beyond NestSeeker.RADIUS
        finder.add(WORLD, new BlockPos(0, 90, 0)); // beyond NestSeeker.VERTICAL
        finder.add(UUID.randomUUID(), new BlockPos(1, 64, 0)); // another world
        finder.markOpen(WORLD, new BlockPos(5, 64, 0), false);
        assertThat(allowed(handOff(finder, Set.of()))).isTrue();
        finder.markOpen(WORLD, new BlockPos(5, 64, 0), true); // an egg was taken out: room again
        assertThat(allowed(handOff(finder, Set.of()))).isFalse();
    }
}
