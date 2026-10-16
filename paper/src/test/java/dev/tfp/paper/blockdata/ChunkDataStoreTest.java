/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blockdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.persist.PersistException;
import dev.tfp.core.persist.VersionedCodec;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

/**
 * M1-SVC-01: the chunk-scale store. The PDC implementation runs against a map-backed fake container;
 * "restart" copies the container's bytes into a fresh chunk, as a chunk save + load does. The in-server
 * unload/reload/restart self-test is M1-TEST-01.
 */
class ChunkDataStoreTest {
    /** Toy chunk-scale record: a support map summary. v1 stored "count" only; v2 adds "radius". */
    record Supports(int count, int radius) {
    }

    static final ChunkDataKey<Supports> V1 = new ChunkDataKey<>("supports", VersionedCodec.<Supports>builder("supports", 1)
            .build((s, out) -> out.putInt("count", s.count()), in -> new Supports(in.requireInt("count"), 0)));
    static final ChunkDataKey<Supports> V2 = new ChunkDataKey<>("supports", VersionedCodec.<Supports>builder("supports", 2)
            .migrate(1, d -> d.putInt("radius", 4))
            .build((s, out) -> out.putInt("count", s.count()).putInt("radius", s.radius()),
                    in -> new Supports(in.requireInt("count"), in.requireInt("radius"))));

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler h) {
        return (T) Proxy.newProxyInstance(ChunkDataStoreTest.class.getClassLoader(), new Class<?>[] {type}, h);
    }

    /** A chunk whose PDC is backed by {@code pdc}. */
    static Chunk chunk(String world, int x, int z, Map<NamespacedKey, Object> pdc) {
        World w = proxy(World.class, (p, m, a) -> switch (m.getName()) {
            case "getName" -> world;
            default -> throw new UnsupportedOperationException(m.getName());
        });
        PersistentDataContainer container = proxy(PersistentDataContainer.class, (p, m, a) -> switch (m.getName()) {
            case "get" -> pdc.get((NamespacedKey) a[0]);
            case "set" -> {
                pdc.put((NamespacedKey) a[0], a[2]);
                yield null;
            }
            case "remove" -> {
                pdc.remove((NamespacedKey) a[0]);
                yield null;
            }
            case "getKeys" -> java.util.Set.copyOf(pdc.keySet());
            default -> throw new UnsupportedOperationException(m.getName());
        });
        return proxy(Chunk.class, (p, m, a) -> switch (m.getName()) {
            case "getWorld" -> w;
            case "getX" -> x;
            case "getZ" -> z;
            case "getPersistentDataContainer" -> container;
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == a[0];
            default -> throw new UnsupportedOperationException(m.getName());
        });
    }

    @Test
    void pdcStoreRoundTripsAcrossARestartAndChecksOwnership() {
        AtomicInteger checks = new AtomicInteger();
        PdcChunkDataStore store = new PdcChunkDataStore(c -> checks.incrementAndGet());
        Map<NamespacedKey, Object> saved = new HashMap<>();
        Chunk before = chunk("world", 3, -2, saved);
        // a foreign plugin's key in the same container is left alone
        saved.put(new NamespacedKey("other", "thing"), new byte[] {9});

        assertThat(store.load(before, V2)).isEmpty();
        store.save(before, V2, new Supports(12, 6));
        assertThat(store.ids(before)).containsExactly("supports");

        Chunk after = chunk("world", 3, -2, new HashMap<>(saved)); // chunk saved and loaded again
        assertThat(store.load(after, V2)).contains(new Supports(12, 6));
        store.remove(after, V2);
        assertThat(store.load(after, V2)).isEmpty();
        assertThat(checks.get()).isEqualTo(6);
    }

    @Test
    void ownerCheckFailureStopsTheAccess() {
        PdcChunkDataStore store = new PdcChunkDataStore(c -> {
            throw new IllegalStateException("not region owner");
        });
        assertThatThrownBy(() -> store.read(chunk("w", 0, 0, new HashMap<>()), V2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void oldVersionMigratesAndNewerVersionIsRefused() {
        ChunkDataStore store = new InMemoryChunkDataStore();
        Chunk c = chunk("world", 0, 0, new HashMap<>());
        store.save(c, V1, new Supports(5, 0));
        assertThat(store.load(c, V2)).contains(new Supports(5, 4));

        store.save(c, V2, new Supports(5, 8));
        assertThatThrownBy(() -> store.load(c, V1)).isInstanceOf(PersistException.NewerVersion.class);
        // the refused bytes are still there, untouched
        assertThat(store.load(c, V2)).contains(new Supports(5, 8));
    }

    @Test
    void inMemoryFakeSeparatesChunksAndWorlds() {
        ChunkDataStore store = new InMemoryChunkDataStore();
        store.save(chunk("a", 0, 0, new HashMap<>()), V2, new Supports(1, 1));
        store.save(chunk("b", 0, 0, new HashMap<>()), V2, new Supports(2, 2));
        assertThat(store.load(chunk("a", 0, 0, new HashMap<>()), V2)).contains(new Supports(1, 1));
        assertThat(store.load(chunk("a", 1, 0, new HashMap<>()), V2)).isEmpty();
        assertThat(store.ids(chunk("b", 0, 0, new HashMap<>()))).containsExactly("supports");
    }

    @Test
    void keyIdsAreValidated() {
        assertThatThrownBy(() -> new ChunkDataKey<>("Bad Id", V2.codec())).isInstanceOf(IllegalArgumentException.class);
    }
}
