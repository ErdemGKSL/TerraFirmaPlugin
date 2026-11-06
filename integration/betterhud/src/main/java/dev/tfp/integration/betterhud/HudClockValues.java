/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import dev.tfp.core.calendar.CalendarFormat;
import dev.tfp.core.clock.ClockSnapshot;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The text placeholders that show the calendar (M2-CAL-02). They read only the published {@link ClockSnapshot}
 * (volatile, lock-free), never the world, so they are safe on BetterHud's async timer on Folia. Shown as
 * {@link CalendarFormat#UNKNOWN} until the calendar has published its first reading.
 *
 * <p>PAR-HUD-15: {@code tfp_season} is TFC's 12-name {@code Month.Style.SEASON} text ("Early Autumn"). TFC adjusts it
 * by the viewer's hemisphere; {@link #season(ClockSnapshot, boolean)} does that, but the placeholder registration
 * passes no player yet, so {@link #TEXTS} gives the northern name (ledger Divergences, follow-up PAR-HUD-16).
 */
final class HudClockValues {
    private HudClockValues() {
    }

    /** Text placeholders by name, as used in our generated BetterHud YAML ({@code [tfp_date]}). */
    static final Map<String, Function<ClockSnapshot, String>> TEXTS;

    static {
        Map<String, Function<ClockSnapshot, String>> t = new LinkedHashMap<>();
        t.put("tfp_date", HudClockValues::date);
        t.put("tfp_season", HudClockValues::season);
        TEXTS = Collections.unmodifiableMap(t);
    }

    static String date(ClockSnapshot s) {
        return s.isSet() ? CalendarFormat.date(s.calendarTicks(), s.daysInMonth()) : CalendarFormat.UNKNOWN;
    }

    /** The northern-hemisphere season name (TFC's default hemisphere and the one at z = 0). */
    static String season(ClockSnapshot s) {
        return season(s, true);
    }

    /** TFC's season name of the hemisphere-adjusted month ({@code CalendarScreen}). */
    static String season(ClockSnapshot s, boolean northernHemisphere) {
        return s.isSet() ? CalendarFormat.seasonName(s.calendarTicks(), s.daysInMonth(), northernHemisphere)
            : CalendarFormat.UNKNOWN;
    }
}
