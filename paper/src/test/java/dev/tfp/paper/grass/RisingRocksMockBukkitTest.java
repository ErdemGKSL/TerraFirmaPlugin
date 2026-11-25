/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.grass;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-GATHER-14: the gates of TFC's rising rock and its placement on grass. */
class RisingRocksMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks()
        .known("tfp:grass/mollisol", "tfp:rock/raw/granite", "tfp:rock/loose/granite");
    private World world;
    private RisingRocks rocks;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("rising_rock");
        world.loadChunk(0, 0);
        rocks = new RisingRocks(blocks, () -> ClockSnapshot.UNSET);
    }

    private static RisingRocks.Snapshot snapshot(List<double[]> players, BlockPos... spots) {
        return new RisingRocks.Snapshot(java.util.Arrays.stream(spots).map(RisingRocks.Spot::new).toList(), players, true, 1L, 0, 0);
    }

    private void grass(int x, int y, int z) {
        Block g = world.getBlockAt(x, y, z);
        g.setType(Material.GRASS_BLOCK);
        blocks.place(g, BlockStateRef.of("tfp:grass/mollisol"), false);
    }

    private void rockUnder(int x, int y, int z) {
        Block r = world.getBlockAt(x, y, z);
        r.setType(Material.STONE);
        blocks.place(r, BlockStateRef.of("tfp:rock/raw/granite"), false);
    }

    @Test
    void aRockRisesOnlyInSpringInTheColdAwayFromPlayers() {
        BlockPos spot = new BlockPos(3, 65, 3);
        RisingRocks.Snapshot calm = snapshot(List.of(), spot);
        assertThat(rocks.decide(calm, 50, 1.0, new Random(1), p -> 5.0)).containsExactly(spot);
        // above 8 C no rock; the boundary 8 C still rises (TFC: temperature > 8 stops it)
        assertThat(rocks.decide(calm, 50, 1.0, new Random(1), p -> 9.0)).isEmpty();
        assertThat(rocks.decide(calm, 50, 1.0, new Random(1), p -> 8.0)).containsExactly(spot);
        // a chance of 0 disables it
        assertThat(rocks.decide(calm, 50, 0.0, new Random(1), p -> 5.0)).isEmpty();
        // not spring
        RisingRocks.Snapshot summer = new RisingRocks.Snapshot(calm.spots(), List.of(), false, 1L, 0, 0);
        assertThat(rocks.decide(summer, 50, 1.0, new Random(1), p -> 5.0)).isEmpty();
        // a player within 20 blocks (19 away) stops it, one 21 away does not, one unreadable counts as near
        assertThat(rocks.decide(snapshot(List.of(new double[] {3, 65, 22}), spot), 50, 1.0, new Random(1), p -> 5.0)).isEmpty();
        assertThat(rocks.decide(snapshot(List.of(new double[] {3, 65, 24}), spot), 50, 1.0, new Random(1), p -> 5.0))
            .containsExactly(spot);
        assertThat(rocks.decide(snapshot(List.of(new double[] {Double.NaN, 0, 0}), spot), 50, 1.0, new Random(1), p -> 5.0)).isEmpty();
    }

    @Test
    void oneRockPerFiveByFiveAndNoTicksNoRock() {
        RisingRocks.Snapshot s = snapshot(List.of(), new BlockPos(3, 65, 3), new BlockPos(4, 65, 3), new BlockPos(9, 65, 3));
        List<BlockPos> risen = rocks.decide(s, 50, 1.0, new Random(2), p -> 0.0);
        assertThat(risen).hasSize(2).contains(new BlockPos(9, 65, 3));
        assertThat(rocks.decide(s, 0, 1.0, new Random(2), p -> 0.0)).isEmpty();
    }

    @Test
    void theRockIsThatUnderTheGrassAndAClearanceOfTwoIsKept() {
        grass(3, 64, 3);
        rockUnder(3, 61, 3);
        assertThat(rocks.apply(world, List.of(new BlockPos(3, 65, 3)))).isEqualTo(1);
        assertThat(blocks.stateAt(world.getBlockAt(3, 65, 3)))
            .contains(BlockStateRef.of("tfp:rock/loose/granite", Map.of("count", "1")));
        grass(5, 64, 3); // two blocks from the rock: refused although a rock lies under it
        rockUnder(5, 62, 3);
        assertThat(rocks.apply(world, List.of(new BlockPos(5, 65, 3)))).isZero();
        grass(6, 64, 3); // three blocks: allowed
        rockUnder(6, 62, 3);
        assertThat(rocks.apply(world, List.of(new BlockPos(6, 65, 3)))).isEqualTo(1);
        // no rock under the grass, or a block already there: nothing
        grass(12, 64, 12);
        assertThat(rocks.apply(world, List.of(new BlockPos(12, 65, 12)))).isZero();
        world.getBlockAt(9, 65, 3).setType(Material.STONE);
        grass(9, 64, 3);
        rockUnder(9, 62, 3);
        assertThat(rocks.apply(world, List.of(new BlockPos(9, 65, 3)))).isZero();
    }

    @Test
    void springFollowsTheHemisphere() {
        boolean found = false;
        for (long t = 0; t < 400L * 24_000L * 8L; t += 24_000L * 3L) {
            boolean north = RisingRocks.spring(t, 8, true);
            boolean south = RisingRocks.spring(t, 8, false);
            assertThat(north && south).isFalse();
            found |= north && !south;
        }
        assertThat(found).isTrue();
    }
}
