/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.spawn;

/**
 * The biome spawner categories used by TFC's biome JSON {@code spawners} field, as the vanilla {@code MobCategory} names
 * them. Each category is a separate spawn list, and an attempt draws from one list only.
 */
public enum SpawnCategory {
    CREATURE,
    MONSTER,
    WATER_AMBIENT,
    WATER_CREATURE,
    UNDERGROUND_WATER_CREATURE;

    /** The serialized name, such as {@code water_creature}. */
    public static SpawnCategory fromSerialized(String name) {
        return valueOf(name.toUpperCase(java.util.Locale.ROOT));
    }
}
