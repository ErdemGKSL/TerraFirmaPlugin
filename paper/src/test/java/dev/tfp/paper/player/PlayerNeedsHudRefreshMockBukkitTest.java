/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
}
