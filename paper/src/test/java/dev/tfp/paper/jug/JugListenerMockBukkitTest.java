/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.jug;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.tag.TagRegistry;
import dev.tfp.core.thirst.Drinkable;
import dev.tfp.core.thirst.Drinkables;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.player.PlayerDataFiles;
import dev.tfp.paper.player.PlayerDataStore;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-POT-07: filling, drinking and pouring a jug or a glass bottle (MockBukkit). */
class JugListenerMockBukkitTest extends MockServerTest {
    private static final Map<Material, String> IDS = Map.of(
        Material.FLOWER_POT, "tfp:ceramic/jug",
        Material.BRICK, "tfp:silica_glass_bottle",
        Material.IRON_INGOT, "tfp:wooden_bucket");
    private static final Drinkable WATER = new Drinkable(new FluidIngredient.Single(Drinkables.WATER), 0, false, 10f,
        List.of());

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    PlayerDataStore store;
    World world;
    PlayerMock player;
    JugListener listener;
    Block water;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(player.getUniqueId());
        water = world.getBlockAt(0, 64, 0);
        water.setType(Material.WATER);
        listener = new JugListener(s -> s == null ? Optional.empty() : Optional.ofNullable(IDS.get(s.getType())),
            (kind, fluid) -> true, b -> "minecraft:water", b -> false, store, () -> 0L, new HudSnapshotCache(() -> true),
            () -> new Drinkables(List.of(WATER), TagRegistry.EMPTY), p -> { }, p -> water);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    private ItemStack hold(Material m, String fluid, int mB) {
        ItemStack s = new ItemStack(m);
        if (mB > 0) {
            ItemFluids.fill(s, new FluidAmount(fluid, mB), 1000, true);
        }
        player.getInventory().setItemInMainHand(s);
        return s;
    }

    private void use(ItemStack held, FluidContainerKind kind) {
        listener.use(player, EquipmentSlot.HAND, held, kind);
    }

    private void setThirst(float v) {
        store.update(player.getUniqueId(), d -> d.withThirst(v));
    }

    @Test
    void anEmptyJugFillsFromAWaterSource() {
        ItemStack held = hold(Material.FLOWER_POT, null, 0);
        use(held, FluidContainerKind.JUG);
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand()))
            .contains(new FluidAmount("minecraft:water", 100));
        assertThat(water.getType()).isEqualTo(Material.AIR);
    }

    @Test
    void aGlassBottleFillsToItsCapacity() {
        ItemStack held = hold(Material.BRICK, null, 0);
        use(held, FluidContainerKind.SILICA_GLASS_BOTTLE);
        assertThat(FluidPdc.read(player.getInventory().getItemInMainHand()))
            .contains(new FluidAmount("minecraft:water", 500));
    }

    @Test
    void drinkingAJugEmptiesItAndRaisesThirst() {
        setThirst(50f);
        ItemStack held = hold(Material.FLOWER_POT, "minecraft:water", 100);
        listener.use(player, EquipmentSlot.HAND, held, FluidContainerKind.JUG);
        PlayerData after = store.get(player.getUniqueId()).orElseThrow();
        // 100 mB = 4 reference drinks of 10 thirst, from 50 (the 2 % break draw may remove the jug instead)
        assertThat(after.thirst()).isEqualTo(90f);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!hand.getType().isAir()) {
            assertThat(ItemFluids.content(hand).isEmpty()).isTrue();
        }
    }

    @Test
    void aBottleDrinkTakesOneHundredAndKeepsTheRest() {
        setThirst(20f);
        ItemStack held = hold(Material.BRICK, "minecraft:water", 500);
        use(held, FluidContainerKind.SILICA_GLASS_BOTTLE);
        assertThat(store.get(player.getUniqueId()).orElseThrow().thirst()).isEqualTo(60f);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!hand.getType().isAir()) { // silica breaks at 0.5 %
            assertThat(ItemFluids.content(hand).amount()).isEqualTo(400);
        }
    }

    @Test
    void aFullPlayerDoesNotDrinkAndKeepsTheFluid() {
        setThirst(100f);
        ItemStack held = hold(Material.FLOWER_POT, "minecraft:water", 100);
        use(held, FluidContainerKind.JUG);
        assertThat(ItemFluids.content(player.getInventory().getItemInMainHand()).amount()).isEqualTo(100);
    }

    @Test
    void sneakingPoursEverythingOut() {
        player.setSneaking(true);
        ItemStack held = hold(Material.FLOWER_POT, "minecraft:water", 100);
        use(held, FluidContainerKind.JUG);
        assertThat(ItemFluids.content(player.getInventory().getItemInMainHand()).isEmpty()).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.FLOWER_POT);
    }

    @Test
    void aFluidThatIsNotDrinkableStaysInTheJug() {
        ItemStack held = hold(Material.FLOWER_POT, "tfp:brine", 100);
        use(held, FluidContainerKind.JUG);
        assertThat(ItemFluids.content(player.getInventory().getItemInMainHand()).amount()).isEqualTo(100);
    }

    @Test
    void aWaterSourceTakenIntoAJugLeavesNoWater() {
        // lava is not water: a source that is neither is ignored (a blow, no change)
        water.setType(Material.STONE);
        ItemStack held = hold(Material.FLOWER_POT, null, 0);
        use(held, FluidContainerKind.JUG);
        assertThat(ItemFluids.content(player.getInventory().getItemInMainHand()).isEmpty()).isTrue();
    }
}
