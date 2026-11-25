/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.growth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/** PAR-FARM-29: the sweeper plays the shapes' random ticks on live blocks, and the survival queue's commit removes. */
class ShapeGrowthMockBukkitTest extends MockServerTest {
    private static FakeCustomBlocks blocks() {
        return new FakeCustomBlocks().known(VineGrowth.ID, "tfp:plant/hanging_vines", "tfp:plant/hanging_vines_plant",
            CactusGrowth.TOP, CactusGrowth.BODY, KelpTreeGrowth.BODY, KelpTreeGrowth.FLOWER);
    }

    private static World loaded(World world) {
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        return world;
    }

    @Test
    void aVineGrowsDownAStoneWallWhenTheSweeperPlaysItsTicks() {
        FakeCustomBlocks blocks = blocks();
        World world = loaded(server.addSimpleWorld("vine"));
        for (int y = 60; y < 80; y++) {
            for (int z = 0; z < 8; z++) {
                world.getBlockAt(1, y, z).setType(Material.STONE);
            }
        }
        Block vine = world.getBlockAt(0, 70, 3);
        BlockStateRef state = BlockStateRef.of(VineGrowth.ID).with("east", "true");
        blocks.place(vine, state, false);
        ColumnSweeper sweeper = new ColumnSweeper(blocks, null, w -> 3, (b, t) -> false, (w, x, y, z, e) -> 8);
        // many sweeps of the same vine: re-read each time (a stale state is dropped)
        for (int i = 0; i < 400; i++) {
            Optional<BlockStateRef> now = blocks.stateAt(vine);
            if (now.isEmpty()) {
                break;
            }
            sweeper.play(new ColumnSweeper.Shaped(vine, now.get(), ShapeGrowth.MAX_TICKS), 100_000);
        }
        long vines = blocks.placed().values().stream().filter(s -> s.id().equals(VineGrowth.ID)).count();
        assertThat(vines).isGreaterThanOrEqualTo(2L);
        // a stale decision (the vine changed since the scan) plays nothing
        assertThat(sweeper.play(new ColumnSweeper.Shaped(vine, BlockStateRef.of(VineGrowth.ID).with("south", "true"), 8), 1)).isFalse();
    }

    @Test
    void aVineInTheColdPlaysNothing() {
        FakeCustomBlocks blocks = blocks();
        World world = loaded(server.addSimpleWorld("coldvine"));
        for (int y = 60; y < 80; y++) {
            world.getBlockAt(1, y, 3).setType(Material.STONE);
        }
        Block vine = world.getBlockAt(0, 70, 3);
        BlockStateRef state = BlockStateRef.of(VineGrowth.ID).with("east", "true");
        blocks.place(vine, state, false);
        ColumnSweeper sweeper = new ColumnSweeper(blocks, null, w -> 3, (b, t) -> false, (w, x, y, z, e) -> 0);
        for (int i = 0; i < 100; i++) {
            sweeper.play(new ColumnSweeper.Shaped(vine, state, ShapeGrowth.MAX_TICKS), 100_000);
        }
        assertThat(blocks.placed()).hasSize(1);
    }

    @Test
    void aColumnWithoutItsAnchorIsRemovedAndTheNextBlockIsAskedFor() {
        FakeCustomBlocks blocks = blocks();
        World world = loaded(server.addSimpleWorld("pop"));
        Block body = world.getBlockAt(4, 70, 4);
        Block head = world.getBlockAt(4, 69, 4);
        BlockStateRef bodyState = BlockStateRef.of("tfp:plant/hanging_vines_plant");
        BlockStateRef headState = BlockStateRef.of("tfp:plant/hanging_vines").with("age", "2");
        blocks.place(body, bodyState, false);
        blocks.place(head, headState, false);
        ShapeSurvivalQueue queue = new ShapeSurvivalQueue(blocks, (b, t) -> false, null);
        ShapeSurvivalQueue.Snapshot snapshot = queue.snapshot(world, 4, 70, 4).orElseThrow();
        ShapeSurvival.Verdict verdict = ShapeSurvival.check(snapshot.cube(), 4, 70, 4, snapshot.state());
        assertThat(verdict.keep()).isFalse();
        // the anchor above is air (no leaves, no sturdy face): the body is removed; the stale state is left alone
        queue.commit(world, 4, 69, 4, headState.with("age", "9"), verdict);
        assertThat(blocks.stateAt(body)).isPresent();
        blocks.remove(world.getBlockAt(4, 69, 4));
        blocks.place(world.getBlockAt(4, 69, 4), headState, false);
        try {
            queue.commit(world, 4, 70, 4, bodyState, verdict);
        } catch (NullPointerException expectedNoScheduler) {
            // the removal asks the neighbours for a check through the scheduler (none in this test)
        }
        assertThat(blocks.stateAt(body)).isEmpty();
    }

    @Test
    void aGrowingPlantOnAStoneAnchorStays() {
        FakeCustomBlocks blocks = blocks();
        World world = loaded(server.addSimpleWorld("stay"));
        world.getBlockAt(4, 71, 4).setType(Material.STONE);
        Block body = world.getBlockAt(4, 70, 4);
        blocks.place(body, BlockStateRef.of("tfp:plant/hanging_vines_plant"), false);
        ShapeSurvivalQueue queue = new ShapeSurvivalQueue(blocks, (b, t) -> false, null);
        ShapeSurvivalQueue.Snapshot snapshot = queue.snapshot(world, 4, 70, 4).orElseThrow();
        assertThat(ShapeSurvival.check(snapshot.cube(), 4, 70, 4, snapshot.state()).keep()).isTrue();
        // a plain vanilla block is not a growing plant
        assertThat(queue.snapshot(world, 4, 71, 4)).isEmpty();
    }
}
