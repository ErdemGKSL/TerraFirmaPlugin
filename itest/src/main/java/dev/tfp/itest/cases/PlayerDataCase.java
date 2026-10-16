/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.player.PlayerDataStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * M1-SVC-02: the server's player data store round-trips a fake player through disk (load as at pre-login,
 * change, unload as at quit, load again), then removes the fake player's files. Runs on the io executor.
 */
public final class PlayerDataCase implements SelfTestCase {
    static final UUID FAKE = UUID.nameUUIDFromBytes("tfp-selftest-player".getBytes(StandardCharsets.UTF_8));

    @Override
    public String name() {
        return "player data round-trip for a fake UUID (M1-SVC-02)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(30);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        return CompletableFuture.supplyAsync(() -> roundTrip(tfp), tfp.scheduler().ioExecutor());
    }

    private static String roundTrip(TfpPlugin tfp) {
        PlayerDataStore store = tfp.playerData();
        if (store == null) {
            throw new AssertionError("no player data store");
        }
        Path dir = tfp.getDataPath().resolve("playerdata");
        deleteFiles(dir);
        try {
            PlayerData first = store.loadBlocking(FAKE);
            if (!first.equals(PlayerData.NEW)) {
                throw new AssertionError("a player without a file should load as NEW, got " + first);
            }
            PlayerData changed = first.withThirst(33.25f).withLastDrinkTick(1234L).withChiselMode("tfp:stair");
            store.update(FAKE, d -> changed);
            store.unload(FAKE).get(10, TimeUnit.SECONDS);
            if (store.isLoaded(FAKE)) {
                throw new AssertionError("still loaded after unload");
            }
            PlayerData again = store.loadBlocking(FAKE);
            if (!again.equals(changed)) {
                throw new AssertionError("reloaded " + again + ", saved " + changed);
            }
            store.unload(FAKE).get(10, TimeUnit.SECONDS);
            return "saved and reloaded " + FAKE + " in " + dir.getFileName();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            store.unload(FAKE).join();
            deleteFiles(dir);
        }
    }

    private static void deleteFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, FAKE + "*")) {
            for (Path f : files) {
                Files.deleteIfExists(f);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
