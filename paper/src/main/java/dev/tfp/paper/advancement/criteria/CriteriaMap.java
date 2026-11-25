/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/advancement/**\/*.json, util/advancements/TFCAdvancements.java @ c3b4d9536
 */
package dev.tfp.paper.advancement.criteria;

import dev.tfp.core.advancement.CriterionRef;
import dev.tfp.core.calendar.CalendarTicks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * PAR-DATA-03: the map from every TFC advancement criterion to the feature event that earns it. Pure and immutable; any
 * thread. {@link #kind} names the route of every criterion ({@link Kind#PENDING} ones wait for the feature to publish an
 * event, follow-up PAR-DATA-19; the tab roots keep vanilla's {@code minecraft:tick}). The listeners of this package ask
 * the lookups below with ids and tag answers, never with Bukkit objects, so the rules test without a server.
 */
public final class CriteriaMap {
    /** How a criterion is earned. */
    public enum Kind {
        /** {@code minecraft:inventory_changed}: an item or tag enters the inventory ({@link #inventory}). */
        INVENTORY,
        /** {@code minecraft:consume_item} ({@link #consumed}). */
        CONSUME,
        /** {@code minecraft:location} with a biome ({@link #biome}). */
        BIOME,
        /** {@code minecraft:location} with a position ({@link #position}). */
        POSITION,
        /** {@code minecraft:player_killed_entity} ({@link #kills}). */
        KILL,
        /** {@code tfc:eat_rotten_food} / {@code tfc:full_nutrition}, from the nutrition module's meal event. */
        NUTRITION,
        /** A tab root: the datapack keeps {@code minecraft:tick}, nothing to award. */
        TICK,
        /**
         * TFC's own trigger earned from a Bukkit event: {@link #hooked}, {@link #stab}, {@link #presentDay} (PAR-DATA-19) and
         * the {@link AdvancementTriggerEvent} the owning features publish (PAR-DATA-23, {@link #publishable}).
         */
        EVENT,
        /** TFC's own trigger whose feature has no event yet (follow-up PAR-DATA-24). */
        PENDING
    }

    /** {@code world/globe_trotter}: TFC's bounds are {@code z >= 9999} and {@code z <= -9999}. */
    public static final int GLOBE_Z = 9999;
    public static final CriterionRef GLOBE_HIGH = CriterionRef.parse("tfp:world/globe_trotter#high");
    public static final CriterionRef GLOBE_LOW = CriterionRef.parse("tfp:world/globe_trotter#low");
    public static final CriterionRef EAT_ROTTEN_FOOD = CriterionRef.parse("tfp:world/eat_rotten_food#special_condition");
    public static final CriterionRef FULL_NUTRITION = CriterionRef.parse("tfp:world/full_nutrition#special_condition");
    /** TFC's {@code PlayerInfo} threshold for {@code FULL_NUTRITION}: the average nutrition reaches this. */
    public static final float FULL_NUTRITION_AVERAGE = 0.999f;

    public static final CriterionRef FISHING = CriterionRef.parse("tfp:world/fishing#special_condition");
    public static final CriterionRef ADVANCED_FISHING = CriterionRef.parse("tfp:world/advanced_fishing#special_condition");
    public static final CriterionRef GREATEST_HUNTER = CriterionRef.parse("tfp:world/greatest_hunter#special_condition");
    public static final CriterionRef THE_FUTURE = CriterionRef.parse("tfp:story/the_future#special_condition");
    /** PAR-DATA-23: the criteria the owning features earn by publishing an {@link AdvancementTriggerEvent}. */
    public static final CriterionRef FIREPIT = CriterionRef.parse("tfp:story/firepit#special_condition");
    public static final CriterionRef GRILL = CriterionRef.parse("tfp:story/grill#special_condition");
    public static final CriterionRef POT = CriterionRef.parse("tfp:story/pot#special_condition");
    public static final CriterionRef FORGE = CriterionRef.parse("tfp:story/forge#special_condition");
    public static final CriterionRef PIT_KILN = CriterionRef.parse("tfp:story/pit_kiln#special_condition");
    public static final CriterionRef POWDERKEG = CriterionRef.parse("tfp:world/powderkeg#special_condition");
    public static final CriterionRef FULL_POWDERKEG = CriterionRef.parse("tfp:world/full_powderkeg#special_condition");
    public static final CriterionRef PERFECTLY_FORGED = CriterionRef.parse("tfp:story/perfectly_forged#special_condition");
    public static final CriterionRef STONE_ANVIL = CriterionRef.parse("tfp:story/stone_anvil#special_condition");
    public static final CriterionRef WINDMILL = CriterionRef.parse("tfp:story/windmill#special_condition");
    public static final CriterionRef FAMILIARITY = CriterionRef.parse("tfp:world/familiarity#special_condition");
    public static final CriterionRef LAVA_LAMP = CriterionRef.parse("tfp:world/lava_lamp#special_condition");
    /** {@code world/fishing} tag of the hooked entity. */
    public static final String SMALL_FISH_TAG = "tfp:small_fish";
    /** {@code world/advanced_fishing} tag of the hooked entity. */
    public static final String LARGE_FISH_TAG = "tfp:needs_large_fishing_bait";
    /** {@code world/greatest_hunter}: the stabbed entity type. */
    public static final String STAB_TARGET = "tfp:rabbit";
    /** {@code world/greatest_hunter}: the minimum horizontal distance of the stab, in blocks. */
    public static final double STAB_DISTANCE = 50;

    /** The EVENT criteria earned from their own Bukkit events, never by a published {@link AdvancementTriggerEvent}. */
    private static final Set<CriterionRef> SPECIAL = Set.of(FISHING, ADVANCED_FISHING, GREATEST_HUNTER, THE_FUTURE);

    private static final Set<CriterionRef> ROOTS = Set.of(
        CriterionRef.parse("tfp:story/root#in_game_condition"), CriterionRef.parse("tfp:world/root#in_game_condition"));

    /** The shared instance (the tables are constants). */
    public static final CriteriaMap DEFAULT = new CriteriaMap();

    private final Map<CriterionRef, Kind> kinds = new LinkedHashMap<>();
    private final Map<String, List<CriterionRef>> inventoryById = new HashMap<>();
    private final List<TagRule> inventoryTags = new ArrayList<>();
    private final Map<String, CriterionRef> consume = new HashMap<>();
    private final Map<String, List<CriterionRef>> biomes = new HashMap<>();
    private final List<EntityRule> kills = new ArrayList<>();
    private final Map<CriterionRef, String> pending = new LinkedHashMap<>();

    private record TagRule(String tag, CriterionRef criterion) { }

    private record EntityRule(String match, CriterionRef criterion) { }

    private CriteriaMap() {
        for (String[] r : rows(CriteriaTable.INVENTORY)) {
            CriterionRef c = ref(r[0], r[1]);
            put(c, Kind.INVENTORY);
            if (r[2].startsWith("#")) {
                inventoryTags.add(new TagRule(r[2].substring(1), c));
            } else {
                inventoryById.computeIfAbsent(r[2], k -> new ArrayList<>(1)).add(c);
            }
        }
        for (String[] r : rows(CriteriaTable.CONSUME)) {
            CriterionRef c = ref(r[0], r[1]);
            put(c, Kind.CONSUME);
            consume.put(r[2], c);
        }
        for (String[] r : rows(CriteriaTable.BIOMES)) {
            CriterionRef c = ref(r[0], r[1]);
            put(c, Kind.BIOME);
            biomes.computeIfAbsent(r[1], k -> new ArrayList<>(2)).add(c);
        }
        for (String[] r : rows(CriteriaTable.KILLS)) {
            CriterionRef c = ref(r[0], r[1]);
            put(c, Kind.KILL);
            kills.add(new EntityRule(r[2], c));
        }
        for (String[] r : rows(CriteriaTable.PENDING)) {
            CriterionRef c = ref(r[0], r[1]);
            put(c, Kind.PENDING);
            pending.put(c, r[2]);
        }
        for (CriterionRef c : List.of(FISHING, ADVANCED_FISHING, GREATEST_HUNTER, THE_FUTURE, FIREPIT, GRILL, POT, FORGE,
                PIT_KILN, POWDERKEG, FULL_POWDERKEG, PERFECTLY_FORGED, STONE_ANVIL, WINDMILL, FAMILIARITY, LAVA_LAMP)) {
            put(c, Kind.EVENT);
        }
        put(GLOBE_HIGH, Kind.POSITION);
        put(GLOBE_LOW, Kind.POSITION);
        put(EAT_ROTTEN_FOOD, Kind.NUTRITION);
        put(FULL_NUTRITION, Kind.NUTRITION);
        for (CriterionRef root : ROOTS) {
            put(root, Kind.TICK);
        }
    }

    private void put(CriterionRef c, Kind kind) {
        if (kinds.put(c, kind) != null) {
            throw new IllegalStateException("criterion mapped twice: " + c);
        }
    }

    private static CriterionRef ref(String advancementPath, String criterion) {
        return new CriterionRef("tfp:" + advancementPath, criterion);
    }

    private static List<String[]> rows(String table) {
        List<String[]> out = new ArrayList<>();
        for (String line : table.split("\n")) {
            if (!line.isBlank()) {
                out.add(line.strip().split(" "));
            }
        }
        return out;
    }

    /** Every mapped criterion with its route. */
    public Map<CriterionRef, Kind> kinds() {
        return java.util.Collections.unmodifiableMap(kinds);
    }

    /** The route of {@code criterion}, empty for a criterion TFC does not have. */
    public Optional<Kind> kind(CriterionRef criterion) {
        return Optional.ofNullable(kinds.get(criterion));
    }

    /** Whether a feature may earn {@code criterion} by publishing an {@link AdvancementTriggerEvent}. */
    public boolean publishable(CriterionRef criterion) {
        return kinds.get(criterion) == Kind.EVENT && !SPECIAL.contains(criterion);
    }

    /** The TFC trigger a {@link Kind#PENDING} criterion waits for (e.g. {@code tfc:lit}), empty for the others. */
    public Optional<String> pendingTrigger(CriterionRef criterion) {
        return Optional.ofNullable(pending.get(criterion));
    }

    /**
     * The criteria an item entering the inventory earns, not yet {@code done}, in table order.
     *
     * @param itemId our item id ({@code tfp:bellows}, {@code minecraft:glass})
     * @param idInTag whether an item id is in an item tag ({@code Tags::is} of the item type)
     */
    public List<CriterionRef> inventory(String itemId, BiPredicate<String, String> idInTag, Predicate<CriterionRef> done) {
        List<CriterionRef> out = new ArrayList<>(2);
        for (CriterionRef c : inventoryById.getOrDefault(itemId, List.of())) {
            if (!done.test(c)) {
                out.add(c);
            }
        }
        for (TagRule r : inventoryTags) {
            if (!done.test(r.criterion()) && idInTag.test(itemId, r.tag())) {
                out.add(r.criterion());
            }
        }
        return out;
    }

    /** The criterion eating {@code itemId} earns ({@code world/fruit}). */
    public Optional<CriterionRef> consumed(String itemId) {
        return Optional.ofNullable(consume.get(itemId));
    }

    /** The criteria standing in a biome earns, by the biome's key ({@code tfp:plains}); none for other namespaces. */
    public List<CriterionRef> biome(String biomeKey) {
        if (!biomeKey.startsWith("tfp:")) {
            return List.of();
        }
        return biomes.getOrDefault(biomeKey.substring(4), List.of());
    }

    /** The {@code globe_trotter} criterion a block z earns, if any. */
    public Optional<CriterionRef> position(int blockZ) {
        if (blockZ >= GLOBE_Z) {
            return Optional.of(GLOBE_HIGH);
        }
        return blockZ <= -GLOBE_Z ? Optional.of(GLOBE_LOW) : Optional.empty();
    }

    /**
     * The criteria killing an entity earns.
     *
     * @param entityId our entity type id ({@code tfp:grizzly_bear})
     * @param idInTag whether an entity type id is in an entity type tag
     */
    public List<CriterionRef> kills(String entityId, BiPredicate<String, String> idInTag) {
        List<CriterionRef> out = new ArrayList<>(2);
        for (EntityRule r : kills) {
            boolean hit = r.match().startsWith("#") ? idInTag.test(entityId, r.match().substring(1)) : r.match().equals(entityId);
            if (hit) {
                out.add(r.criterion());
            }
        }
        return out;
    }

    /** The criteria a meal earns ({@code eat_rotten_food}; {@code full_nutrition} after a non-rotten meal). */
    public List<CriterionRef> meal(boolean rotten, float averageNutrition) {
        if (rotten) {
            return List.of(EAT_ROTTEN_FOOD);
        }
        return averageNutrition >= FULL_NUTRITION_AVERAGE ? List.of(FULL_NUTRITION) : List.of();
    }

    /** TFC {@code hooked_entity}: the criteria pulling in an entity of our type id earns (by the two entity type tags). */
    public List<CriterionRef> hooked(String entityId, BiPredicate<String, String> idInTag) {
        List<CriterionRef> out = new ArrayList<>(2);
        if (idInTag.test(entityId, SMALL_FISH_TAG)) {
            out.add(FISHING);
        }
        if (idInTag.test(entityId, LARGE_FISH_TAG)) {
            out.add(ADVANCED_FISHING);
        }
        return out;
    }

    /** TFC {@code stab_entity}: a javelin hit on {@code entityId} {@code horizontalDistance} blocks from the thrower. */
    public Optional<CriterionRef> stab(String entityId, double horizontalDistance) {
        return STAB_TARGET.equals(entityId) && horizontalDistance >= STAB_DISTANCE
            ? Optional.of(GREATEST_HUNTER) : Optional.empty();
    }

    /**
     * TFC {@code ServerCalendar.checkIfInTheFuture}: the calendar date (day clamped to 28, as TFC) is after the real date
     * {@code today}.
     */
    public Optional<CriterionRef> presentDay(java.time.LocalDate today, long calendarTicks, int daysInMonth) {
        long year = CalendarTicks.year(calendarTicks, daysInMonth);
        int month = CalendarTicks.month(calendarTicks, daysInMonth).ordinal() + 1;
        int day = Math.max(1, Math.min(28, CalendarTicks.dayOfMonth(calendarTicks, daysInMonth)));
        java.time.LocalDate calendar = java.time.LocalDate.of(
            (int) Math.max(java.time.Year.MIN_VALUE, Math.min(java.time.Year.MAX_VALUE, year)), month, day);
        return today.isBefore(calendar) ? Optional.of(THE_FUTURE) : Optional.empty();
    }
}
