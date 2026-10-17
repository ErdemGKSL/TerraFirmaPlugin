/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java @ c3b4d9536
 * (onTickChunk: the per-event accumulation and melting branches, lines 280-297; placeSnowOrSnowPile and
 * placeSnowOrSnowPileAt, lines 396-470; removeSnowAt, lines 568-600), and
 * src/main/java/net/dries007/tfc/common/blocks/SnowPileBlock.java (removePileOrSnow: a layer above one is removed,
 * a single layer is removed entirely), IcePileBlock.java (removeIcePileOrIce: ice melts to water when the block
 * below blocks motion or is liquid, otherwise it is removed).
 */
package dev.tfp.core.weather;

/**
 * One column's snow and ice change at one instant (M2-WTH-02), as TFC decides it for a single surface position.
 *
 * <p>TFC works on chunks: it samples a position per event and walks the chunk's surface sequentially. This rule is the
 * per-column decision those events make. The Paper glue reads the column, calls {@link #decide}, and writes the change.
 * Not modelled here (the Paper glue does not do them yet): snow piles on vegetation and loose rocks, icicles, the
 * sideways smoothing of accumulation onto neighbours, ice under a solid top, and the catch-up over elapsed time.
 *
 * <p>Pure and thread-safe. Temperatures are {@code float}, as in TFC.
 */
public final class SnowRule {
    /** TFC: accumulation needs {@code realTemperature < -2f} (strict). */
    public static final float FREEZE_BELOW = -2f;
    /** TFC: melting needs {@code temperature > 2f} (strict). */
    public static final float MELT_ABOVE = 2f;
    /** Vanilla snow layers: 1 to 8. */
    public static final int MAX_SNOW_LAYERS = 8;

    private SnowRule() {
    }

    /** The block at the top of the column: the highest block that blocks motion or is liquid (TFC's MOTION_BLOCKING heightmap, minus one). */
    public enum Cover {
        /** Nothing above the surface is blocking. Never a top in a loaded column; decides nothing. */
        AIR,
        /** A snow layer; {@link Column#snowLayers()} gives its count. */
        SNOW,
        /** Ice (TFC's ice block). */
        ICE,
        /** Still water. */
        WATER,
        /** A solid block with a full top face: a snow layer can rest on it (vanilla {@code canSurvive}). */
        GROUND,
        /** Any other blocking block that a snow layer cannot rest on (a fence, a partial block). Decides nothing. */
        OTHER
    }

    /** What changes at one column. The position of the change is given by {@link #placedAboveTop()}. */
    public enum SnowChange {
        NONE,
        /** A new snow layer of one, on the air above the top (TFC {@code Blocks.SNOW} placement). */
        ADD_SNOW_LAYER,
        /** Still water at the top becomes ice (TFC {@code IcePileBlock.placeIcePileOrIce}). */
        FREEZE_WATER,
        /** One snow layer is removed from the top: layers n to n-1, or a single layer to air. */
        REMOVE_SNOW_LAYER,
        /** Ice at the top melts to water, because the block below blocks motion or is liquid. */
        MELT_ICE_TO_WATER,
        /** Ice at the top with nothing below it to hold water is removed (TFC removes it to air). */
        MELT_ICE_TO_AIR;

        /** True when the change is written at the air position above the top, not at the top itself. */
        public boolean placedAboveTop() {
            return this == ADD_SNOW_LAYER;
        }
    }

    /**
     * The inputs of one decision, read at the column's surface.
     *
     * @param top               the block at the top of the column (see {@link Cover})
     * @param snowLayers        the layer count when {@code top} is {@link Cover#SNOW} (1 to 8), otherwise 0
     * @param supportedBelow    whether the block below the top blocks motion or is liquid (TFC {@code removeIcePileOrIce})
     * @param waterEdge         whether a horizontal neighbour of the top, at its level, is not water (TFC {@code isAdjacentToNotWater})
     * @param skyAbove          whether the air above the top can see the sky (vanilla {@code canSeeSky}); TFC applies it to snow placement only
     * @param temperature       the instant temperature at the column, degrees Celsius
     * @param precipitation     the precipitation at the column with the sky gate left out, as TFC's {@code isPrecipitating}:
     *                          {@link PrecipitationType#NONE} when nothing is falling, otherwise {@code RAIN} or {@code SNOW}
     */
    public record Column(Cover top, int snowLayers, boolean supportedBelow, boolean waterEdge, boolean skyAbove,
            float temperature, PrecipitationType precipitation) {
        public Column {
            if (top == null || precipitation == null) {
                throw new NullPointerException("top and precipitation are required");
            }
            if (top == Cover.SNOW && (snowLayers < 1 || snowLayers > MAX_SNOW_LAYERS)) {
                throw new IllegalArgumentException("snow layers must be 1 to " + MAX_SNOW_LAYERS + ": " + snowLayers);
            }
            if (top != Cover.SNOW && snowLayers != 0) {
                throw new IllegalArgumentException("snow layers only apply to a snow top: " + top);
            }
        }
    }

    /**
     * TFC's decision for one column. Accumulation (temperature below {@value #FREEZE_BELOW}) with precipitation:
     * a snow layer goes on the air above a ground or full-height snow top that can see the sky; otherwise still water
     * at the top freezes, when a neighbour at its level is not water. Melting (temperature above {@value #MELT_ABOVE}):
     * one snow layer goes, or ice melts. Between the two thresholds nothing changes.
     *
     * @return the change; never {@code null}
     */
    public static SnowChange decide(Column c) {
        if (c.temperature() < FREEZE_BELOW) {
            if (c.precipitation() == PrecipitationType.NONE) {
                return SnowChange.NONE;
            }
            if (c.skyAbove() && restsSnow(c)) {
                return SnowChange.ADD_SNOW_LAYER;
            }
            if (c.top() == Cover.WATER && c.waterEdge()) {
                return SnowChange.FREEZE_WATER;
            }
            return SnowChange.NONE;
        }
        if (c.temperature() > MELT_ABOVE) {
            return switch (c.top()) {
                case SNOW -> SnowChange.REMOVE_SNOW_LAYER;
                case ICE -> c.supportedBelow() ? SnowChange.MELT_ICE_TO_WATER : SnowChange.MELT_ICE_TO_AIR;
                default -> SnowChange.NONE;
            };
        }
        return SnowChange.NONE;
    }

    /**
     * A snow layer can rest on the air above the top: a ground top, or a snow top at its full eight layers
     * (vanilla {@code SnowLayerBlock.canSurvive}). Ice does not hold snow in vanilla either.
     */
    private static boolean restsSnow(Column c) {
        return c.top() == Cover.GROUND || (c.top() == Cover.SNOW && c.snowLayers() == MAX_SNOW_LAYERS);
    }

    /**
     * The layer count after {@link SnowChange#REMOVE_SNOW_LAYER}: one less, and 0 means the block is removed.
     *
     * @param layers the current count, 1 to 8
     * @return {@code layers - 1}; 0 is "remove the block" (TFC {@code SnowPileBlock.removePileOrSnow})
     */
    public static int layersAfterRemove(int layers) {
        if (layers < 1 || layers > MAX_SNOW_LAYERS) {
            throw new IllegalArgumentException("snow layers must be 1 to " + MAX_SNOW_LAYERS + ": " + layers);
        }
        return layers - 1;
    }
}
