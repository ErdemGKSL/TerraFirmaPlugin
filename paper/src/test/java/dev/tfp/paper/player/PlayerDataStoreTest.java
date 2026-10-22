/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.DataMapBinary;
import dev.tfp.core.persist.PersistException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * M1-SVC-02 without a server: join (pre-login load), play (update), quit (async save), rejoin, crash
 * safety of the file writes, and restart (a new store on the same directory).
 */
class PlayerDataStoreTest {
    static final Logger LOG = Logger.getLogger("PlayerDataStoreTest");
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e7");

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Drains the writer before JUnit deletes {@link #dir}: some tests start saves they never wait for
     * ({@code unloadAbsent} saves in the background), and a write still creating its temp file and renaming it
     * into {@code dir} made the @TempDir cleanup fail now and then. Interrupting instead ({@code shutdownNow})
     * would not help: the interrupted write can still leave its temp file behind.
     */
    @AfterEach
    void stop() throws InterruptedException {
        io.shutdown();
        assertThat(io.awaitTermination(10, TimeUnit.SECONDS)).as("pending player data writes finished").isTrue();
    }

    static PlayerData played() {
        return PlayerData.NEW.withThirst(42.5f).withLastDrinkTick(1_000L).withIntoxicationTick(9_000L)
                .withChiselMode("tfp:stair")
                .withNutrition(List.of(
                        new PlayerData.FoodRecord(4, 5f, 1.5f, 0, new PlayerData.Nutrients(1.2f, 0, 0.5f, 0, 0), 1.0f),
                        new PlayerData.FoodRecord(0, 20f, 0f, 2, PlayerData.Nutrients.NONE, 0f)));
    }

    PlayerDataStore store() {
        return new PlayerDataStore(new PlayerDataFiles(dir), io, LOG);
    }

    @Test
    void joinQuitRejoinAndRestartKeepTheData() throws Exception {
        PlayerDataStore store = store();
        assertThat(store.loadBlocking(ALEX)).isEqualTo(PlayerData.NEW); // first join
        store.update(ALEX, d -> played());
        store.unload(ALEX).get(10, TimeUnit.SECONDS); // quit
        assertThat(store.isLoaded(ALEX)).isFalse();

        assertThat(store.loadBlocking(ALEX)).isEqualTo(played()); // rejoin
        store.close(10, TimeUnit.SECONDS); // server stop

        PlayerDataStore restarted = store();
        assertThat(restarted.load(ALEX).get(10, TimeUnit.SECONDS)).isEqualTo(played());
    }

    @Test
    void quickRejoinWaitsForThePendingSave() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        PlayerDataFiles slow = new PlayerDataFiles(dir, (ch, buf) -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
        });
        PlayerDataStore store = new PlayerDataStore(slow, io, LOG);
        store.loadBlocking(ALEX);
        store.update(ALEX, d -> played());
        var saving = store.unload(ALEX);
        var rejoin = java.util.concurrent.CompletableFuture.supplyAsync(() -> store.loadBlocking(ALEX), io);
        Thread.sleep(50);
        assertThat(rejoin).isNotDone(); // waits for the save instead of reading the old file
        release.countDown();
        saving.get(10, TimeUnit.SECONDS);
        assertThat(rejoin.get(10, TimeUnit.SECONDS)).isEqualTo(played());
    }

    @Test
    void crashMidWriteLeavesTheOldFile() throws Exception {
        PlayerDataFiles good = new PlayerDataFiles(dir);
        good.write(ALEX, played());
        byte[] before = Files.readAllBytes(good.file(ALEX));

        // The process "dies" half-way through writing the new file.
        PlayerDataFiles crashing = new PlayerDataFiles(dir, (ch, buf) -> {
            buf.limit(buf.limit() / 2);
            ch.write(buf);
            throw new IOException("simulated crash");
        });
        assertThatThrownBy(() -> crashing.write(ALEX, PlayerData.NEW.withThirst(1f))).hasMessageContaining("simulated crash");
        assertThat(Files.readAllBytes(good.file(ALEX))).isEqualTo(before);

        // A real crash leaves the half-written temp file behind: it is ignored and cleaned at startup.
        Files.write(dir.resolve(ALEX + ".123" + PlayerDataFiles.TEMP_SUFFIX), new byte[] {'T', 'F', 'P', 'P', 0, 0});
        PlayerDataStore restarted = store();
        assertThat(restarted.loadBlocking(ALEX)).isEqualTo(played());
        assertThat(new PlayerDataFiles(dir).deleteStaleTempFiles()).isEqualTo(1);
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).noneMatch(n -> n.endsWith(PlayerDataFiles.TEMP_SUFFIX));
        }
    }

    @Test
    void tornMainFileFallsBackToTheBackup() throws Exception {
        PlayerDataFiles files = new PlayerDataFiles(dir);
        files.write(ALEX, played());
        files.write(ALEX, played().withThirst(10f)); // the first version is now the backup
        byte[] main = Files.readAllBytes(files.file(ALEX));
        Files.write(files.file(ALEX), java.util.Arrays.copyOf(main, main.length - 3)); // torn by a non-atomic FS

        PlayerDataFiles.Loaded loaded = files.read(ALEX).orElseThrow();
        assertThat(loaded.fromBackup()).isTrue();
        assertThat(loaded.data()).isEqualTo(played());

        // A flipped bit is caught by the checksum.
        main[main.length - 1] ^= 1;
        Files.write(files.file(ALEX), main);
        assertThat(files.read(ALEX).orElseThrow().fromBackup()).isTrue();

        // Both unreadable: refuse (the listener disallows the login) and touch nothing.
        Files.write(files.backup(ALEX), new byte[] {1, 2, 3});
        assertThatThrownBy(() -> files.read(ALEX)).isInstanceOf(PersistException.class).hasMessageContaining("backup");
        assertThatThrownBy(() -> store().loadBlocking(ALEX)).isInstanceOf(PersistException.class);
    }

    @Test
    void newerDataIsRefusedAndNotOverwritten() throws Exception {
        DataMap newer = PlayerDataCodec.CODEC.encode(played()).putInt("v", PlayerDataCodec.VERSION + 1);
        Path file = new PlayerDataFiles(dir).file(ALEX);
        Files.createDirectories(dir);
        Files.write(file, PlayerDataFiles.frame(DataMapBinary.encode(newer)));
        byte[] before = Files.readAllBytes(file);
        assertThatThrownBy(() -> store().loadBlocking(ALEX)).isInstanceOf(PersistException.NewerVersion.class);
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
    }

    @Test
    void codecRoundTripsAndKeepsTfcFieldNames() {
        DataMap m = PlayerDataCodec.CODEC.encode(played());
        assertThat(m.keys()).containsExactly("v", "thirst", "lastDrinkTick", "intoxication", "chiselMode", "nutrition",
                "thirstTick", "passiveExhaustion");
        assertThat(m.getList("nutrition").getFirst().keys()).contains("hunger", "water", "grain", "dairy", "decay_modifier");
        assertThat(PlayerDataCodec.CODEC.decode(m)).isEqualTo(played());
        assertThat(PlayerDataCodec.CODEC.fromBytes(PlayerDataCodec.CODEC.toBytes(PlayerData.NEW))).isEqualTo(PlayerData.NEW);
    }

    @Test
    void autosaveWritesOnlyChangedPlayersAndAbsentPlayersAreUnloaded() throws Exception {
        PlayerDataStore store = store();
        UUID bob = UUID.randomUUID();
        store.loadBlocking(ALEX);
        store.loadBlocking(bob);
        store.update(ALEX, d -> d.withThirst(5f));
        store.saveDirty().get(10, TimeUnit.SECONDS);
        assertThat(Files.exists(new PlayerDataFiles(dir).file(ALEX))).isTrue();
        assertThat(Files.exists(new PlayerDataFiles(dir).file(bob))).isFalse();

        // bob joined, then left without a quit event (another plugin refused the login after we loaded him)
        assertThat(store.unloadAbsent(Set.of(ALEX, bob), 0)).isEqualTo(0);
        assertThat(store.unloadAbsent(Set.of(ALEX), 0)).isEqualTo(1);
        assertThat(store.loaded()).containsExactly(ALEX);
        assertThatThrownBy(() -> store.update(bob, d -> d)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aPlayerStillLoggingInIsKeptPastTheShortGrace() {
        PlayerDataStore store = store();
        store.loadBlocking(ALEX);
        // never seen online yet (a slow resource pack download): the short grace does not unload him
        assertThat(store.unloadAbsent(Set.of(), 0)).isEqualTo(0);
        assertThat(store.loaded()).containsExactly(ALEX);
    }

    @Test
    void concurrentUpdatesAreNotLost() throws Exception {
        PlayerDataStore store = store();
        store.loadBlocking(ALEX);
        store.update(ALEX, d -> d.withThirst(0f));
        int threads = 4;
        int each = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < each; i++) {
                        store.update(ALEX, d -> d.withLastDrinkTick(d.lastDrinkTick() == Long.MIN_VALUE ? 1 : d.lastDrinkTick() + 1));
                    }
                });
            }
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(store.get(ALEX).orElseThrow().lastDrinkTick()).isEqualTo((long) threads * each);
    }
}
