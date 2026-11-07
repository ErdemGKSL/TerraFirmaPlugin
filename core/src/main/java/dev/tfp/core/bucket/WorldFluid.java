/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.bucket;

import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.thirst.Drinkables;
import java.util.Optional;

/**
 * The fluids that exist in our world: vanilla water and lava only (NP-FLUID-01). Maps them to the container fluid a bucket
 * takes up, and a container fluid to the world fluid it is placed as.
 *
 * <ul>
 *   <li>Picking up water gives {@code tfp:salt_water} in a salty (ocean) biome and {@code minecraft:water} elsewhere: the
 *       fallback rule; the plugin asks PAR-FLUID-03's world water kinds first. Lava gives {@code minecraft:lava}.</li>
 *   <li>Placing: {@code minecraft:water} and TFC's world waters ({@code tfp:salt_water}, {@code tfp:spring_water},
 *       {@code tfp:river_water}) are placed as vanilla water, the closest fluid a vanilla client shows;
 *       {@code minecraft:lava} as lava. Every other fluid (milk, brine, beer, dyes, molten metals...) cannot be placed: TFC
 *       would place its own fluid block, which vanilla clients cannot see (ledger divergence).</li>
 * </ul>
 */
public enum WorldFluid {
    WATER, LAVA;

    /** The container fluid of a picked-up world fluid. */
    public String pickedUp(boolean saltyBiome) {
        if (this == LAVA) {
            return TfcFluids.LAVA;
        }
        return saltyBiome ? Drinkables.SALT_WATER : TfcFluids.WATER;
    }

    /** The world fluid that {@code fluidId} is placed as, or empty when it cannot be placed in the world. */
    public static Optional<WorldFluid> placedAs(String fluidId) {
        if (fluidId == null) {
            return Optional.empty();
        }
        return switch (fluidId) {
            case TfcFluids.WATER, "tfp:salt_water", "tfp:spring_water", "tfp:river_water" -> Optional.of(WATER);
            case TfcFluids.LAVA -> Optional.of(LAVA);
            default -> Optional.empty();
        };
    }
}
