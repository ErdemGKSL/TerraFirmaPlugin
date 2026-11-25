/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/Faunas.java @ c3b4d9536 (registerFish spawn placement)
 */
package dev.tfp.core.aquatic;

import dev.tfp.core.spawn.SpawnCategory;
import dev.tfp.core.spawn.SpawnEntry;
import dev.tfp.core.spawn.SpawnRandom;
import dev.tfp.core.spawn.SpawnRules;
import dev.tfp.core.spawn.SpawnTables;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Plans one water spawn pass of a biome, as vanilla's periodic cycle does for its three water lists: for each of
 * {@code water_ambient}, {@code water_creature} and {@code underground_water_creature} one weighted pick and its group, scattered
 * around a random column. The placement in water and TFC's gate run afterwards on the region thread. Pure.
 */
public final class AquaticSpawns {
    /** Columns around the group's start a member may be drawn at (vanilla {@code nextInt(5) - nextInt(5)}). */
    static final int SCATTER = 5;
    static final List<SpawnCategory> CATEGORIES = List.of(SpawnCategory.WATER_AMBIENT, SpawnCategory.WATER_CREATURE,
        SpawnCategory.UNDERGROUND_WATER_CREATURE);

    private AquaticSpawns() {
    }

    /** One planned member: its entry and chunk-local column. */
    public record Member(SpawnEntry entry, int x, int z) {
    }

    /** The members of one pass over the biome's water lists. */
    public static List<Member> pass(SpawnTables.BiomeSpawns biome, SpawnRandom rand) {
        List<Member> out = new ArrayList<>();
        for (SpawnCategory category : CATEGORIES) {
            Optional<SpawnEntry> entry = SpawnRules.pick(biome.of(category), rand);
            entry.ifPresent(e -> out.addAll(group(e, rand)));
        }
        return List.copyOf(out);
    }

    static List<Member> group(SpawnEntry entry, SpawnRandom rand) {
        int size = entry.minCount() + rand.nextInt(entry.maxCount() - entry.minCount() + 1);
        int startX = rand.nextInt(16);
        int startZ = rand.nextInt(16);
        List<Member> members = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int x = startX;
            int z = startZ;
            for (int attempt = 0; attempt < 4; attempt++) {
                int nx = startX + rand.nextInt(SCATTER) - rand.nextInt(SCATTER);
                int nz = startZ + rand.nextInt(SCATTER) - rand.nextInt(SCATTER);
                if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                    x = nx;
                    z = nz;
                    break;
                }
            }
            members.add(new Member(entry, x, z));
        }
        return members;
    }
}
