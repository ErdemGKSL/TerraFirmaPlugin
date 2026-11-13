/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/ServerCalendar.java @ c3b4d9536 (updateDayTime,
 * overrideDoDaylightCycleToFalse)
 */
package dev.tfp.paper.calendar;

import dev.tfp.core.calendar.CalendarState;
import dev.tfp.core.calendar.SleepSkip;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.GameRule;
import org.bukkit.World;

/**
 * PAR-CLIM-01: holds every overworld-type world ({@link World.Environment#NORMAL}) to the calendar, on the global
 * thread, each time {@link CalendarService} publishes a state.
 * <ul>
 *   <li><b>Day time.</b> TFC {@code updateDayTime}: the vanilla day time is {@code calendarTicks + 18000}, so the sun,
 *       the moon phase and vanilla's night checks follow the calendar.</li>
 *   <li><b>Daylight rule.</b> TFC forces {@code doDaylightCycle} off and sets the day time itself every tick. We set it
 *       once per second, so the rule mirrors the calendar's running flag instead: on while the calendar runs (the client
 *       moves the sun smoothly between our corrections, and vanilla fires the night skip that {@link CalendarModule}
 *       turns into a calendar skip), off while the calendar is arrested (no player online with the time stop on), so the
 *       sun stops with the calendar. Ledger divergence.</li>
 * </ul>
 * Global thread only (Folia: world time and game rules belong to the global region).
 */
public final class DaylightSync {
    /** The vanilla daylight rule ({@code doDaylightCycle}; {@code advance_time} on newer versions), or null if absent. */
    public static final GameRule<Boolean> DAYLIGHT = daylightRule();

    private final Supplier<? extends List<World>> worlds;
    /** True while {@link #apply} changes a world, so our own rule and time changes are told apart from others'. */
    private volatile boolean applying;

    public DaylightSync(Supplier<? extends List<World>> worlds) {
        this.worlds = worlds;
    }

    /** Holds every overworld-type world to {@code state}. Global thread. */
    public void apply(CalendarState state) {
        long dayTime = SleepSkip.vanillaDayTime(state.calendarTicks());
        boolean running = state.running();
        applying = true;
        try {
            for (World w : worlds.get()) {
                if (w.getEnvironment() != World.Environment.NORMAL) {
                    continue;
                }
                GameRule<Boolean> rule = DAYLIGHT;
                if (rule != null && !Boolean.valueOf(running).equals(w.getGameRuleValue(rule))) {
                    w.setGameRule(rule, running);
                }
                if (w.getFullTime() != dayTime) {
                    w.setFullTime(dayTime);
                }
            }
        } finally {
            applying = false;
        }
    }

    /** True while {@link #apply} runs (events it causes itself). */
    public boolean applying() {
        return applying;
    }

    /** Whether {@code rule} is the vanilla daylight rule. */
    public static boolean isDaylightRule(GameRule<?> rule) {
        return rule != null && rule.equals(DAYLIGHT);
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    static GameRule<Boolean> daylightRule() {
        for (GameRule<?> rule : GameRule.values()) {
            if (rule.getType() != Boolean.class) {
                continue;
            }
            String key = rule.getKey().getKey();
            String name = rule.getName();
            if (key.equals("advance_time") || name.equals("advance_time")
                    || key.equalsIgnoreCase("doDaylightCycle") || name.equals("doDaylightCycle")) {
                return (GameRule<Boolean>) rule;
            }
        }
        return null;
    }
}
