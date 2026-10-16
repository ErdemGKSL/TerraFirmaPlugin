/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.tag.TagType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Off-thread reload + atomic swap while other threads keep using the registries. */
class RegistriesReloadTest {
    @TempDir
    Path tmp;

    final ExecutorService loaderPool = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdown() {
        loaderPool.shutdownNow();
    }

    static final KnownIds IDS = KnownIds.permissive(Set.of());

    /** A data root whose every entry carries generation {@code gen}, with {@code 10 + gen % 5} recipes. */
    Path generation(int gen) throws IOException {
        Path root = tmp.resolve("gen" + gen).resolve("data");
        int count = 10 + gen % 5;
        for (int i = 0; i < count; i++) {
            Path f = root.resolve("tfp/recipe/r" + i + ".json");
            Files.createDirectories(f.getParent());
            Files.writeString(f, "{\"type\": \"tfc:heating\", \"ingredient\": {\"item\": \"tfp:g" + gen + "\"}, \"temperature\": " + gen + "}");
        }
        Path tag = root.resolve("tfp/tags/item/gen.json");
        Files.createDirectories(tag.getParent());
        Files.writeString(tag, "{\"values\": [\"tfp:g" + gen + "\"]}");
        Path count0 = root.resolve("tfp/tfp/fuel/count.json");
        Files.createDirectories(count0.getParent());
        Files.writeString(count0, "{\"ingredient\": {\"item\": \"tfp:g" + gen + "\"}, \"duration\": " + count + ", \"temperature\": " + gen + "}");
        return root;
    }

    /** One "operation": everything read from one snapshot must belong to one generation. */
    static boolean consistent(RegistrySnapshot s) {
        Registry<SchemaRecord> recipes = s.get(DefinitionKinds.RECIPE);
        if (recipes.size() == 0) {
            return s.tags().size(TagType.ITEM) == 0; // the initial empty snapshot
        }
        SchemaRecord fuel = s.get(DefinitionKinds.key("fuel")).getOrThrow("tfp:count");
        float gen = fuel.require("temperature", Float.class);
        if (fuel.require("duration", Integer.class) != recipes.size()) {
            return false;
        }
        for (SchemaRecord r : recipes.values()) {
            if (r.require("temperature", Float.class) != gen) {
                return false;
            }
        }
        return s.tags().contains(TagType.ITEM, "tfp:gen", "tfp:g" + (int) gen);
    }

    @Test
    void readersAlwaysSeeOneCompleteGenerationDuringReloads() throws Exception {
        Registries registries = new Registries();
        int generations = 30;
        List<Path> roots = new ArrayList<>();
        for (int g = 1; g <= generations; g++) {
            roots.add(generation(g));
        }
        int readers = 4;
        AtomicBoolean stop = new AtomicBoolean();
        AtomicLong reads = new AtomicLong();
        CountDownLatch started = new CountDownLatch(readers);
        ExecutorService readerPool = Executors.newFixedThreadPool(readers);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < readers; i++) {
            results.add(readerPool.submit(() -> {
                started.countDown();
                long lastVersion = 0;
                while (!stop.get()) {
                    long version = registries.version();
                    RegistrySnapshot s = registries.get();
                    if (!consistent(s)) {
                        return "inconsistent snapshot at version " + version;
                    }
                    if (version < lastVersion) {
                        return "version went backwards";
                    }
                    lastVersion = version;
                    reads.incrementAndGet();
                }
                return null;
            }));
        }
        started.await();
        for (Path root : roots) {
            Registries.ReloadResult r = registries.reload(loaderPool, () -> DataLoader.standard(IDS).load(List.of(root)))
                    .get(30, TimeUnit.SECONDS);
            assertThat(r.published()).isTrue();
        }
        stop.set(true);
        for (Future<String> f : results) {
            assertThat(f.get(30, TimeUnit.SECONDS)).isNull();
        }
        readerPool.shutdown();
        assertThat(reads.get()).isPositive();
        assertThat(registries.version()).isEqualTo(generations);
        assertThat(registries.get().get(DefinitionKinds.key("fuel")).getOrThrow("tfp:count").require("temperature", Float.class))
                .isEqualTo((float) generations);
    }

    @Test
    void aSlowOlderReloadNeverReplacesANewerOne() throws Exception {
        Registries registries = new Registries();
        Path oldRoot = generation(1);
        Path newRoot = generation(2);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Registries.ReloadResult> slow = registries.reload(loaderPool, () -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return DataLoader.standard(IDS).load(List.of(oldRoot));
        });
        Registries.ReloadResult fast = registries.reload(loaderPool, () -> DataLoader.standard(IDS).load(List.of(newRoot)))
                .get(30, TimeUnit.SECONDS);
        assertThat(fast.published()).isTrue();
        release.countDown();
        assertThat(slow.get(30, TimeUnit.SECONDS).published()).isFalse();
        assertThat(registries.get()).isSameAs(fast.snapshot());
    }

    @Test
    void aFailedReloadKeepsTheCurrentSnapshot() throws Exception {
        Registries registries = new Registries();
        Path good = generation(3);
        RegistrySnapshot before = registries.reload(loaderPool, () -> DataLoader.standard(IDS).load(List.of(good))).get().snapshot();
        Path bad = tmp.resolve("bad/data");
        Files.createDirectories(bad.resolve("tfp/recipe"));
        Files.writeString(bad.resolve("tfp/recipe/x.json"), "{\"type\": \"tfc:nope\"}");
        CompletableFuture<Registries.ReloadResult> failed = registries.reload(loaderPool, () -> DataLoader.standard(IDS).load(List.of(bad)));
        assertThatThrownBy(failed::get).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(DataLoadException.class);
        assertThat(registries.get()).isSameAs(before);
        assertThat(registries.version()).isEqualTo(1L);
    }
}
