/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.Test;

/**
 * The not-ready path of {@link TfcChunkGenerator#generateNoise}: a chunk generated before CraftEngine loaded its configuration
 * (the fresh-world crash: "Chunk system error" from a thrown refusal) must not throw and must not write any block.
 * No server needed: the chunk data and world info are recording proxies.
 */
class TfcChunkGeneratorNotReadyTest {

    /** Every call on the chunk data, by method name. */
    private static ChunkData recordingChunkData(List<String> calls) {
        return (ChunkData) Proxy.newProxyInstance(ChunkData.class.getClassLoader(), new Class<?>[] {ChunkData.class},
            (proxy, method, args) -> {
                calls.add(method.getName());
                return switch (method.getName()) {
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "ChunkData";
                    default -> null;
                };
            });
    }

    private static WorldInfo world() {
        return (WorldInfo) Proxy.newProxyInstance(WorldInfo.class.getClassLoader(), new Class<?>[] {WorldInfo.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> "world";
                case "getSeed" -> 42L;
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                default -> null;
            });
    }

    private static BlockVisuals notLoaded() {
        return new BlockVisuals() {
            @Override
            public boolean isLoaded() {
                return false;
            }

            @Override
            public Optional<BlockData> blockData(BlockStateRef state) {
                throw new AssertionError("no lookup before CraftEngine loaded");
            }

            @Override
            public long generation() {
                return 0;
            }

            @Override
            public void onReload(Runnable listener) {
            }
        };
    }

    private static final class Records extends Handler {
        final List<LogRecord> severe = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel() == Level.SEVERE) {
                severe.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static void assertNotReadyChunksAreLeftEmpty(Supplier<BlockVisuals> visuals) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        Records records = new Records();
        log.addHandler(records);
        TfcChunkGenerator generator = new TfcChunkGenerator(OptionalLong.of(1), visuals, log);

        List<String> calls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ChunkData data = recordingChunkData(calls);
            int x = -20 + i;
            assertThatCode(() -> generator.generateNoise(world(), new Random(0), x, 13, data)).doesNotThrowAnyException();
        }

        assertThat(calls).as("no block written, no read needed").isEmpty();
        assertThat(generator.refusedChunks()).isEqualTo(3);
        assertThat(records.severe).as("logged once, not per chunk").hasSize(1);
        assertThat(records.severe.get(0).getMessage())
            .contains("refused chunk -20,13", "CraftEngine has not loaded its configuration", "left empty");
    }

    @Test
    void chunkBeforeCraftEngineLoadedIsLeftEmptyWithoutThrowing() {
        assertNotReadyChunksAreLeftEmpty(TfcChunkGeneratorNotReadyTest::notLoaded);
    }

    @Test
    void chunkBeforeThePluginEnabledIsLeftEmptyWithoutThrowing() {
        assertNotReadyChunksAreLeftEmpty(() -> null);
    }

    @Test
    void aFailingStateTableIsCaughtAndTheChunkCleared() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        Records records = new Records();
        log.addHandler(records);
        TfcChunkGenerator generator = new TfcChunkGenerator(OptionalLong.of(1), () -> {
            throw new IllegalStateException("boom");
        }, log);
        List<String> calls = new ArrayList<>();
        assertThatCode(() -> generator.generateNoise(world(), new Random(0), 0, 0, recordingChunkData(calls)))
            .doesNotThrowAnyException();
        assertThat(calls).as("only the clear back to air").doesNotContain("setBlock").contains("setRegion");
        assertThat(records.severe).hasSize(1);
        assertThat(records.severe.get(0).getThrown()).hasMessage("boom");
    }
}
