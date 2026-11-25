/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import dev.tfp.datagen.DatagenException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PAR-WORLD-59: CraftEngine's plant pools as carrier-policy pools of the selector. The plant looks of {@code PlantBlockGen}
 * (land plants: the {@code sugar_cane}, {@code sapling} and {@code tripwire} pools) and {@code PlantShapeGen} (the water
 * plants: the {@code kelp} pool) are no explicit carriers: each distinct look takes one {@code auto_state} of a pool. The
 * pools, their sizes, the states other families take from them ({@link #RESERVED}: the twigs' shared tripwire state,
 * PAR-WORLD-44), the pick order and the {@code auto_state} appearance live here, so no generator outside {@code hitbox/}
 * chooses or writes a pool state ({@code HardcodedCarrierTest}); {@code GeneratedCarrierTest} checks the total per pool.
 */
public final class PlantPools {
    public static final String SUGAR_CANE = "sugar_cane";
    public static final String SAPLING = "sapling";
    public static final String TRIPWIRE = "tripwire";
    public static final String KELP = "kelp";

    /**
     * CraftEngine's plant pools and their sizes (research report section 5; the same on 1.21.4+ and 26.x), in the order a
     * land look tries them: the small pools first, the tripwire last (the only one tinted looks may use).
     */
    public static final Map<String, Integer> LAND = sizes(SUGAR_CANE, 15, SAPLING, 8, TRIPWIRE, 126);
    /** The water plants' pool (waterlogged by nature); its last state is the shared empty one under item displays. */
    public static final Map<String, Integer> WATER = sizes(KELP, 25);
    /** Every plant pool. */
    public static final Map<String, Integer> SIZES = all();
    /** States of a plant pool that another family takes: the twigs share one tripwire state (HitboxDemands.TWIG_POOL). */
    public static final Map<String, Integer> RESERVED = Map.of(HitboxDemands.TWIG_POOL, 1);

    private PlantPools() {
    }

    private static Map<String, Integer> sizes(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return Collections.unmodifiableMap(m);
    }

    private static Map<String, Integer> all() {
        Map<String, Integer> m = new LinkedHashMap<>(LAND);
        m.putAll(WATER);
        return Collections.unmodifiableMap(m);
    }

    /** Whether {@code pool} is one of CraftEngine's plant pools. */
    public static boolean isPlantPool(String pool) {
        return SIZES.containsKey(pool);
    }

    /** The states of {@code pool} the plant generators may use: its size less the reserved states. */
    public static int capacity(String pool) {
        Integer size = SIZES.get(pool);
        if (size == null) {
            throw new DatagenException(pool + " is no plant pool " + SIZES.keySet());
        }
        return size - RESERVED.getOrDefault(pool, 0);
    }

    /** The {@code kelp} looks with a model: every state but the shared empty one. */
    public static int waterModels() {
        return capacity(KELP) - 1;
    }

    /**
     * One generator run's use of the plant pools: picks the pool of each new look in a fixed order (deterministic, the
     * generator's TFC enum order) and counts the states taken.
     */
    public static final class Allocator {
        private final Map<String, Integer> use = new LinkedHashMap<>();

        public Allocator() {
            SIZES.keySet().forEach(k -> use.put(k, 0));
        }

        /**
         * The land pool of a new look: the first of {@link #LAND} with room, only the tripwire for a tinted look (its model
         * carries the baked tint; the small pools are kept for untinted looks first). Empty when the pools are full.
         */
        public Optional<String> land(boolean tinted) {
            for (String candidate : LAND.keySet()) {
                if ((!tinted || candidate.equals(TRIPWIRE)) && use.get(candidate) < capacity(candidate)) {
                    use.merge(candidate, 1, Integer::sum);
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
        }

        /** Takes one state of {@code pool}; false when it is full. */
        public boolean take(String pool) {
            if (use.get(pool) >= capacity(pool)) {
                return false;
            }
            use.merge(pool, 1, Integer::sum);
            return true;
        }

        /** The states taken per pool. */
        public Map<String, Integer> use() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(use));
        }
    }

    /** The appearance stem of a plant-pool state: {@code auto_state: {type: <pool>, id: <id>}}. */
    public static Map<String, Object> appearance(String pool, String id) {
        if (!isPlantPool(pool)) {
            throw new DatagenException(pool + " is no plant pool " + SIZES.keySet());
        }
        Map<String, Object> auto = new LinkedHashMap<>();
        auto.put("type", pool);
        auto.put("id", id);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("auto_state", auto);
        return v;
    }

    /** The pool and id of an appearance's {@code auto_state}, when it names a plant pool. */
    public static Optional<List<String>> poolState(Map<?, ?> appearance) {
        if (appearance.get("auto_state") instanceof Map<?, ?> m && isPlantPool(String.valueOf(m.get("type")))) {
            return Optional.of(List.of(String.valueOf(m.get("type")), String.valueOf(m.get("id"))));
        }
        return Optional.empty();
    }
}
