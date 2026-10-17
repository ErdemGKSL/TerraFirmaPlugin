/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blockdata;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.blockdata.ChunkDataStoreTest.Supports;
import dev.tfp.paper.scheduler.ThreadAssertionException;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.exception.UnimplementedOperationException;

/**
 * M1-TEST-02 sample: the production {@link PdcChunkDataStore} (real {@code ThreadAsserts} owner check) on a
 * MockBukkit chunk with MockBukkit's real persistent data container.
 */
class PdcChunkDataStoreMockBukkitTest extends MockServerTest {
    @Test
    void roundTripsThroughARealContainerAndLeavesForeignKeysAlone() {
        World world = server.addSimpleWorld("tfp_test");
        Chunk chunk = world.getChunkAt(2, -3);
        NamespacedKey foreign = new NamespacedKey("other", "supports");
        chunk.getPersistentDataContainer().set(foreign, PersistentDataType.BYTE_ARRAY, new byte[] {7});
        PdcChunkDataStore store = new PdcChunkDataStore();

        store.save(chunk, ChunkDataStoreTest.V2, new Supports(9, 3));

        assertThat(world.getChunkAt(2, -3).getPersistentDataContainer()
                .has(new NamespacedKey(PdcChunkDataStore.NAMESPACE, "supports"))).isTrue();
        assertThat(store.load(world.getChunkAt(2, -3), ChunkDataStoreTest.V2)).contains(new Supports(9, 3));
        assertThat(store.ids(chunk)).containsExactly("supports");
        store.remove(chunk, ChunkDataStoreTest.V2);
        assertThat(store.load(chunk, ChunkDataStoreTest.V2)).isEmpty();
        assertThat(chunk.getPersistentDataContainer().get(foreign, PersistentDataType.BYTE_ARRAY)).containsExactly(7);
    }

    // MockBukkit 4.45.1 has no Folia region ownership (isOwnedByCurrentRegion unimplemented); the region-thread check is covered by the in-server self-test matrix.
    @Test
    void accessFromANonOwningThreadFailsTheThreadAssertion() {
        Chunk chunk = server.addSimpleWorld("tfp_test").getChunkAt(0, 0);
        PdcChunkDataStore store = new PdcChunkDataStore();
        RuntimeException thrown = null;
        try {
            offMainThread(() -> store.ids(chunk));
        } catch (UnimplementedOperationException e) {
            Assumptions.abort("MockBukkit 4.45.1 does not implement region ownership: " + e.getMessage());
        } catch (ThreadAssertionException e) {
            thrown = e;
        }
        assertThat(thrown).isInstanceOf(ThreadAssertionException.class)
                .hasMessageContaining("not region owner");
    }
}
