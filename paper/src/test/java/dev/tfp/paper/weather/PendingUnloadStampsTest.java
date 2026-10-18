/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.ChunkPos;
import org.junit.jupiter.api.Test;

/** The next-unload override: set once, taken once, per chunk. */
class PendingUnloadStampsTest {
    private static final long CHUNK = ChunkPos.key(12, -50);
    private static final long OTHER = ChunkPos.key(13, -50);

    @Test
    void nothingPendingUntilSet() {
        assertThat(new PendingUnloadStamps().take(CHUNK)).isNull();
    }

    @Test
    void setIsTakenOnceThenGone() {
        PendingUnloadStamps pending = new PendingUnloadStamps();
        pending.set(CHUNK, 1000L);
        assertThat(pending.take(CHUNK)).isEqualTo(1000L);
        assertThat(pending.take(CHUNK)).isNull();
    }

    @Test
    void aSecondSetReplacesTheFirst() {
        PendingUnloadStamps pending = new PendingUnloadStamps();
        pending.set(CHUNK, 1000L);
        pending.set(CHUNK, 2000L);
        assertThat(pending.take(CHUNK)).isEqualTo(2000L);
    }

    @Test
    void otherChunksAreNotAffected() {
        PendingUnloadStamps pending = new PendingUnloadStamps();
        pending.set(CHUNK, 1000L);
        assertThat(pending.take(OTHER)).isNull();
        assertThat(pending.take(CHUNK)).isEqualTo(1000L);
    }
}
