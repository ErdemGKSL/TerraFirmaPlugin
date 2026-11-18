/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/TFCColors.java @ c3b4d9536 (getClimateColor and the grass, foliage and
 * water lookups; the colormap index arithmetic only, the colours come from the colormap PNGs in tools/datagen; the
 * autumn and winter window of getSeasonalFoliageColor for the dormant shift).
 */
package dev.tfp.core.climate;

/**
 * M2-SEA-01 / PAR-CLIM-05: the colour band of a position, from TFC's colormap lookup.
 *
 * <p>TFC looks up grass, foliage and water colours in a 256 x 256 colormap: the temperature index runs along x
 * (warm to the left, index 0 at +30 C) and the groundwater index along y (wet at the top, index 0 at 500 mm and
 * more). The client shows the colour of the position's current climate, so a colour changes over the year as the
 * instant temperature and groundwater change. Our client sees biome ids, not colours, so the 256 x 256 indices are
 * grouped into {@value #TEMPERATURE_BANDS} temperature bands x {@value #RAINFALL_BANDS} rainfall bands (one datapack
 * biome variant per band, built by {@code tools/datagen}). The band colour is the colormap at the band's sample index.
 *
 * <p>PAR-CLIM-05 (owner decision delegated to the manager, 2026-11-18): option (a), more temperature bands. The first
 * layout had four temperature bands of 64 indices (about 12.5 C each), so 14.5 C (June) and 5.1 C (October) at one
 * site fell into the same band and showed the same colour. The temperature axis now has seven sample points, every 32
 * indices from 32 to 224 (about 6.3 C apart): the four first ones keep their index and their variant name
 * {@code t0..t3}, and three new ones sit half-way between them ({@code t0h}, {@code t1h}, {@code t2h}). A band holds
 * the indices nearest its sample point, except the edge between {@code t1h} (index 128) and {@code t2} (index 160),
 * which is at index {@value #FREEZING_INDEX}: TFC's temperature index of 0 C. So every band from {@code t2} on is at or
 * below 0 C, where TFC shows snow ({@link #isFreezing}). 7 x 4 = 28 variants per biome is 1.75 times the first 16, and
 * the client's biome registry stays below 4,096 entries (a 12-bit global palette).
 *
 * <p>PAR-CLIM-05 repair (owner criterion: October grass warmer and yellower than June at the same site): TFC's grass
 * colormap alone goes the other way, a colder reading at a fixed groundwater is a darker, bluer green. So the season is
 * an input of the band through the <b>dormant shift</b> (option (c), month/phase-based, no new variants): in the months
 * where TFC's leaves show their autumn or winter colour ({@link #dormant}, the window of TFC's
 * {@code getSeasonalFoliageColor}), a non-freezing band moves to the driest rainfall band ({@link #dormantBand}), the
 * straw yellow of TFC's grass colormap. At a site with 186 mm June {@code t1r1} {@code #70a353} becomes October
 * {@code t1hr3} {@code #fad259}. Evergreen climates (TFC's evergreen leaves) and snowy bands keep the plain band.
 */
public final class SeasonalColour {
    /** Temperature bands (colormap x axis). */
    public static final int TEMPERATURE_BANDS = 7;
    /** Rainfall (groundwater) bands (colormap y axis). */
    public static final int RAINFALL_BANDS = 4;
    /** Number of bands: one datapack biome variant per TFC biome and band. */
    public static final int BAND_COUNT = TEMPERATURE_BANDS * RAINFALL_BANDS;
    /**
     * TFC's temperature index of 0 C: {@code 255 - (int) (20 * 255 / 50)}. An index at or above it is a temperature
     * below about 0.2 C (index 153 holds 0 to 0.19 C, index 154 starts below 0 C).
     */
    public static final int FREEZING_INDEX = 153;

    /** Datapack namespace of the variants (our namespace). */
    public static final String NAMESPACE = "tfp";
    /** Path prefix of the variants: {@code tfp:seasonal/<biome>/<variant>}. */
    public static final String VARIANT_FOLDER = "seasonal/";

    private static final int MAX_INDEX = 255;

    /**
     * The first temperature index of each temperature band, warm first; band {@code t} covers
     * {@code [TEMPERATURE_BAND_START[t], TEMPERATURE_BAND_START[t + 1])}, the last one to 255. The edges lie half-way
     * between the sample points of {@link #TEMPERATURE_SAMPLE}, except the edge at {@link #FREEZING_INDEX}.
     */
    private static final int[] TEMPERATURE_BAND_START = {0, 48, 80, 112, FREEZING_INDEX, 176, 208};
    /** The temperature index each band's colour is read at: every 32 indices, 23.7 C down to -14.1 C. */
    private static final int[] TEMPERATURE_SAMPLE = {32, 64, 96, 128, 160, 192, 224};
    /** The variant name part of each temperature band: the first layout's {@code t0..t3}, and the half steps. */
    private static final String[] TEMPERATURE_NAME = {"t0", "t0h", "t1", "t1h", "t2", "t2h", "t3"};
    /** The first temperature band at or below 0 C. */
    private static final int FIRST_FREEZING_BAND = 4;

    /**
     * The first groundwater (rainfall) colormap index of each rainfall band, wet first; band {@code r} covers indices
     * {@code [RAINFALL_BAND_START[r], RAINFALL_BAND_START[r + 1])}, the last one to 255. The edges are chosen to follow
     * the colour map's change, not an even split: at -6 C TFC's grass goes from green to olive and yellow between about
     * 150 and 250 mm of groundwater (TFCColors.java:293-308 reads {@code colormap[temperature | rainfall << 8]} with
     * this index). An even split put the band centred at 186 mm in the green part, so a dry autumn stayed dark green.
     * The edges are the indices of 250, 150 and 75 mm (groundwater 251, 151 and 77 mm where the index steps):
     * band 0 is above about 250 mm, band 1 about 150 to 250 mm, band 2 about 75 to 150 mm, band 3 below about 75 mm.
     */
    private static final int[] RAINFALL_BAND_START = {0, 128, 179, 217};

    private SeasonalColour() {
    }

    /**
     * TFC's temperature index: {@code 255 - clamp((int) ((t + 20) * 255 / 50), 0, 255)}. Degrees Celsius; a NaN
     * temperature casts to 0 as in TFC, which gives the coldest index.
     */
    public static int temperatureIndex(float temperature) {
        return MAX_INDEX - clamp((int) ((temperature + 20f) * 255f / 50f));
    }

    /**
     * TFC's groundwater index: {@code 255 - clamp((int) (groundwater * 255 / 500), 0, 255)}. Millimetres of
     * instant groundwater (base groundwater plus instant rainfall, see {@link OverworldClimate#instantGroundwater}).
     */
    public static int rainfallIndex(float groundwater) {
        return MAX_INDEX - clamp((int) (groundwater * 255f / 500f));
    }

    /** The temperature band of a temperature index: the band {@code t} whose range holds it. Band 0 is the warmest. */
    public static int temperatureBand(int temperatureIndex) {
        return bandOf(TEMPERATURE_BAND_START, temperatureIndex);
    }

    /**
     * The rainfall band of a groundwater index: the band {@code r} whose range of {@link #RAINFALL_BAND_START} holds
     * {@code rainfallIndex}. Band 0 is the wettest (index 0 is 500 mm).
     */
    public static int rainfallBand(int rainfallIndex) {
        return bandOf(RAINFALL_BAND_START, rainfallIndex);
    }

    /**
     * The band of a position: {@code rainfallBand * TEMPERATURE_BANDS + temperatureBand}, in {@code [0, BAND_COUNT)}.
     * Band 0 is the hot and wet corner, band {@code BAND_COUNT - 1} the cold and dry one.
     */
    public static int band(float temperature, float groundwater) {
        int t = temperatureBand(temperatureIndex(temperature));
        int r = rainfallBand(rainfallIndex(groundwater));
        return r * TEMPERATURE_BANDS + t;
    }

    /**
     * The band of a position with the season: {@link #band(float, float)}, moved to the driest rainfall band by
     * {@link #dormantBand} when {@code dormant}.
     */
    public static int band(float temperature, float groundwater, boolean dormant) {
        int band = band(temperature, groundwater);
        return dormant ? dormantBand(band) : band;
    }

    /**
     * The band dormant grass shows instead of {@code band}: the same temperature band in the driest rainfall band, the
     * straw yellow of TFC's grass colormap (hue about 45 degrees in every non-freezing temperature band). The driest band
     * rather than one step drier, because a site's groundwater swings by more than a band over the year: a site with a
     * dry summer and a wet autumn (rainfall variance) would otherwise show a greener October than June. A freezing band
     * ({@link #isFreezing}) stays: grass under snow does not yellow.
     */
    public static int dormantBand(int band) {
        checkBand(band);
        if (isFreezing(band)) {
            return band;
        }
        return (RAINFALL_BANDS - 1) * TEMPERATURE_BANDS + band % TEMPERATURE_BANDS;
    }

    /**
     * Whether the grass of a column is dormant (shows its autumn and winter tone) at {@code fractionOfYear}: the window
     * in which TFC's {@code TFCColors.getSeasonalFoliageColor} shows the autumn or the winter leaf colour, i.e. not the
     * spring/summer colour. TFC's branches: a winter-controlled climate (average sea-level temperature at most 12 C) has
     * {@code x = 1.25 * max(t, -20) + 7.6} and the southern hemisphere a half-year offset; a dry-season climate
     * (above 12.5 C, rainfall variance beyond 0.4 and minimum rainfall at most 120 mm) has
     * {@code x = -0.2604 * (0.4 - |v|) * (120 - minRain) + 24.05} and a half-year offset for a negative variance;
     * anything else is evergreen (never dormant). With {@code a = x^3 / 4096 - x^2 / 256}, autumn starts at
     * {@code (a + 8.5) / 12} and spring at {@code 1 - (a + 10.5) / 12}. TFC's per-block hash jitter is left out (the
     * phase is a whole month).
     *
     * @param averageTemperature the column's annual average sea-level temperature, degrees Celsius
     * @param averageRainfall    the column's annual average rainfall, mm
     * @param rainfallVariance   the column's rainfall variance in [-1, 1]
     * @param northern           whether the column is in a northern hemisphere band
     * @param fractionOfYear     the calendar's fraction of the year, 0 at January 1
     */
    public static boolean dormant(float averageTemperature, float averageRainfall, float rainfallVariance, boolean northern,
            float fractionOfYear) {
        float temp = averageTemperature;
        float rainVarAbs = Math.abs(rainfallVariance);
        if (temp > 12f && rainVarAbs < 0.4f) {
            return false;
        }
        final float x;
        float seasonOffset = 0;
        if (temp <= 12f) {
            x = 1.25f * Math.max(temp, -20f) + 7.6f;
            if (!northern) {
                seasonOffset = 0.5f;
            }
        } else {
            final float minRain = averageRainfall * (1 - rainVarAbs);
            if (rainVarAbs > 0.4f && temp > 12.5f && minRain <= 120) {
                if (rainfallVariance < 0) {
                    seasonOffset = 0.5f;
                }
                x = -.2604f * (0.4f - rainVarAbs) * (120f - minRain) + 18.75f + 5.3f;
            } else {
                return false;
            }
        }
        final float cubedTerm = x * x * x / 4096;
        final float squaredTerm = x * x / 256;
        final float timeOfYear = (1 + fractionOfYear + seasonOffset) % 1;
        final float autumnStart = (cubedTerm - squaredTerm + 8.5f) / 12f;
        final float autumnEnd = (cubedTerm - squaredTerm + 10.5f) / 12f;
        final float springStart = 1f - autumnEnd;
        return timeOfYear > autumnStart || timeOfYear <= springStart;
    }

    /** The temperature index the colour of {@code band} is read at, for the colormap lookup in the datagen. */
    public static int centreTemperatureIndex(int band) {
        checkBand(band);
        return TEMPERATURE_SAMPLE[band % TEMPERATURE_BANDS];
    }

    /**
     * The groundwater index at the middle of the rainfall range of {@code band}, for the colormap lookup in the datagen.
     * The rainfall bands are uneven (see {@link #RAINFALL_BAND_START}), so the centre is the middle of that band's range:
     * 63, 153, 197 and 236 for rainfall bands 0 to 3.
     */
    public static int centreRainfallIndex(int band) {
        checkBand(band);
        int r = band / TEMPERATURE_BANDS;
        int first = RAINFALL_BAND_START[r];
        int last = (r + 1 < RAINFALL_BANDS ? RAINFALL_BAND_START[r + 1] : MAX_INDEX + 1) - 1;
        return (first + last) / 2;
    }

    /**
     * Whether every temperature of {@code band} is at or below about 0 C (temperature index {@value #FREEZING_INDEX}
     * or more), where TFC's {@code WeatherHelpers.getPrecipitationAt} shows snow instead of rain.
     */
    public static boolean isFreezing(int band) {
        checkBand(band);
        return band % TEMPERATURE_BANDS >= FIRST_FREEZING_BAND;
    }

    /**
     * The variant name of {@code band} within its biome: {@code <temperature part>r<rainfall band>}, e.g. {@code t1r2}
     * or {@code t1hr2} (the half step between {@code t1} and {@code t2}).
     */
    public static String variantName(int band) {
        checkBand(band);
        return TEMPERATURE_NAME[band % TEMPERATURE_BANDS] + "r" + (band / TEMPERATURE_BANDS);
    }

    /** The datapack biome path of {@code biomePath}'s variant in {@code band}: {@code seasonal/<biome>/<variant>}. */
    public static String variantPath(String biomePath, int band) {
        return VARIANT_FOLDER + biomePath + "/" + variantName(band);
    }

    /** The full datapack biome id of {@code biomePath}'s variant in {@code band}: {@code tfp:seasonal/<biome>/<variant>}. */
    public static String variantId(String biomePath, int band) {
        return NAMESPACE + ":" + variantPath(biomePath, band);
    }

    private static int bandOf(int[] starts, int index) {
        int b = 0;
        while (b + 1 < starts.length && index >= starts[b + 1]) {
            b++;
        }
        return b;
    }

    private static int clamp(int index) {
        return Math.clamp(index, 0, MAX_INDEX);
    }

    private static void checkBand(int band) {
        if (band < 0 || band >= BAND_COUNT) {
            throw new IllegalArgumentException("band out of range: " + band);
        }
    }
}
