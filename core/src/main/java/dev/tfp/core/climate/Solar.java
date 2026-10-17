/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/overworld/SolarCalculator.java @ c3b4d9536
 * Also: src/main/java/net/dries007/tfc/client/overworld/SkyPos.java (the sun position record)
 */
package dev.tfp.core.climate;

/**
 * Sun position and the vanilla-scaled "sun based day time" that TFC's temperature model uses for the daily cycle.
 * The trigonometry follows TFC: {@code Mth.sin} is a lookup table, {@code Math} is used elsewhere, and the cosine comes
 * from JOML's sign-from-quadrant helper.
 */
public final class Solar {
    /**
     * A sun position in radians (TFC's {@code SkyPos}).
     *
     * @param zenith  the solar zenith angle; above {@code HALF_PI} the sun is below the horizon
     * @param azimuth the solar azimuth angle
     */
    public record SunPosition(float zenith, float azimuth) {}

    private Solar() {}

    /**
     * The latitude at {@code z}, in {@code [-pi/2, pi/2]}. Poles and equator are a triangle wave of the z coordinate with
     * period {@code 2 * hemisphereScale}.
     */
    public static float latitude(int z, float hemisphereScale) {
        return TfcMath.triangle(-TfcMath.HALF_PI, 0, 1 / (4 * hemisphereScale), z - 0.5f * hemisphereScale);
    }

    /**
     * The sun's position at {@code z} for the given fraction of the year (January 1 is 0) and of the day (midnight is 0).
     */
    public static SunPosition sunPosition(int z, float hemisphereScale, float fractionOfYear, float fractionOfDay) {
        final double latitude = latitude(z, hemisphereScale);
        // Approximation based on sin with amplitude of 23.44 degrees
        final double declination = 23.44f * TfcMath.DEG_TO_RAD * TfcMath.sin(TfcMath.TWO_PI * (284f / 365f + fractionOfYear));
        // Zero at solar noon (fractionOfDay = 0.5)
        final double hourAngle = TfcMath.TWO_PI * (0.5f - fractionOfDay);

        final double sinL = Math.sin(latitude);
        final double cosL = TfcMath.cosFromSin(sinL, latitude);
        final double sinD = Math.sin(declination);
        final double cosD = TfcMath.cosFromSin(sinD, declination);

        // cos(z) = sin(L)sin(d) + cos(L)cos(d)cos(h)
        final double solarZenithAngle = Math.acos(TfcMath.clamp(sinL * sinD + cosL * cosD * Math.cos(hourAngle), -1, 1));

        final double sinZ = Math.sin(solarZenithAngle);
        final double cosZ = TfcMath.cosFromSin(sinZ, solarZenithAngle);

        // cos(a) = (sin(d) - cos(z)sin(L)) / (sin(z)cos(L)), in [0, 180] before noon and [180, 360] after
        final double absSolarAzimuthAngle = Math.acos(TfcMath.clamp((sinD - cosZ * sinL) / (sinZ * cosL), -1, 1));
        final double solarAzimuthAngle = hourAngle < 0 ? absSolarAzimuthAngle : TfcMath.TWO_PI - absSolarAzimuthAngle;

        return new SunPosition((float) solarZenithAngle, (float) solarAzimuthAngle);
    }

    /**
     * A day time on the vanilla scale (noon = 6000, midnight = 18000, range [0, 24000)), derived from the sun's zenith
     * angle. Sunrise is 0 or 24000, sunset 12000. Always scaled to 24000 whatever the real day length.
     */
    public static int sunBasedDayTime(int z, float hemisphereScale, float fractionOfYear, float fractionOfDay) {
        final float zenith = sunPosition(z, hemisphereScale, fractionOfYear, fractionOfDay).zenith();
        if (fractionOfDay < 0.5) {
            // Midnight -> Noon
            if (zenith > TfcMath.HALF_PI) {
                // Midnight -> Sunrise
                final float minZenith = sunPosition(z, hemisphereScale, fractionOfYear, 0f).zenith();
                return (int) TfcMath.clampedMap(zenith, minZenith, TfcMath.HALF_PI, 18_000, 24_000);
            } else {
                // Sunrise -> Noon
                final float maxZenith = sunPosition(z, hemisphereScale, fractionOfYear, 0.5f).zenith();
                return (int) TfcMath.clampedMap(zenith, TfcMath.HALF_PI, maxZenith, 0, 6_000);
            }
        } else {
            // Noon -> Midnight
            if (zenith < TfcMath.HALF_PI) {
                // Noon -> Sunset
                final float maxZenith = sunPosition(z, hemisphereScale, fractionOfYear, 0.5f).zenith();
                return (int) TfcMath.clampedMap(zenith, maxZenith, TfcMath.HALF_PI, 6_000, 12_000);
            } else {
                // Sunset -> Midnight
                final float minZenith = sunPosition(z, hemisphereScale, fractionOfYear, 1f).zenith();
                return (int) TfcMath.clampedMap(zenith, TfcMath.HALF_PI, minZenith, 12_000, 18_000);
            }
        }
    }
}
