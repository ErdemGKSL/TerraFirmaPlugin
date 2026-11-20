/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.harvest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gather.HeldTool;
import dev.tfp.core.gather.ToolTag;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-GATHER-02 on MockBukkit: allium needs a sharp tool or shears. */
class HarvestListenerMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:plant/allium");
    private final FakeCustomItems items = new FakeCustomItems().define("tfp:plant/allium", n -> new ItemStack(Material.ALLIUM, n));
    private World world;
    private Block block;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("harvest");
        block = world.getBlockAt(0, 64, 0);
        blocks.place(block, BlockStateRef.of("tfp:plant/allium"), false);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    private BlockBreakEvent breakWith(ItemStack tool, HeldTool held, boolean handledElsewhere) {
        player.getInventory().setItemInMainHand(tool);
        HarvestListener listener = new HarvestListener(blocks, items, s -> held, id -> handledElsewhere, () -> 0.0);
        BlockBreakEvent e = new BlockBreakEvent(block, player);
        listener.onBreak(e);
        return e;
    }

    private int dropped() {
        return world.getEntitiesByClass(Item.class).stream().mapToInt(i -> i.getItemStack().getAmount()).sum();
    }

    @Test
    void knifeCutsThePlant() {
        BlockBreakEvent e = breakWith(new ItemStack(Material.IRON_SWORD), HeldTool.of(ToolTag.SHARP), false);
        assertThat(e.isDropItems()).isFalse();
        assertThat(dropped()).isEqualTo(1);
    }

    @Test
    void handYieldsNothing() {
        BlockBreakEvent e = breakWith(null, HeldTool.NONE, false);
        assertThat(e.isDropItems()).isFalse();
        assertThat(dropped()).isZero();
    }

    @Test
    void creativeYieldsNothing() {
        player.setGameMode(GameMode.CREATIVE);
        breakWith(new ItemStack(Material.IRON_SWORD), HeldTool.of(ToolTag.SHARP), false);
        assertThat(dropped()).isZero();
    }

    @Test
    void blocksAnotherRuntimeDrivesAreLeftAlone() {
        BlockBreakEvent e = breakWith(new ItemStack(Material.IRON_SWORD), HeldTool.of(ToolTag.SHARP), true);
        assertThat(e.isDropItems()).isTrue();
        assertThat(dropped()).isZero();
    }
}
