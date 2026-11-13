/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.calendar.CalendarState;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.SleepSkip;
import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.calendar.CalendarService;
import dev.tfp.paper.calendar.DaylightSync;
import dev.tfp.paper.scheduler.Scheduler;
import io.papermc.paper.event.world.WorldGameRuleChangeEvent;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;

/**
 * {@code calendar.sleep_skip} (PAR-CLIM-16, the server side of PAR-CLIM-01), on the running server:
 * <ol>
 *   <li>With the calendar forced to run, {@code CalendarService.skip} to 9 pm holds the calendar world at
 *       {@code calendarTicks + 18000} with the daylight rule on (TFC {@code updateDayTime}).</li>
 *   <li>A {@link TimeSkipEvent} {@code NIGHT_SKIP} fired on the global thread (where vanilla fires it once everyone
 *       sleeps) moves the calendar to 5 am the next day (TFC {@code onPlayersFinishedSleeping}), the player ticks by
 *       {@code slept * dayLength / 20} (TFC {@code skipForwardBy}), publishes the new state, and sets the event amount
 *       so vanilla lands on the calendar's new day time.</li>
 *   <li>A console {@code /gamerule} change of the daylight rule is cancelled.</li>
 *   <li>With the override off, the time stop on and no player online, the calendar stops and turns the rule off.</li>
 * </ol>
 */
public final class CalendarSleepSkipCase implements SelfTestCase {
    private static final Duration STEP = Duration.ofSeconds(10);

    @Override
    public String name() {
        return "calendar.sleep_skip: night skip moves the calendar to 5 am, daylight rule held (PAR-CLIM-16)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(60);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        return OnThread.script("calendar-sleep", () -> check(tfp));
    }

    private static String check(TfpPlugin tfp) throws Exception {
        CalendarService cal = tfp.calendar();
        if (cal == null) {
            throw new AssertionError("no calendar service");
        }
        GameRule<Boolean> rule = DaylightSync.DAYLIGHT;
        if (rule == null) {
            throw new SelfTestCase.Skip("no daylight game rule on this server version");
        }
        GlobalClockHolder clock = tfp.clock();
        Scheduler s = tfp.scheduler();
        if (!OnThread.poll(STEP, () -> clock.get().isSet() && cal.world() != null)) {
            throw new AssertionError("the calendar was never loaded");
        }
        World world = cal.world();
        StringBuilder out = new StringBuilder();
        boolean previous = cal.setRunWhenEmpty(true);
        try {
            // 1. Running: the daylight rule turns on with the next publication.
            if (!pollGlobal(s, () -> clock.get().running() && Boolean.TRUE.equals(world.getGameRuleValue(rule)))) {
                throw new AssertionError("forced to run, the calendar did not turn " + ruleName(rule) + " on in "
                        + world.getName() + ": " + clock.get() + ", rule = " + global(s, () -> world.getGameRuleValue(rule)));
            }
            out.append(OnThread.global(s, STEP, () -> sleepSkip(cal, clock, world, rule)));

            // 2. A console /gamerule change of the daylight rule is cancelled.
            out.append("; ").append(OnThread.global(s, STEP, () -> gameRuleCancelled(tfp, world, rule)));
        } finally {
            cal.setRunWhenEmpty(previous);
        }

        // 3. Empty server with the time stop on: the calendar stops and the rule goes off.
        if (!cal.config().enableTimeStopWhenServerEmpty()) {
            out.append("; time stop off in calendar.yml, empty-server check not applicable");
        } else if (previous) {
            out.append("; run-when-empty override was already on, empty-server check not applicable");
        } else if (!Bukkit.getOnlinePlayers().isEmpty()) {
            out.append("; players online, empty-server check not applicable");
        } else {
            if (!pollGlobal(s, () -> !clock.get().running() && Boolean.FALSE.equals(world.getGameRuleValue(rule)))) {
                throw new AssertionError("empty server with the time stop on: the calendar did not stop or "
                        + ruleName(rule) + " stayed on: " + clock.get() + ", rule = "
                        + global(s, () -> world.getGameRuleValue(rule)));
            }
            out.append("; empty server: calendar stopped, ").append(ruleName(rule)).append(" off");
        }
        return out.toString();
    }

    /** Global thread: 9 pm, then a NIGHT_SKIP; one task, so no calendar advance runs in between. */
    private static String sleepSkip(CalendarService cal, GlobalClockHolder clock, World world, GameRule<Boolean> rule) {
        CalendarService.Skipped nine = cal.skip(TimeCommand.setTimeOfDay(TimeCommand.hourTicksOfDay(21)));
        if (nine == null) {
            throw new AssertionError("skip to 9 pm returned null (calendar not loaded)");
        }
        CalendarState before = nine.state();
        if (CalendarTicks.hourOfDay(before.calendarTicks()) != 21 || before.calendarTicks() % 1000 != 0) {
            throw new AssertionError("skip to 9 pm landed at " + before.calendarTicks());
        }
        long expectedDayTime = before.calendarTicks() + SleepSkip.VANILLA_DAY_TIME_OFFSET;
        if (world.getFullTime() != expectedDayTime) {
            throw new AssertionError("at 9 pm the world full time is " + world.getFullTime() + ", expected "
                    + expectedDayTime + " (calendar " + before.calendarTicks() + " + 18000)");
        }
        if (!Boolean.TRUE.equals(world.getGameRuleValue(rule))) {
            throw new AssertionError(ruleName(rule) + " is off while the calendar runs");
        }
        if (!SleepSkip.canSleepAt(before.calendarTicks())) {
            throw new AssertionError("9 pm is outside TFC's sleep window");
        }

        int slept = SleepSkip.calendarTicksSlept(before.calendarTicks());
        TimeSkipEvent event = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.NIGHT_SKIP,
                24_000L - Math.floorMod(world.getFullTime(), 24_000L)); // vanilla's own amount: to the next dawn
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            throw new AssertionError("the NIGHT_SKIP event was cancelled");
        }
        CalendarState after = cal.state();
        if (after.calendarTicks() != before.calendarTicks() + slept) {
            throw new AssertionError("calendar moved " + before.calendarTicks() + " -> " + after.calendarTicks()
                    + ", expected +" + slept + " (TFC onPlayersFinishedSleeping)");
        }
        long dayBefore = before.calendarTicks() / CalendarTicks.CALENDAR_TICKS_IN_DAY;
        long dayAfter = after.calendarTicks() / CalendarTicks.CALENDAR_TICKS_IN_DAY;
        long tod = after.calendarTicks() % CalendarTicks.CALENDAR_TICKS_IN_DAY;
        if (dayAfter != dayBefore + 1 || Math.abs(tod - 5 * CalendarTicks.CALENDAR_TICKS_IN_HOUR) > 1) {
            throw new AssertionError("woke at day " + dayAfter + " tick-of-day " + tod + ", expected day "
                    + (dayBefore + 1) + " at 5 am (5000)");
        }
        long playerDelta = after.playerTicks() - before.playerTicks();
        long expectedPlayer = (long) slept * before.dayLengthMinutes() / CalendarTicks.PLAYER_TICKS_PER_SECOND;
        if (playerDelta != expectedPlayer) {
            throw new AssertionError("player ticks moved " + playerDelta + ", expected " + expectedPlayer
                    + " (slept " + slept + " x dayLength " + before.dayLengthMinutes() + " / 20)");
        }
        ClockSnapshot published = clock.get();
        if (published.calendarTicks() != after.calendarTicks() || published.playerTicks() != after.playerTicks()) {
            throw new AssertionError("published clock " + published + " is not the new state " + after);
        }
        long landing = world.getFullTime() + event.getSkipAmount();
        long expectedLanding = SleepSkip.vanillaDayTime(after.calendarTicks());
        if (landing != expectedLanding) {
            throw new AssertionError("vanilla would land at " + landing + " (full time " + world.getFullTime()
                    + " + amount " + event.getSkipAmount() + "), expected " + expectedLanding);
        }
        return "9 pm at calendar " + before.calendarTicks() + " (world " + expectedDayTime + "); NIGHT_SKIP slept "
                + slept + " calendar / " + playerDelta + " player ticks to " + after.calendarTicks()
                + " (5 am), vanilla lands at " + landing;
    }

    /** Global thread: dispatches {@code /gamerule <rule> false} from the console and checks it was cancelled. */
    private static String gameRuleCancelled(TfpPlugin tfp, World world, GameRule<Boolean> rule) {
        AtomicReference<WorldGameRuleChangeEvent> seen = new AtomicReference<>();
        Listener probe = new Listener() { };
        Bukkit.getPluginManager().registerEvent(WorldGameRuleChangeEvent.class, probe, EventPriority.MONITOR,
                (l, e) -> {
                    if (e instanceof WorldGameRuleChangeEvent g && DaylightSync.isDaylightRule(g.getGameRule())
                            && g.getWorld().equals(world)) {
                        seen.set(g);
                    }
                }, tfp, false);
        String used = null;
        try {
            for (String name : new String[] {ruleName(rule), rule.getKey().getKey(), rule.getKey().toString()}) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule " + name + " false");
                if (seen.get() != null) {
                    used = name;
                    break;
                }
            }
        } finally {
            HandlerList.unregisterAll(probe);
        }
        WorldGameRuleChangeEvent e = seen.get();
        if (e == null) {
            throw new AssertionError("/gamerule " + ruleName(rule) + " false fired no WorldGameRuleChangeEvent for "
                    + world.getName());
        }
        if (!e.isCancelled()) {
            throw new AssertionError("the console /gamerule " + used + " false was not cancelled");
        }
        if (!Boolean.TRUE.equals(world.getGameRuleValue(rule))) {
            throw new AssertionError(ruleName(rule) + " is off after the cancelled /gamerule");
        }
        return "console /gamerule " + used + " false cancelled";
    }

    private static boolean pollGlobal(Scheduler s, java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + STEP.toNanos();
        while (true) {
            if (Boolean.TRUE.equals(OnThread.global(s, STEP, condition))) {
                return true;
            }
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(200);
        }
    }

    private static Object global(Scheduler s, java.util.concurrent.Callable<Object> body) {
        try {
            return OnThread.global(s, STEP, body);
        } catch (Exception e) {
            return "unreadable (" + e + ")";
        }
    }

    /** The rule's command name ({@code doDaylightCycle}, or {@code advance_time} on newer versions). */
    @SuppressWarnings({"deprecation", "removal"})
    private static String ruleName(GameRule<?> rule) {
        return rule.getName();
    }
}
