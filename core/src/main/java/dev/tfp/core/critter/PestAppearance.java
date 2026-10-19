/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/Pest.java (finalizeSpawn) @ c3b4d9536
 */
package dev.tfp.core.critter;

import dev.tfp.core.spawn.SpawnRandom;

/**
 * The two spawn-time choices of a pest: the rare custom name and the baby chance. TFC makes the name roll first, then the baby
 * roll, after the vanilla {@code finalizeSpawn}, whose draws are not modelled here.
 *
 * @param named the pest is named "Pak" (one in 1000)
 * @param baby  the pest is a baby (chance 0.2)
 */
public record PestAppearance(boolean named, boolean baby) {
    /** TFC {@code random.nextInt(1000) == 0}. */
    public static final int NAME_ONE_IN = 1000;
    /** TFC {@code random.nextFloat() < 0.2f}. */
    public static final float BABY_CHANCE = 0.2f;

    /** The two rolls in TFC's order. */
    public static PestAppearance roll(SpawnRandom rand) {
        boolean named = rand.nextInt(NAME_ONE_IN) == 0;
        boolean baby = rand.nextFloat() < BABY_CHANCE;
        return new PestAppearance(named, baby);
    }
}
