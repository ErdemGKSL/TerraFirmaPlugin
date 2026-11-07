/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.bucket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.bucket.BucketConfig;
import dev.tfp.core.bucket.BucketKind;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Map;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-FLUID-02: filling and emptying buckets on the world (MockBukkit). */
class BucketListenerMockBukkitTest extends MockServerTest {
    /** Stand-in materials for the CraftEngine items (identified by material in these tests). */
    private static final Map<Material, String> IDS = Map.of(
            Material.FLOWER_POT, BucketKind.WOODEN.itemId(),
            Material.IRON_INGOT, BucketKind.RED_STEEL.itemId(),
            Material.BRICK, BucketListener.SALT_WATER_BUCKET,
            Material.NETHER_BRICK, "tfp:bucket/beer");

    private World world;
    private PlayerMock player;
    private BucketListener listener;
    private BucketConfig config = BucketConfig.DEFAULTS;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        listener = new BucketListener(
                s -> s == null ? Optional.empty() : Optional.ofNullable(IDS.get(s.getType())),
                (kind, fluid) -> kind == BucketKind.WOODEN.container() ? !fluid.equals("minecraft:lava") : true,
                () -> config,
                id -> id.equals(BucketListener.SALT_WATER_BUCKET) ? Optional.of(new ItemStack(Material.BRICK))
                        : Optional.empty());
        server.getPluginManager().registerEvents(listener, plugin);
    }

    private Block waterSource(int x, Biome biome) {
        Block b = world.getBlockAt(x, 64, 0);
        b.setType(Material.WATER);
        b.setBiome(biome);
        return b;
    }

    private ItemStack filledWooden(String fluid) {
        ItemStack s = new ItemStack(Material.FLOWER_POT);
        ItemFluids.fill(s, new FluidAmount(fluid, 1000), 1000, true);
        BucketVisuals.sync(s);
        return s;
    }

    private boolean use(ItemStack held, BucketKind kind, Block block, BlockFace face) {
        player.getInventory().setItemInMainHand(held);
        return listener.useTank(player, EquipmentSlot.HAND, held, kind, new BucketListener.Hit(block, face));
    }

    @Test
    void anEmptyWoodenBucketFillsFromARiverAndTakesTheSource() {
        Block water = waterSource(0, Biome.RIVER);
        assertThat(use(new ItemStack(Material.FLOWER_POT), BucketKind.WOODEN, water, BlockFace.UP)).isTrue();
        ItemStack held = player.getInventory().getItemInMainHand();
        assertThat(FluidPdc.read(held)).contains(new FluidAmount("minecraft:water", 1000));
        assertThat(water.getType()).isEqualTo(Material.AIR);
        // the model data itself is not checked here: MockBukkit's item meta copies drop custom_model_data
    }

    @Test
    void oceanWaterIsSaltWater() {
        Block water = waterSource(0, Biome.OCEAN);
        assertThat(use(new ItemStack(Material.FLOWER_POT), BucketKind.WOODEN, water, BlockFace.UP)).isTrue();
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand()))
                .contains(new FluidAmount("tfp:salt_water", 1000));
    }

    @Test
    void theWorldWaterKindDecidesTheFluid() {
        BucketListener springs = new BucketListener(
                s -> s == null ? Optional.empty() : Optional.ofNullable(IDS.get(s.getType())),
                (kind, fluid) -> true, () -> config, id -> Optional.empty(), block -> "tfp:spring_water");
        Block water = waterSource(0, Biome.PLAINS);
        ItemStack held = new ItemStack(Material.FLOWER_POT);
        player.getInventory().setItemInMainHand(held);
        assertThat(springs.useTank(player, EquipmentSlot.HAND, held, BucketKind.WOODEN,
                new BucketListener.Hit(water, BlockFace.UP))).isTrue();
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand()))
                .contains(new FluidAmount("tfp:spring_water", 1000));
    }

    @Test
    void theWoodenBucketTakesNoLavaButTheRedSteelBucketDoes() {
        Block lava = world.getBlockAt(0, 64, 0);
        lava.setType(Material.LAVA);
        assertThat(use(new ItemStack(Material.FLOWER_POT), BucketKind.WOODEN, lava, BlockFace.UP)).isFalse();
        assertThat(lava.getType()).isEqualTo(Material.LAVA);
        assertThat(use(new ItemStack(Material.IRON_INGOT), BucketKind.RED_STEEL, lava, BlockFace.UP)).isTrue();
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand()))
                .contains(new FluidAmount("minecraft:lava", 1000));
        assertThat(lava.getType()).isEqualTo(Material.AIR);
    }

    @Test
    void emptyingOnTheGroundPlacesATransientFlowingBlockByDefault() {
        Block ground = world.getBlockAt(0, 63, 0);
        ground.setType(Material.STONE);
        Block above = ground.getRelative(BlockFace.UP);
        above.setType(Material.AIR);
        assertThat(use(filledWooden("minecraft:water"), BucketKind.WOODEN, ground, BlockFace.UP)).isTrue();
        assertThat(above.getType()).isEqualTo(Material.WATER);
        assertThat(((Levelled) above.getBlockData()).getLevel()).isEqualTo(7);
        ItemStack held = player.getInventory().getItemInMainHand();
        assertThat(FluidPdc.read(held)).isEmpty();
    }

    @Test
    void theFluidLayerTakesTheFluidTint() {
        assertThat(BucketVisuals.tint("minecraft:lava")).isEqualTo(BucketVisuals.LAVA_LOOK);
        assertThat(BucketVisuals.tint("minecraft:water"))
                .isEqualTo(TfcFluids.get("minecraft:water").orElseThrow().rgb());
        assertThat(BucketVisuals.tint("tfp:beer")).isEqualTo(TfcFluids.get("tfp:beer").orElseThrow().rgb());
        assertThat(BucketVisuals.tint("tfp:nonsense")).isEqualTo(0xFFFFFF);
        assertThat(BucketVisuals.sync(new ItemStack(Material.FLOWER_POT))).isFalse();
    }

    @Test
    void sourcePlacementWhenConfigured() {
        config = new BucketConfig(true, false);
        Block ground = world.getBlockAt(0, 63, 0);
        ground.setType(Material.STONE);
        Block above = ground.getRelative(BlockFace.UP);
        assertThat(use(filledWooden("tfp:spring_water"), BucketKind.WOODEN, ground, BlockFace.UP)).isTrue();
        assertThat(above.getType()).isEqualTo(Material.WATER);
        assertThat(((Levelled) above.getBlockData()).getLevel()).isZero();
    }

    @Test
    void fluidsWithoutAWorldBlockStayInTheBucket() {
        Block ground = world.getBlockAt(0, 63, 0);
        ground.setType(Material.STONE);
        ItemStack beer = filledWooden("tfp:beer");
        assertThat(use(beer, BucketKind.WOODEN, ground, BlockFace.UP)).isFalse();
        assertThat(ground.getRelative(BlockFace.UP).getType()).isEqualTo(Material.AIR);
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand()))
                .contains(new FluidAmount("tfp:beer", 1000));
    }

    @Test
    void aStackSplitsOneFilledBucketOff() {
        Block water = waterSource(0, Biome.RIVER);
        assertThat(use(new ItemStack(Material.FLOWER_POT, 3), BucketKind.WOODEN, water, BlockFace.UP)).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand())).isEmpty();
        boolean given = false;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s != null && FluidPdc.read(s).isPresent()) {
                given = true;
                assertThat(s.getAmount()).isEqualTo(1);
            }
        }
        assertThat(given).isTrue();
    }

    @Test
    void creativeKeepsTheHeldBucket() {
        player.setGameMode(GameMode.CREATIVE);
        Block water = waterSource(0, Biome.RIVER);
        assertThat(use(new ItemStack(Material.FLOWER_POT), BucketKind.WOODEN, water, BlockFace.UP)).isTrue();
        assertThat(water.getType()).isEqualTo(Material.AIR);
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand())).isEmpty();
    }

    @Test
    void aCancelledBucketEventChangesNothing() {
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void deny(PlayerBucketFillEvent e) {
                e.setCancelled(true);
            }
        }, plugin);
        Block water = waterSource(0, Biome.RIVER);
        assertThat(use(new ItemStack(Material.FLOWER_POT), BucketKind.WOODEN, water, BlockFace.UP)).isFalse();
        assertThat(water.getType()).isEqualTo(Material.WATER);
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand())).isEmpty();
    }

    @Test
    void aTfcSaltWaterBucketPlacesVanillaWaterAndBecomesAVanillaBucket() {
        Block ground = world.getBlockAt(0, 63, 0);
        ground.setType(Material.STONE);
        ItemStack salt = new ItemStack(Material.BRICK);
        player.getInventory().setItemInMainHand(salt);
        assertThat(listener.useFluidBucket(player, EquipmentSlot.HAND, salt, BucketListener.SALT_WATER_BUCKET,
                new BucketListener.Hit(ground, BlockFace.UP))).isTrue();
        Block above = ground.getRelative(BlockFace.UP);
        assertThat(above.getType()).isEqualTo(Material.WATER);
        assertThat(((Levelled) above.getBlockData()).getLevel()).isZero();
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.BUCKET);

        ItemStack beer = new ItemStack(Material.NETHER_BRICK);
        player.getInventory().setItemInMainHand(beer);
        Block other = world.getBlockAt(5, 63, 0);
        other.setType(Material.STONE);
        assertThat(listener.useFluidBucket(player, EquipmentSlot.HAND, beer, "tfp:bucket/beer",
                new BucketListener.Hit(other, BlockFace.UP))).isFalse();
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.NETHER_BRICK);
    }

    @Test
    void aVanillaBucketFilledInTheOceanGivesTheSaltWaterBucket() {
        Block water = waterSource(0, Biome.OCEAN);
        PlayerBucketFillEvent event = new PlayerBucketFillEvent(player, water, water, BlockFace.UP, Material.BUCKET,
                new ItemStack(Material.WATER_BUCKET), EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertThat(event.getItemStack().getType()).isEqualTo(Material.BRICK);

        Block river = waterSource(3, Biome.RIVER);
        PlayerBucketFillEvent fresh = new PlayerBucketFillEvent(player, river, river, BlockFace.UP, Material.BUCKET,
                new ItemStack(Material.WATER_BUCKET), EquipmentSlot.HAND);
        server.getPluginManager().callEvent(fresh);
        assertThat(fresh.getItemStack().getType()).isEqualTo(Material.WATER_BUCKET);
    }
}
