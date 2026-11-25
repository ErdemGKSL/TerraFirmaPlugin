/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/Faunas.java @ c3b4d9536 (registerFish spawn placement)
 */
package dev.tfp.core.aquatic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.spawn.SpawnCategory;
import dev.tfp.core.spawn.SpawnEntry;
import dev.tfp.core.spawn.SpawnRandom;
import dev.tfp.core.spawn.SpawnTables;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The water pass picks one entry per water list and scatters its group inside the chunk. */
class AquaticSpawnsTest {
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

    private static SpawnTables.BiomeSpawns biome() {
        return new SpawnTables.BiomeSpawns(0.1f, Map.of(
            SpawnCategory.WATER_AMBIENT, List.of(new SpawnEntry("ocean", SpawnCategory.WATER_AMBIENT, "tfc:cod", 10, 3, 5)),
            SpawnCategory.WATER_CREATURE, List.of(new SpawnEntry("ocean", SpawnCategory.WATER_CREATURE, "tfc:squid", 1, 1, 1)),
            SpawnCategory.CREATURE, List.of(new SpawnEntry("ocean", SpawnCategory.CREATURE, "tfc:cow", 1, 1, 1))));
    }

    @Test
    void oneGroupPerWaterListAndNoLandCreatures() {
        List<AquaticSpawns.Member> members = AquaticSpawns.pass(biome(), fixed(7));
        // cod group size 3 + 7 % 3 = 4, squid 1; no underground list, no cow
        assertThat(members).extracting(m -> m.entry().type()).containsExactly("tfc:cod", "tfc:cod", "tfc:cod", "tfc:cod", "tfc:squid");
    }

    @Test
    void membersStayInsideTheChunk() {
        for (int v = 0; v < 64; v++) {
            for (AquaticSpawns.Member m : AquaticSpawns.pass(biome(), fixed(v))) {
                assertThat(m.x()).isBetween(0, 15);
                assertThat(m.z()).isBetween(0, 15);
            }
        }
    }

    @Test
    void anEmptyBiomePlansNothing() {
        assertThat(AquaticSpawns.pass(new SpawnTables.BiomeSpawns(0.1f, Map.of()), fixed(1))).isEmpty();
    }
}
