/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536 (MIN_EXTENSION,
 *       MAX_EXTENSION, getExtensionLength, doPush receiver positions),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/IBellowsConsumer.java @ c3b4d9536 (registerDefaultOffsets)
 */
package dev.tfp.core.bellows;

import java.util.List;

/**
 * The geometry and the stroke of a bellows, as pure rules: where the air goes and how far the bellows is pressed
 * {@code time} ticks after a push.
 */
public final class BellowsMotion {
    /** {@code BellowsBlockEntity.MIN_EXTENSION}. */
    public static final float MIN_EXTENSION = 0.125f;
    /** {@code BellowsBlockEntity.MAX_EXTENSION}. */
    public static final float MAX_EXTENSION = 0.625f;
    /** The ticks of one hand push stroke (press 10 ticks, release 10 ticks). */
    public static final int STROKE_TICKS = 20;

    /**
     * {@code IBellowsConsumer.Offset}: {@code out} blocks straight from the bellows output face, {@code side} blocks to
     * the clockwise side (seen from above) and {@code up} blocks up.
     */
    public record Offset(int out, int side, int up) {
        /** The block offset {@code {dx, dy, dz}} from the bellows for a bellows facing {@code facing}. */
        public int[] delta(String facing) {
            int fx = stepX(facing);
            int fz = stepZ(facing);
            // Direction.getClockWise (looking down): north -> east -> south -> west -> north: (x, z) -> (-z, x)
            int cx = -fz;
            int cz = fx;
            return new int[] {out * fx + side * cx, up, out * fz + side * cz};
        }
    }

    /** {@code registerDefaultOffsets}: the blast furnace (1, 0, 0) and the charcoal forge (1, 0, -1). */
    public static final List<Offset> DEFAULT_OFFSETS = List.of(new Offset(1, 0, 0), new Offset(1, 0, -1));

    private BellowsMotion() {
    }

    public static int stepX(String facing) {
        return switch (facing) {
            case "east" -> 1;
            case "west" -> -1;
            default -> 0;
        };
    }

    public static int stepZ(String facing) {
        return switch (facing) {
            case "south" -> 1;
            case "north" -> -1;
            default -> 0;
        };
    }

    /**
     * TFC {@code getExtensionLength} without a crankshaft: the stroke follows the last push. {@code now - lastPushed}
     * under 10: it grows by 0.05 a tick from {@link #MIN_EXTENSION}; under 20 it shrinks back; later it rests.
     */
    public static float extension(long now, long lastPushed) {
        int time = (int) (now - lastPushed);
        if (time < 0) {
            return MIN_EXTENSION;
        }
        if (time < 10) {
            return time * 0.05f + MIN_EXTENSION;
        }
        if (time < STROKE_TICKS) {
            return (STROKE_TICKS - time) * 0.05f + MIN_EXTENSION;
        }
        return MIN_EXTENSION;
    }
}
