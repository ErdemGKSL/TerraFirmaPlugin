/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.volcano;

/**
 * What a volcano or hot spring feature places at a position. The rock kinds carry the rock index of the block
 * ({@link VolcanoBlock#rock()}, in {@code PlaceholderRocks.names()} order).
 */
public enum VolcanoKind {
    /** Air: an empty hot spring's pool, or a hole in a spring's rim. */
    AIR,
    /** Lava ({@code minecraft:lava}): calderas, volcano fissures, lava hot springs. */
    LAVA,
    /** TFC's spring water ({@code tfc:fluid/spring_water}): hot spring pools and fissures. */
    SPRING_WATER,
    /** Raw rock ({@code tfc:rock/raw/<rock>}): the basalt walls of volcano fissures. */
    RAW_ROCK,
    /** Hardened rock ({@code tfc:rock/hardened/<rock>}): the bed and fissure walls of hot springs. */
    HARDENED_ROCK,
    /** Rock gravel ({@code tfc:rock/gravel/<rock>}): under a hot spring's pool. */
    GRAVEL,
    /** Magma ({@code tfc:rock/magma/<rock>}): lava hot springs where they touch water. */
    MAGMA,
    /** A gem ore ({@code tfc:ore/<gem>/<rock>}): the decoration of gem fissures and gem hot springs. */
    GEM_ORE,
    /** Dirt: the grass under a caldera's lava ({@code IGrassBlock.getDirt}). */
    DIRT;

    /** Whether this kind is a fluid source block (TFC's {@code isAirOrEmptyFluid} is true for these and air). */
    public boolean isFluid() {
        return this == LAVA || this == SPRING_WATER;
    }

    /** Whether the block of this kind names a rock. */
    public boolean hasRock() {
        return this == RAW_ROCK || this == HARDENED_ROCK || this == GRAVEL || this == MAGMA || this == GEM_ORE;
    }
}
