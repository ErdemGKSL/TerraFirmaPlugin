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
 * into {@value #BANDS_PER_AXIS} x {@value #BANDS_PER_AXIS} bands of 64 indices each (datapack biome variant per band,
 * built by {@code tools/datagen}). The band colour is the colormap at the band's centre index.
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
    /** Colormap indices per band along one axis. */
    public static final int INDICES_PER_BAND = 256 / BANDS_PER_AXIS;

    /** Datapack namespace of the variants (our namespace). */
    public static final String NAMESPACE = "tfp";
    /** Path prefix of the variants: {@code tfp:seasonal/<biome>/<variant>}. */
    public static final String VARIANT_FOLDER = "seasonal/";

    private static final int MAX_INDEX = 255;

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
     * The band of a position: {@code rainfallBand * BANDS_PER_AXIS + temperatureBand}, in {@code [0, BAND_COUNT)}.
     * Band 0 is the hot and dry corner, band {@code BAND_COUNT - 1} the cold and wet one.
     */
    public static int band(float temperature, float groundwater) {
        int t = temperatureIndex(temperature) / INDICES_PER_BAND;
        int r = rainfallIndex(groundwater) / INDICES_PER_BAND;
        return r * BANDS_PER_AXIS + t;
    }

    /** The temperature index at the centre of {@code band}, for the colormap lookup in the datagen. */
    public static int centreTemperatureIndex(int band) {
        checkBand(band);
        return (band % BANDS_PER_AXIS) * INDICES_PER_BAND + INDICES_PER_BAND / 2;
    }

    /** The groundwater index at the centre of {@code band}, for the colormap lookup in the datagen. */
    public static int centreRainfallIndex(int band) {
        checkBand(band);
        return (band / BANDS_PER_AXIS) * INDICES_PER_BAND + INDICES_PER_BAND / 2;
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
