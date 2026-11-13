/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.toxicity.hud.api.placeholder.HudPlaceholder;
import org.junit.jupiter.api.Test;

/** PAR-HUD-15: {@code tfp_season} shows TFC's 12 season names; PAR-HUD-16: per the viewer's hemisphere. */
class HudClockValuesTest {
    private static final HudSnapshot NORTH = HudSnapshot.DEFAULT;
    private static final HudSnapshot SOUTH = HudSnapshot.DEFAULT.withNorthernHemisphere(false);

    private static ClockSnapshot at(Month month, int day) {
        return new ClockSnapshot(1, 0, CalendarTicks.of(1000, month, day, 12, 0, 30), 30, true);
    }

    @Test
    void seasonPlaceholderIsTfcsTwelveNameSeasonOfTheNorthernMonth() {
        assertThat(HudClockValues.TEXTS.get("tfp_season").apply(at(Month.SEPTEMBER, 30), NORTH)).isEqualTo("Early Autumn");
        assertThat(HudClockValues.TEXTS.get("tfp_date").apply(at(Month.SEPTEMBER, 30), NORTH)).isEqualTo("September 30, 1000");
        assertThat(HudClockValues.TEXTS.get("tfp_season").apply(at(Month.FEBRUARY, 1), NORTH)).isEqualTo("Late Winter");
    }

    @Test
    void southernHemisphereUsesTheOppositeMonth() {
        assertThat(HudClockValues.season(at(Month.SEPTEMBER, 30), false)).isEqualTo("Early Spring");
        assertThat(HudClockValues.season(at(Month.JANUARY, 1), false)).isEqualTo("Summer");
        assertThat(HudClockValues.season(at(Month.JANUARY, 1), true)).isEqualTo("Winter");
        assertThat(HudClockValues.TEXTS.get("tfp_season").apply(at(Month.SEPTEMBER, 30), SOUTH)).isEqualTo("Early Spring");
        // The date is the same in both hemispheres.
        assertThat(HudClockValues.TEXTS.get("tfp_date").apply(at(Month.SEPTEMBER, 30), SOUTH)).isEqualTo("September 30, 1000");
    }

    @Test
    void unsetClockIsADashInBothHemispheres() {
        assertThat(HudClockValues.season(ClockSnapshot.UNSET, true)).isEqualTo("-");
        assertThat(HudClockValues.season(ClockSnapshot.UNSET, false)).isEqualTo("-");
        assertThat(HudClockValues.TEXTS.get("tfp_season").apply(ClockSnapshot.UNSET, SOUTH)).isEqualTo("-");
    }

    /** PAR-HUD-16: the registered placeholder reads each viewer's published hemisphere by uuid, nothing else. */
    @Test
    void registeredSeasonPlaceholderFollowsEachViewersPublishedHemisphere() {
        HudSnapshotCache cache = new HudSnapshotCache(() -> true);
        GlobalClockHolder clock = new GlobalClockHolder();
        Map<String, HudPlaceholder<?>> registry = new LinkedHashMap<>();
        HudClockValues.TEXTS.forEach((name, read) -> registry.put(name, BetterHudIntegration.clockPlaceholder(clock, cache, read)));
        UUID north = UUID.randomUUID();
        UUID south = UUID.randomUUID();
        cache.put(north, NORTH);
        cache.put(south, SOUTH);

        assertThat(PlaceholderProbe.value(registry, "tfp_season", north)).hasValue("-");
        assertThat(PlaceholderProbe.value(registry, "tfp_season", south)).hasValue("-");

        ClockSnapshot september = at(Month.SEPTEMBER, 30);
        clock.publish(september.playerTicks(), september.calendarTicks(), september.daysInMonth(), true);
        assertThat(PlaceholderProbe.value(registry, "tfp_season", north)).hasValue("Early Autumn");
        assertThat(PlaceholderProbe.value(registry, "tfp_season", south)).hasValue("Early Spring");
        assertThat(PlaceholderProbe.value(registry, "tfp_date", south)).hasValue("September 30, 1000");
        // A player with nothing published yet reads TFC's default (northern) hemisphere.
        assertThat(PlaceholderProbe.value(registry, "tfp_season", UUID.randomUUID())).hasValue("Early Autumn");

        // Crossing into the other hemisphere changes only that player's text.
        cache.put(north, NORTH.withNorthernHemisphere(false));
        assertThat(PlaceholderProbe.value(registry, "tfp_season", north)).hasValue("Early Spring");
        assertThat(PlaceholderProbe.value(registry, "tfp_season", south)).hasValue("Early Spring");
    }
}
