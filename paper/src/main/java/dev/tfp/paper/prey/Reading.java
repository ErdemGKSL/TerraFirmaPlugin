/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.bukkit.entity.LivingEntity;

/**
 * One sensor reading: the living entities near an animal, with line of sight tested lazily and remembered for the life of
 * the reading. This is vanilla's {@code NearestVisibleLivingEntities}: its {@code lineOfSightTest} is a memoised
 * {@code computeIfAbsent}, and {@code findClosest(predicate)} tests the goal's predicate before the line of sight, so a
 * goal pays the ray cast only for the entities it would pick. The kind of each entity (species, player, tag bits) is
 * likewise read once per reading, however many goals and periods ask (PAR-FAUNA-26). Owning thread only.
 */
public final class Reading {
    /** No reading yet. */
    public static final Reading NONE = new Reading(List.of(), e -> false, e -> 0);

    private static final byte UNKNOWN = 0;
    private static final byte SEEN = 1;
    private static final byte HIDDEN = 2;
    private static final int NO_KIND = -1;

    private final List<LivingEntity> near;
    private final Predicate<LivingEntity> sight;
    private final ToIntFunction<LivingEntity> kindOf;
    private final byte[] memo;
    private int[] kinds;
    private String[] species;

    /**
     * @param near   the entities in range (not yet tested for line of sight)
     * @param sight  the line-of-sight test, asked at most once per entity
     * @param kindOf the entity's kind bits ({@link SpeciesTags}), asked at most once per entity; never negative
     */
    public Reading(List<LivingEntity> near, Predicate<LivingEntity> sight, ToIntFunction<LivingEntity> kindOf) {
        this.near = near;
        this.sight = sight;
        this.kindOf = kindOf;
        this.memo = new byte[near.size()];
    }

    public int size() {
        return near.size();
    }

    public LivingEntity get(int i) {
        return near.get(i);
    }

    /** Whether entity {@code i} is in sight; asks the test the first time only. */
    public boolean visible(int i) {
        byte m = memo[i];
        if (m == UNKNOWN) {
            m = sight.test(near.get(i)) ? SEEN : HIDDEN;
            memo[i] = m;
        }
        return m == SEEN;
    }

    /** The kind bits of entity {@code i} ({@link SpeciesTags#PLAYER}, {@link SpeciesTags#LAND_PREDATOR}, ...). */
    public int kind(int i) {
        if (kinds == null) {
            kinds = new int[near.size()];
            Arrays.fill(kinds, NO_KIND);
        }
        int k = kinds[i];
        if (k == NO_KIND) {
            k = kindOf.applyAsInt(near.get(i));
            kinds[i] = k;
        }
        return k;
    }

    /** The species of entity {@code i} ({@link SpeciesCache#of}). */
    public String species(int i) {
        if (species == null) {
            species = new String[near.size()];
        }
        String s = species[i];
        if (s == null) {
            s = SpeciesCache.of(near.get(i));
            species[i] = s;
        }
        return s;
    }

    /** Every visible entity (tests every line of sight not tested yet). */
    public List<LivingEntity> visible() {
        List<LivingEntity> out = new ArrayList<>(near.size());
        for (int i = 0; i < near.size(); i++) {
            if (visible(i)) {
                out.add(near.get(i));
            }
        }
        return out;
    }
}
