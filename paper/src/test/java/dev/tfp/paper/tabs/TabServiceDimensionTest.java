/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tabs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ClimateFormat;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.Solar;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.tabs.TabLayout.Page;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * PAR-SURV-07: the pages outside the overworld and before the clock is published, as the ledger rows "TFC's calendar
 * and climate tabs always have the client calendar" and "TFC's climate tab in the Nether and the End" record.
 */
class TabServiceDimensionTest {
    private static final long SEED = 42L;
    private static final int DIM = 8;
    private static final ClockSnapshot CLOCK =
        new ClockSnapshot(5, 100, CalendarTicks.of(1002, Month.MAY, 3, 10, 30, DIM), DIM, true);

    private final TabService service = new TabService(null, null, Messages.fromJar(), () -> CLOCK,
        id -> Optional.of(PlayerData.NEW), () -> ClimateTemperatureStyle.CELSIUS, false);

    private static TabService.Capture capture(boolean overworld, int z, ClockSnapshot clock) {
        return new TabService.Capture(UUID.randomUUID(), "en_us", SEED, overworld, new BlockPos(0, 80, z), 20,
            Optional.of(PlayerData.NEW), clock, ClimateTemperatureStyle.CELSIUS);
    }

    private static int zIn(boolean north) {
        float scale = OverworldClimate.forWorldSeed(SEED, OverworldClimate.DEFAULT_TEMPERATURE_SCALE).hemisphereScale();
        return IntStream.iterate(0, z -> z + 1000).filter(z -> Solar.inNorthernHemisphere(z, scale) == north)
            .findFirst().orElseThrow();
    }

    @Test
    void outsideTheOverworldTheClimatePageIsUnknownEvenWithARunningClock() {
        TabPage.ClimateInput in = TabService.climateInput(capture(false, 0, CLOCK));
        assertThat(in.known()).isFalse();
        List<String> lines = service.build(Page.CLIMATE, capture(false, 0, CLOCK)).plainLines();
        assertThat(String.join("\n", lines)).contains(ClimateFormat.UNKNOWN);
        assertThat(TabService.climateInput(capture(true, 0, CLOCK)).known()).isTrue();
    }

    @Test
    void outsideTheOverworldTheCalendarUsesTheNorthernHemisphereAtAnyZ() {
        int south = zIn(false);
        int north = zIn(true);
        List<String> northern = service.build(Page.CALENDAR, capture(true, north, CLOCK)).plainLines();
        List<String> southern = service.build(Page.CALENDAR, capture(true, south, CLOCK)).plainLines();
        assertThat(southern).as("the overworld flips the season in the south").isNotEqualTo(northern);
        assertThat(service.build(Page.CALENDAR, capture(false, south, CLOCK)).plainLines()).isEqualTo(northern);
    }

    @Test
    void beforeTheClockIsPublishedBothPagesAreUnknown() {
        assertThat(TabService.climateInput(capture(true, 0, ClockSnapshot.UNSET)).known()).isFalse();
        assertThat(String.join("\n", service.build(Page.CALENDAR, capture(true, 0, ClockSnapshot.UNSET)).plainLines()))
            .contains(ClimateFormat.UNKNOWN);
    }
}
