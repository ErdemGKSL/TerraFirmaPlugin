/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

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
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-HUD-12: a game-mode change through {@link PlayerNeedsHudService#onGameModeChange}. Creative and spectator publish
 * the snapshot with the needs off (the bars and the popup hide) and pause the thirst; back in survival or adventure the
 * bars show again at once and the thirst resumes from where it was, with nothing of the paused time caught up.
 */
class GameModeNeedsMockBukkitTest extends MockServerTest {
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000012a1");

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    final long[] now = {0L};
    PlayerDataStore store;
    PlayerMock alex;
    HudSnapshotCache hud;
    PlayerNeedsHudService needs;

    @BeforeEach
    void setUpPlayer() {
        store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        alex.setGameMode(GameMode.SURVIVAL);
        hud = new HudSnapshotCache(() -> true);
        needs = new PlayerNeedsHudService(store, new ServerScheduler(plugin, 1), hud, () -> now[0],
                ItemHeatHooks.Capacities.UNIT);
    }

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    /** What the server does: the event (before the change), the change, then the player's next tick. */
    private void switchTo(GameMode mode) {
        needs.onGameModeChange(new PlayerGameModeChangeEvent(alex, mode));
        alex.setGameMode(mode);
        server.getScheduler().performOneTick();
    }

    @Test
    void creativeHidesTheBarsAndPausesTheThirstSurvivalRestoresThemWithNoCatchUp() {
        store.update(ALEX, d -> d.withThirst(80f));
        needs.publish(alex); // starts the clock at 0
        assertThat(hud.snapshot(ALEX).needsActive()).isTrue();

        now[0] = 5_000L;
        switchTo(GameMode.CREATIVE);
        float atSwitch = store.get(ALEX).orElseThrow().thirst();
        assertThat(atSwitch).as("the survival time before the switch counts").isLessThan(80f);
        assertThat(hud.snapshot(ALEX).needsActive()).isFalse();
        assertThat(hud.snapshot(ALEX).thirst()).isEqualTo(atSwitch);

        now[0] = 15_000L;
        needs.publish(alex); // a throttled reading in creative
        assertThat(store.get(ALEX).orElseThrow().thirst()).isEqualTo(atSwitch);
        assertThat(hud.snapshot(ALEX).needsActive()).isFalse();

        now[0] = 20_000L;
        switchTo(GameMode.SURVIVAL);
        assertThat(hud.snapshot(ALEX).needsActive()).isTrue();
        assertThat(store.get(ALEX).orElseThrow().thirst()).as("no catch-up").isEqualTo(atSwitch);
        assertThat(store.get(ALEX).orElseThrow().thirstTick()).isEqualTo(20_000L);

        now[0] = 25_000L;
        needs.publish(alex);
        assertThat(store.get(ALEX).orElseThrow().thirst()).as("decays again").isLessThan(atSwitch);
    }

    @Test
    void spectatorIsLikeCreativeAndAdventureLikeSurvival() {
        needs.publish(alex);
        switchTo(GameMode.ADVENTURE);
        needs.publish(alex);
        assertThat(hud.snapshot(ALEX).needsActive()).isTrue();

        switchTo(GameMode.SPECTATOR);
        assertThat(hud.snapshot(ALEX).needsActive()).isFalse();
        float paused = store.get(ALEX).orElseThrow().thirst();
        now[0] = 1_000_000L;
        needs.publish(alex);
        assertThat(store.get(ALEX).orElseThrow().thirst()).isEqualTo(paused);

        switchTo(GameMode.CREATIVE); // no change of kind: nothing scheduled, still hidden and paused
        assertThat(hud.snapshot(ALEX).needsActive()).isFalse();
        assertThat(store.get(ALEX).orElseThrow().thirst()).isEqualTo(paused);

        switchTo(GameMode.ADVENTURE);
        assertThat(hud.snapshot(ALEX).needsActive()).isTrue();
        assertThat(store.get(ALEX).orElseThrow().thirst()).isEqualTo(paused);
    }

    /** The held-item popup's record is still published in creative; the gate ({@code needsActive}) hides it. */
    @Test
    void theHeldHeatIsKeptBehindTheGate() {
        ItemStack ingot = new ItemStack(Material.COPPER_INGOT);
        HeatPdc.write(ingot, new HeatState(900f, 0L));
        alex.getInventory().setItemInMainHand(ingot);
        switchTo(GameMode.CREATIVE);
        assertThat(hud.snapshot(ALEX).needsActive()).isFalse();
        assertThat(hud.snapshot(ALEX).heldTemperature()).isEqualTo(900);
        switchTo(GameMode.SURVIVAL);
        assertThat(hud.snapshot(ALEX).needsActive()).isTrue();
    }

    /**
     * The event handler alone, before the mode is applied and before any tick (Paper fires the event before the change):
     * the snapshot already holds the new mode's flag and the clock is settled at the switch, so the HUD never shows the
     * previous mode's state while waiting for a later tick.
     */
    private void eventOnly(GameMode mode) {
        GameMode before = alex.getGameMode();
        needs.onGameModeChange(new PlayerGameModeChangeEvent(alex, mode));
        assertThat(alex.getGameMode()).as("the mode is not applied yet").isEqualTo(before);
    }

    @Test
    void theSnapshotBuiltDuringTheEventHoldsTheNewMode() {
        store.update(ALEX, d -> d.withThirst(80f));
        needs.publish(alex);
        assertThat(hud.snapshot(ALEX).needsActive()).isTrue();

        now[0] = 5_000L;
        eventOnly(GameMode.CREATIVE);
        assertThat(hud.snapshot(ALEX).needsActive()).as("survival -> creative, in the event").isFalse();
        assertThat(store.get(ALEX).orElseThrow().thirstTick()).isEqualTo(5_000L);
        float atSwitch = store.get(ALEX).orElseThrow().thirst();
        assertThat(atSwitch).as("the survival time before the switch counts").isLessThan(80f);
        assertThat(hud.snapshot(ALEX).thirst()).isEqualTo(atSwitch);
        alex.setGameMode(GameMode.CREATIVE);
        server.getScheduler().performOneTick(); // the next-tick safety net agrees
        assertThat(hud.snapshot(ALEX).needsActive()).isFalse();

        now[0] = 9_000L;
        eventOnly(GameMode.SURVIVAL);
        assertThat(hud.snapshot(ALEX).needsActive()).as("creative -> survival, in the event").isTrue();
        assertThat(store.get(ALEX).orElseThrow().thirstTick()).isEqualTo(9_000L);
        assertThat(store.get(ALEX).orElseThrow().thirst()).as("no catch-up").isEqualTo(atSwitch);
        alex.setGameMode(GameMode.SURVIVAL);
        server.getScheduler().performOneTick();
        assertThat(hud.snapshot(ALEX).needsActive()).isTrue();

        now[0] = 12_000L;
        eventOnly(GameMode.SPECTATOR);
        assertThat(hud.snapshot(ALEX).needsActive()).as("survival -> spectator, in the event").isFalse();
        assertThat(store.get(ALEX).orElseThrow().thirstTick()).isEqualTo(12_000L);
        alex.setGameMode(GameMode.SPECTATOR);
        server.getScheduler().performOneTick();
        float paused = store.get(ALEX).orElseThrow().thirst();

        now[0] = 20_000L;
        eventOnly(GameMode.ADVENTURE);
        assertThat(hud.snapshot(ALEX).needsActive()).as("spectator -> adventure, in the event").isTrue();
        assertThat(store.get(ALEX).orElseThrow().thirstTick()).isEqualTo(20_000L);
        assertThat(store.get(ALEX).orElseThrow().thirst()).as("no catch-up").isEqualTo(paused);
    }

    /** PROTOCOL section 4 rule 7: a player joining in creative gets the bars hidden on the next tick, not after the throttle. */
    @Test
    void aJoinInCreativePublishesTheHiddenBars() {
        alex.setGameMode(GameMode.CREATIVE);
        assertThat(hud.snapshot(ALEX).needsActive()).as("the default snapshot").isTrue();
        needs.onJoin(new PlayerJoinEvent(alex, (Component) null));
        server.getScheduler().performOneTick();
        assertThat(hud.snapshot(ALEX).needsActive()).isFalse();
    }

    /** A switch before the player data is loaded (or after it is unloaded) publishes nothing and throws nothing. */
    @Test
    void aSwitchWithoutDataDoesNothing() {
        store.unload(ALEX).join();
        switchTo(GameMode.CREATIVE);
        assertThat(store.isLoaded(ALEX)).isFalse();
        assertThat(hud.snapshot(ALEX).needsActive()).isTrue(); // the default snapshot, nothing published
    }
}
