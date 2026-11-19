/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player.exhaustion;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.player.PlayerDataFiles;
import dev.tfp.paper.player.PlayerDataStore;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-SURV-15: the vanilla exhaustion event becomes TFC's 0.4x pool, thirst and food bar. */
class ExhaustionModuleMockBukkitTest extends MockServerTest {
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000015a1");

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    PlayerDataStore store;
    PlayerMock alex;
    ExhaustionModule module;

    @BeforeEach
    void setUpPlayer() {
        store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        store.update(ALEX, d -> d.withThirstClock(100f, 0L, 0L));
        alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        alex.setGameMode(GameMode.SURVIVAL);
        alex.setFoodLevel(20);
        alex.setSaturation(0f);
        module = new ExhaustionModule(store, () -> 0L, new HudSnapshotCache(() -> true));
    }

    private EntityExhaustionEvent fire(EntityExhaustionEvent.ExhaustionReason reason, float amount) {
        EntityExhaustionEvent e = new EntityExhaustionEvent(alex, reason, amount);
        module.onExhaustion(e);
        return e;
    }

    @Test
    void sprintExhaustionIsScaledToTheTfcPoolAndTakesFoodAtFour() {
        for (int i = 0; i < 101; i++) {
            assertThat(fire(EntityExhaustionEvent.ExhaustionReason.SPRINT, 0.1f).isCancelled()).isTrue();
        }
        assertThat(alex.getFoodLevel()).isEqualTo(19);
        assertThat(store.get(ALEX).orElseThrow().thirst()).isEqualTo(95f);
        assertThat(store.get(ALEX).orElseThrow().pendingFoodPoints()).isZero();
    }

    @Test
    void regenIsLeftToTheRegenerator() {
        assertThat(fire(EntityExhaustionEvent.ExhaustionReason.REGEN, 6f).isCancelled()).isFalse();
    }

    @Test
    void creativeAddsNothing() {
        alex.setGameMode(GameMode.CREATIVE);
        fire(EntityExhaustionEvent.ExhaustionReason.JUMP, 40f);
        assertThat(alex.getFoodLevel()).isEqualTo(20);
        assertThat(store.get(ALEX).orElseThrow().thirst()).isEqualTo(100f);
    }
}
