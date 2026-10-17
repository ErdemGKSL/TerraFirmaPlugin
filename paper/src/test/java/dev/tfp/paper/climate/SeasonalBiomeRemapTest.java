/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeasonalBiomeRemapTest {
    private static final int DAYS = ClockSnapshot.DEFAULT_DAYS_IN_MONTH;
    private static final OverworldClimate CLIMATE = OverworldClimate.forWorldSeed(42L, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
    private static final ChunkClimate WET_SEASONAL = new ChunkClimate(12f, 300f, 0.6f, 40f);

    /** Plains west of x = 8 in every chunk, ocean east of it; one climate everywhere. */
    private static final SeasonalBiomeRemap.Terrain TERRAIN = new SeasonalBiomeRemap.Terrain() {
        @Override
        public int biomeId(int blockX, int blockZ) {
            return (blockX & 15) < 8 ? TfcBiomes.PLAINS : TfcBiomes.OCEAN;
        }

        @Override
        public SeasonalBiomeRemap.ChunkInputs climates(int chunkX, int chunkZ) {
            return (x, z) -> WET_SEASONAL;
        }
    };

    @Test
    void mapsAnInstantClimateToItsBandVariant() {
        // 0 C and 0 mm: temperature band 2, groundwater band 3 (see SeasonalColourTest)
        assertThat(SeasonalBiomeRemap.variantId("plains", 0f, 0f)).contains("tfp:seasonal/plains/t2r3");
        assertThat(SeasonalBiomeRemap.variantId("plains", 30f, 500f)).contains("tfp:seasonal/plains/t0r0");
    }

    @Test
    void unknownReadingKeepsTheBaseBiome() {
        assertThat(SeasonalBiomeRemap.variantId("plains", Float.NaN, 100f)).isEqualTo(Optional.empty());
        assertThat(SeasonalBiomeRemap.variantId("plains", 10f, Float.NaN)).isEqualTo(Optional.empty());
    }

    @Test
    void sameClimateGivesTheSameVariant() {
        assertThat(SeasonalBiomeRemap.variantId("shore", 12.3f, 250f))
            .isEqualTo(SeasonalBiomeRemap.variantId("shore", 12.3f, 250f));
    }

    @Test
    void variantIndicesCoverEveryDatapackBiomeOnce() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < SeasonalBiomeRemap.VARIANT_COUNT; i++) {
            ids.add(SeasonalBiomeRemap.variantIdOf(i));
        }
        assertThat(ids).hasSize(2_000);
        int index = SeasonalBiomeRemap.variantIndex(TfcBiomes.PLAINS, 7);
        assertThat(SeasonalBiomeRemap.variantIdOf(index)).isEqualTo(SeasonalColour.variantId("plains", 7));
        assertThatThrownBy(() -> SeasonalBiomeRemap.variantIndex(TfcBiomes.COUNT, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SeasonalBiomeRemap.variantIndex(0, SeasonalColour.BAND_COUNT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void baseIndexIsTheServerPlaceholder() {
        assertThat(SeasonalBiomeRemap.baseIdOf(SeasonalBiomeRemap.baseIndex(TfcBiomes.PLAINS))).isEqualTo("minecraft:plains");
        assertThat(SeasonalBiomeRemap.baseIdOf(SeasonalBiomeRemap.baseIndex(TfcBiomes.OCEAN)))
            .isEqualTo("minecraft:" + BiomeKeys.vanillaKey(TfcBiomes.OCEAN));
    }

    @Test
    void seasonIsSampledInItsMiddleMonthAtNoon() {
        assertThat(CalendarTicks.month(SeasonalBiomeRemap.sampleTick(Season.SPRING, DAYS), DAYS)).isEqualTo(Month.APRIL);
        assertThat(CalendarTicks.month(SeasonalBiomeRemap.sampleTick(Season.SUMMER, DAYS), DAYS)).isEqualTo(Month.JULY);
        assertThat(CalendarTicks.month(SeasonalBiomeRemap.sampleTick(Season.FALL, DAYS), DAYS)).isEqualTo(Month.OCTOBER);
        assertThat(CalendarTicks.month(SeasonalBiomeRemap.sampleTick(Season.WINTER, DAYS), DAYS)).isEqualTo(Month.JANUARY);
        assertThat(CalendarTicks.hourOfDay(SeasonalBiomeRemap.sampleTick(Season.SUMMER, DAYS))).isEqualTo(12);
    }

    @Test
    void seasonOfTheClock() {
        assertThat(SeasonalBiomeRemap.seasonOf(ClockSnapshot.UNSET)).isNull();
        long july = CalendarTicks.of(1003, Month.JULY, 2, 6, 0, DAYS);
        assertThat(SeasonalBiomeRemap.seasonOf(new ClockSnapshot(5, 0, july, DAYS, true))).isEqualTo(Season.SUMMER);
        long december = CalendarTicks.of(1003, Month.DECEMBER, 1, 0, 0, DAYS);
        assertThat(SeasonalBiomeRemap.seasonOf(new ClockSnapshot(6, 0, december, DAYS, true))).isEqualTo(Season.WINTER);
    }

    @Test
    void positionAndSeasonGiveTheBandOfTheSampledClimate() {
        int x = 3, y = 70, z = -4321;
        for (Season season : Season.values()) {
            long tick = SeasonalBiomeRemap.sampleTick(season, DAYS);
            float t = CLIMATE.instantTemperature(new BlockPos(x, y, z), WET_SEASONAL, tick, DAYS);
            float g = CLIMATE.instantGroundwater(WET_SEASONAL, tick, DAYS);
            assertThat(SeasonalBiomeRemap.variantIndexAt(TERRAIN, CLIMATE, x, y, z, season, DAYS))
                .isEqualTo(SeasonalBiomeRemap.variantIndex(TfcBiomes.PLAINS, SeasonalColour.band(t, g)));
        }
    }

    @Test
    void theSeasonChangesTheVariantAwayFromTheEquator() {
        boolean differs = false;
        for (int z = -20_000; z <= 20_000; z += 2_500) {
            differs |= SeasonalBiomeRemap.variantIndexAt(TERRAIN, CLIMATE, 0, 64, z, Season.SUMMER, DAYS)
                != SeasonalBiomeRemap.variantIndexAt(TERRAIN, CLIMATE, 0, 64, z, Season.WINTER, DAYS);
        }
        assertThat(differs).isTrue();
    }

    @Test
    void chunkTableHoldsTheRemapOfEveryCell() {
        int chunkX = -3, chunkZ = 250, minY = -64, height = 384;
        SeasonalChunkTable table = SeasonalBiomeRemap.compute(TERRAIN, CLIMATE, chunkX, chunkZ, minY, height, Season.FALL, DAYS);
        assertThat(table.sections()).isEqualTo(24);
        assertThat(table.season()).isEqualTo(Season.FALL);
        for (int section = 0; section < table.sections(); section++) {
            for (int cell = 0; cell < SeasonalChunkTable.CELLS_PER_SECTION; cell++) {
                int qx = cell & 3, qz = (cell >> 2) & 3, qy = cell >> 4;
                int x = (chunkX << 4) + (qx << 2), z = (chunkZ << 4) + (qz << 2);
                int y = minY + (section << 4) + (qy << 2);
                int biome = TERRAIN.biomeId(x, z);
                assertThat(table.baseAt(cell)).isEqualTo(SeasonalBiomeRemap.baseIndex(biome));
                assertThat(table.variantAt(section, cell))
                    // the fake terrain has the same biome at a cell's corner and its centre
                    .isEqualTo(SeasonalBiomeRemap.variantIndexAt(TERRAIN, CLIMATE, x + 2, y + 2, z + 2, Season.FALL, DAYS));
            }
        }
    }
}
