/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gather;

/**
 * The kinds of block whose harvest yields this package covers: plants, leaves, fallen leaves, twigs, and the groundcover stick.
 */
public enum HarvestKind {
    PLANT,
    LEAVES,
    FALLEN_LEAVES,
    TWIG,
    STICK;

    /** The kind of a block id such as {@code tfc:wood/twig/oak}; throws for any block this package does not cover. */
    public static HarvestKind of(String blockId) {
        if (blockId == null) {
            throw new IllegalArgumentException("blockId must not be null");
        }
        if (blockId.startsWith("tfc:plant/")) {
            return PLANT;
        }
        if (blockId.startsWith("tfc:wood/leaves/")) {
            return LEAVES;
        }
        if (blockId.startsWith("tfc:wood/fallen_leaves/")) {
            return FALLEN_LEAVES;
        }
        if (blockId.startsWith("tfc:wood/twig/")) {
            return TWIG;
        }
        if (blockId.equals("tfc:groundcover/stick")) {
            return STICK;
        }
        throw new IllegalArgumentException("not a harvest block: " + blockId);
    }
}
