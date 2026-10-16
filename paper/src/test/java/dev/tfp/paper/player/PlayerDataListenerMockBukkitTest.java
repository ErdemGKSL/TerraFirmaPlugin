/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * M1-TEST-02 sample: {@link PlayerDataListener} registered on MockBukkit's plugin manager. Pre-login is
 * fired from an async thread (as the server does), the quit by disconnecting a {@link PlayerMock}.
 */
class PlayerDataListenerMockBukkitTest extends MockServerTest {
    static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-0000000057e7");

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    @Test
    @SuppressWarnings("removal") // the short AsyncPlayerPreLoginEvent constructor is all a test needs
    void preLoginLoadsAndQuitSaves() throws Exception {
        PlayerDataFiles files = new PlayerDataFiles(dir);
        PlayerDataStore store = new PlayerDataStore(files, io, Logger.getLogger("test"));
        server.getPluginManager().registerEvents(new PlayerDataListener(store, Messages.fromJar(), Logger.getLogger("test")),
                plugin);

        AsyncPlayerPreLoginEvent login = offMainThread(() -> {
            AsyncPlayerPreLoginEvent e = new AsyncPlayerPreLoginEvent("Steve", InetAddress.getLoopbackAddress(), STEVE);
            server.getPluginManager().callEvent(e);
            return e;
        });
        assertThat(login.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(store.get(STEVE)).contains(PlayerData.NEW);

        PlayerMock steve = new PlayerMock(server, "Steve", STEVE);
        server.addPlayer(steve);
        store.update(STEVE, d -> d.withThirst(12.5f));
        steve.disconnect();

        assertThat(store.isLoaded(STEVE)).isFalse();
        // the quit save is asynchronous: a new store (a restart) waits for it through the file
        io.shutdown();
        assertThat(io.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(files.read(STEVE)).hasValueSatisfying(l -> assertThat(l.data().thirst()).isEqualTo(12.5f));
    }
}
