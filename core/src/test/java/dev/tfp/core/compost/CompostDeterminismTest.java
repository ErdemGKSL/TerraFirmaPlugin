/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java @ c3b4d9536 (getReadyTicks, randomTick)
 */
package dev.tfp.core.compost;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.compost.CompostAddition.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The composter rules are pure: the same inputs give the same bins, in any order, on any thread. */
class CompostDeterminismTest {

    /** Replays a seeded mix of interactions and settles, and returns every bin it passes through. */
    private static List<CompostBin> replay(long seed) {
        final Random random = new Random(seed);
        final long readyTicks = CompostRules.readyTicks(CompostRules.DEFAULT_READY_TICKS, 60f + random.nextInt(300), random.nextInt(5));
        final List<CompostBin> trace = new ArrayList<>();
        CompostBin bin = CompostBin.empty();
        long tick = 0;
        for (int i = 0; i < 400; i++) {
            tick += 1 + random.nextInt(4_000);
            bin = bin.settle(tick, readyTicks);
            final int roll = random.nextInt(10);
            if (roll < 4) {
                bin = bin.add(new CompostAddition(Kind.GREEN, 1 + random.nextInt(4)), random.nextBoolean(), tick).bin();
            } else if (roll < 8) {
                bin = bin.add(new CompostAddition(Kind.BROWN, 1 + random.nextInt(4)), random.nextBoolean(), tick).bin();
            } else if (roll == 8) {
                bin = bin.add(new CompostAddition(Kind.POISON, 0), false, tick).bin();
            } else {
                bin = bin.extract(tick).bin();
            }
            trace.add(bin);
        }
        return trace;
    }

    @Test
    void repeatedReplaysAreIdentical() {
        for (long seed = 1; seed <= 30; seed++) {
            assertThat(replay(seed)).as("seed %d", seed).isEqualTo(replay(seed));
        }
    }

    @Test
    void replaysAgreeAcrossThreads() {
        final List<List<CompostBin>> expected = IntStream.rangeClosed(1, 8).mapToObj(s -> replay(s)).toList();
        final List<List<CompostBin>> parallel = IntStream.range(0, 64).parallel()
            .mapToObj(i -> replay(1 + i % 8))
            .toList();
        for (int i = 0; i < parallel.size(); i++) {
            assertThat(parallel.get(i)).isEqualTo(expected.get(i % 8));
        }
    }

    @Test
    void equalInputsGiveEqualHashes() {
        final CompostBin a = new CompostBin(16, 16, CompostBin.Type.READY, 1234L, 1);
        final CompostBin b = new CompostBin(16, 16, CompostBin.Type.READY, 1234L, 1);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(CompostRules.readyTicks(288_000L, 0f, 4)).isEqualTo(CompostRules.readyTicks(288_000L, 0f, 4));
    }
}
