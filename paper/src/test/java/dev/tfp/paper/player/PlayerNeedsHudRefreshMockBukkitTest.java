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
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
