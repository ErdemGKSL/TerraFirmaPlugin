/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/animals/ProducingAnimalConfig.java @ c3b4d9536,
 * src/main/java/net/dries007/tfc/config/animals/ProducingMammalConfig.java and
 * src/main/java/net/dries007/tfc/config/ServerConfig.java (the produce values of each species)
 */
package dev.tfp.core.livestock.products;

import dev.tfp.core.livestock.LivestockSpecies;

import java.util.Optional;

/**
 * The product configuration of one species: which product it makes, how long the cooldown lasts and the minimum
 * familiarity it needs. The glue passes the server config values, so the record is the only place the cooldown and
 * the threshold live.
 *
 * @param kind            which product
 * @param produceTicks    player ticks from one harvest to the next (TFC {@code produceTicks})
 * @param produceFamiliarity the familiarity an animal must be strictly above to produce (TFC {@code produceFamiliarity},
 *                        a double, so the comparison is done in double precision)
 */
public record ProductSpec(ProductKind kind, int produceTicks, double produceFamiliarity) {
    /** TFC {@code ICalendar.PLAYER_TICKS_IN_DEFAULT_HOUR}, 60 * 20. */
    public static final int PLAYER_TICKS_IN_HOUR = 60 * 20;
    /** TFC default {@code MinProduceFamiliarity} for every producing species in {@code ServerConfig}. */
    public static final double DEFAULT_PRODUCE_FAMILIARITY = 0.15;

    public ProductSpec {
        if (kind == null) {
            throw new IllegalArgumentException("kind is null");
        }
        if (produceTicks < 0) {
            throw new IllegalArgumentException("produceTicks < 0: " + produceTicks);
        }
    }

    /**
     * The TFC {@code ServerConfig} defaults for a species, or empty if TFC gives the species no product.
     * Hours are converted with {@link #PLAYER_TICKS_IN_HOUR}, as TFC does.
     */
    public static Optional<ProductSpec> defaults(LivestockSpecies species) {
        return switch (species) {
            case COW -> Optional.of(milk(24));
            case GOAT -> Optional.of(milk(72));
            case YAK -> Optional.of(milk(24));
            case ALPACA -> Optional.of(shear(120));
            case SHEEP -> Optional.of(shear(96));
            case MUSK_OX -> Optional.of(shear(96));
            case BACTRIAN_CAMEL -> Optional.of(shear(168));
            case CHICKEN -> Optional.of(egg(30));
            case DUCK -> Optional.of(egg(32));
            case QUAIL -> Optional.of(egg(28));
            default -> Optional.empty();
        };
    }

    private static ProductSpec milk(int hours) {
        return new ProductSpec(ProductKind.MILK, hours * PLAYER_TICKS_IN_HOUR, DEFAULT_PRODUCE_FAMILIARITY);
    }

    private static ProductSpec shear(int hours) {
        return new ProductSpec(ProductKind.SHEAR, hours * PLAYER_TICKS_IN_HOUR, DEFAULT_PRODUCE_FAMILIARITY);
    }

    private static ProductSpec egg(int hours) {
        return new ProductSpec(ProductKind.EGG, hours * PLAYER_TICKS_IN_HOUR, DEFAULT_PRODUCE_FAMILIARITY);
    }
}
