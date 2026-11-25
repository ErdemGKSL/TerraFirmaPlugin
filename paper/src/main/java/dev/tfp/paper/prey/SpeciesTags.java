/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * The entity-tag memberships the senses ask about ({@code tfp:land_predators}, {@code tfp:ramming_animals},
 * {@code tfp:not_rammed_by_rammers}), memoised per species id. The memo is dropped every few seconds, so a
 * {@code /tfp reload data} reaches the animals without a restart. Thread-safe.
 */
public final class SpeciesTags {
    public static final String LAND_PREDATORS = "tfp:land_predators";
    public static final String RAMMING_ANIMALS = "tfp:ramming_animals";
    public static final String NOT_RAMMED = "tfp:not_rammed_by_rammers";

    public static final int LAND_PREDATOR = 1;
    public static final int RAMMER = 2;
    public static final int NOT_RAMMED_BY_RAMMERS = 4;
    /** Not a tag: the entity is a player ({@link #kind}). */
    public static final int PLAYER = 8;
    /** The kinds any {@code Avoidance} form can avoid: the cheap pre-filter before the sighting. */
    public static final int MAY_AVOID = PLAYER | LAND_PREDATOR | RAMMER;

    /** About 4.3 s of {@link System#nanoTime()}. */
    private static final int EPOCH_SHIFT = 32;

    /** Whether the species id ({@code tfp:wolf}) is in the entity tag. */
    private final BiPredicate<String, String> speciesInTag;
    private final Map<String, Integer> memo = new ConcurrentHashMap<>();
    private volatile long epoch = System.nanoTime() >>> EPOCH_SHIFT;

    public SpeciesTags(BiPredicate<String, String> speciesInTag) {
        this.speciesInTag = speciesInTag;
    }

    /** The kind bits of an entity: its species' tag bits, plus {@link #PLAYER} for a player. */
    public int kind(org.bukkit.entity.Entity e) {
        return e instanceof org.bukkit.entity.Player ? PLAYER : flags(SpeciesCache.ours(e));
    }

    /** The tag bits of one of our species ({@code tfc:wolf}); 0 for null (not one of our animals). */
    public int flags(String species) {
        if (species == null) {
            return 0;
        }
        long e = System.nanoTime() >>> EPOCH_SHIFT;
        if (e != epoch) {
            memo.clear();
            epoch = e;
        }
        Integer f = memo.get(species);
        if (f == null) {
            String id = "tfp:" + species.substring(species.indexOf(':') + 1);
            f = (speciesInTag.test(id, LAND_PREDATORS) ? LAND_PREDATOR : 0)
                    | (speciesInTag.test(id, RAMMING_ANIMALS) ? RAMMER : 0)
                    | (speciesInTag.test(id, NOT_RAMMED) ? NOT_RAMMED_BY_RAMMERS : 0);
            memo.put(species, f);
        }
        return f;
    }
}
