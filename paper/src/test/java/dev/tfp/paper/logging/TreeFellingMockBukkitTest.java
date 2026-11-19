/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.logging;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-GATHER-03 on MockBukkit with a fake CraftEngine and a queue standing in for the region scheduler. */
class TreeFellingMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:wood/log/oak", "tfp:wood/wood/oak");
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final List<BlockBreakEvent> breaks = new ArrayList<>();
    private final List<Float> rolls = new ArrayList<>();
    private int wears;
    private int axeLife = Integer.MAX_VALUE;
    private World world;
    private PlayerMock player;
    private TreeFelling felling;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("fell");
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.getChunkAt(cx, cz);
            }
        }
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        felling = new TreeFelling(blocks, new TreeFelling.Env() {
            @Override
            public boolean isAxe(ItemStack stack) {
                return stack.getType() == Material.IRON_AXE || stack.getType() == Material.STONE_AXE;
            }

            @Override
            public boolean isInefficient(ItemStack stack) {
                return stack.getType() == Material.STONE_AXE;
            }

            @Override
            public boolean isLoggingBlock(BlockStateRef state) {
                return state.id().startsWith("tfp:wood/");
            }

            @Override
            public void region(World w, int chunkX, int chunkZ, Runnable task) {
                queue.add(task);
            }

            @Override
            public void regionDelayed(World w, int chunkX, int chunkZ, long delayTicks, Runnable task) {
                queue.add(task);
            }

            @Override
            public boolean wear(Player p) {
                wears++;
                return --axeLife > 0;
            }

            @Override
            public float nextFloat() {
                return rolls.isEmpty() ? 0f : rolls.removeFirst();
            }
        });
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            void onBreak(BlockBreakEvent e) {
                if (e.getBlock().getY() >= 0) {
                    breaks.add(e);
                }
            }
        }, plugin);
    }

    private Block log(int x, int y, int z, String id, String branch) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.OAK_LOG);
        blocks.place(b, BlockStateRef.of(id).with("axis", "y").with("branch_direction", branch), false);
        return b;
    }

    private void column(int height) {
        for (int y = 0; y < height; y++) {
            log(0, y, 0, "tfp:wood/log/oak", "down");
        }
    }

    private BlockBreakEvent cut(Block block, Material tool) {
        player.getInventory().setItemInMainHand(new ItemStack(tool));
        BlockBreakEvent e = new BlockBreakEvent(block, player);
        felling.onBreak(e);
        return e;
    }

    private void drain() {
        while (!queue.isEmpty()) {
            queue.poll().run();
        }
    }

    private boolean standing(int y) {
        return blocks.stateAt(world.getBlockAt(0, y, 0)).isPresent();
    }

    @Test
    void trunkAndBranchesFallAndTheBreakIsCancelled() {
        column(5);
        log(1, 3, 0, "tfp:wood/wood/oak", "west");
        BlockBreakEvent e = cut(world.getBlockAt(0, 0, 0), Material.IRON_AXE);
        assertThat(e.isCancelled()).isTrue();
        drain();
        for (int y = 0; y < 5; y++) {
            assertThat(standing(y)).as("log " + y).isFalse();
        }
        assertThat(blocks.stateAt(world.getBlockAt(1, 3, 0))).isEmpty();
        assertThat(breaks).hasSize(6);
        assertThat(breaks).allMatch(BlockBreakEvent::isDropItems);
        assertThat(wears).isEqualTo(6);
        assertThat(breaks.getLast().getBlock()).as("the cut block last").isEqualTo(world.getBlockAt(0, 0, 0));
    }

    @Test
    void logsComeDownInBatchesOfSixteen() {
        column(40);
        cut(world.getBlockAt(0, 0, 0), Material.IRON_AXE);
        queue.poll().run();
        assertThat(breaks).hasSize(TreeFelling.BATCH);
        assertThat(queue).hasSize(1);
        drain();
        assertThat(breaks).hasSize(40);
    }

    @Test
    void placedLogsAndOtherToolsAreLeftToVanilla() {
        column(3);
        log(5, 0, 0, "tfp:wood/log/oak", "none");
        assertThat(cut(world.getBlockAt(5, 0, 0), Material.IRON_AXE).isCancelled()).isFalse();
        assertThat(cut(world.getBlockAt(0, 0, 0), Material.STICK).isCancelled()).isFalse();
        player.setGameMode(GameMode.SPECTATOR);
        assertThat(cut(world.getBlockAt(0, 0, 0), Material.IRON_AXE).isCancelled()).isFalse();
        assertThat(queue).isEmpty();
        assertThat(standing(0)).isTrue();
    }

    @Test
    void inefficientAxeDropsWithSixtyPercent() {
        column(3);
        rolls.addAll(List.of(0.9f, 0.5f, 0.6f));
        cut(world.getBlockAt(0, 0, 0), Material.STONE_AXE);
        drain();
        assertThat(breaks.stream().map(BlockBreakEvent::isDropItems).toList()).containsExactly(false, true, false);
    }

    @Test
    void theFallStopsWhenTheAxeBreaks() {
        column(5);
        axeLife = 2;
        cut(world.getBlockAt(0, 0, 0), Material.IRON_AXE);
        drain();
        assertThat(breaks).hasSize(2);
        assertThat(standing(0)).isTrue();
        assertThat(standing(4)).isFalse();
    }

    @Test
    void creativeCostsNoDurability() {
        column(3);
        player.setGameMode(GameMode.CREATIVE);
        cut(world.getBlockAt(0, 0, 0), Material.IRON_AXE);
        drain();
        assertThat(breaks).hasSize(3);
        assertThat(wears).isZero();
    }

    @Test
    void aCancelledCutLeavesTheLogStanding() {
        column(3);
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.LOW)
            void deny(BlockBreakEvent e) {
                if (e.getBlock().getY() == 1) {
                    e.setCancelled(true);
                }
            }
        }, plugin);
        cut(world.getBlockAt(0, 0, 0), Material.IRON_AXE);
        drain();
        assertThat(standing(1)).isTrue();
        assertThat(standing(0)).isFalse();
    }
}
