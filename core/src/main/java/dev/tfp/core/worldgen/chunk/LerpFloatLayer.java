/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/chunkdata/LerpFloatLayer.java @ c3b4d9536 (the arithmetic of getValue, scaled,
 * apply; the NBT and network codecs are not ported, the chunk data is recomputed from the seed)
 */
package dev.tfp.core.worldgen.chunk;

import dev.tfp.core.worldgen.Mth;

/**
 * A square of floats known at the four corners of a unit square and interpolated bilinearly inside. The values are named
 * {@code valueXZ}, where {@code 0} and {@code 1} mark the low or high corner.
 */
public record LerpFloatLayer(float value00, float value01, float value10, float value11) {
    /** The value at {@code (deltaX, deltaZ)} in the unit square. */
    public float getValue(float deltaX, float deltaZ) {
        return lerp4(value00, value01, value10, value11, deltaX, deltaZ);
    }

    /**
     * The sub-square with origin {@code (originX, originZ)} and width {@code width}, as a new layer with its own corners.
     * Each corner is the parent's bilinear value there, so the sub-square interpolates to the same surface.
     */
    public LerpFloatLayer scaled(float originX, float originZ, float width) {
        return new LerpFloatLayer(
            lerp4(value00, value01, value10, value11, originX, originZ),
            lerp4(value00, value01, value10, value11, originX, originZ + width),
            lerp4(value00, value01, value10, value11, originX + width, originZ),
            lerp4(value00, value01, value10, value11, originX + width, originZ + width)
        );
    }

    /** A new layer with every corner clamped to {@code [min, max]}. */
    public LerpFloatLayer clamped(float min, float max) {
        return new LerpFloatLayer(
            Mth.clamp(value00, min, max),
            Mth.clamp(value01, min, max),
            Mth.clamp(value10, min, max),
            Mth.clamp(value11, min, max)
        );
    }

    /** {@code Helpers.lerp4}: the two-step bilinear interpolation, in the same operation order as TFC. */
    static float lerp4(float value00, float value01, float value10, float value11, float delta0, float delta1) {
        final float value0 = lerp(delta1, value00, value01);
        final float value1 = lerp(delta1, value10, value11);
        return lerp(delta0, value0, value1);
    }

    /** {@code Helpers.lerp(float, float, float)}: {@code min + (max - min) * delta}. */
    static float lerp(float delta, float min, float max) {
        return min + (max - min) * delta;
    }
}
