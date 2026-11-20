/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-GATHER-01 on MockBukkit with fake CraftEngine blocks and items. */
class GatherListenerMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:rock/loose/granite", "tfp:rock/loose/basalt",
        "tfp:groundcover/stick", "tfp:rock/mossy_loose/granite");
    private final FakeCustomItems items = new FakeCustomItems()
        .define("tfp:rock/loose/granite", n -> new ItemStack(Material.STONE, n))
        .define("tfp:rock/loose/basalt", n -> new ItemStack(Material.BASALT, n));
    private GatherListener listener;
    private Block block;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        World world = server.addSimpleWorld("gather");
        listener = new GatherListener(blocks, items);
        block = world.getBlockAt(0, 64, 0);
        world.getBlockAt(0, 63, 0).setType(Material.STONE);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    private PlayerInteractEvent click(ItemStack item) {
        player.getInventory().setItemInMainHand(item);
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, block, BlockFace.UP,
            EquipmentSlot.HAND);
        listener.onInteract(e);
        return e;
    }

    @Test
    void emptyHandPicksUpALooseRockWithItsCount() {
        blocks.place(block, BlockStateRef.of("tfp:rock/loose/granite").with("count", "2"), false);
        PlayerInteractEvent e = click(null);
        assertThat(blocks.stateAt(block)).isEmpty();
        // the fake item factory stands in for the CE item: the inventory got 2 of the granite stand-in (Material.STONE)
        assertThat(player.getInventory().contains(Material.STONE, 2)).isTrue();
        assertThat(e.useInteractedBlock()).isEqualTo(Event.Result.DENY);
    }

    @Test
    void groundcoverGivesAVanillaStickAndAnyItemInTheHandStillPicksUp() {
        blocks.place(block, BlockStateRef.of("tfp:groundcover/stick"), false);
        click(new ItemStack(Material.IRON_AXE));
        assertThat(blocks.stateAt(block)).isEmpty();
        assertThat(player.getInventory().contains(Material.STICK, 1)).isTrue();
    }

    @Test
    void creativeYieldsNothingButRemovesTheBlock() {
        player.setGameMode(GameMode.CREATIVE);
        blocks.place(block, BlockStateRef.of("tfp:groundcover/stick"), false);
        click(null);
        assertThat(blocks.stateAt(block)).isEmpty();
        assertThat(player.getInventory().contains(Material.STICK)).isFalse();
    }

    @Test
    void holdingTheSameRockStacksOneMoreUpToThree() {
        blocks.place(block, BlockStateRef.of("tfp:rock/loose/granite"), false);
        ItemStack held = items.create("tfp:rock/loose/granite", 2).orElseThrow();
        click(held);
        assertThat(blocks.stateAt(block).orElseThrow().properties()).containsEntry("count", "2");
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        // a block with 3 rocks picks up instead
        blocks.place(block, BlockStateRef.of("tfp:rock/loose/granite").with("count", "3"), false);
        click(items.create("tfp:rock/loose/granite", 1).orElseThrow());
        assertThat(blocks.stateAt(block)).isEmpty();
    }

    @Test
    void otherRockItemPicksUpAndSneakingWithAnItemLeavesTheClick() {
        blocks.place(block, BlockStateRef.of("tfp:rock/loose/granite"), false);
        player.setSneaking(true);
        PlayerInteractEvent e = click(items.create("tfp:rock/loose/basalt", 1).orElseThrow());
        assertThat(blocks.stateAt(block)).isPresent();
        assertThat(e.useInteractedBlock()).isNotEqualTo(Event.Result.DENY);
        player.setSneaking(false);
        click(items.create("tfp:rock/loose/basalt", 1).orElseThrow());
        assertThat(blocks.stateAt(block)).isEmpty();
    }

    @Test
    void otherBlocksAreIgnored() {
        blocks.known("tfp:rock/raw/granite");
        blocks.place(block, BlockStateRef.of("tfp:rock/raw/granite"), false);
        PlayerInteractEvent e = click(null);
        assertThat(blocks.stateAt(block)).isPresent();
        assertThat(e.useInteractedBlock()).isNotEqualTo(Event.Result.DENY);
    }
}
