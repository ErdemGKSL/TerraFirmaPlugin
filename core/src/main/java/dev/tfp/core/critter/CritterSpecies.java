/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/TFCEntities.java @ c3b4d9536 (the four critter registrations),
 * src/main/java/net/dries007/tfc/common/entities/aquatic/AquaticCritter.java and
 * src/generated/resources/data/tfc/tags/entity_type/caught_in_lobster_traps.json
 */
package dev.tfp.core.critter;

/**
 * The four water critters: isopod, lobster, crayfish and horseshoe crab. Each is an {@code AquaticCritter} in the water-ambient
 * category, salt or fresh by its factory, and each is one of the four species a lobster trap can hold.
 *
 * <p>Their spawn lists are in the biome data and are ported with the spawn rules ({@code dev.tfp.core.spawn}). Their loot is in
 * {@code dev.tfp.core.hunt}. Escape and trap behaviour is goal AI and is not here.
 */
public enum CritterSpecies {
    /** TFC {@code isopod}: salt water. */
    ISOPOD(true),
    /** TFC {@code lobster}: salt water. */
    LOBSTER(true),
    /** TFC {@code crayfish}: fresh water. */
    CRAYFISH(false),
    /** TFC {@code horseshoe_crab}: salt water. */
    HORSESHOE_CRAB(true);

    private final boolean salty;

    CritterSpecies(boolean salty) {
        this.salty = salty;
    }

    /** Whether the critter lives in salt water (its factory is {@code AquaticCritter::salty}). */
    public boolean salty() {
        return salty;
    }

    /**
     * TFC {@code AquaticCritter.canSpawnIn}: a salty critter needs the salt water source, a fresh one the fresh water source.
     * Flowing water and other fluids never match.
     */
    public boolean canSpawnIn(CritterFluid fluid) {
        return fluid == (salty ? CritterFluid.SALT_SOURCE : CritterFluid.FRESH_SOURCE);
    }
}
