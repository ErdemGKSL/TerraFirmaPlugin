/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/pet/Dog.java and TFCCat.java (willListenTo) @ c3b4d9536
 */
package dev.tfp.core.pet;

/**
 * The two pet species. Only the command rules that differ between them are kept here: the cat sometimes ignores a SIT.
 */
public enum PetSpecies {
    /** TFC {@code Dog}: listens to every command the health gate allows. */
    DOG(0f),
    /** TFC {@code TFCCat}: a server-side SIT is refused with a 10% chance, drawn by the caller. */
    CAT(0.1f);

    private final float sitRefusalChance;

    PetSpecies(float sitRefusalChance) {
        this.sitRefusalChance = sitRefusalChance;
    }

    /** The chance in [0, 1) that the server refuses a SIT (TFC {@code TFCCat.willListenTo}: {@code nextFloat() < 0.1f}). */
    public float sitRefusalChance() {
        return sitRefusalChance;
    }

    /**
     * Whether a server-side SIT is refused for this roll. TFC refuses when {@code nextFloat() < 0.1f}, so the caller
     * passes the roll in [0, 1). A dog never refuses.
     */
    public boolean refusesSit(float roll) {
        return sitRefusalChance > 0f && roll < sitRefusalChance;
    }
}
