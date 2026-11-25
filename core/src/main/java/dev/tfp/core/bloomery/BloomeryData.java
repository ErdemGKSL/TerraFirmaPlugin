/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BloomeryBlockEntity.java @ c3b4d9536 (litTick, the lit state)
 */
package dev.tfp.core.bloomery;

import dev.tfp.core.persist.VersionedCodec;

/**
 * The state of one bloomery (PAR-METAL-14): TFC's {@code litTick} and the recipe duration fixed when it was lit. The
 * contents are the device record's slots, one unit each, in TFC's {@code inputStacks} order.
 *
 * @param litTick  the calendar tick the bloomery was lit at ({@code 0} while unlit)
 * @param duration the burn time of the recipe it was lit for, {@code 0} while unlit
 */
public record BloomeryData(long litTick, int duration) {
    /** A new bloomery: unlit. */
    public static final BloomeryData FRESH = new BloomeryData(0L, 0);

    public static final VersionedCodec<BloomeryData> CODEC = VersionedCodec.<BloomeryData>builder("bloomery", 1)
        .build((d, out) -> out.putLong("litTick", d.litTick()).putInt("duration", d.duration()),
            in -> new BloomeryData(in.getLong("litTick", 0L), in.getInt("duration", 0)));

    public BloomeryData {
        if (duration < 0) {
            throw new IllegalArgumentException("duration must be >= 0: " + duration);
        }
    }

    /** Whether the bloomery is lit (the block shows {@code lit=true}). */
    public boolean lit() {
        return duration > 0;
    }

    public BloomeryData lit(long tick, int recipeDuration) {
        return new BloomeryData(tick, recipeDuration);
    }

    public BloomeryData unlit() {
        return FRESH;
    }

    /** The tick the recipe completes at; only meaningful while {@link #lit}. */
    public long finishTick() {
        return litTick + duration;
    }
}
