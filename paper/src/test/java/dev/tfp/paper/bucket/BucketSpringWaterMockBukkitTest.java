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
import java.util.Map;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-FLUID-10: spring water buckets and the fresh water of waterlogged blocks (MockBukkit). */
class BucketSpringWaterMockBukkitTest extends MockServerTest {
    /** Stand-in materials for the CraftEngine items. */
    private static final Map<String, Material> ITEMS = Map.of(
            BucketListener.SALT_WATER_BUCKET, Material.BRICK,
            BucketListener.SPRING_WATER_BUCKET, Material.NETHER_BRICK);
    /** The column of the stand-in hot spring pool. */
    private static final int SPRING_X = 5;

    private World world;
    private PlayerMock player;
    private BucketListener listener;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        listener = new BucketListener(
                s -> s != null && s.getType() == Material.FLOWER_POT ? Optional.of(BucketKind.WOODEN.itemId())
                        : Optional.empty(),
                (kind, fluid) -> true,
                () -> BucketConfig.DEFAULTS,
                id -> Optional.ofNullable(ITEMS.get(id)).map(ItemStack::new),
                BucketListener::biomeWaterFluid,
                block -> block.getX() == SPRING_X && block.getType() == Material.WATER);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    private Block waterSource(int x, Biome biome) {
        Block b = world.getBlockAt(x, 70, 0);
        b.setType(Material.WATER);
        b.setBiome(biome);
        return b;
    }

    private Block waterloggedStairs(int x, Biome biome) {
        Block b = world.getBlockAt(x, 60, 0);
        b.setType(Material.OAK_STAIRS);
        Waterlogged data = (Waterlogged) b.getBlockData();
        data.setWaterlogged(true);
        b.setBlockData(data);
        b.setBiome(biome);
        return b;
    }

    private PlayerBucketFillEvent vanillaFill(Block block) {
        PlayerBucketFillEvent event = new PlayerBucketFillEvent(player, block, block, BlockFace.UP, Material.BUCKET,
                new ItemStack(Material.WATER_BUCKET), EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private boolean fillWooden(Block block) {
        ItemStack held = new ItemStack(Material.FLOWER_POT);
        player.getInventory().setItemInMainHand(held);
        return listener.useTank(player, EquipmentSlot.HAND, held, BucketKind.WOODEN,
                new BucketListener.Hit(block, BlockFace.UP));
    }

    @Test
    void aVanillaBucketFromASpringPoolGivesTheSpringWaterBucket() {
        Block spring = waterSource(SPRING_X, Biome.PLAINS);
        assertThat(vanillaFill(spring).getItemStack().getType()).isEqualTo(Material.NETHER_BRICK);
    }

    @Test
    void aVanillaBucketBesideTheSpringStaysAWaterBucket() {
        Block lake = waterSource(SPRING_X + 2, Biome.PLAINS);
        assertThat(vanillaFill(lake).getItemStack().getType()).isEqualTo(Material.WATER_BUCKET);
    }

    @Test
    void aTankBucketFromASpringPoolHoldsSpringWater() {
        Block spring = waterSource(SPRING_X, Biome.PLAINS);
        assertThat(fillWooden(spring)).isTrue();
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand()))
                .contains(new FluidAmount("tfp:spring_water", 1000));
        assertThat(spring.getType()).isEqualTo(Material.AIR);
    }

    @Test
    void aTankBucketFromAWaterloggedBlockInAnOceanHoldsFreshWater() {
        Block stairs = waterloggedStairs(0, Biome.OCEAN);
        assertThat(fillWooden(stairs)).isTrue();
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand()))
                .contains(new FluidAmount("minecraft:water", 1000));
        assertThat(stairs.getType()).isEqualTo(Material.OAK_STAIRS);
        assertThat(((Waterlogged) stairs.getBlockData()).isWaterlogged()).isFalse();
    }

    @Test
    void aVanillaBucketFromAWaterloggedBlockInAnOceanStaysAWaterBucket() {
        Block stairs = waterloggedStairs(0, Biome.OCEAN);
        assertThat(vanillaFill(stairs).getItemStack().getType()).isEqualTo(Material.WATER_BUCKET);
    }

    @Test
    void oceanSourceWaterIsStillSalt() {
        Block ocean = waterSource(0, Biome.OCEAN);
        assertThat(vanillaFill(ocean).getItemStack().getType()).isEqualTo(Material.BRICK);
        Block other = waterSource(1, Biome.OCEAN);
        assertThat(fillWooden(other)).isTrue();
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand()))
                .contains(new FluidAmount("tfp:salt_water", 1000));
    }

    @Test
    void withoutTheHotspringModuleNoWaterIsSpringWater() {
        Block water = waterSource(SPRING_X, Biome.PLAINS);
        assertThat(BucketListener.moduleSpringWater().test(water)).isFalse();
    }
}
