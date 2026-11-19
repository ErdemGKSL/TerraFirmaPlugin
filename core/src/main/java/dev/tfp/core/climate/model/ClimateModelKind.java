/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/ClimateModels.java @ c3b4d9536
 */
package dev.tfp.core.climate.model;

/**
 * Which climate model a level uses (TFC {@code SelectClimateModelEvent} default handler): the overworld model for an overworld
 * with the TFC chunk generator, the biome-based model for everything else.
 */
public enum ClimateModelKind {
    OVERWORLD,
    BIOME_BASED;

    /**
     * @param overworldEnvironment the level is the overworld dimension
     * @param tfcGenerator         the level uses our TFC chunk generator
     */
    public static ClimateModelKind select(boolean overworldEnvironment, boolean tfcGenerator) {
        return overworldEnvironment && tfcGenerator ? OVERWORLD : BIOME_BASED;
    }
}
