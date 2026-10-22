/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/IngotPileBlock.java @ c3b4d9536 (COUNT, SHAPES, getShape) and
 * src/main/java/net/dries007/tfc/common/blocks/devices/DoubleIngotPileBlock.java (DOUBLE_COUNT, SHAPES, getShape)
 */
package dev.tfp.core.pile;

/**
 * The two ingot pile kinds of TFC: the single ingot pile and the double ingot pile.
 *
 * <p>Each kind has a capacity (the largest count of its block state, {@code COUNT_1_64} and {@code COUNT_1_36}), and is built
 * in layers of {@link #perLayer()} ingots. The collision shape is one of {@link #layers()} boxes, chosen by the layer of the top
 * ingot, so the height rises by one layer per {@code perLayer} ingots.
 *
 * <p>The item tag is a caller fact: TFC's {@code Tags.Items.INGOTS} ({@code c:ingots}) for the single pile, and
 * {@code TFCTags.Items.DOUBLE_INGOTS} ({@code c:double_ingots}) for the double pile. The core does not check item tags.
 */
public enum PileKind {
    /** The single ingot pile: {@code tfc:ingot_pile}, 64 ingots in 8 layers of 8. */
    INGOT("c:ingots", 64, 8),
    /** The double ingot pile: {@code tfc:double_ingot_pile}, 36 ingots in 6 layers of 6. */
    DOUBLE_INGOT("c:double_ingots", 36, 6);

    /** The full collision height of a pile, in sixteenths of a block. */
    public static final int FULL_HEIGHT_SIXTEENTHS = 16;

    private final String itemTag;
    private final int capacity;
    private final int perLayer;

    PileKind(String itemTag, int capacity, int perLayer) {
        this.itemTag = itemTag;
        this.capacity = capacity;
        this.perLayer = perLayer;
    }

    /** The item tag of the ingots this pile accepts (a caller fact, see the class comment). */
    public String itemTag() {
        return itemTag;
    }

    /** The largest count of this pile (TFC {@code COUNT_1_64} and {@code COUNT_1_36}). */
    public int capacity() {
        return capacity;
    }

    /** The number of ingots in one layer. */
    public int perLayer() {
        return perLayer;
    }

    /** The number of layers, which is also the number of collision shapes (TFC {@code SHAPES.length}). */
    public int layers() {
        return capacity / perLayer;
    }

    /**
     * The collision shape index of a count, as TFC's {@code SHAPES[(count - 1) / perLayer]}.
     *
     * @param count a count in {@code 1..capacity}
     * @return the index in {@code 0..layers()-1}
     */
    public int shapeIndex(int count) {
        checkCount(count);
        return (count - 1) / perLayer;
    }

    /**
     * The 1-based layer of the ingot at a 0-based index, as TFC's rendering {@code layer = (i + perLayer) / perLayer}.
     * Odd layers are turned 90 degrees on the client; that is presentation and is not decided here.
     *
     * @param index a 0-based ingot index in {@code 0..capacity-1}
     * @return the layer in {@code 1..layers()}
     */
    public int layerOf(int index) {
        if (index < 0 || index >= capacity) {
            throw new IllegalArgumentException("ingot index out of range for " + this + ": " + index);
        }
        return (index + perLayer) / perLayer;
    }

    /**
     * The top of the collision box of a count, in sixteenths of a block. The single pile tops are 2, 4 .. 16 (TFC's {@code SHAPES});
     * the double pile tops are {@code 16 / 6} times the layer, ending at 16.
     *
     * @param count a count in {@code 1..capacity}
     */
    public double topSixteenths(int count) {
        int layer = shapeIndex(count) + 1;
        return layer * (double) FULL_HEIGHT_SIXTEENTHS / layers();
    }

    private void checkCount(int count) {
        if (count < 1 || count > capacity) {
            throw new IllegalArgumentException("count out of range for " + this + ": " + count);
        }
    }
}
