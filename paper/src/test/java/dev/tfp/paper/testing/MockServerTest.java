/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.testing;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

/**
 * Base class for Paper-glue tests on MockBukkit (M1-TEST-02, {@code plugin-testing} layer 4): a fresh
 * {@link ServerMock} and an owning {@link PluginMock} per test. Use it for listeners, PDC codecs and
 * scheduler wiring; use the fakes of <b>our</b> adapter interfaces ({@code dev.tfp.paper.integration.Fake*})
 * for CraftEngine/BetterModel/BetterHud, never mocks of their internals.
 *
 * <p>Known limits (do not trust MockBukkit for them): {@code isOwnedByCurrentRegion} is
 * {@code isPrimaryThread()} (the thread that created the mock), so it sees "main vs other thread" but not
 * wrong-region access; no worldgen pipeline, packets or NMS; the bootstrapper/loader and Brigadier
 * lifecycle commands do not run. Those belong to the in-server self-test ({@code :itest}).
 */
public abstract class MockServerTest {
    protected ServerMock server;
    protected PluginMock plugin;

    @BeforeEach
    void startMockServer() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TerraFirmaPluginTest");
    }

    @AfterEach
    void stopMockServer() {
        MockBukkit.unmock();
    }

    protected Plugin plugin() {
        return plugin;
    }

    /** Runs {@code body} on a fresh non-primary thread (async events, wrong-thread checks) and returns its value. */
    protected static <T> T offMainThread(Supplier<T> body) {
        try {
            return CompletableFuture.supplyAsync(body, r -> Thread.ofPlatform().name("tfp-test-async").start(r))
                    .get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) {
                throw re;
            }
            if (c instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(c);
        } catch (InterruptedException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@link #offMainThread(Supplier)} for a body without a result. */
    protected static void offMainThread(Runnable body) {
        offMainThread(() -> {
            body.run();
            return null;
        });
    }
}
