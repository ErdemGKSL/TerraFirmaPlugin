/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.collapse.explosion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.ExplosionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-PHYS-05: explosion events reach the sink with the centre and the unfiltered block list. */
class ExplosionCollapseListenerMockBukkitTest extends MockServerTest {
    private record Call(BlockPos center, List<BlockPos> blown) {}

    private final List<Call> calls = new ArrayList<>();
    private WorldMock world;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("boom");
        server.getPluginManager().registerEvents(
                new ExplosionCollapseListener((w, c, b) -> calls.add(new Call(c, b))), plugin);
    }

    private List<Block> stones(int n) {
        List<Block> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Block b = world.getBlockAt(i, 70, 3);
            b.setType(Material.STONE);
            list.add(b);
        }
        return list;
    }

    @Test
    void anEntityExplosionPassesItsCentreAndBlocks() {
        Player player = server.addPlayer();
        server.getPluginManager().callEvent(new EntityExplodeEvent(player,
                new org.bukkit.Location(world, 4.7, 69.2, -1.5), stones(3), 1f, ExplosionResult.DESTROY));
        assertThat(calls).singleElement().satisfies(c -> {
            assertThat(c.center()).isEqualTo(new BlockPos(4, 69, -2));
            assertThat(c.blown()).containsExactly(new BlockPos(0, 70, 3), new BlockPos(1, 70, 3), new BlockPos(2, 70, 3));
        });
    }

    @Test
    void aBlockExplosionUsesTheSourceBlock() {
        Block source = world.getBlockAt(8, 64, 8);
        List<Block> blown = stones(2);
        server.getPluginManager().callEvent(new BlockExplodeEvent(source, source.getState(), blown, 1f,
                ExplosionResult.DESTROY));
        assertThat(calls).singleElement().satisfies(c -> {
            assertThat(c.center()).isEqualTo(new BlockPos(8, 64, 8));
            assertThat(c.blown()).hasSize(2);
        });
    }

    @Test
    void anEmptyListAndACancelledEventAreIgnored() {
        Player player = server.addPlayer();
        server.getPluginManager().callEvent(new EntityExplodeEvent(player, player.getLocation(), new ArrayList<>(), 1f,
                ExplosionResult.DESTROY));
        EntityExplodeEvent cancelled = new EntityExplodeEvent(player, player.getLocation(), stones(1), 1f,
                ExplosionResult.DESTROY);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        assertThat(calls).isEmpty();
    }

    @Test
    void theListIsReadBeforeAHighPriorityFilterEmptiesIt() {
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.HIGH)
            public void filter(EntityExplodeEvent event) {
                event.blockList().clear();
            }
        }, plugin);
        Player player = server.addPlayer();
        server.getPluginManager().callEvent(new EntityExplodeEvent(player, player.getLocation(), stones(4), 1f,
                ExplosionResult.DESTROY));
        assertThat(calls).singleElement().satisfies(c -> assertThat(c.blown()).hasSize(4));
    }
}
