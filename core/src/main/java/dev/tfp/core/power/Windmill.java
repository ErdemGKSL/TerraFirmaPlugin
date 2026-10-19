/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536 (SLOTS, MIN_SPEED, MAX_SPEED,
 *       LERP_SPEED lines 34-37, the 40 tick sample line 49, targetBeforeWind line 73, windFactor line 77, targetSpeed and ramp lines
 *       79-83, the obstruction disk lines 93-114)
 */
package dev.tfp.core.power;

import dev.tfp.core.rotation.SourceSpeed;
import dev.tfp.core.worldgen.Mth;

/**
 * The windmill's speed and obstruction, as pure functions of its blade count and the wind magnitude the caller reads from the
 * climate. The wind itself is {@link WindSpeed}. Obstruction offsets use the same plane convention as {@link WaterWheel}.
 */
public final class Windmill {
    private Windmill() {}

    /** TFC {@code SLOTS}: the most blades a windmill takes, and the top of the COUNT property. */
    public static final int SLOTS = 5;

    /** TFC {@code MIN_SPEED = Mth.TWO_PI / (20 * 20)}: the speed added once there are two or more blades. */
    public static final float MIN_SPEED = (float) (2 * Math.PI) / (20 * 20);

    /** TFC {@code MAX_SPEED = Mth.TWO_PI / (8 * 20) - MIN_SPEED}: the blade-count span of the speed. */
    public static final float MAX_SPEED = (float) (2 * Math.PI) / (8 * 20) - MIN_SPEED;

    /** TFC {@code LERP_SPEED = MIN_SPEED / (5 * 20)}: the largest change in speed per tick. */
    public static final float LERP_SPEED = MIN_SPEED / (5 * 20);

    /** TFC server tick: the obstruction is checked once every 40 game ticks (line 49). */
    public static final int SAMPLE_INTERVAL_TICKS = 40;

    /** The wind speed above which the windmill stops gaining (TFC {@code Math.min(wind, 0.5f)}, line 77): about 57 km/h. */
    public static final float WIND_CAP = 0.5f;

    /** TFC {@code * 4f} on the capped wind (line 77). */
    public static final float WIND_GAIN = 4f;

    /** The radius of the obstruction disk: offsets with {@code dH^2 + dy^2 < 7^2} are scanned (line 102). */
    public static final int OBSTRUCTION_RADIUS = 7;

    /** The window for the obstruction scan, {@code -6..6} on both axes; the disk is cut down by the radius test. */
    static final int SCAN_EXTENT = 6;

    /**
     * Whether the cell at offset {@code (dH, dy)} is a solid block (any block with a non-empty collision shape that is not air).
     * The centre is never asked.
     */
    @FunctionalInterface
    public interface SolidAt {
        boolean solid(int dH, int dy);
    }

    /**
     * The speed a windmill with {@code count} blades reaches before wind is applied. TFC: {@code Mth.map(count, 1, SLOTS, 0, MAX_SPEED)}
     * plus {@link #MIN_SPEED} when there is more than one blade (line 73). A single blade therefore targets zero speed.
     *
     * @param count the number of blades, 1 to {@link #SLOTS}
     * @throws IllegalArgumentException if {@code count} is outside 1 to {@link #SLOTS}
     */
    public static float targetBeforeWind(int count) {
        if (count < 1 || count > SLOTS) {
            throw new IllegalArgumentException("count out of range: " + count);
        }
        return Mth.map((float) count, 1f, (float) SLOTS, 0f, MAX_SPEED) + (count > 1 ? MIN_SPEED : 0f);
    }

    /** TFC {@code Math.min(wind, 0.5f) * 4f} (line 77). Ranges over [0, 2]. */
    public static float windFactor(float wind) {
        return Math.min(wind, WIND_CAP) * WIND_GAIN;
    }

    /** The target speed: {@code windFactor(wind) * targetBeforeWind(count)} (line 79). */
    public static float targetSpeed(int count, float wind) {
        return windFactor(wind) * targetBeforeWind(count);
    }

    /**
     * One tick of the windmill's speed: an obstructed windmill is stopped at once, otherwise the speed ramps toward the target by
     * at most {@link #LERP_SPEED} (TFC lines 69-82 for the same ramp, through {@link SourceSpeed#ramp}).
     */
    public static float step(float current, float target, boolean obstructed) {
        return obstructed ? 0f : SourceSpeed.ramp(current, target, LERP_SPEED);
    }

    /**
     * Whether any solid block lies in the obstruction disk: offsets {@code -6..6} on both axes, with {@code dH^2 + dy^2 < 49},
     * excluding the centre (TFC {@code isObstructedBySolidBlocks}, lines 93-114).
     */
    public static boolean isObstructed(SolidAt solid) {
        for (int dH = -SCAN_EXTENT; dH <= SCAN_EXTENT; dH++) {
            for (int dy = -SCAN_EXTENT; dy <= SCAN_EXTENT; dy++) {
                if (dH * dH + dy * dy < OBSTRUCTION_RADIUS * OBSTRUCTION_RADIUS && (dH != 0 || dy != 0) && solid.solid(dH, dy)) {
                    return true;
                }
            }
        }
        return false;
    }
}
