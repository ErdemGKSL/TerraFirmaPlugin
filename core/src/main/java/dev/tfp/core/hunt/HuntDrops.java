/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/loot_table/entities/*.json (all 83 animal and fish loot tables) and
 * src/main/java/net/dries007/tfc/util/loot/AnimalYieldProvider.java @ c3b4d9536
 */
package dev.tfp.core.hunt;

import static dev.tfp.core.hunt.HuntCondition.IS_MALE;
import static dev.tfp.core.hunt.HuntCondition.KILLED_BY_PLAYER;
import static dev.tfp.core.hunt.HuntCondition.NOT_PREDATED;
import static dev.tfp.core.hunt.HuntCondition.PLAYER_KILL;

import dev.tfp.core.hunt.HuntCount.AnimalYield;
import dev.tfp.core.hunt.HuntCount.Bound;
import dev.tfp.core.hunt.HuntCount.Fixed;
import dev.tfp.core.hunt.HuntCount.Uniform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * The loot tables of TFC's animals and fish: for each species id (the file name of its loot table) the drops it can yield.
 * Species without drops are listed with an empty list. The table is the TFC data as exported; see the golden test for the
 * row-by-row check against the JSON.
 */
public final class HuntDrops {

    private static final Map<String, List<HuntDrop>> TABLE = table();

    private HuntDrops() {
    }

    /** The species ids, in loot table file-name order. */
    public static Set<String> species() {
        return TABLE.keySet();
    }

    /**
     * The drops of a species, in the order TFC rolls them.
     *
     * @throws IllegalArgumentException when the species has no loot table
     */
    public static List<HuntDrop> drops(String species) {
        List<HuntDrop> drops = TABLE.get(species);
        if (drops == null) {
            throw new IllegalArgumentException("no hunt loot table for " + species);
        }
        return drops;
    }

    /**
     * Rolls every drop of a species for one kill, in TFC's order, keeping the stacks with a count above 0.
     *
     * @param rng a source of uniform draws in [0, 1)
     */
    public static List<HuntYield> yields(String species, HuntKill kill, DoubleSupplier rng) {
        List<HuntYield> out = new ArrayList<>();
        for (HuntDrop drop : drops(species)) {
            int count = drop.yield(kill, rng);
            if (count > 0) {
                out.add(new HuntYield(drop.item(), count));
            }
        }
        return List.copyOf(out);
    }

    private static Map<String, List<HuntDrop>> table() {
        Map<String, List<HuntDrop>> m = new LinkedHashMap<>();
        species(m, "alpaca",
            yieldOf("tfc:food/camelidae", c(6f), u(10f, 16f)),
            range("minecraft:bone", 1, 4, NOT_PREDATED),
            one("tfc:medium_sheepskin_hide", NOT_PREDATED));
        species(m, "arctic_char",
            one("tfc:food/arctic_char", PLAYER_KILL));
        species(m, "armadillo",
            one("minecraft:armadillo_scute"),
            one("tfc:food/armadillo", NOT_PREDATED));
        species(m, "bactrian_camel",
            yieldOf("tfc:food/camelidae", c(6f), u(13f, 19f)),
            range("minecraft:bone", 1, 4, NOT_PREDATED),
            one("tfc:large_sheepskin_hide", NOT_PREDATED));
        species(m, "bison",
            range("tfc:food/bison", 12, 20, NOT_PREDATED),
            one("tfc:large_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 10, NOT_PREDATED));
        species(m, "black_bear",
            one("tfc:large_raw_hide"),
            range("minecraft:bone", 1, 6));
        species(m, "bluegill",
            one("tfc:food/bluegill", PLAYER_KILL));
        species(m, "boar",
            range("tfc:food/pork", 5, 10, NOT_PREDATED),
            one("tfc:small_raw_hide", chance(0.8f)),
            range("minecraft:bone", 1, 3, NOT_PREDATED));
        species(m, "bongo",
            range("tfc:food/venison", 6, 10, NOT_PREDATED),
            one("tfc:medium_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 6, NOT_PREDATED));
        species(m, "burbot",
            one("tfc:food/burbot", PLAYER_KILL));
        species(m, "caribou",
            range("tfc:food/venison", 6, 11, NOT_PREDATED),
            one("tfc:medium_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 6, NOT_PREDATED));
        species(m, "cat",
            one("tfc:small_raw_hide"),
            range("minecraft:bone", 1, 3));
        species(m, "chicken",
            yieldOf("tfc:food/chicken", c(2f), u(1f, 6f)),
            range("minecraft:feather", 4, 12, NOT_PREDATED));
        species(m, "cod",
            one("tfc:food/cod", PLAYER_KILL));
        species(m, "cougar",
            one("tfc:large_raw_hide"),
            range("minecraft:bone", 1, 6));
        species(m, "cow",
            yieldOf("tfc:food/beef", c(6f), u(17f, 23f)),
            one("tfc:large_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 4, NOT_PREDATED));
        species(m, "crappie",
            one("tfc:food/crappie", PLAYER_KILL));
        species(m, "crayfish",
            one("tfc:food/shellfish", PLAYER_KILL));
        species(m, "crocodile",
            one("tfc:large_raw_hide"),
            range("minecraft:bone", 1, 7));
        species(m, "deer",
            range("tfc:food/venison", 4, 10, NOT_PREDATED),
            one("tfc:medium_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 6, NOT_PREDATED));
        species(m, "direwolf",
            one("tfc:medium_raw_hide"),
            range("minecraft:bone", 1, 4));
        species(m, "dog",
            one("tfc:small_raw_hide"),
            range("minecraft:bone", 1, 3));
        species(m, "dolphin",
            range("tfc:blubber", 2, 7),
            range("minecraft:bone", 1, 5));
        species(m, "donkey",
            yieldOf("tfc:food/horse_meat", c(4f), u(15f, 21f)),
            one("tfc:medium_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 6, NOT_PREDATED));
        species(m, "dromedary_camel",
            yieldOf("tfc:food/camelidae", c(8f), u(13f, 19f)),
            one("tfc:large_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 4, NOT_PREDATED));
        species(m, "duck",
            yieldOf("tfc:food/duck", c(2f), u(1f, 6f)),
            range("minecraft:feather", 4, 10, NOT_PREDATED));
        species(m, "fox",
            one("tfc:small_raw_hide"),
            range("minecraft:bone", 1, 1));
        species(m, "frog",
            range("tfc:food/frog_legs", 2, 2),
            range("minecraft:bone", 1, 2));
        species(m, "gazelle",
            range("tfc:food/venison", 3, 8, NOT_PREDATED),
            one("tfc:medium_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 6, NOT_PREDATED));
        species(m, "goat",
            yieldOf("tfc:food/chevon", c(4f), u(7f, 13f)),
            one("tfc:medium_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 4, NOT_PREDATED),
            one("tfc:goat_horn", IS_MALE));
        species(m, "grizzly_bear",
            one("tfc:large_raw_hide"),
            range("minecraft:bone", 1, 6));
        species(m, "grouse",
            range("tfc:food/grouse", 2, 3, NOT_PREDATED),
            range("minecraft:bone", 1, 2, NOT_PREDATED),
            range("minecraft:feather", 4, 10, NOT_PREDATED));
        species(m, "horse",
            yieldOf("tfc:food/horse_meat", c(4f), u(15f, 21f)),
            one("tfc:medium_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 6, NOT_PREDATED));
        species(m, "horseshoe_crab",
            one("tfc:food/shellfish", PLAYER_KILL));
        species(m, "hyena",
            one("tfc:small_raw_hide"),
            range("minecraft:bone", 1, 3));
        species(m, "isopod",
            one("tfc:food/shellfish", PLAYER_KILL));
        species(m, "jellyfish");
        species(m, "jerboa");
        species(m, "lake_trout",
            one("tfc:food/lake_trout", PLAYER_KILL));
        species(m, "largemouth_bass",
            one("tfc:food/largemouth_bass", PLAYER_KILL));
        species(m, "lemming");
        species(m, "leopard_seal",
            range("tfc:blubber", 3, 6),
            one("tfc:medium_raw_hide"),
            range("minecraft:bone", 1, 4));
        species(m, "lion",
            one("tfc:large_raw_hide"),
            range("minecraft:bone", 1, 6));
        species(m, "lobster",
            one("tfc:food/shellfish", PLAYER_KILL));
        species(m, "manatee",
            range("tfc:blubber", 2, 7),
            range("minecraft:bone", 1, 5));
        species(m, "mongoose");
        species(m, "moose",
            range("tfc:food/venison", 10, 20, NOT_PREDATED),
            one("tfc:large_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 10, NOT_PREDATED));
        species(m, "muksun",
            one("tfc:food/muksun", PLAYER_KILL));
        species(m, "mule",
            yieldOf("tfc:food/horse_meat", c(4f), u(15f, 21f)),
            one("tfc:medium_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 6, NOT_PREDATED));
        species(m, "musk_ox",
            yieldOf("tfc:food/mutton", c(6f), u(13f, 19f)),
            range("minecraft:bone", 1, 4, NOT_PREDATED),
            one("tfc:large_sheepskin_hide", NOT_PREDATED));
        species(m, "northern_pike",
            one("tfc:food/northern_pike", PLAYER_KILL));
        species(m, "ocelot");
        species(m, "octopoteuthis",
            range("minecraft:glow_ink_sac", 1, 10, KILLED_BY_PLAYER),
            one("tfc:food/calamari", KILLED_BY_PLAYER));
        species(m, "orca",
            range("tfc:blubber", 2, 7),
            range("minecraft:bone", 1, 5));
        species(m, "pacu",
            one("tfc:food/pacu", PLAYER_KILL));
        species(m, "panda");
        species(m, "panther",
            one("tfc:large_raw_hide"),
            range("minecraft:bone", 1, 6));
        species(m, "peacock_bass",
            one("tfc:food/peacock_bass", PLAYER_KILL));
        species(m, "peafowl",
            range("tfc:food/peafowl", 2, 4, NOT_PREDATED),
            range("minecraft:bone", 1, 2, NOT_PREDATED),
            range("minecraft:feather", 8, 14, NOT_PREDATED));
        species(m, "penguin",
            range("minecraft:feather", 1, 3),
            one("tfc:small_raw_hide", chance(0.5f)),
            range("minecraft:bone", 1, 2));
        species(m, "pheasant",
            range("tfc:food/pheasant", 2, 3, NOT_PREDATED),
            range("minecraft:bone", 1, 2, NOT_PREDATED),
            range("minecraft:feather", 4, 10, NOT_PREDATED));
        species(m, "pig",
            yieldOf("tfc:food/pork", c(4f), u(9f, 15f)),
            one("tfc:medium_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 3, NOT_PREDATED));
        species(m, "polar_bear",
            one("tfc:large_raw_hide"),
            range("minecraft:bone", 1, 6));
        species(m, "pufferfish",
            one("minecraft:pufferfish", KILLED_BY_PLAYER));
        species(m, "quail",
            yieldOf("tfc:food/quail", c(1f), u(1f, 6f)),
            range("minecraft:feather", 4, 12, NOT_PREDATED));
        species(m, "rabbit",
            one("tfc:food/rabbit", NOT_PREDATED),
            one("tfc:small_raw_hide", chance(0.5f)),
            one("minecraft:bone", NOT_PREDATED),
            one("minecraft:rabbit_foot", chance(0.1f)));
        species(m, "rainbow_trout",
            one("tfc:food/rainbow_trout", PLAYER_KILL));
        species(m, "rat");
        species(m, "red_piranha",
            one("tfc:food/red_piranha", PLAYER_KILL));
        species(m, "sabertooth",
            one("tfc:large_raw_hide"),
            range("minecraft:bone", 1, 8));
        species(m, "salmon",
            one("tfc:food/salmon", PLAYER_KILL));
        species(m, "sheep",
            yieldOf("tfc:food/mutton", c(4f), u(12f, 18f)),
            range("minecraft:bone", 1, 4, NOT_PREDATED),
            one("tfc:small_sheepskin_hide", NOT_PREDATED));
        species(m, "smallmouth_bass",
            one("tfc:food/smallmouth_bass", PLAYER_KILL));
        species(m, "spotted_gudgeon",
            one("tfc:food/spotted_gudgeon", PLAYER_KILL));
        species(m, "squid",
            range("minecraft:ink_sac", 1, 10, KILLED_BY_PLAYER),
            one("tfc:food/calamari", KILLED_BY_PLAYER));
        species(m, "tiger",
            one("tfc:large_raw_hide"),
            range("minecraft:bone", 1, 7));
        species(m, "tilapia",
            one("tfc:food/tilapia", PLAYER_KILL));
        species(m, "tropical_fish",
            one("tfc:food/tropical_fish", PLAYER_KILL));
        species(m, "turkey",
            range("tfc:food/turkey", 2, 4, NOT_PREDATED),
            range("minecraft:bone", 1, 2, NOT_PREDATED),
            range("minecraft:feather", 6, 10, NOT_PREDATED));
        species(m, "turtle",
            one("minecraft:turtle_scute"),
            one("tfc:food/turtle"));
        species(m, "wildebeest",
            range("tfc:food/venison", 8, 14, NOT_PREDATED),
            one("tfc:small_raw_hide", chance(0.8f)),
            range("minecraft:bone", 1, 3, NOT_PREDATED));
        species(m, "wolf",
            one("tfc:small_raw_hide"),
            range("minecraft:bone", 1, 3));
        species(m, "yak",
            yieldOf("tfc:food/chevon", c(8f), u(13f, 19f)),
            one("tfc:large_raw_hide", NOT_PREDATED),
            range("minecraft:bone", 1, 4, NOT_PREDATED));
        return Collections.unmodifiableMap(m);
    }

    private static void species(Map<String, List<HuntDrop>> m, String id, HuntDrop... drops) {
        m.put(id, List.of(drops));
    }

    private static HuntDrop one(String item, HuntCondition... conditions) {
        return new HuntDrop(item, new Fixed(1), List.of(conditions));
    }

    private static HuntDrop range(String item, int min, int max, HuntCondition... conditions) {
        return new HuntDrop(item, new Uniform(min, max), List.of(conditions));
    }

    private static HuntDrop yieldOf(String item, Bound min, Bound max, HuntCondition... conditions) {
        return new HuntDrop(item, new AnimalYield(min, max), List.of(conditions));
    }

    private static Bound c(float value) {
        return Bound.of(value);
    }

    private static Bound u(float lo, float hi) {
        return Bound.between(lo, hi);
    }

    private static HuntCondition chance(float p) {
        return HuntCondition.randomChance(p);
    }
}
