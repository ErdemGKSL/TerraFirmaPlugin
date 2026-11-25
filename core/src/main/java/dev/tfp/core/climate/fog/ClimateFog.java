/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java @ c3b4d9536
 * (getFog; also client/ClientForgeEventHandler.java onFogRender)
 */
package dev.tfp.core.climate.fog;

/**
 * PAR-CLIM-14: TFC's foggy mornings. One day in {@value #FOGGY_DAY_RARITY} is foggy (a calendar-day seeded roll), with a
 * random strength; the fog ramps in from 4 to 6 am, holds until 10 am and fades out by noon, scaled by the local rainfall
 * and the sky light. Pure and platform-free. The random source is a private copy of Minecraft's Xoroshiro draws, because
 * the climate package's one is package-private; {@code ClimateFogTest} pins it with the same golden values.
 */
public final class ClimateFog {
    /** {@code OverworldClimateModel.FOGGY_DAY_RARITY}. */
    public static final int FOGGY_DAY_RARITY = 10;
    /** {@code OverworldClimateModel.FOGGY_RAINFALL_MINIMUM} and {@code FOGGY_RAINFALL_PEAK}, mm/year. */
    public static final float FOGGY_RAINFALL_MINIMUM = 150f;
    public static final float FOGGY_RAINFALL_PEAK = 300f;
    /** The salt TFC's {@code getFog} passes to {@code seededRandom}. */
    public static final long FOG_SALT = 129341623413L;
    /** The client never pulls the far plane closer than {@code 1 - 0.86} of the render distance. */
    public static final float MAX_CLIENT_FOG = 0.86f;

    private static final long CALENDAR_TICKS_IN_DAY = 24_000L;

    private ClimateFog() {
    }

    /**
     * The day's fog strength roll, {@code 0} on a clear day, else the untransformed {@code fogModifier} in [0, 1).
     * Depends only on the climate seed and the calendar day.
     */
    public static float dayStrength(long climateSeed, long totalCalendarDays) {
        final Xoroshiro random = seededRandom(climateSeed, totalCalendarDays);
        if (random.nextInt(FOGGY_DAY_RARITY) != 0) {
            return 0f;
        }
        return random.nextFloat();
    }

    /** True when {@code totalCalendarDays} is a foggy day. */
    public static boolean isFoggyDay(long climateSeed, long totalCalendarDays) {
        return seededRandom(climateSeed, totalCalendarDays).nextInt(FOGGY_DAY_RARITY) == 0;
    }

    /** The time-of-day ramp before easing: 0 outside 4 am to noon, 1 from 6 to 10 am. */
    public static float timeRamp(float hourOfDay) {
        if (4 <= hourOfDay && hourOfDay < 6) {
            return map(hourOfDay, 4, 6, 0, 1);
        } else if (6 <= hourOfDay && hourOfDay < 10) {
            return 1;
        } else if (10 <= hourOfDay && hourOfDay < 12) {
            return map(hourOfDay, 10, 12, 1, 0);
        }
        return 0;
    }

    /** {@code Helpers.easeInOutCubic}. */
    public static float easeInOutCubic(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - cube(-2 * x + 2) / 2;
    }

    /**
     * TFC {@code getFog}: the fog scale in [0, 1) at a calendar instant and place.
     *
     * @param climateSeed     {@code OverworldClimate.climateSeed()}
     * @param calendarTicks   the calendar tick (non-negative)
     * @param instantRainfall the instant rainfall at the position, mm/year
     * @param skyLight        the sky light at the position, 0 to 15
     */
    public static float fog(long climateSeed, long calendarTicks, float instantRainfall, int skyLight) {
        return fogWithStrength(dayStrength(climateSeed, calendarTicks / CALENDAR_TICKS_IN_DAY), calendarTicks, instantRainfall,
            skyLight);
    }

    /** {@link #fog} for a given day roll (a debug override passes a foggy day of any strength; 0 is a clear day). */
    public static float fogWithStrength(float strength, long calendarTicks, float instantRainfall, int skyLight) {
        if (strength == 0f) {
            return 0f;
        }
        final float fractionOfDay = (float) (calendarTicks % CALENDAR_TICKS_IN_DAY) / CALENDAR_TICKS_IN_DAY;
        final float hourOfDay = 24 * fractionOfDay;
        final float scaledTime = timeRamp(hourOfDay);
        final float rainfallModifier = clampedMap(instantRainfall, FOGGY_RAINFALL_MINIMUM, FOGGY_RAINFALL_PEAK, 0, 1);
        final float skylightModifier = clampedMap(skyLight, 0f, 10f, 0f, 1f);
        return easeInOutCubic(scaledTime) * strength * rainfallModifier * skylightModifier;
    }

    /**
     * {@code ClientForgeEventHandler.onFogRender}: the terrain fog far plane for a fog scale, in blocks, or
     * {@code renderDistance} itself when there is no fog.
     */
    public static float farPlane(float renderDistance, float fog) {
        return fog == 0 ? renderDistance : renderDistance * (1 - Math.min(MAX_CLIENT_FOG, fog));
    }

    /** The near plane that goes with {@link #farPlane}: far minus {@code clamp(renderDistance / 10, 4, 64)}. */
    public static float nearPlane(float renderDistance, float fog) {
        final float far = farPlane(renderDistance, fog);
        return far - Math.max(4f, Math.min(64f, renderDistance / 10.0f));
    }

    /**
     * The substitute's quantised level, 0 (none) to {@code levels}: what a datapack fog variant set can express. 0 only
     * for fog 0; any fog above 0 is at least level 1.
     */
    public static int level(float fog, int levels) {
        if (fog <= 0f) {
            return 0;
        }
        return Math.max(1, Math.min(levels, Math.round(Math.min(fog, 1f) * levels)));
    }

    private static Xoroshiro seededRandom(long climateSeed, long day) {
        long seed = day;
        seed *= seed * 6364136223846793005L + 1442695040888963407L;
        return new Xoroshiro(seed + climateSeed, FOG_SALT);
    }

    private static float cube(float x) {
        return x * x * x;
    }

    private static float map(float value, float inMin, float inMax, float outMin, float outMax) {
        final float delta = (value - inMin) / (inMax - inMin);
        return outMin + delta * (outMax - outMin);
    }

    private static float clampedMap(float value, float inMin, float inMax, float outMin, float outMax) {
        final float delta = (value - inMin) / (inMax - inMin);
        if (delta < 0) {
            return outMin;
        }
        if (delta > 1) {
            return outMax;
        }
        return outMin + delta * (outMax - outMin);
    }

    /** Xoroshiro128++ with Minecraft's {@code nextInt(bound)} and {@code nextFloat}. */
    static final class Xoroshiro {
        private long lo;
        private long hi;

        Xoroshiro(long lo, long hi) {
            if ((lo | hi) == 0) {
                lo = -7046029254386353131L;
                hi = 7640891576956012809L;
            }
            this.lo = lo;
            this.hi = hi;
        }

        long nextLong() {
            final long s0 = lo;
            long s1 = hi;
            final long result = Long.rotateLeft(s0 + s1, 17) + s0;
            s1 ^= s0;
            lo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
            hi = Long.rotateLeft(s1, 28);
            return result;
        }

        int nextInt(int bound) {
            long m = Integer.toUnsignedLong((int) nextLong()) * bound;
            long low = m & 0xFFFF_FFFFL;
            if (low < bound) {
                final int threshold = Integer.remainderUnsigned(-bound, bound);
                while (low < threshold) {
                    m = Integer.toUnsignedLong((int) nextLong()) * bound;
                    low = m & 0xFFFF_FFFFL;
                }
            }
            return (int) (m >> 32);
        }

        float nextFloat() {
            return (float) (nextLong() >>> 40) * 0x1.0p-24f;
        }
    }
}
