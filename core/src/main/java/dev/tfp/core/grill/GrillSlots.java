/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/GrillBlock.java @ c3b4d9536 (SLOT_RENDER_SHAPES,
 *       SLOT_BOUNDS = bounds().inflate(0.01), SLOT_CENTERS, getSlotForSelection),
 *       src/main/java/net/dries007/tfc/client/render/blockentity/GrillBlockEntityRenderer.java @ c3b4d9536 (the item
 *       placement: SLOT_CENTERS + 0.003125 up, scale 0.3, x 90 then z 180)
 */
package dev.tfp.core.grill;

import java.util.List;

/**
 * Where the five grill slots sit on the block (PAR-HEAT-08): TFC's selection boxes, which slot a click at a point inside
 * the block picks, and the centre each item is drawn at. TFC does not turn them with the axis.
 */
public final class GrillSlots {
    /** An axis-aligned box in block-local coordinates (0..1). */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        /** Vanilla {@code AABB.inflate}. */
        public Box inflate(double d) {
            return new Box(minX - d, minY - d, minZ - d, maxX + d, maxY + d, maxZ + d);
        }

        /** Vanilla {@code AABB.contains(x, y, z)}: min inclusive, max exclusive. */
        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
        }
    }

    /** A point in block-local coordinates. */
    public record Point(double x, double y, double z) {
    }

    /** TFC {@code SLOT_RENDER_SHAPES}, index 0 = slot 4 ({@code SLOT_EXTRA_INPUT_START}) .. 4 = slot 8. */
    public static final List<Box> RENDER_SHAPES = List.of(
        new Box(0.2, 0.65, 0.2, 0.4, 0.7, 0.4),
        new Box(0.2, 0.6, 0.6, 0.4, 0.7, 0.8),
        new Box(0.6, 0.6, 0.2, 0.8, 0.7, 0.4),
        new Box(0.6, 0.6, 0.6, 0.8, 0.7, 0.8),
        new Box(0.4, 0.65, 0.4, 0.6, 0.7, 0.6));
    /** TFC {@code SLOT_BOUNDS}: the shapes grown by 0.01. */
    public static final List<Box> BOUNDS = RENDER_SHAPES.stream().map(b -> b.inflate(0.01)).toList();
    /** TFC {@code SLOT_CENTERS}: the centre of each bound with y = 0.625. */
    public static final List<Point> CENTERS = BOUNDS.stream()
        .map(b -> new Point((b.minX() + b.maxX()) / 2, 0.625, (b.minZ() + b.maxZ()) / 2)).toList();
    /** {@code GrillBlockEntityRenderer}: the item sits 0.003125 above its centre, scaled to 0.3. */
    public static final double ITEM_LIFT = 0.003125;
    public static final float ITEM_SCALE = 0.3f;
    /** TFC {@code GRILL_SHAPE} top: {@code box(2, 0, 2, 14, 11, 14)}, where a click from above lands. */
    public static final double TOP = 11 / 16.0;
    /** TFC {@code FirepitBlock.BASE_SHAPE} plate top: {@code box(0, 0, 0, 16, 2, 16)}, the rim around the grill's core. */
    public static final double BASE_TOP = 2 / 16.0;
    /** TFC {@code GRILL_SHAPE} core: {@code box(2, 0, 2, 14, 11, 14)} in x and z. */
    public static final double CORE_MIN = 2 / 16.0;
    public static final double CORE_MAX = 14 / 16.0;

    /**
     * Where a click from above at block-local {@code (x, z)} lands on TFC's grill shape: the 11 px core top, or the 2 px
     * base plate on the outer rim (x or z outside 2..14 px).
     */
    public static double topHitY(double x, double z) {
        boolean core = x >= CORE_MIN && x <= CORE_MAX && z >= CORE_MIN && z <= CORE_MAX;
        return core ? TOP : BASE_TOP;
    }

    /** TFC {@code tryInsertLog(..., hit.y - pos.y < 0.6)}: fuel goes in only on a hit below this height. */
    public static final double LOG_INSERT_BELOW = 0.6;

    private GrillSlots() {
    }

    /**
     * TFC {@code getSlotForSelection}: the grill slot (4 to 8) whose bound holds the hit point (block-local), or -1. TFC
     * walks a hash map of the slot numbers, which visits them in ascending order, so where two bounds overlap at a corner
     * the lower slot wins.
     */
    public static int slotAt(double x, double y, double z) {
        for (int i = 0; i < BOUNDS.size(); i++) {
            if (BOUNDS.get(i).contains(x, y, z)) {
                return GrillSim.SLOT_EXTRA_INPUT_START + i;
            }
        }
        return -1;
    }

    /** The centre of grill slot {@code slot} (4 to 8). */
    public static Point center(int slot) {
        if (!GrillSim.isGrillSlot(slot)) {
            throw new IllegalArgumentException("not a grill slot: " + slot);
        }
        return CENTERS.get(slot - GrillSim.SLOT_EXTRA_INPUT_START);
    }
}
