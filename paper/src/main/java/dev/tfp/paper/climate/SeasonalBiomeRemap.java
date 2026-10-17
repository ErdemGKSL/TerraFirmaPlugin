/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import dev.tfp.core.climate.SeasonalColour;
import java.util.Optional;

/**
 * M2-SEA-01 step 3, decision only: which seasonal biome variant a chunk's biome palette should carry for a position's
 * instant climate. Pure, so it runs on the CPU executor (the same pool as {@link PlayerClimateService}) and never
 * touches the world.
 *
 * <p>The packet rewrite that uses this (PacketEvents chunk data: swap each biome palette entry for the variant's
 * network id, resend the loaded chunks at each season change) is not wired yet. It needs the network ids of the
 * datapack variants from the client's biome registry and a handler on the chunk packet. That is the M2-SEA-01
 * follow-up, and {@link dev.tfp.paper.packet.NoopPacketHandler} is still the only handler.
 */
public final class SeasonalBiomeRemap {
    private SeasonalBiomeRemap() {
    }

    /**
     * The variant id for {@code basePath} (the biome's path in our namespace, e.g. {@code plains}) at the given instant
     * temperature (degrees Celsius) and groundwater (mm, base plus instant rainfall).
     *
     * @return the datapack biome id {@code tfp:seasonal/<biome>/<variant>}, or empty when either value is unknown (NaN),
     *         so the caller keeps the base biome
     */
    public static Optional<String> variantId(String basePath, float temperature, float groundwater) {
        if (Float.isNaN(temperature) || Float.isNaN(groundwater)) {
            return Optional.empty();
        }
        return Optional.of(SeasonalColour.variantId(basePath, SeasonalColour.band(temperature, groundwater)));
    }
}
