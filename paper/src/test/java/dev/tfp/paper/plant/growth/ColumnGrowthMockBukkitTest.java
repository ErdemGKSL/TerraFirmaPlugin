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
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/** PAR-FARM-25: column growth within TFC's age 25 cap and the free cells. */
class ColumnGrowthMockBukkitTest extends MockServerTest {
    private static final String HEAD = "tfp:plant/hanging_vines";
    private static final String BODY = "tfp:plant/hanging_vines_plant";

    private FakeCustomBlocks blocks() {
        return new FakeCustomBlocks().known(HEAD, BODY, "tfp:plant/winged_kelp", "tfp:plant/winged_kelp_plant");
    }

    @Test
    void theHeadsAreTfcsWeepingTwistingAndKelpPlants() {
        assertThat(ColumnGrowth.HEADS).hasSize(10);
        assertThat(ColumnGrowth.kind(HEAD)).get().extracting(ColumnGrowth.Kind::dy).isEqualTo(-1);
        assertThat(ColumnGrowth.kind("tfp:plant/tree_fern")).get().extracting(ColumnGrowth.Kind::dy).isEqualTo(1);
        assertThat(ColumnGrowth.kind("tfp:plant/winged_kelp")).get().extracting(ColumnGrowth.Kind::water).isEqualTo(true);
        assertThat(ColumnGrowth.kind("tfp:plant/winged_kelp").get().body()).isEqualTo("tfp:plant/winged_kelp_plant");
        assertThat(ColumnGrowth.kind("tfp:plant/allium")).isEmpty();
    }

    @Test
    void theGrownBlocksNeverPassTheAgeCapOrTheFreeCells() {
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            int age = random.nextInt(26);
            int free = random.nextInt(40);
            int grown = ColumnGrowth.grown(age, free, 5_000_000L, 3, random);
            assertThat(grown).isBetween(0, Math.min(free, 25 - age));
        }
        assertThat(ColumnGrowth.grown(25, 10, 5_000_000L, 3, random)).isZero();
        // the free cells cap a long absence (age 20 has room 5 but only 3 cells are free), and a long absence grows something
        int most = 0;
        for (int i = 0; i < 50; i++) {
            most = Math.max(most, ColumnGrowth.grown(20, 3, 5_000_000L, 3, random));
        }
        assertThat(most).isBetween(1, 3);
    }

    @Test
    void aWeepingColumnGrowsDownwardTheOldHeadBecomesABody() {
        FakeCustomBlocks blocks = blocks();
        World world = server.addSimpleWorld("column");
        Block head = world.getBlockAt(5, 70, 5);
        BlockStateRef state = BlockStateRef.of(HEAD).with("age", "3");
        blocks.place(head, state, false);
        ColumnSweeper sweeper = new ColumnSweeper(blocks, null, w -> 3);
        ColumnGrowth.Kind kind = ColumnGrowth.kind(HEAD).orElseThrow();
        assertThat(sweeper.free(head, kind, 22)).isEqualTo(22);
        assertThat(sweeper.grow(new ColumnSweeper.Growth(head, state, kind, 22, 2))).isTrue();
        assertThat(blocks.stateAt(world.getBlockAt(5, 70, 5))).get().extracting(BlockStateRef::id).isEqualTo(BODY);
        assertThat(blocks.stateAt(world.getBlockAt(5, 69, 5))).get().extracting(BlockStateRef::id).isEqualTo(BODY);
        BlockStateRef newHead = blocks.stateAt(world.getBlockAt(5, 68, 5)).orElseThrow();
        assertThat(newHead.id()).isEqualTo(HEAD);
        assertThat(newHead.properties()).containsEntry("age", "5");
    }

    @Test
    void anObstacleStopsTheColumnAndAChangedHeadIsLeftAlone() {
        FakeCustomBlocks blocks = blocks();
        World world = server.addSimpleWorld("column2");
        Block head = world.getBlockAt(8, 70, 8);
        BlockStateRef state = BlockStateRef.of(HEAD).with("age", "0");
        blocks.place(head, state, false);
        world.getBlockAt(8, 68, 8).setType(Material.STONE);
        ColumnSweeper sweeper = new ColumnSweeper(blocks, null, w -> 3);
        ColumnGrowth.Kind kind = ColumnGrowth.kind(HEAD).orElseThrow();
        assertThat(sweeper.free(head, kind, 25)).isEqualTo(1);
        assertThat(sweeper.grow(new ColumnSweeper.Growth(head, state, kind, 25, 5))).isTrue();
        assertThat(blocks.stateAt(world.getBlockAt(8, 69, 8)).orElseThrow().properties()).containsEntry("age", "1");
        // the head was changed meanwhile: the stale decision is dropped
        assertThat(sweeper.grow(new ColumnSweeper.Growth(head, state, kind, 25, 1))).isFalse();
    }

    @Test
    void kelpGrowsThroughWaterAndKeepsItsFluid() {
        FakeCustomBlocks blocks = blocks();
        World world = server.addSimpleWorld("kelp");
        Block head = world.getBlockAt(2, 40, 2);
        for (int y = 41; y < 46; y++) {
            world.getBlockAt(2, y, 2).setType(Material.WATER);
        }
        BlockStateRef state = BlockStateRef.of("tfp:plant/winged_kelp").with("age", "10").with("fluid", "salt_water");
        blocks.place(head, state, false);
        ColumnSweeper sweeper = new ColumnSweeper(blocks, null, w -> 3);
        ColumnGrowth.Kind kind = ColumnGrowth.kind("tfp:plant/winged_kelp").orElseThrow();
        assertThat(sweeper.free(head, kind, 15)).isEqualTo(5);
        assertThat(sweeper.grow(new ColumnSweeper.Growth(head, state, kind, 5, 3))).isTrue();
        BlockStateRef body = blocks.stateAt(world.getBlockAt(2, 41, 2)).orElseThrow();
        assertThat(body.id()).isEqualTo("tfp:plant/winged_kelp_plant");
        assertThat(body.properties()).containsEntry("fluid", "salt_water");
        assertThat(blocks.stateAt(world.getBlockAt(2, 43, 2)).orElseThrow().properties()).containsEntry("age", "13");
    }
}
