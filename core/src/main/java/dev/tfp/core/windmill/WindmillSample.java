/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536 (serverTick lines
 *       39-58: clientTick first, then the obstruction check every 40 ticks; clientTick lines 60-85; the saved field
 *       obstructed)
 */
package dev.tfp.core.windmill;

import dev.tfp.core.power.Windmill;
import dev.tfp.core.rotation.SourceSpeed;

/**
 * The state of one windmill between two obstruction checks, and its 40-tick step. TFC ramps the speed every tick toward
 * {@link Windmill#targetSpeed} of the blade count and the wind (held at 0 while obstructed) and checks the obstruction disk
 * every {@link Windmill#SAMPLE_INTERVAL_TICKS} ticks. Here the ramp over the ticks between two checks is one closed-form
 * step with the blade count and the wind read at the check ({@link #advance}).
 *
 * @param speed      the windmill's speed, radians per tick (TFC {@code node.rotation().speed()}), never negative
 * @param obstructed whether the last check found a solid block in the disk (TFC {@code obstructed})
 */
public record WindmillSample(float speed, boolean obstructed) {
    /** A new windmill: at rest and not obstructed (TFC constructor). */
    public static final WindmillSample FRESH = new WindmillSample(0f, false);

    /**
     * The state after {@code ticks} ticks of TFC {@code clientTick} with {@code count} blades in a wind of {@code wind}: held at
     * 0 while obstructed, otherwise ramped toward the target by at most {@code ticks * LERP_SPEED}.
     *
     * @param count the blade count, 1 to {@link Windmill#SLOTS} (clamped)
     * @param wind  the wind magnitude (TFC {@code getWind(level, pos).length()})
     */
    public WindmillSample advance(long ticks, int count, float wind) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks < 0: " + ticks);
        }
        if (obstructed) {
            return new WindmillSample(0f, true);
        }
        if (ticks == 0) {
            return this;
        }
        float target = Windmill.targetSpeed(Math.clamp(count, 1, Windmill.SLOTS), wind);
        return new WindmillSample(SourceSpeed.ramp(speed, target, Windmill.LERP_SPEED * ticks), false);
    }

    /**
     * One check: the ramp of the {@code elapsed} ticks since the last one, then TFC's obstruction test.
     *
     * @param obstructedNow {@link Windmill#isObstructed} of the disk now, or {@code null} when the disk could not be read
     *                      completely (a cell in a chunk that is not loaded); the flag then stays
     */
    public WindmillSample sample(long elapsed, int count, float wind, Boolean obstructedNow) {
        WindmillSample after = advance(elapsed, count, wind);
        return obstructedNow == null ? after : new WindmillSample(after.speed, obstructedNow);
    }
}
