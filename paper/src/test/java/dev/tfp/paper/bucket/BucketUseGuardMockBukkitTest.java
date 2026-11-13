/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.bucket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.bucket.BucketConfig;
import dev.tfp.core.bucket.BucketKind;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.itemdata.FluidPdc;
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
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-FLUID-13: the guard that makes the interact events of one click act once is keyed on the player's own tick, not on
 * the world's day time, so buckets keep working while the world time stands still (daylight cycle off, tick freeze).
 * MockBukkit never advances the world time by itself: the world time stays fixed through every test here.
 */
class BucketUseGuardMockBukkitTest extends MockServerTest {
    private static final long FIXED_TIME = 6000;

    private World world;
    private PlayerMock player;
    private BucketListener.Hit hit;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        world.setFullTime(FIXED_TIME);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        player.setTicksLived(100);
        BucketListener listener = new BucketListener(
                s -> s != null && s.getType() == Material.FLOWER_POT ? Optional.of(BucketKind.WOODEN.itemId())
                        : Optional.empty(),
                (kind, fluid) -> true, () -> BucketConfig.DEFAULTS, id -> Optional.empty(),
                BucketListener::biomeWaterFluid, block -> false, p -> hit);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    private PlayerInteractEvent click(EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.HAND ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, hit.block(), hit.face(),
                hand);
        server.getPluginManager().callEvent(e);
        return e;
    }

    private Block water(int x) {
        Block b = world.getBlockAt(x, 64, 0);
        b.setType(Material.WATER);
        return b;
    }

    @Test
    void twoClicksFillThenEmptyWhileTheWorldTimeStandsStill() {
        Block source = water(0);
        Block ground = world.getBlockAt(3, 63, 0);
        ground.setType(Material.STONE);
        player.getInventory().setItemInMainHand(new ItemStack(Material.FLOWER_POT));

        hit = new BucketListener.Hit(source, BlockFace.UP);
        assertThat(click(EquipmentSlot.HAND).useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(source.getType()).isEqualTo(Material.AIR);
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand()))
                .contains(new FluidAmount("minecraft:water", 1000));

        // the next click comes some player ticks later; the world time has not moved
        player.setTicksLived(player.getTicksLived() + 4);
        assertThat(world.getFullTime()).isEqualTo(FIXED_TIME);
        hit = new BucketListener.Hit(ground, BlockFace.UP);
        assertThat(click(EquipmentSlot.HAND).useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(ground.getRelative(BlockFace.UP).getType()).isEqualTo(Material.WATER);
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand())).isEmpty();
    }

    @Test
    void theOffHandEventOfOneClickDoesNotActAgain() {
        Block first = water(0);
        Block second = water(1);
        player.getInventory().setItemInMainHand(new ItemStack(Material.FLOWER_POT));
        player.getInventory().setItemInOffHand(new ItemStack(Material.FLOWER_POT));

        hit = new BucketListener.Hit(first, BlockFace.UP);
        click(EquipmentSlot.HAND);
        assertThat(first.getType()).isEqualTo(Material.AIR);
        hit = new BucketListener.Hit(second, BlockFace.UP);
        PlayerInteractEvent off = click(EquipmentSlot.OFF_HAND);
        assertThat(off.useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(second.getType()).isEqualTo(Material.WATER);
        assertThat(FluidPdc.read(player.getInventory().getItemInOffHand())).isEmpty();

        // a later click with the off hand acts
        player.setTicksLived(player.getTicksLived() + 1);
        click(EquipmentSlot.OFF_HAND);
        assertThat(second.getType()).isEqualTo(Material.AIR);
        assertThat(FluidPdc.read(player.getInventory().getItemInOffHand()))
                .contains(new FluidAmount("minecraft:water", 1000));
    }

    @Test
    void aRespawnedPlayerIsNotHeldByTheOldEntitysLastUse() {
        Block first = water(0);
        Block second = water(1);
        player.getInventory().setItemInMainHand(new ItemStack(Material.FLOWER_POT, 2));
        hit = new BucketListener.Hit(first, BlockFace.UP);
        click(EquipmentSlot.HAND);
        assertThat(first.getType()).isEqualTo(Material.AIR);

        server.getPluginManager().callEvent(new PlayerRespawnEvent(player, player.getLocation(), false, false, false,
                PlayerRespawnEvent.RespawnReason.DEATH));
        // the new entity happens to be at the same tick count
        hit = new BucketListener.Hit(second, BlockFace.UP);
        click(EquipmentSlot.HAND);
        assertThat(second.getType()).isEqualTo(Material.AIR);
    }
}
