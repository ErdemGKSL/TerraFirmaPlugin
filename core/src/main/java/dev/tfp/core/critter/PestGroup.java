/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/tags/entity_type/{universal,cold,desert,tropical}_pests.json and
 * src/main/java/net/dries007/tfc/common/TFCTags.java (the four pest tags) @ c3b4d9536
 */
package dev.tfp.core.critter;

import java.util.List;

import dev.tfp.core.spawn.SpawnRandom;

/**
 * The four infestation pest tags. Each tag holds one species here, in the tag file's order. A pick from a tag draws once
 * from the random source, as TFC's {@code Helpers.randomEntity} does through the tag's holder set.
 */
public enum PestGroup {
    /** {@code tfc:universal_pests}: spawned during infestations everywhere but the coldest biomes. */
    UNIVERSAL(List.of(PestSpecies.RAT)),
    /** {@code tfc:cold_pests}: spawned during infestations in boreal climates. */
    COLD(List.of(PestSpecies.LEMMING)),
    /** {@code tfc:desert_pests}: spawned during infestations in dry climates. */
    DESERT(List.of(PestSpecies.JERBOA)),
    /** {@code tfc:tropical_pests}: spawned during infestations in tropical climates. */
    TROPICAL(List.of(PestSpecies.MONGOOSE));

    private final List<PestSpecies> members;

    PestGroup(List<PestSpecies> members) {
        this.members = members;
    }

    /** The species of this tag, in the tag file's order. */
    public List<PestSpecies> members() {
        return members;
    }

    /**
     * A uniform pick from the tag: one draw in [0, size), as TFC's holder set pick.
     *
     * @param rand the random source; one draw is consumed even for a one-member tag
     */
    public PestSpecies pick(SpawnRandom rand) {
        return members.get(rand.nextInt(members.size()));
    }
}
