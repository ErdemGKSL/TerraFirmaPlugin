/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The prompt refresh path of {@link PlayerNeedsHudService#refresh}: a change to a need (here what {@code /tfp debug thirst}
 * and {@code /tfp debug hunger} do) reaches the HUD snapshot on the next tick of the player's thread, without waiting for
 * the {@value PlayerNeedsHudService#PERIOD_TICKS}-tick throttle (owner review 2026-10-22: at GUI scale 2 the thirst bar
 * still showed full right after the command). Also the armour reading.
 */
class PlayerNeedsHudRefreshMockBukkitTest extends MockServerTest {
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e5");

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    @Test
    void refreshPublishesAChangedNeedOnTheNextTick() {
        PlayerDataStore store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        PlayerMock alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        HudSnapshotCache hud = new HudSnapshotCache(() -> true);
        PlayerNeedsHudService needs = new PlayerNeedsHudService(store, new ServerScheduler(plugin, 1), hud, () -> 0L);

        store.update(ALEX, d -> d.withThirst(10f));
        alex.setFoodLevel(7);
        needs.refresh(alex);
        server.getScheduler().performOneTick();

        assertThat(hud.snapshot(ALEX).thirst()).isEqualTo(10);
        assertThat(hud.snapshot(ALEX).hunger()).isEqualTo(7);
    }

    /** MockBukkit's player has no armour attribute instance: the reading is then 0 (no bar), never an exception. */
    @Test
    void publishReadsTheArmourAttributeAndZeroWithoutOne() {
        PlayerDataStore store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        PlayerMock alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        HudSnapshotCache hud = new HudSnapshotCache(() -> true);
        PlayerNeedsHudService needs = new PlayerNeedsHudService(store, new ServerScheduler(plugin, 1), hud, () -> 0L);

        AttributeInstance armor = alex.getAttribute(Attribute.ARMOR);
        if (armor != null) {
            armor.setBaseValue(12);
        }
        needs.publish(alex);
        assertThat(hud.snapshot(ALEX).armor()).isEqualTo(armor == null ? 0 : 12);
        assertThat(hud.snapshot(ALEX).armor()).isEqualTo(PlayerNeedsHudService.armor(alex));
    }

    /**
     * PAR-HUD-08: the main-hand stack's temperature (lazy, from its stored heat record) is published; a cold or empty
     * hand publishes 0, so the popup ({@code tfp_held_temp > 0}) hides.
     */
    @Test
    void publishReadsTheHeldItemTemperature() {
        PlayerDataStore store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        PlayerMock alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        HudSnapshotCache hud = new HudSnapshotCache(() -> true);
        ItemHeatHooks heat = new ItemHeatHooks(() -> 100L, HeatConfig.DEFAULT, ItemHeatHooks.Capacities.UNIT);
        PlayerNeedsHudService needs = new PlayerNeedsHudService(store, new ServerScheduler(plugin, 1), hud, () -> 0L,
                heat::temperature);

        needs.publish(alex);
        assertThat(hud.snapshot(ALEX).heldTemperature()).isZero();

        ItemStack ingot = new ItemStack(Material.COPPER_INGOT);
        HeatPdc.write(ingot, new HeatState(900f, 100L));
        alex.getInventory().setItemInMainHand(ingot);
        needs.publish(alex);
        assertThat(hud.snapshot(ALEX).heldTemperature()).isEqualTo(900);

        alex.getInventory().setItemInMainHand(new ItemStack(Material.COPPER_INGOT));
        needs.publish(alex);
        assertThat(hud.snapshot(ALEX).heldTemperature()).isZero();
    }

    /**
     * PAR-HUD-08 review: Paper fires {@link PlayerItemHeldEvent} before the selected slot changes. The republish runs on
     * the player's next tick, after the server applied the new slot, so it shows the newly selected stack's temperature.
     */
    @Test
    void hotbarChangePublishesTheNewSlotsTemperatureOnTheNextTick() {
        PlayerDataStore store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        PlayerMock alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        HudSnapshotCache hud = new HudSnapshotCache(() -> true);
        ItemHeatHooks heat = new ItemHeatHooks(() -> 100L, HeatConfig.DEFAULT, ItemHeatHooks.Capacities.UNIT);
        PlayerNeedsHudService needs = new PlayerNeedsHudService(store, new ServerScheduler(plugin, 1), hud, () -> 0L,
                heat::temperature);
        ItemStack hot = new ItemStack(Material.COPPER_INGOT);
        HeatPdc.write(hot, new HeatState(900f, 100L));
        alex.getInventory().setItem(3, hot);
        alex.getInventory().setItem(0, new ItemStack(Material.COPPER_INGOT));
        alex.getInventory().setHeldItemSlot(0);
        needs.publish(alex);
        assertThat(hud.snapshot(ALEX).heldTemperature()).isZero();

        needs.onHeld(new PlayerItemHeldEvent(alex, 0, 3)); // still slot 0 while the event runs, as on Paper
        assertThat(alex.getInventory().getItemInMainHand().isSimilar(hot)).isFalse();
        alex.getInventory().setHeldItemSlot(3); // what the server does after the event
        server.getScheduler().performOneTick();
        assertThat(hud.snapshot(ALEX).heldTemperature()).isEqualTo(900);

        needs.onHeld(new PlayerItemHeldEvent(alex, 3, 0));
        alex.getInventory().setHeldItemSlot(0);
        server.getScheduler().performOneTick();
        assertThat(hud.snapshot(ALEX).heldTemperature()).isZero();
    }

    /** Swapping hands and dropping the held stack republish the held temperature on the next tick, keeping the needs. */
    @Test
    void swapHandsAndDropRepublishTheHeldTemperature() {
        PlayerDataStore store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        PlayerMock alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        HudSnapshotCache hud = new HudSnapshotCache(() -> true);
        ItemHeatHooks heat = new ItemHeatHooks(() -> 100L, HeatConfig.DEFAULT, ItemHeatHooks.Capacities.UNIT);
        PlayerNeedsHudService needs = new PlayerNeedsHudService(store, new ServerScheduler(plugin, 1), hud, () -> 0L,
                heat::temperature);
        store.update(ALEX, d -> d.withThirst(42f));
        needs.publish(alex);
        assertThat(hud.snapshot(ALEX).heldTemperature()).isZero();

        ItemStack hot = new ItemStack(Material.COPPER_INGOT);
        HeatPdc.write(hot, new HeatState(900f, 100L));
        alex.getInventory().setItemInOffHand(hot);
        needs.onSwapHands(new PlayerSwapHandItemsEvent(alex, hot, new ItemStack(Material.AIR)));
        alex.getInventory().setItemInMainHand(hot); // the swap the server applies after the event
        alex.getInventory().setItemInOffHand(null);
        server.getScheduler().performOneTick();
        assertThat(hud.snapshot(ALEX).heldTemperature()).isEqualTo(900);
        assertThat(hud.snapshot(ALEX).thirst()).isEqualTo(42);

        alex.getInventory().setItemInMainHand(null);
        needs.onDrop(new PlayerDropItemEvent(alex, alex.getWorld().dropItem(alex.getLocation(), hot)));
        server.getScheduler().performOneTick();
        assertThat(hud.snapshot(ALEX).heldTemperature()).isZero();
    }

    /**
     * PAR-HUD-08 review: an inventory click or a drag into the held slot republishes the held temperature on the next
     * tick (taking the stack out again publishes 0). A click or drag whose clicker is not a player publishes nothing.
     */
    @Test
    void inventoryClickAndDragRepublishTheHeldTemperature() {
        PlayerDataStore store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        PlayerMock alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        HudSnapshotCache hud = new HudSnapshotCache(() -> true);
        ItemHeatHooks heat = new ItemHeatHooks(() -> 100L, HeatConfig.DEFAULT, ItemHeatHooks.Capacities.UNIT);
        PlayerNeedsHudService needs = new PlayerNeedsHudService(store, new ServerScheduler(plugin, 1), hud, () -> 0L,
                heat::temperature);
        alex.getInventory().setHeldItemSlot(0);
        needs.publish(alex);
        assertThat(hud.snapshot(ALEX).heldTemperature()).isZero();
        ItemStack hot = new ItemStack(Material.COPPER_INGOT);
        HeatPdc.write(hot, new HeatState(900f, 100L));
        // A 9-slot chest is open (MockBukkit's own crafting view does not convert raw slots); hotbar slot 0 is raw 36.
        InventoryView view = alex.openInventory(server.createInventory(null, 9));

        // A click that places the hot stack into the held hotbar slot.
        needs.onInventoryClick(new InventoryClickEvent(view, InventoryType.SlotType.QUICKBAR, HOTBAR_0_RAW,
                ClickType.LEFT, InventoryAction.PLACE_ALL));
        alex.getInventory().setItemInMainHand(hot); // what the server applies after the event
        server.getScheduler().performOneTick();
        assertThat(hud.snapshot(ALEX).heldTemperature()).isEqualTo(900);

        // A click that picks it up again.
        needs.onInventoryClick(new InventoryClickEvent(view, InventoryType.SlotType.QUICKBAR, HOTBAR_0_RAW,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        alex.getInventory().setItemInMainHand(null);
        server.getScheduler().performOneTick();
        assertThat(hud.snapshot(ALEX).heldTemperature()).isZero();

        // A drag that spreads the hot cursor stack over the held slot.
        needs.onInventoryDrag(new InventoryDragEvent(view, null, hot.clone(), false, Map.of(HOTBAR_0_RAW, hot.clone())));
        alex.getInventory().setItemInMainHand(hot);
        server.getScheduler().performOneTick();
        assertThat(hud.snapshot(ALEX).heldTemperature()).isEqualTo(900);

        // Not a player: the same changes publish nothing.
        InventoryView npcView = withViewer(view, notAPlayer());
        alex.getInventory().setItemInMainHand(null);
        needs.onInventoryClick(new InventoryClickEvent(npcView, InventoryType.SlotType.QUICKBAR, HOTBAR_0_RAW,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
        needs.onInventoryDrag(new InventoryDragEvent(npcView, null, hot.clone(), false, Map.of(HOTBAR_0_RAW, hot.clone())));
        server.getScheduler().performOneTick();
        assertThat(hud.snapshot(ALEX).heldTemperature()).isEqualTo(900);
    }

    /**
     * PAR-HUD-08 review: picking up an item that lands in the held slot republishes the held temperature on the next
     * tick; a pickup by a non-player entity publishes nothing.
     */
    @Test
    void pickupRepublishesTheHeldTemperature() {
        PlayerDataStore store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        PlayerMock alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        HudSnapshotCache hud = new HudSnapshotCache(() -> true);
        ItemHeatHooks heat = new ItemHeatHooks(() -> 100L, HeatConfig.DEFAULT, ItemHeatHooks.Capacities.UNIT);
        PlayerNeedsHudService needs = new PlayerNeedsHudService(store, new ServerScheduler(plugin, 1), hud, () -> 0L,
                heat::temperature);
        needs.publish(alex);
        assertThat(hud.snapshot(ALEX).heldTemperature()).isZero();
        ItemStack hot = new ItemStack(Material.COPPER_INGOT);
        HeatPdc.write(hot, new HeatState(900f, 100L));
        Item dropped = alex.getWorld().dropItem(alex.getLocation(), hot);

        needs.onPickup(new EntityPickupItemEvent(alex, dropped, 0));
        alex.getInventory().setItemInMainHand(hot); // the stack lands in the empty held slot after the event
        server.getScheduler().performOneTick();
        assertThat(hud.snapshot(ALEX).heldTemperature()).isEqualTo(900);

        Zombie zombie = alex.getWorld().spawn(alex.getLocation(), Zombie.class);
        alex.getInventory().setItemInMainHand(null);
        needs.onPickup(new EntityPickupItemEvent(zombie, dropped, 0));
        server.getScheduler().performOneTick();
        assertThat(hud.snapshot(ALEX).heldTemperature()).isEqualTo(900);
    }

    /** Raw slot of hotbar slot 0 below a 9-slot chest (chest 0-8, main inventory 9-35). */
    private static final int HOTBAR_0_RAW = 36;

    /** A human entity that is not a player (Bukkit allows non-player viewers); every call but identity returns null. */
    private static HumanEntity notAPlayer() {
        return (HumanEntity) Proxy.newProxyInstance(HumanEntity.class.getClassLoader(), new Class<?>[] {HumanEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "not a player";
                    default -> null;
                });
    }

    /** {@code view} with {@code viewer} as its player: what {@code getWhoClicked} returns. */
    private static InventoryView withViewer(InventoryView view, HumanEntity viewer) {
        return (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(),
                new Class<?>[] {InventoryView.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getPlayer") && method.getParameterCount() == 0) {
                        return viewer;
                    }
                    try {
                        return method.invoke(view, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /** Without the plugin's heat hooks (the public constructor outside the plugin class loader) the value is 0. */
    @Test
    void withoutHeatHooksTheHeldTemperatureIsZero() {
        PlayerDataStore store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        PlayerMock alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        HudSnapshotCache hud = new HudSnapshotCache(() -> true);
        PlayerNeedsHudService needs = new PlayerNeedsHudService(store, new ServerScheduler(plugin, 1), hud, () -> 0L);
        ItemStack ingot = new ItemStack(Material.COPPER_INGOT);
        HeatPdc.write(ingot, new HeatState(900f, 0L));
        alex.getInventory().setItemInMainHand(ingot);
        needs.publish(alex);
        assertThat(hud.snapshot(ALEX).heldTemperature()).isZero();
    }
}
