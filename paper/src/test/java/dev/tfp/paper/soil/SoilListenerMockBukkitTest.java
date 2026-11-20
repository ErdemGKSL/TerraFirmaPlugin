/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.soil;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.soil.SoilTable;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.itemdata.FluidPdc;
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
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-GATHER-05 on MockBukkit with a fake CraftEngine: iron hoe and iron shovel stand in for the tool tags. */
class SoilListenerMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:dirt/podzol", "tfp:grass/podzol",
        "tfp:clay/podzol", "tfp:rooted_dirt/podzol", "tfp:farmland/podzol", "tfp:grass_path/podzol", "tfp:mud/podzol");
    private SoilListener listener;
    private Block block;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        World world = server.addSimpleWorld("soil");
        listener = new SoilListener(blocks, s -> s.getType() == Material.IRON_HOE, s -> s.getType() == Material.IRON_SHOVEL,
            fluid -> fluid.equals("minecraft:water") || fluid.equals("tfp:salt_water"), SoilTable.Config.DEFAULT,
            (p, hand) -> {
                ItemStack held = p.getInventory().getItem(hand);
                Damageable meta = (Damageable) held.getItemMeta();
                meta.setDamage(meta.getDamage() + 1);
                held.setItemMeta(meta);
            });
        block = world.getBlockAt(0, 64, 0);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    private PlayerInteractEvent click(ItemStack item, BlockFace face) {
        player.getInventory().setItemInMainHand(item);
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, block, face, EquipmentSlot.HAND);
        listener.onInteract(e);
        return e;
    }

    private String id() {
        return blocks.stateAt(block).orElseThrow().id();
    }

    private static int damage(ItemStack s) {
        return ((Damageable) s.getItemMeta()).getDamage();
    }

    @Test
    void hoeTillsDirtAndGrassAndLosesDurability() {
        blocks.place(block, BlockStateRef.of("tfp:dirt/podzol"), false);
        PlayerInteractEvent e = click(new ItemStack(Material.IRON_HOE), BlockFace.UP);
        assertThat(id()).isEqualTo("tfp:farmland/podzol");
        assertThat(damage(player.getInventory().getItemInMainHand())).isEqualTo(1);
        assertThat(e.useInteractedBlock()).isEqualTo(Event.Result.DENY);
        blocks.place(block, BlockStateRef.of("tfp:grass/podzol"), false);
        click(new ItemStack(Material.IRON_HOE), BlockFace.UP);
        assertThat(id()).isEqualTo("tfp:farmland/podzol");
    }

    @Test
    void shovelFlattensToPathAndCreativeKeepsDurability() {
        blocks.place(block, BlockStateRef.of("tfp:clay/podzol"), false);
        player.setGameMode(GameMode.CREATIVE);
        click(new ItemStack(Material.IRON_SHOVEL), BlockFace.UP);
        assertThat(id()).isEqualTo("tfp:grass_path/podzol");
        assertThat(damage(player.getInventory().getItemInMainHand())).isZero();
    }

    @Test
    void hoeTurnsRootedDirtToDirt() {
        blocks.place(block, BlockStateRef.of("tfp:rooted_dirt/podzol"), false);
        click(new ItemStack(Material.IRON_HOE), BlockFace.UP);
        assertThat(id()).isEqualTo("tfp:dirt/podzol");
    }

    @Test
    void needsAirAboveAndNotTheBottomFace() {
        blocks.place(block, BlockStateRef.of("tfp:dirt/podzol"), false);
        click(new ItemStack(Material.IRON_HOE), BlockFace.DOWN);
        assertThat(id()).isEqualTo("tfp:dirt/podzol");
        block.getRelative(BlockFace.UP).setType(Material.STONE);
        click(new ItemStack(Material.IRON_HOE), BlockFace.UP);
        assertThat(id()).isEqualTo("tfp:dirt/podzol");
    }

    @Test
    void adventureAndMissingTargetDoNothing() {
        blocks.place(block, BlockStateRef.of("tfp:dirt/podzol"), false);
        player.setGameMode(GameMode.ADVENTURE);
        click(new ItemStack(Material.IRON_HOE), BlockFace.UP);
        assertThat(id()).isEqualTo("tfp:dirt/podzol");
        player.setGameMode(GameMode.SURVIVAL);
        FakeCustomBlocks bare = new FakeCustomBlocks().known("tfp:dirt/podzol");
        SoilListener noFarmland = new SoilListener(bare, s -> true, s -> false, f -> true, SoilTable.Config.DEFAULT, (p, h) -> { });
        bare.place(block, BlockStateRef.of("tfp:dirt/podzol"), false);
        ItemStack hoe = new ItemStack(Material.IRON_HOE);
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, hoe, block, BlockFace.UP, EquipmentSlot.HAND);
        noFarmland.onInteract(e);
        assertThat(bare.stateAt(block).orElseThrow().id()).isEqualTo("tfp:dirt/podzol");
        assertThat(e.useInteractedBlock()).isNotEqualTo(Event.Result.DENY);
    }

    @Test
    void waterContainerTurnsDirtToMudAndDrains100() {
        blocks.place(block, BlockStateRef.of("tfp:dirt/podzol"), false);
        ItemStack bucket = new ItemStack(Material.BUCKET);
        FluidPdc.write(bucket, new FluidAmount("minecraft:water", 1000));
        click(bucket, BlockFace.UP);
        assertThat(id()).isEqualTo("tfp:mud/podzol");
        assertThat(FluidPdc.content(player.getInventory().getItemInMainHand()).amount()).isEqualTo(900);
        blocks.place(block, BlockStateRef.of("tfp:rooted_dirt/podzol"), false);
        click(player.getInventory().getItemInMainHand(), BlockFace.UP);
        assertThat(id()).isEqualTo("tfp:mud/podzol");
    }

    @Test
    void notEnoughWaterOrWrongFluidOrGrassDoNothing() {
        blocks.place(block, BlockStateRef.of("tfp:dirt/podzol"), false);
        ItemStack low = new ItemStack(Material.BUCKET);
        FluidPdc.write(low, new FluidAmount("minecraft:water", 99));
        click(low, BlockFace.UP);
        assertThat(id()).isEqualTo("tfp:dirt/podzol");
        ItemStack lava = new ItemStack(Material.BUCKET);
        FluidPdc.write(lava, new FluidAmount("minecraft:lava", 1000));
        click(lava, BlockFace.UP);
        assertThat(id()).isEqualTo("tfp:dirt/podzol");
        blocks.place(block, BlockStateRef.of("tfp:grass/podzol"), false);
        ItemStack full = new ItemStack(Material.BUCKET);
        FluidPdc.write(full, new FluidAmount("minecraft:water", 1000));
        click(full, BlockFace.UP);
        assertThat(id()).isEqualTo("tfp:grass/podzol");
    }
}
