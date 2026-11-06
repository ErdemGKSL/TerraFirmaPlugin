/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rendered;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-INFRA-32: the listener starts, feeds and stops a player's per-tick dig task, per game mode. */
class ServerTimeMiningMockBukkitTest extends MockServerTest {
    /** A driver that says "mining" while {@link #mining} is set and counts the acknowledged ticks. */
    static final class FakeDriver implements MiningDriver {
        boolean mining = true;
        final Map<UUID, Integer> advanced = new HashMap<>();

        @Override
        public boolean isServerMining(Player player) {
            return mining;
        }

        @Override
        public void advance(Player player) {
            advanced.merge(player.getUniqueId(), 1, Integer::sum);
        }

        int count(Player p) {
            return advanced.getOrDefault(p.getUniqueId(), 0);
        }
    }

    static final class Handle implements Scheduler.TaskHandle {
        final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    final FakeDriver driver = new FakeDriver();
    final List<Handle> started = new ArrayList<>();
    MiningDriver installed;
    ServerTimeMining mining;
    WorldMock world;
    PlayerMock player;
    Block block;

    @BeforeEach
    void setUp() {
        installed = driver;
        mining = new ServerTimeMining(() -> installed, (p, tick, retired) -> {
            Handle h = new Handle();
            started.add(h);
            return h;
        });
        server.getPluginManager().registerEvents(mining, plugin);
        world = server.addSimpleWorld("mining");
        player = server.addPlayer();
        player.teleport(world.getSpawnLocation());
        player.setGameMode(GameMode.SURVIVAL);
        block = world.getBlockAt(1, 64, 1);
    }

    void digStart() {
        server.getPluginManager().callEvent(new BlockDamageEvent(player, block, BlockFace.UP, new ItemStack(Material.AIR), false));
    }

    void swing() {
        server.getPluginManager().callEvent(new PlayerAnimationEvent(player, PlayerAnimationType.ARM_SWING));
    }

    /** {@code n} server ticks with a swing in every {@code swingEvery}-th one (0: none). */
    void ticks(int n, int swingEvery) {
        for (int i = 1; i <= n; i++) {
            if (swingEvery > 0 && i % swingEvery == 0) {
                swing();
            }
            mining.tick(player);
        }
    }

    @Test
    void everyServerTickCountsOnceWhateverTheSwingRate() {
        digStart();
        assertThat(started).hasSize(1);
        ticks(100, 3);
        assertThat(driver.count(player)).isEqualTo(100);
        digStart();
        assertThat(started).as("a new dig reuses the running task").hasSize(1);
    }

    @Test
    void abortStopsTheTask() {
        digStart();
        ticks(5, 1);
        server.getPluginManager().callEvent(new BlockDamageAbortEvent(player, block, new ItemStack(Material.AIR)));
        ticks(5, 1);
        assertThat(driver.count(player)).isEqualTo(5);
        assertThat(started.getFirst().isCancelled()).isTrue();
        assertThat(mining.active()).isZero();
    }

    @Test
    void creativeAndSpectatorDoNotStartAndSwitchingStops() {
        player.setGameMode(GameMode.CREATIVE);
        digStart();
        player.setGameMode(GameMode.SPECTATOR);
        digStart();
        assertThat(started).isEmpty();

        player.setGameMode(GameMode.ADVENTURE);
        digStart();
        ticks(3, 1);
        assertThat(driver.count(player)).as("adventure digs like survival").isEqualTo(3);
        player.setGameMode(GameMode.CREATIVE);
        ticks(3, 1);
        assertThat(driver.count(player)).isEqualTo(3);
        assertThat(mining.active()).isZero();
    }

    @Test
    void instantBreaksAndAMissingDriverDoNothing() {
        server.getPluginManager().callEvent(new BlockDamageEvent(player, block, BlockFace.UP, new ItemStack(Material.AIR), true));
        installed = MiningDriver.NONE;
        digStart();
        assertThat(started).isEmpty();
    }

    @Test
    void aBlockThePluginDoesNotMineStopsAfterTheTimeout() {
        driver.mining = false;
        digStart();
        ticks(MiningSession.NOT_MINING_TIMEOUT + 1, 1);
        assertThat(driver.count(player)).isZero();
        assertThat(mining.active()).isZero();
    }

    @Test
    void quitForgetsTheDig() {
        digStart();
        player.disconnect();
        assertThat(mining.active()).isZero();
        assertThat(started.getFirst().isCancelled()).isTrue();
    }
}
