/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/TFCSquid.java (line 43-44, 52-54, 86-107, 111-130),
 * src/main/java/net/dries007/tfc/common/entities/aquatic/Octopoteuthis.java (line 106, 151-153) @ c3b4d9536
 */
package dev.tfp.core.aquatic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.spawn.SpawnRandom;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Golden tests for the squid and octopoteuthis size rules, with hand-traced shrink sequences from {@code finalizeSpawn}
 * (TFC line 95-104).
 */
class SquidSizeGoldenTest {

    /** A fixed draw: {@code nextInt(bound)} is {@code value % bound}, as in the spawn tests. */
    private static SpawnRandom fixed(int value) {
        return new SpawnRandom() {
            @Override
            public int nextInt(int bound) {
                return value % bound;
            }

            @Override
            public float nextFloat() {
                return 0f;
            }
        };
    }

    @Test
    void constantsMatchTfc() {
        // TFCSquid.MAX_SIZE = 127, MIN_SIZE = 1 (line 43-44); scale is size / 128f * 5f (line 129)
        assertThat(SquidSize.MAX_SIZE).isEqualTo(127);
        assertThat(SquidSize.MIN_SIZE).isEqualTo(1);
        assertThat(SquidSize.scaleOf(22)).isEqualTo(0.859375f);
        assertThat(SquidSize.SQUID_SPAWN).isEqualTo(new SquidSize.SizeRange(22, 65));
        assertThat(SquidSize.OCTOPOTEUTHIS_SPAWN).isEqualTo(new SquidSize.SizeRange(22, 100));
    }

    @Test
    void everyClampedSizeReadsBackExactly() {
        // size / 128f * 5f is exact in float for 1 to 127, and ceil(scale / 5f * 128f) reads it back (line 118)
        for (int size = SquidSize.MIN_SIZE; size <= SquidSize.MAX_SIZE; size++) {
            assertThat(SquidSize.sizeOf(SquidSize.scaleOf(size))).as("size %d", size).isEqualTo(size);
        }
        assertThat(SquidSize.clamp(0)).isEqualTo(1);
        assertThat(SquidSize.clamp(200)).isEqualTo(127);
    }

    @Test
    void rolledSizeCoversTheRangeInclusively() {
        // TFCSquid.finalizeSpawn line 91: Mth.nextInt(random, min, max) = nextInt(max - min + 1) + min
        assertThat(SquidSize.rolledSize(SquidSize.SQUID_SPAWN, fixed(0))).isEqualTo(22);
        assertThat(SquidSize.rolledSize(SquidSize.SQUID_SPAWN, fixed(43))).isEqualTo(65);
        assertThat(SquidSize.rolledSize(SquidSize.OCTOPOTEUTHIS_SPAWN, fixed(78))).isEqualTo(100);
    }

    @Test
    void naturalSpawnThatFitsKeepsItsSize() {
        // collides never: the rolled size stays (lines 95-104 never run)
        assertThat(SquidSize.spawnedSize(SquidSize.SQUID_SPAWN, 40, true, s -> false)).contains(40);
    }

    @Test
    void naturalSpawnShrinksByPointEightUntilItFits() {
        // rolled 65, collides while size > 40: 65 -> 52 (65 * 0.8 = 52.0) -> 41 (52 * 0.8 = 41.6) -> 32 (41 * 0.8 = 32.8), 32 clears
        assertThat(SquidSize.spawnedSize(SquidSize.SQUID_SPAWN, 65, true, s -> s > 40)).contains(32);
    }

    @Test
    void naturalSpawnBelowTheRangeMinimumIsCancelled() {
        // rolled 65, collides while size >= 23: 65 -> 52 -> 41 -> 32 -> 25 -> 20 (25 * 0.8 = 20.0), 20 < 22 cancels the spawn (line 98-102)
        assertThat(SquidSize.spawnedSize(SquidSize.SQUID_SPAWN, 65, true, s -> s >= 23)).isEqualTo(Optional.empty());
        // rolled 22, always colliding: 22 -> 17 (22 * 0.8 = 17.6) < 22, cancelled after one shrink
        assertThat(SquidSize.spawnedSize(SquidSize.SQUID_SPAWN, 22, true, s -> true)).isEqualTo(Optional.empty());
    }

    @Test
    void nonNaturalSpawnNeverShrinks() {
        // only NATURAL and CHUNK_GENERATION shrink (line 93); any other spawn type keeps the rolled size
        assertThat(SquidSize.spawnedSize(SquidSize.SQUID_SPAWN, 65, false, s -> true)).contains(65);
    }

    @Test
    void octopoteuthisLightLevelFollowsItsSize() {
        // Octopoteuthis.getLightLevel (line 106): (int) Mth.clamp(size * 0.234375F, 1, 15)
        assertThat(SquidSize.lightLevel(1)).isEqualTo(1);
        assertThat(SquidSize.lightLevel(22)).isEqualTo(5);
        assertThat(SquidSize.lightLevel(63)).isEqualTo(14);
        assertThat(SquidSize.lightLevel(64)).isEqualTo(15);
        assertThat(SquidSize.lightLevel(100)).isEqualTo(15);
        assertThat(SquidSize.lightLevel(127)).isEqualTo(15);
    }
}
