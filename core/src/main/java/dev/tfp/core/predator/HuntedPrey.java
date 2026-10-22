/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/TFCTags.java @ c3b4d9536 (HUNTED_BY_LAND_PREDATORS),
 * src/data/java/net/dries007/tfc/data/providers/BuiltinEntityTags.java (HUNTED_BY_LAND_PREDATORS, BIRD_PREY, LARGE_PREY),
 * src/generated/resources/data/tfc/tags/entity_type/hunted_by_land_predators.json, bird_prey.json, large_prey.json
 */
package dev.tfp.core.predator;

import java.util.Set;

/**
 * The prey a land predator accepts: the expansion of TFC's {@code tfc:hunted_by_land_predators} entity tag.
 *
 * <p>The tag is {@code #tfc:bird_prey}, {@code #tfc:large_prey} and five single types. The expansion is kept as a constant
 * so the rule is pure; the list is the tag file at c3b4d9536 and must be kept in step with it.
 */
public final class HuntedPrey {

    /** {@code #tfc:bird_prey}: turkey, pheasant, grouse, chicken, penguin, quail, duck. */
    static final Set<String> BIRD_PREY = Set.of(
        "tfc:turkey", "tfc:pheasant", "tfc:grouse", "tfc:chicken", "tfc:penguin", "tfc:quail", "tfc:duck"
    );

    /** {@code #tfc:large_prey}: deer, caribou, bongo, pig, cow, sheep, yak, musk ox, horse, mule, donkey, goat, two camels. */
    static final Set<String> LARGE_PREY = Set.of(
        "tfc:deer", "tfc:caribou", "tfc:bongo", "tfc:pig", "tfc:cow", "tfc:sheep", "tfc:yak", "tfc:musk_ox",
        "tfc:horse", "tfc:mule", "tfc:donkey", "tfc:goat", "tfc:dromedary_camel", "tfc:bactrian_camel"
    );

    /** The five single types of {@code hunted_by_land_predators.json}. */
    static final Set<String> SMALL_PREY = Set.of(
        "tfc:rat", "tfc:jerboa", "tfc:lemming", "tfc:mongoose", "tfc:armadillo"
    );

    private HuntedPrey() {
    }

    /**
     * Whether a land predator may choose this entity type as prey (TFC {@code Helpers.isEntity(e, HUNTED_BY_LAND_PREDATORS)}).
     *
     * @param type the entity type key, for example {@code tfc:deer}
     * @return {@code true} if the type is in the expanded tag
     */
    public static boolean isHuntedByLandPredator(String type) {
        return BIRD_PREY.contains(type) || LARGE_PREY.contains(type) || SMALL_PREY.contains(type);
    }
}
