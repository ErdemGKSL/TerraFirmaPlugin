/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itemplace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
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

/** PAR-GATHER-07 on MockBukkit with fake CraftEngine blocks. */
class ItemPlaceListenerMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:groundcover/stick", "tfp:groundcover/rotten_flesh");
    private final FakeCustomItems items = new FakeCustomItems();
    private ItemPlaceListener listener;
    private World world;
    private Block ground;
    private PlayerMock player;
    private int piles;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("itemplace");
        ground = world.getBlockAt(0, 63, 0);
        ground.setType(Material.STONE);
        SnowPilePlacer placer = new SnowPilePlacer() {
            @Override
            public boolean placePile(Block block) {
                piles++;
                return true;
            }

            @Override
            public boolean addLayer(Block block) {
                return false;
            }
        };
        listener = new ItemPlaceListener(blocks, items, () -> Optional.of(placer), b -> b.getType() == Material.SHORT_GRASS);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    private PlayerInteractEvent click(ItemStack item, Block clicked, BlockFace face) {
        player.getInventory().setItemInMainHand(item);
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, clicked, face, EquipmentSlot.HAND);
        listener.onInteract(e);
        return e;
    }

    @Test
    void stickOnTheGroundPlacesGroundcoverAndConsumesOne() {
        PlayerInteractEvent e = click(new ItemStack(Material.STICK, 4), ground, BlockFace.UP);
        assertThat(blocks.stateAt(world.getBlockAt(0, 64, 0))).hasValueSatisfying(s -> assertThat(s.id()).isEqualTo("tfp:groundcover/stick"));
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(3);
        assertThat(e.useItemInHand()).isEqualTo(Event.Result.DENY);
    }

    @Test
    void creativeKeepsTheStack() {
        player.setGameMode(GameMode.CREATIVE);
        click(new ItemStack(Material.STICK, 4), ground, BlockFace.UP);
        assertThat(blocks.stateAt(world.getBlockAt(0, 64, 0))).isPresent();
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(4);
    }

    @Test
    void adventureModeDoesNothing() {
        player.setGameMode(GameMode.ADVENTURE);
        PlayerInteractEvent e = click(new ItemStack(Material.STICK, 4), ground, BlockFace.UP);
        assertThat(blocks.stateAt(world.getBlockAt(0, 64, 0))).isEmpty();
        assertThat(e.useItemInHand()).isNotEqualTo(Event.Result.DENY);
    }

    @Test
    void rottenFleshOnASideFaceWithNoFloorStillEats() {
        // the placement spot (0,64,1) has air below, so nothing is placed and the vanilla use goes on
        PlayerInteractEvent e = click(new ItemStack(Material.ROTTEN_FLESH, 2), ground, BlockFace.SOUTH);
        assertThat(blocks.stateAt(world.getBlockAt(0, 63, 1))).isEmpty();
        assertThat(e.useItemInHand()).isNotEqualTo(Event.Result.DENY);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
    }

    @Test
    void snowOnAPileableBlockPlacesAPile() {
        Block plant = world.getBlockAt(0, 64, 0);
        plant.setType(Material.SHORT_GRASS);
        PlayerInteractEvent e = click(new ItemStack(Material.SNOW, 8), plant, BlockFace.UP);
        assertThat(piles).isEqualTo(1);
        assertThat(e.useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(7);
    }

    @Test
    void snowOnAFullBlockStaysVanilla() {
        PlayerInteractEvent e = click(new ItemStack(Material.SNOW, 8), ground, BlockFace.UP);
        assertThat(piles).isZero();
        assertThat(e.useItemInHand()).isNotEqualTo(Event.Result.DENY);
    }
}
