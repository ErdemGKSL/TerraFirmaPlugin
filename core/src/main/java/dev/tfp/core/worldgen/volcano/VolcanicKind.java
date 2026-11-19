/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.volcano;

/**
 * What a volcanic feature of {@link VolcanicFeatures} places at a position: the kinds of {@link VolcanoKind} (same names)
 * plus those of the springs, rivulets, crater lakes and groundcover patches (PAR-WORLD-14). The rock kinds carry the rock
 * index of the block ({@link VolcanicBlock#rock()}, in {@code PlaceholderRocks.names()} order).
 */
public enum VolcanicKind {
    AIR,
    /** Lava ({@code minecraft:lava}): calderas, fissures, lava springs. */
    LAVA,
    /** TFC's spring water ({@code tfc:fluid/spring_water}). */
    SPRING_WATER,
    /** Fresh water ({@code minecraft:water}, a source): water springs, the Kelimutu water lake, a rivulet next to water. */
    WATER,
    /** TFC's salt water ({@code tfc:fluid/salt_water}): a rivulet's cut next to salt water. */
    SALT_WATER,
    /** Raw rock ({@code tfc:rock/raw/<rock>}). */
    RAW_ROCK,
    /** Hardened rock ({@code tfc:rock/hardened/<rock>}). */
    HARDENED_ROCK,
    /** Rock gravel ({@code tfc:rock/gravel/<rock>}). */
    GRAVEL,
    /** Magma ({@code tfc:rock/magma/<rock>}): lava hot springs and the rivulets' veins. */
    MAGMA,
    /** A gem ore ({@code tfc:ore/<gem>/<rock>}). */
    GEM_ORE,
    /** Sulfur ore ({@code tfc:ore/sulfur/<rock>}): the sulfur rivulets of crater lakes. */
    SULFUR_ORE,
    DIRT,
    /** {@code tfc:groundcover/pumice}. */
    PUMICE,
    /** {@code tfc:groundcover/obsidian_shard}. */
    OBSIDIAN_SHARD;

    /** Whether this kind is a fluid source block. */
    public boolean isFluid() {
        return this == LAVA || this == SPRING_WATER || this == WATER || this == SALT_WATER;
    }

    /** Whether the block of this kind names a rock. */
    public boolean hasRock() {
        return this == RAW_ROCK || this == HARDENED_ROCK || this == GRAVEL || this == MAGMA || this == GEM_ORE || this == SULFUR_ORE;
    }

    /** Whether this kind is a groundcover block (not solid, no sturdy top, not replaceable). */
    public boolean isGroundcover() {
        return this == PUMICE || this == OBSIDIAN_SHARD;
    }

    /** {@code BlockState.isSolid}: a full block. */
    public boolean isSolid() {
        return this != AIR && !isFluid() && !isGroundcover();
    }

    /** The kind of the same name. */
    static VolcanicKind of(VolcanoKind kind) {
        return valueOf(kind.name());
    }

    /** The {@link VolcanoKind} of the same name; only for the kinds the cinder cone and hot spring features place. */
    VolcanoKind legacy() {
        return VolcanoKind.valueOf(name());
    }
}
