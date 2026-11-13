/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** The newest-reading-wins table of PAR-CLIM-02 and the sample's rain/snow helpers. */
class PrecipitationSamplesTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final BlockPos POS = new BlockPos(1, 70, 2);

    private static PrecipitationSample sample(long seq, PrecipitationType type) {
        return new PrecipitationSample(seq, seq * 20, POS, type);
    }

    @Test
    void helpersFollowVanillaIsRainingAt() {
        assertThat(sample(1, PrecipitationType.RAIN).isRaining()).isTrue();
        assertThat(sample(1, PrecipitationType.SNOW).isRaining()).isFalse();
        assertThat(sample(1, PrecipitationType.SNOW).isSnowing()).isTrue();
        assertThat(sample(1, PrecipitationType.SNOW).isPrecipitating()).isTrue();
        assertThat(sample(1, PrecipitationType.NONE).isPrecipitating()).isFalse();
    }

    @Test
    void newerReadingWinsAndOlderOrEqualIsDropped() {
        PrecipitationSamples table = new PrecipitationSamples();
        assertThat(table.latest(A)).isEmpty();
        assertThat(table.offer(A, sample(5, PrecipitationType.RAIN))).isTrue();
        assertThat(table.offer(A, sample(4, PrecipitationType.SNOW))).isFalse();
        assertThat(table.offer(A, sample(5, PrecipitationType.SNOW))).isFalse();
        assertThat(table.latest(A)).contains(sample(5, PrecipitationType.RAIN));
        assertThat(table.offer(A, sample(6, PrecipitationType.NONE))).isTrue();
        assertThat(table.latest(A).orElseThrow().type()).isEqualTo(PrecipitationType.NONE);
        assertThat(table.latest(B)).isEmpty();
        table.remove(A);
        assertThat(table.latest(A)).isEmpty();
        assertThat(table.size()).isZero();
    }

    @Test
    void concurrentOffersKeepTheHighestSequence() throws Exception {
        PrecipitationSamples table = new PrecipitationSamples();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            int offset = t;
            futures.add(pool.submit(() -> {
                go.await();
                for (long seq = offset; seq < 10_000; seq += 8) {
                    table.offer(A, sample(seq, PrecipitationType.RAIN));
                }
                return null;
            }));
        }
        go.countDown();
        for (var f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(table.latest(A).orElseThrow().clockSequence()).isEqualTo(9_999L);
    }
}
