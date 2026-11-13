/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/ClientHelpers.java @ c3b4d9536 (inNorthernHemisphere)
 *       src/main/java/net/dries007/tfc/client/overworld/SolarCalculator.java @ c3b4d9536 (getInNorthernHemisphere)
 */
package dev.tfp.paper.hud;

import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.Solar;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * PAR-HUD-16: the hemisphere a player's HUD season text follows. TFC's {@code CalendarScreen} and HUD ask
 * {@code ClientHelpers.inNorthernHemisphere()}: {@code SolarCalculator.getInNorthernHemisphere(z, hemisphereScale)} at the
 * player's block z with the level's climate hemisphere scale. Here the scale is the overworld climate's
 * ({@link OverworldClimate#hemisphereScale()}, the same climate the calendar tab uses); outside the overworld there is no
 * TFC climate model with hemispheres, so the player counts as northern (the calendar tab does the same).
 */
public final class HudHemisphere {
    private HudHemisphere() {
    }

    /** The pure part: whether block z {@code z} of a world is in the northern hemisphere. */
    public static boolean north(boolean overworld, long worldSeed, int z) {
        if (!overworld) {
            return true;
        }
        float scale = OverworldClimate.forWorldSeed(worldSeed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE).hemisphereScale();
        return Solar.inNorthernHemisphere(z, scale);
    }

    /** Player thread only: the hemisphere at the player's block position. */
    public static boolean north(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        return north(world.getEnvironment() == World.Environment.NORMAL, world.getSeed(), at.getBlockZ());
    }
}
