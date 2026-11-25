/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/IngotPileBlockModel.java @ c3b4d9536 (render: layer, x, y, z of ingot i),
 *       src/main/java/net/dries007/tfc/client/model/DoubleIngotPileBlockModel.java @ c3b4d9536 (render)
 */
package dev.tfp.core.pile;

/**
 * Where the ingot at an index lies in its block: TFC's client model code ({@code IngotPileBlockModel.render}), pure. Ingots
 * fill a layer in rows (4 a row, two rows for the single pile; 3 a row, two rows for the double), the layers stack upward, and
 * odd layers (the first included) are turned 90 degrees about the block's vertical axis. Offsets are in blocks, from the corner
 * of the block, before the turn.
 */
public final class PileLayout {
    private PileLayout() {}

    /**
     * The place of one ingot.
     *
     * @param x       offset along x, before the turn
     * @param y       offset up
     * @param z       offset along z, before the turn
     * @param rotated whether the ingot's layer is turned 90 degrees about the block's centre (odd layers)
     */
    public record Placement(double x, double y, double z, boolean rotated) {}

    /** Ingots in a row of a layer: {@code i % 4} for the single pile, {@code i % 3} for the double. */
    public static int columns(PileKind kind) {
        return switch (kind) {
            case INGOT -> 4;
            case DOUBLE_INGOT -> 3;
        };
    }

    /** The distance between two ingots of a row: {@code 0.25f} and {@code 0.33f}. */
    public static double columnStep(PileKind kind) {
        return switch (kind) {
            case INGOT -> 0.25;
            case DOUBLE_INGOT -> 0.33;
        };
    }

    /** The height of one layer in blocks: {@code 0.125} (one eighth) and one sixth. */
    public static double layerHeight(PileKind kind) {
        return switch (kind) {
            case INGOT -> 0.125;
            case DOUBLE_INGOT -> 1.0 / 6;
        };
    }

    /** The second row of a layer starts half a block along z. */
    public static final double ROW_OFFSET = 0.5;

    /**
     * The place of the ingot at {@code index}.
     *
     * @param index a 0-based ingot index in {@code 0..capacity-1}
     */
    public static Placement of(PileKind kind, int index) {
        int layer = kind.layerOf(index);
        double x = (index % columns(kind)) * columnStep(kind);
        double y = (layer - 1) * layerHeight(kind);
        double z = index % kind.perLayer() >= columns(kind) ? ROW_OFFSET : 0;
        return new Placement(x, y, z, layer % 2 == 1);
    }

    /** The place of a whole layer as one unit (the ingots at their first-ingot offsets are inside the layer model). */
    public static Placement ofLayer(PileKind kind, int layer) {
        if (layer < 1 || layer > kind.layers()) {
            throw new IllegalArgumentException("layer out of range for " + kind + ": " + layer);
        }
        return new Placement(0, (layer - 1) * layerHeight(kind), 0, layer % 2 == 1);
    }
}
