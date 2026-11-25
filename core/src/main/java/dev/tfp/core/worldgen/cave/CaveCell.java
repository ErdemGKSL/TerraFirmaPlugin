/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/RockSpikeBlock.java @ c3b4d9536 (PART, FLUID)
 *       src/main/java/net/dries007/tfc/common/blocks/ThinSpikeBlock.java @ c3b4d9536 (TIP, FLUID)
 */
package dev.tfp.core.worldgen.cave;

/**
 * PAR-WORLD-20: one block as the cave features read and write it. Immutable.
 *
 * @param kind  what the block is
 * @param rock  the rock index ({@code PlaceholderRocks}) of a raw, hardened or spike block, else -1
 * @param part  the part of a rock spike, else null
 * @param fluid the fluid in or of the block ({@link Fluid#EMPTY} for dry blocks)
 * @param tip   whether a thin spike (calcite, icicle) is a tip
 */
public record CaveCell(Kind kind, int rock, Part part, Fluid fluid, boolean tip) {
    /** The block kinds the features tell apart. */
    public enum Kind {
        /** {@code minecraft:cave_air}: what the carvers leave. */
        CAVE_AIR,
        /** {@code minecraft:air}. */
        AIR,
        /** A fluid source block ({@link #fluid} says which). */
        FLUID,
        /** A raw rock ({@code #c:stones/raw}). */
        RAW,
        /** A hardened rock ({@code #c:stones/hardened}). */
        HARDENED,
        /** A rock spike ({@code RockSpikeBlock}). */
        SPIKE,
        /** {@code tfc:calcite}. */
        CALCITE,
        /** {@code tfc:icicle}. */
        ICICLE,
        /** {@code tfc:rock/mossy_cobble/<rock>} (cave vegetation). */
        MOSSY_COBBLE,
        /** {@code tfc:rock/cobble/<rock>} (cave vegetation). */
        COBBLE,
        /** {@code tfc:groundcover/guano}. */
        GUANO,
        /** {@code minecraft:cobweb}. */
        COBWEB,
        /** {@code minecraft:hanging_roots}. */
        HANGING_ROOTS,
        /** A block of {@code #minecraft:ice}. */
        ICE,
        /** Any other block of {@code #minecraft:base_stone_overworld}. */
        STONE,
        /** Anything else. */
        OTHER
    }

    /** {@code RockSpikeBlock.Part}. */
    public enum Part { BASE, MIDDLE, TIP }

    /** The fluids of the world's blocks. */
    public enum Fluid { EMPTY, WATER, RIVER_WATER, SALT_WATER, LAVA }

    public static final CaveCell CAVE_AIR = new CaveCell(Kind.CAVE_AIR, -1, null, Fluid.EMPTY, false);
    public static final CaveCell AIR = new CaveCell(Kind.AIR, -1, null, Fluid.EMPTY, false);
    public static final CaveCell OTHER = new CaveCell(Kind.OTHER, -1, null, Fluid.EMPTY, false);
    public static final CaveCell ICE = new CaveCell(Kind.ICE, -1, null, Fluid.EMPTY, false);
    public static final CaveCell STONE = new CaveCell(Kind.STONE, -1, null, Fluid.EMPTY, false);

    public static CaveCell fluid(Fluid fluid) {
        return new CaveCell(Kind.FLUID, -1, null, fluid, false);
    }

    public static CaveCell raw(int rock) {
        return new CaveCell(Kind.RAW, rock, null, Fluid.EMPTY, false);
    }

    public static CaveCell hardened(int rock) {
        return new CaveCell(Kind.HARDENED, rock, null, Fluid.EMPTY, false);
    }

    public static CaveCell spike(int rock, Part part, Fluid fluid) {
        return new CaveCell(Kind.SPIKE, rock, part, fluid, false);
    }

    public static CaveCell of(Kind kind, int rock) {
        return new CaveCell(kind, rock, null, Fluid.EMPTY, false);
    }

    public static CaveCell thinSpike(Kind kind, Fluid fluid, boolean tip) {
        return new CaveCell(kind, -1, null, fluid, tip);
    }

    /** {@code BlockState.isAir()}. */
    public boolean isAir() {
        return kind == Kind.CAVE_AIR || kind == Kind.AIR;
    }

    /** {@code #minecraft:base_stone_overworld} (TFC: raw and hardened rock). */
    public boolean baseStone() {
        return kind == Kind.RAW || kind == Kind.HARDENED || kind == Kind.STONE;
    }

    /** {@code RockSettings.isRawOrHardened}: the wall a spike hangs from. */
    public boolean rawOrHardened() {
        return kind == Kind.RAW || kind == Kind.HARDENED;
    }

    /** {@code FluidHelpers.isAirOrEmptyFluid}: air, or a fluid's own source block. */
    public boolean airOrEmptyFluid() {
        return isAir() || kind == Kind.FLUID;
    }

    /**
     * Whether the block's faces are sturdy ({@code isFaceSturdy}): the full blocks. {@link Kind#OTHER} counts as a full block
     * (in a cave it is soil, ore or another full block the terrain wrote).
     */
    public boolean sturdy() {
        return switch (kind) {
            case RAW, HARDENED, STONE, ICE, OTHER, MOSSY_COBBLE, COBBLE -> true;
            default -> false;
        };
    }
}
