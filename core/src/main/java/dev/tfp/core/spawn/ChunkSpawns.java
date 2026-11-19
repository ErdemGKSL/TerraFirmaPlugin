/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: net.minecraft.world.level.NaturalSpawner.spawnMobsForChunkGeneration (vanilla, which TFC relies on for the
 * initial animals of a chunk) with TFC's creature lists @ c3b4d9536
 */
package dev.tfp.core.spawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Which animals a chunk starts with, and which a top-up pass adds (PAR-FAUNA-04). Pure: it draws the species, the group
 * and the member columns; the placement and the TFC gate ({@link SpawnRules#accepts}) need the world and run on the
 * region thread afterwards.
 */
public final class ChunkSpawns {
    /** Vanilla lowers the creature probability by this much after each group. */
    static final float PROBABILITY_STEP = 0.01f;
    /** Columns around the group's start a member may be drawn at (vanilla {@code nextInt(5) - nextInt(5)}). */
    static final int SCATTER = 5;
    private static final int MAX_GROUPS = 32;

    private ChunkSpawns() {
    }

    /** One planned animal: its entry and chunk-local column. */
    public record Member(SpawnEntry entry, int x, int z) {
    }

    /**
     * Chunk generation: while a draw is under the biome's creature probability, pick a creature entry and scatter its group
     * from a random column; the probability drops by {@value #PROBABILITY_STEP} per group.
     */
    public static List<Member> forNewChunk(SpawnTables.BiomeSpawns biome, SpawnRandom rand) {
        List<Member> out = new ArrayList<>();
        float probability = biome.probability();
        List<SpawnEntry> creatures = biome.of(SpawnCategory.CREATURE);
        for (int groups = 0; groups < MAX_GROUPS && rand.nextFloat() < probability; groups++) {
            Optional<SpawnEntry> entry = SpawnRules.pick(creatures, rand);
            if (entry.isEmpty()) {
                break;
            }
            out.addAll(group(entry.get(), rand));
            probability -= PROBABILITY_STEP;
        }
        return List.copyOf(out);
    }

    /** One top-up attempt: a single creature group from a random column, or nothing if the biome has none. */
    public static List<Member> topUp(SpawnTables.BiomeSpawns biome, SpawnRandom rand) {
        return SpawnRules.pick(biome.of(SpawnCategory.CREATURE), rand).map(e -> group(e, rand)).orElse(List.of());
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
