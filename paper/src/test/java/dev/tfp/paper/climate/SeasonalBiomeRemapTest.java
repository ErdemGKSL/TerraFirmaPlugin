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
    /** A temperate chunk (15 C, 150 mm) with a dry winter half-year (variance 0.6: dry January). */
    private static final float LATE_AUTUMN_TEMPERATURE = 15f;
    private static final float LATE_AUTUMN_RAINFALL = 150f;
    private static final float LATE_AUTUMN_VARIANCE = 0.6f;
    private static final int LATE_AUTUMN_Z = 0;

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
    void phaseIsSampledInTheMiddleOfItsMonthAtNoon() {
        for (Month month : Month.values()) {
            long tick = SeasonalBiomeRemap.sampleTick(month, DAYS);
            assertThat(CalendarTicks.month(tick, DAYS)).isEqualTo(month);
            assertThat(CalendarTicks.dayOfMonth(tick, DAYS)).isEqualTo(DAYS / 2 + 1);
            assertThat(CalendarTicks.hourOfDay(tick)).isEqualTo(12);
            assertThat(CalendarTicks.year(tick, DAYS)).isEqualTo(CalendarTicks.FIRST_YEAR);
        }
    }

    @Test
    void phaseOfTheClock() {
        assertThat(SeasonalBiomeRemap.phaseOf(ClockSnapshot.UNSET)).isNull();
        long july = CalendarTicks.of(1003, Month.JULY, 2, 6, 0, DAYS);
        assertThat(SeasonalBiomeRemap.phaseOf(new ClockSnapshot(5, 0, july, DAYS, true))).isEqualTo(Month.JULY);
        long december = CalendarTicks.of(1003, Month.DECEMBER, 1, 0, 0, DAYS);
        assertThat(SeasonalBiomeRemap.phaseOf(new ClockSnapshot(6, 0, december, DAYS, true))).isEqualTo(Month.DECEMBER);
    }

    @Test
    void phaseFollowsTheMonthAcrossTheLateAutumnAndLateWinterBoundaries() {
        // last minute of October, first of November: early and late autumn are two phases
        assertThat(phaseAt(Month.OCTOBER, 1, 0, 0)).isEqualTo(Month.OCTOBER);
        assertThat(phaseAt(Month.OCTOBER, DAYS, 23, 59)).isEqualTo(Month.OCTOBER);
        assertThat(phaseAt(Month.NOVEMBER, 1, 0, 0)).isEqualTo(Month.NOVEMBER);
        assertThat(phaseAt(Month.NOVEMBER, DAYS, 23, 59)).isEqualTo(Month.NOVEMBER);
        assertThat(phaseAt(Month.DECEMBER, 1, 0, 0)).isEqualTo(Month.DECEMBER);
        // late winter and early spring are two phases
        assertThat(phaseAt(Month.FEBRUARY, DAYS, 23, 59)).isEqualTo(Month.FEBRUARY);
        assertThat(phaseAt(Month.MARCH, 1, 0, 0)).isEqualTo(Month.MARCH);
        // one phase per month, through the year and across the year boundary
        assertThat(phaseAt(Month.JANUARY, 1, 0, 0)).isEqualTo(Month.JANUARY);
        for (Month month : Month.values()) {
            assertThat(phaseAt(month, DAYS / 2 + 1, 12, 0)).isEqualTo(month);
        }
        // the phase follows the configured month length
        assertThat(SeasonalBiomeRemap.phaseOf(CalendarTicks.of(1005, Month.NOVEMBER, 30, 23, 59, 30), 30))
            .isEqualTo(Month.NOVEMBER);
    }

    @Test
    void phasesDoNotGrowTheVariantRegistry() {
        assertThat(SeasonalBiomeRemap.PHASE_COUNT).isEqualTo(12);
        // a variant is a colour band of a biome; the phase only picks which band, so the datapack stays 125 x 16
        assertThat(SeasonalBiomeRemap.VARIANT_COUNT).isEqualTo(TfcBiomes.COUNT * SeasonalColour.BAND_COUNT).isEqualTo(2_000);
        // the table stores variant indices as short, and the client's global palette stays at 12 bits with vanilla's biomes
        assertThat(SeasonalBiomeRemap.VARIANT_COUNT).isLessThan(Short.MAX_VALUE);
        assertThat(SeasonalBiomeRemap.VARIANT_COUNT + 128).isLessThanOrEqualTo(1 << 12);
        Set<String> ids = new HashSet<>();
        for (Month phase : Month.values()) {
            SeasonalChunkTable table = SeasonalBiomeRemap.compute(TERRAIN, CLIMATE, 5, 400, -64, 384, phase, DAYS);
            for (int section = 0; section < table.sections(); section++) {
                for (int cell = 0; cell < SeasonalChunkTable.CELLS_PER_SECTION; cell++) {
                    int variant = table.variantAt(section, cell);
                    assertThat(variant).isBetween(0, SeasonalBiomeRemap.VARIANT_COUNT - 1);
                    ids.add(SeasonalBiomeRemap.variantIdOf(variant));
                }
            }
        }
        assertThat(ids).allMatch(id -> id.matches("tfp:seasonal/(plains|ocean)/t[0-3]r[0-3]"));
    }

    @Test
    void dryAutumnStaysYellowThroughLateAutumn() {
        // A temperate chunk (15 C) whose rainfall falls through the autumn: October (about 134 mm) and November (about
        // 105 mm) both fall in the 75 to 150 mm rainfall band, so late autumn keeps October's band. That band's colour
        // is the yellow of the colormap at its centre (#ead159 at 12 C), where the even bands gave dark green. The
        // variant is t1r2 in all four months (February and March too: 105 and 135 mm).
        ChunkClimate dryAutumn = new ChunkClimate(LATE_AUTUMN_TEMPERATURE, LATE_AUTUMN_RAINFALL, LATE_AUTUMN_VARIANCE, 0f);
        SeasonalBiomeRemap.Terrain terrain = new SeasonalBiomeRemap.Terrain() {
            @Override
            public int biomeId(int blockX, int blockZ) {
                return TfcBiomes.PLAINS;
            }

            @Override
            public SeasonalBiomeRemap.ChunkInputs climates(int chunkX, int chunkZ) {
                return (x, z) -> dryAutumn;
            }
        };
        for (Month month : new Month[] {Month.OCTOBER, Month.NOVEMBER, Month.FEBRUARY, Month.MARCH}) {
            int variant = SeasonalBiomeRemap.variantIndexAt(terrain, CLIMATE, 0, 70, LATE_AUTUMN_Z, month, DAYS);
            assertThat(SeasonalBiomeRemap.variantIdOf(variant)).as(month.name()).isEqualTo("tfp:seasonal/plains/t1r2");
        }
    }

    @Test
    void positionAndPhaseGiveTheBandOfTheSampledClimate() {
        int x = 3, y = 70, z = -4321;
        for (Month phase : Month.values()) {
            long tick = SeasonalBiomeRemap.sampleTick(phase, DAYS);
            float t = CLIMATE.instantTemperature(new BlockPos(x, y, z), WET_SEASONAL, tick, DAYS);
            float g = CLIMATE.instantGroundwater(WET_SEASONAL, tick, DAYS);
            assertThat(SeasonalBiomeRemap.variantIndexAt(TERRAIN, CLIMATE, x, y, z, phase, DAYS))
                .isEqualTo(SeasonalBiomeRemap.variantIndex(TfcBiomes.PLAINS, SeasonalColour.band(t, g)));
        }
    }

    @Test
    void thePhaseChangesTheVariantAwayFromTheEquator() {
        boolean differs = false;
        for (int z = -20_000; z <= 20_000; z += 2_500) {
            differs |= SeasonalBiomeRemap.variantIndexAt(TERRAIN, CLIMATE, 0, 64, z, Month.JULY, DAYS)
                != SeasonalBiomeRemap.variantIndexAt(TERRAIN, CLIMATE, 0, 64, z, Month.JANUARY, DAYS);
        }
        assertThat(differs).isTrue();
    }

    private static Month phaseAt(Month month, int day, int hour, int minute) {
        long tick = CalendarTicks.of(1004, month, day, hour, minute, DAYS);
        return SeasonalBiomeRemap.phaseOf(new ClockSnapshot(1, 0, tick, DAYS, true));
    }

    @Test
    void chunkTableHoldsTheRemapOfEveryCell() {
        int chunkX = -3, chunkZ = 250, minY = -64, height = 384;
        SeasonalChunkTable table = SeasonalBiomeRemap.compute(TERRAIN, CLIMATE, chunkX, chunkZ, minY, height, Month.OCTOBER, DAYS);
        assertThat(table.sections()).isEqualTo(24);
        assertThat(table.phase()).isEqualTo(Month.OCTOBER);
        for (int section = 0; section < table.sections(); section++) {
            for (int cell = 0; cell < SeasonalChunkTable.CELLS_PER_SECTION; cell++) {
                int qx = cell & 3, qz = (cell >> 2) & 3, qy = cell >> 4;
                int x = (chunkX << 4) + (qx << 2), z = (chunkZ << 4) + (qz << 2);
                int y = minY + (section << 4) + (qy << 2);
                int biome = TERRAIN.biomeId(x, z);
                assertThat(table.baseAt(cell)).isEqualTo(SeasonalBiomeRemap.baseIndex(biome));
                assertThat(table.variantAt(section, cell))
                    // the fake terrain has the same biome at a cell's corner and its centre
                    .isEqualTo(SeasonalBiomeRemap.variantIndexAt(TERRAIN, CLIMATE, x + 2, y + 2, z + 2, Month.OCTOBER, DAYS));
            }
        }
    }
}
