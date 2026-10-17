/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/settings/Settings.java @ c3b4d9536 (the fields the region generator reads)
 */
package dev.tfp.core.worldgen.region;

/**
 * The world settings that shape the region map. {@link #OVERWORLD} is TFC's overworld world preset
 * ({@code src/generated/resources/data/tfc/worldgen/world_preset/overworld.json} at c3b4d9536).
 * The rock layer settings and the spawn fields are not here: rocks are chosen by id only, and spawn is not ported.
 */
public record RegionSettings(
    float continentalness,
    boolean finiteContinents,
    int temperatureScale,
    float temperatureConstant,
    int rainfallScale,
    float rainfallConstant
) {
    public static final RegionSettings OVERWORLD = new RegionSettings(0.5f, false, 20_000, 0f, 20_000, 0f);
}
