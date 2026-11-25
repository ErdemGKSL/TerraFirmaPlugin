/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.coral;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.coral.CoralRules;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-50 on MockBukkit: the support rule removes an unsupported coral, wall fan or sea pickle through the custom
 * block (which drops TFC's loot, as TFC's {@code updateOrDestroy}), one tick after the update, and keeps supported ones,
 * also on a CraftEngine support (its real block answers the face test).
 */
class CoralSupportQueueTest extends MockServerTest {
    World world;
    FakeCustomBlocks custom;
    CoralMockBukkitTest.Ticks ticks;
    CoralSupportQueue queue;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("coral_support");
        custom = new FakeCustomBlocks();
        CoralRules.all().forEach(c -> custom.known(c.id()));
        custom.known(CoralRules.SEA_PICKLE, "tfp:rock/raw/granite");
        ticks = new CoralMockBukkitTest.Ticks();
        // MockBukkit has no isFaceSturdy: solid blocks are sturdy on every face
        queue = new CoralSupportQueue(ticks, custom, (data, face, support) -> data.getMaterial().isSolid());
        world.loadChunk(0, 0);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                world.getBlockAt(x, 63, z).setType(Material.STONE);
                for (int y = 64; y <= 66; y++) {
                    world.getBlockAt(x, y, z).setType(Material.WATER);
                }
            }
        }
    }

    static BlockStateRef state(String id, String... kv) {
        final Map<String, String> p = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            p.put(kv[i], kv[i + 1]);
        }
        return BlockStateRef.of(id, p);
    }

    @Test
    void supportedCoralStays() {
        final Block b = world.getBlockAt(1, 64, 1);
        custom.place(b, state("tfp:coral/tube_coral", "waterlogged", "true"), false);
        queue.request(b);
        assertThat(ticks.later).hasSize(1);
        ticks.elapse();
        assertThat(custom.stateAt(b)).isPresent();
        assertThat(queue.pending()).isZero();
    }

    @Test
    void coralWithoutFloorIsRemovedWithItsDrops() {
        final Block b = world.getBlockAt(1, 65, 1);
        custom.place(b, state("tfp:coral/tube_coral_fan", "waterlogged", "true"), false);
        queue.request(b);
        // a second update in the same tick coalesces
        queue.request(b);
        assertThat(ticks.later).hasSize(1);
        assertThat(custom.stateAt(b)).isPresent();
        ticks.elapse();
        assertThat(custom.stateAt(b)).isEmpty();
    }

    @Test
    void wallFanNeedsItsWall() {
        // a north-facing fan hangs on the block to its south
        final Block fan = world.getBlockAt(2, 65, 2);
        final BlockStateRef s = state("tfp:coral/brain_coral_wall_fan", "facing", "north", "waterlogged", "true");
        custom.place(fan, s, false);
        world.getBlockAt(2, 65, 3).setType(Material.STONE);
        queue.request(fan);
        ticks.elapse();
        assertThat(custom.stateAt(fan)).contains(s);
        world.getBlockAt(2, 65, 3).setType(Material.WATER);
        queue.request(fan);
        ticks.elapse();
        assertThat(custom.stateAt(fan)).isEmpty();
    }

    @Test
    void craftEngineSupportAnswersByItsRealBlock() {
        final Block floor = world.getBlockAt(3, 63, 3);
        custom.place(floor, BlockStateRef.of("tfp:rock/raw/granite"), false);
        final Block pickle = world.getBlockAt(3, 64, 3);
        custom.place(pickle, state(CoralRules.SEA_PICKLE, "pickles", "2", "waterlogged", "true"), false);
        queue.request(pickle);
        ticks.elapse();
        assertThat(custom.stateAt(pickle)).isPresent();
        final Block high = world.getBlockAt(3, 66, 3);
        custom.place(high, state(CoralRules.SEA_PICKLE, "pickles", "1", "waterlogged", "true"), false);
        queue.request(high);
        ticks.elapse();
        assertThat(custom.stateAt(high)).isEmpty();
    }

    @Test
    void changedBlockIsNotRemoved() {
        final Block b = world.getBlockAt(4, 65, 4);
        custom.place(b, state("tfp:coral/fire_coral", "waterlogged", "true"), false);
        final CoralSupportQueue.Snapshot s = queue.snapshot(b).orElseThrow();
        assertThat(s.unsupported()).isTrue();
        final BlockStateRef dead = state("tfp:coral/fire_dead_coral", "waterlogged", "false");
        custom.place(b, dead, false);
        queue.commit(b, s);
        assertThat(custom.stateAt(b)).contains(dead);
    }

    @Test
    void otherBlocksAreIgnored() {
        queue.request(world.getBlockAt(0, 64, 0));
        assertThat(ticks.later).isEmpty();
    }
}
