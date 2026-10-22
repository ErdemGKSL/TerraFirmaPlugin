/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/TFCEntities.java @ c3b4d9536 (the four pest registrations) and
 * src/main/java/net/dries007/tfc/common/entities/prey/Pest.java
 */
package dev.tfp.core.critter;

/**
 * The four pest species. Every one is a {@code Pest}; they differ only in which infestation group they belong to.
 * Pests have no biome spawner entries: they only appear through infestations ({@link PestInfestation}).
 */
public enum PestSpecies {
    /** TFC {@code rat}: the universal pest, the fallback of every climate. */
    RAT("rat"),
    /** TFC {@code lemming}: the boreal pest, the only one below -3 degrees. */
    LEMMING("lemming"),
    /** TFC {@code mongoose}: the tropical pest, dry-enough hot climates above 12 degrees. */
    MONGOOSE("mongoose"),
    /** TFC {@code jerboa}: the desert pest, rainfall under 160. */
    JERBOA("jerboa");

    private final String id;

    PestSpecies(String id) {
        this.id = id;
    }

    /** The registry path, such as {@code rat}; the namespace is {@code tfc}. */
    public String id() {
        return id;
    }
}
