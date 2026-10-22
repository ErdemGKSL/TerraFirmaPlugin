/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/worldgen/biome/*.json @ c3b4d9536, the {@code spawners} entries
 */
package dev.tfp.core.spawn;

/**
 * One entry of a biome's spawn list: a species, its weight in the list, and its group size range.
 *
 * @param biome    the biome file name, such as {@code plains}
 * @param category the spawner category the entry sits in
 * @param type     the namespaced entity type, such as {@code tfc:cow} or {@code minecraft:zombie}
 * @param weight   the weight in the category list, zero or more
 * @param minCount the smallest group size
 * @param maxCount the largest group size, at least {@code minCount}
 */
public record SpawnEntry(String biome, SpawnCategory category, String type, int weight, int minCount, int maxCount) {
    public SpawnEntry {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("spawn entry without a type in " + biome);
        }
        if (weight < 0) {
            throw new IllegalArgumentException("negative weight for " + type + " in " + biome);
        }
        if (minCount < 0 || maxCount < minCount) {
            throw new IllegalArgumentException("bad group size " + minCount + ".." + maxCount + " for " + type + " in " + biome);
        }
    }

    /** True for a TFC species, which is gated by its {@link FaunaRule}; vanilla entries are not gated here. */
    public boolean isTfc() {
        return type.startsWith("tfc:");
    }
}
