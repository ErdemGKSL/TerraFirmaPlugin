/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.calendar.Season;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.SeasonalColour;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.paper.worldgen.BiomeKeys;
import java.util.Locale;
import java.util.Optional;

/**
 * M2-SEA-01/02, decision only: which seasonal biome variant a chunk's biome palette should carry. Pure, so it runs on the
 * CPU executor and never touches the world.
 *
 * <p>The packet rewrite (M2-SEA-02) works on <b>indices</b>, not strings: a variant is
 * {@code tfcBiome * BAND_COUNT + band} in {@code [0, VARIANT_COUNT)}, and a server-side base biome (the vanilla placeholder
 * of {@link BiomeKeys}) is its index in {@link BiomeKeys#VANILLA}. The packet thread maps both to the client's network ids
 * once per user ({@code SeasonalBiomeHandler}).
 *
 * <p>The climate of a season is sampled at one fixed instant: the middle day of the season's middle month (April, July,
 * October, January) at noon, in the first calendar year. So a chunk shows one colour per season, the same every year,
 * and the chunk only changes at a season change (when the chunks are resent). The southern hemisphere gets its opposite
 * colour from the climate model's latitude term, not from the season name.
 */
public final class SeasonalBiomeRemap {
    /** Number of variant indices: one per TFC biome and colour band. */
    public static final int VARIANT_COUNT = TfcBiomes.COUNT * SeasonalColour.BAND_COUNT;
    /** Number of base (vanilla placeholder) indices. */
    public static final int BASE_COUNT = BiomeKeys.VANILLA.size();

    private static final Month[] MIDDLE_MONTH = new Month[Season.values().length];
    private static final byte[] BASE_OF_BIOME = new byte[TfcBiomes.COUNT];

    static {
        MIDDLE_MONTH[Season.SPRING.ordinal()] = Month.APRIL;
        MIDDLE_MONTH[Season.SUMMER.ordinal()] = Month.JULY;
        MIDDLE_MONTH[Season.FALL.ordinal()] = Month.OCTOBER;
        MIDDLE_MONTH[Season.WINTER.ordinal()] = Month.JANUARY;
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            BASE_OF_BIOME[id] = (byte) BiomeKeys.VANILLA.indexOf(BiomeKeys.vanillaKey(id));
        }
    }

    /** The TFC world as the remap needs it. Implementations must be thread-safe and pure (computed from the seed). */
    public interface Terrain {
        /** The TFC biome id at a block column (as the {@code BiomeProvider} returns it, before the vanilla placeholder). */
        int biomeId(int blockX, int blockZ);

        /** The climate inputs of one chunk, per block column (the chunk data is built once per call). */
        ChunkInputs climates(int chunkX, int chunkZ);
    }

    /** The climate inputs of the block columns of one chunk. */
    @FunctionalInterface
    public interface ChunkInputs {
        ChunkClimate at(int blockX, int blockZ);
    }

    private SeasonalBiomeRemap() {
    }

    /**
     * The variant id for {@code basePath} (the biome's path in our namespace, e.g. {@code plains}) at the given instant
     * temperature (degrees Celsius) and groundwater (mm, base plus instant rainfall).
     *
     * @return the datapack biome id {@code tfp:seasonal/<biome>/<variant>}, or empty when either value is unknown (NaN),
     *         so the caller keeps the base biome
     */
    public static Optional<String> variantId(String basePath, float temperature, float groundwater) {
        if (Float.isNaN(temperature) || Float.isNaN(groundwater)) {
            return Optional.empty();
        }
        return Optional.of(SeasonalColour.variantId(basePath, SeasonalColour.band(temperature, groundwater)));
    }

    /** The season of the calendar now, or null before the calendar published anything. */
    public static Season seasonOf(ClockSnapshot clock) {
        return clock.isSet() ? CalendarTicks.month(clock.calendarTicks(), clock.daysInMonth()).season() : null;
    }

    /** The calendar tick at which {@code season}'s climate is sampled: its middle month, middle day, noon, first year. */
    public static long sampleTick(Season season, int daysInMonth) {
        return CalendarTicks.of(CalendarTicks.FIRST_YEAR, MIDDLE_MONTH[season.ordinal()], daysInMonth / 2 + 1, 12, 0,
                daysInMonth);
    }

    /** The variant index of a TFC biome in a band. */
    public static int variantIndex(int tfcBiome, int band) {
        if (tfcBiome < 0 || tfcBiome >= TfcBiomes.COUNT || band < 0 || band >= SeasonalColour.BAND_COUNT) {
            throw new IllegalArgumentException("biome " + tfcBiome + " band " + band);
        }
        return tfcBiome * SeasonalColour.BAND_COUNT + band;
    }

    /** The datapack biome id of a variant index: {@code tfp:seasonal/<biome>/t<T>r<R>}. */
    public static String variantIdOf(int variantIndex) {
        return SeasonalColour.variantId(biomePath(variantIndex / SeasonalColour.BAND_COUNT),
                variantIndex % SeasonalColour.BAND_COUNT);
    }

    /** The base index (into {@link BiomeKeys#VANILLA}) of a TFC biome's server-side placeholder. */
    public static int baseIndex(int tfcBiome) {
        return BASE_OF_BIOME[tfcBiome];
    }

    /** The registry id of a base index, e.g. {@code minecraft:plains}. */
    public static String baseIdOf(int baseIndex) {
        return "minecraft:" + BiomeKeys.VANILLA.get(baseIndex);
    }

    /** Our datapack path of a TFC biome: its TFC name in lower case ({@code plains}, {@code shore}, ...). */
    public static String biomePath(int tfcBiome) {
        return TfcBiomes.name(tfcBiome).toLowerCase(Locale.ROOT);
    }

    /**
     * The variant index for a block position in a season (the pure remap). The biome comes from the terrain at the
     * column, the band from the instant temperature at the position and the instant groundwater of its column at the
     * season's {@linkplain #sampleTick sample tick}.
     */
    public static int variantIndexAt(Terrain terrain, OverworldClimate climate, int x, int y, int z, Season season,
            int daysInMonth) {
        int biome = sanitize(terrain.biomeId(x, z));
        ChunkClimate chunk = terrain.climates(x >> 4, z >> 4).at(x, z);
        long tick = sampleTick(season, daysInMonth);
        float groundwater = climate.instantGroundwater(chunk, tick, daysInMonth);
        float temperature = climate.instantTemperature(new BlockPos(x, y, z), chunk, tick, daysInMonth);
        return variantIndex(biome, SeasonalColour.band(temperature, groundwater));
    }

    /**
     * The table of one chunk for one season: for every 4 x 4 x 4 biome cell the base index the server sends and the variant
     * index to send instead. The biome of a cell is the terrain's at the cell's lower corner (where the Bukkit
     * {@code BiomeProvider} is asked); the temperature and groundwater are taken at the cell's centre.
     *
     * @param minY   the world's minimum build height (a multiple of 16)
     * @param height the world's build height (a multiple of 16)
     */
    public static SeasonalChunkTable compute(Terrain terrain, OverworldClimate climate, int chunkX, int chunkZ, int minY,
            int height, Season season, int daysInMonth) {
        int sections = height >> 4;
        int cellsY = sections * 4;
        ChunkInputs inputs = terrain.climates(chunkX, chunkZ);
        long tick = sampleTick(season, daysInMonth);
        byte[] base = new byte[SeasonalChunkTable.COLUMNS];
        short[] variants = new short[sections * SeasonalChunkTable.CELLS_PER_SECTION];
        for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
                int bx = (chunkX << 4) + (qx << 2);
                int bz = (chunkZ << 4) + (qz << 2);
                int biome = sanitize(terrain.biomeId(bx, bz));
                int column = (qz << 2) | qx;
                base[column] = BASE_OF_BIOME[biome];
                ChunkClimate chunk = inputs.at(bx + 2, bz + 2);
                float groundwater = climate.instantGroundwater(chunk, tick, daysInMonth);
                for (int cy = 0; cy < cellsY; cy++) {
                    float t = climate.instantTemperature(new BlockPos(bx + 2, minY + (cy << 2) + 2, bz + 2), chunk, tick,
                            daysInMonth);
                    int section = cy >> 2;
                    int cell = ((cy & 3) << 4) | column;
                    variants[section * SeasonalChunkTable.CELLS_PER_SECTION + cell] =
                            (short) variantIndex(biome, SeasonalColour.band(t, groundwater));
                }
            }
        }
        return new SeasonalChunkTable(chunkX, chunkZ, minY, sections, season, base, variants);
    }

    private static int sanitize(int biome) {
        return biome < 0 || biome >= TfcBiomes.COUNT ? TfcBiomes.PLAINS : biome;
    }
}
