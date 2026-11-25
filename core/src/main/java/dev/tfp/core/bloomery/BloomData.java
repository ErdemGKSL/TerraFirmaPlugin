/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BloomBlockEntity.java @ c3b4d9536 (count, maxCount, getState)
 */
package dev.tfp.core.bloomery;

import dev.tfp.core.persist.VersionedCodec;

/**
 * The state of one bloom block (PAR-METAL-14): the number of bloom items left and the number it was made with. The bloom item
 * is the device record's slot 0.
 *
 * @param count    the items left, at least 0
 * @param maxCount the items the bloom was made with, at least {@code count}
 */
public record BloomData(int count, int maxCount) {
    public static final BloomData FRESH = new BloomData(0, 0);

    public static final VersionedCodec<BloomData> CODEC = VersionedCodec.<BloomData>builder("bloom", 1)
        .build((d, out) -> out.putInt("count", d.count()).putInt("maxCount", d.maxCount()),
            in -> new BloomData(in.getInt("count", 0), in.getInt("maxCount", 0)));

    public BloomData {
        if (count < 0 || maxCount < count) {
            throw new IllegalArgumentException("bad bloom count " + count + " of " + maxCount);
        }
    }

    /** TFC {@code setBloom}: a new bloom of {@code count} items. */
    public static BloomData made(int count) {
        return new BloomData(count, count);
    }

    /** TFC {@code dropBloom}: one item taken. */
    public BloomData dropped() {
        return new BloomData(Math.max(0, count - 1), maxCount);
    }

    /** The {@code layers} of the block for this count ({@link BloomeryRules#bloomLayers}), 0 when nothing is left. */
    public int layers() {
        return BloomeryRules.bloomLayers(count, maxCount);
    }
}
