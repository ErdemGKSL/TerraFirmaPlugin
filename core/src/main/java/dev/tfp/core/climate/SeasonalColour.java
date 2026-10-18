/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/TFCColors.java @ c3b4d9536 (getClimateColor and the grass, foliage and
 * water lookups; the colormap index arithmetic only, the colours come from the colormap PNGs in tools/datagen).
 */
package dev.tfp.core.climate;

/**
 * M2-SEA-01: the colour band of a position, from TFC's colormap lookup.
 *
 * <p>TFC looks up grass, foliage and water colours in a 256 x 256 colormap: the temperature index runs along x
 * (warm to the left, index 0 at +30 C) and the groundwater index along y (wet at the top, index 0 at 500 mm and
 * more). The client shows the colour of the position's current climate, so a colour changes over the year as the
 * instant temperature and groundwater change. Our client sees biome ids, not colours, so the 256 indices are grouped
 * into {@value #BANDS_PER_AXIS} x {@value #BANDS_PER_AXIS} bands (datapack biome variant per band, built by
 * {@code tools/datagen}). The temperature axis has equal bands of 64 indices; the groundwater (rainfall) axis has
 * uneven bands whose edges follow the colour map's change (see {@link #RAINFALL_BAND_START}). The band colour is the
 * colormap at the band's centre index.
 *
 * <p>The season is not an input of the band. TFC's grass and foliage colours take no season: the season is in the
 * instant temperature and groundwater the calendar gives at the time. The season is the trigger for resending chunks
 * (M2-SEA-01 step 4), not part of the colour.
 */
public final class SeasonalColour {
    /** Bands along each colormap axis. */
    public static final int BANDS_PER_AXIS = 4;
    /** Number of bands: one datapack biome variant per TFC biome and band. */
    public static final int BAND_COUNT = BANDS_PER_AXIS * BANDS_PER_AXIS;
    /** Colormap indices per temperature band (the temperature axis is even; rainfall bands are not, see below). */
    public static final int INDICES_PER_BAND = 256 / BANDS_PER_AXIS;

    /** Datapack namespace of the variants (our namespace). */
    public static final String NAMESPACE = "tfp";
    /** Path prefix of the variants: {@code tfp:seasonal/<biome>/<variant>}. */
    public static final String VARIANT_FOLDER = "seasonal/";

    private static final int MAX_INDEX = 255;

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

    /**
     * The rainfall band of a groundwater index: the band {@code r} whose range of {@link #RAINFALL_BAND_START} holds
     * {@code rainfallIndex}. Band 0 is the wettest (index 0 is 500 mm).
     */
    public static int rainfallBand(int rainfallIndex) {
        int r = 0;
        while (r + 1 < BANDS_PER_AXIS && rainfallIndex >= RAINFALL_BAND_START[r + 1]) {
            r++;
        }
        return r;
    }

    /**
     * The band of a position: {@code rainfallBand * BANDS_PER_AXIS + temperatureBand}, in {@code [0, BAND_COUNT)}.
     * Band 0 is the hot and wet corner, band {@code BAND_COUNT - 1} the cold and dry one.
     */
    public static int band(float temperature, float groundwater) {
        int t = temperatureIndex(temperature) / INDICES_PER_BAND;
        int r = rainfallBand(rainfallIndex(groundwater));
        return r * BANDS_PER_AXIS + t;
    }

    /** The temperature index at the centre of {@code band}, for the colormap lookup in the datagen. */
    public static int centreTemperatureIndex(int band) {
        checkBand(band);
        return (band % BANDS_PER_AXIS) * INDICES_PER_BAND + INDICES_PER_BAND / 2;
    }

    /**
     * The groundwater index at the middle of the rainfall range of {@code band}, for the colormap lookup in the datagen.
     * The rainfall bands are uneven (see {@link #RAINFALL_BAND_START}), so the centre is the middle of that band's range:
     * 63, 153, 197 and 236 for rainfall bands 0 to 3.
     */
    public static int centreRainfallIndex(int band) {
        checkBand(band);
        int r = band / BANDS_PER_AXIS;
        int first = RAINFALL_BAND_START[r];
        int last = (r + 1 < BANDS_PER_AXIS ? RAINFALL_BAND_START[r + 1] : MAX_INDEX + 1) - 1;
        return (first + last) / 2;
    }

    /** The variant name of {@code band} within its biome: {@code t<temperature band>r<rainfall band>}. */
    public static String variantName(int band) {
        checkBand(band);
        return "t" + (band % BANDS_PER_AXIS) + "r" + (band / BANDS_PER_AXIS);
    }

    /** The datapack biome path of {@code biomePath}'s variant in {@code band}: {@code seasonal/<biome>/<variant>}. */
    public static String variantPath(String biomePath, int band) {
        return VARIANT_FOLDER + biomePath + "/" + variantName(band);
    }

    /** The full datapack biome id of {@code biomePath}'s variant in {@code band}: {@code tfp:seasonal/<biome>/<variant>}. */
    public static String variantId(String biomePath, int band) {
        return NAMESPACE + ":" + variantPath(biomePath, band);
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
