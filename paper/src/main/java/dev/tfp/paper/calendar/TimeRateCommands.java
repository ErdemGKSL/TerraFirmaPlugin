/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/TimeCommand.java @ c3b4d9536 (setDayLength, setMonthLength,
 * setTimeFromWeather)
 */
package dev.tfp.paper.calendar;

import dev.tfp.core.calendar.CalendarFormat;
import dev.tfp.core.calendar.CalendarState;
import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.worldgen.WorldSettingsLookup;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * PAR-CLIM-13: the work behind TFC's {@code /time set dayLength|monthLength|rain|clear} ({@code /tfp time set ...}).
 * The calendar is written on the global thread only ({@link CalendarService}); a command already there (the console)
 * runs inline so its reply is sent while the command runs (RCON), anything else hops to it.
 *
 * <ul>
 *   <li><b>dayLength</b>: TFC {@code setCalendarTickRate(20 / minutes)}, 0 for {@code disabled}
 *       ({@link CalendarState#withDayLength}); persisted with the calendar. Player ticks are unchanged.</li>
 *   <li><b>monthLength</b>: TFC {@code setMonthLength} ({@link CalendarState#withDaysInMonth}); may move the date
 *       backward, as in TFC.</li>
 *   <li><b>rain / clear</b>: TFC {@code setTimeFromWeather} ({@link TimeCommand#weatherSkip}) at the sender's position
 *       (the console: the calendar world's spawn). The chunk climate is sampled once; the search runs on the global
 *       thread against the state it then skips, so the two cannot disagree. Outside an overworld TFC's model has no
 *       rain ({@code getRain = -1}). Nothing found: no change (TFC is silent; we say so).</li>
 * </ul>
 */
public final class TimeRateCommands {
    private final Supplier<CalendarService> calendar;
    private final Scheduler scheduler;
    private final BooleanSupplier onGlobalThread;

    /**
     * @param calendar       the calendar service, or null while not enabled
     * @param onGlobalThread true on the thread that owns the calendar (Paper main, Folia global region)
     */
    public TimeRateCommands(Supplier<CalendarService> calendar, Scheduler scheduler, BooleanSupplier onGlobalThread) {
        this.calendar = calendar;
        this.scheduler = scheduler;
        this.onGlobalThread = onGlobalThread;
    }

    /** {@code set dayLength}: {@code minutes} at least 1, or 0 for TFC's {@code disabled}. */
    public CompletableFuture<Reply> setDayLength(int minutes) {
        TimeCommand.checkDayLength(minutes);
        return onCalendar(cal -> {
            CalendarState next = cal.update(st -> st.withDayLength(minutes));
            if (next == null) {
                return Reply.error("time.not_loaded");
            }
            return minutes == CalendarState.DISABLED_DAY_LENGTH ? Reply.ok("time.set.day_length_disabled")
                    : Reply.ok("time.set.day_length", minutes);
        });
    }

    /** {@code set monthLength}: 1..1000 days. */
    public CompletableFuture<Reply> setMonthLength(int days) {
        TimeCommand.checkMonthLength(days);
        return onCalendar(cal -> {
            CalendarState next = cal.update(st -> st.withDaysInMonth(days));
            return next == null ? Reply.error("time.not_loaded")
                    : Reply.ok("time.set.month_length", days, CalendarFormat.dateTime(next.calendarTicks(), next.daysInMonth()));
        });
    }

    /** {@code set rain|clear}: skips to the first calendar tick (in steps of 1,000) with the wanted weather at {@code at}. */
    public CompletableFuture<Reply> skipToWeather(Location at, boolean rain) {
        CalendarService cal = calendar.get();
        if (cal == null) {
            return CompletableFuture.completedFuture(Reply.error("command.not_enabled"));
        }
        World world = at != null && at.getWorld() != null ? at.getWorld() : cal.world();
        if (world == null) {
            return CompletableFuture.completedFuture(Reply.error("time.not_loaded"));
        }
        Location pos = at != null && at.getWorld() != null ? at : world.getSpawnLocation();
        boolean overworld = world.getEnvironment() == World.Environment.NORMAL;
        long seed = world.getSeed();
        int x = pos.getBlockX();
        int z = pos.getBlockZ();
        String label = rain ? "rain" : "clear";
        if (!overworld) {
            return onCalendar(c -> search(c, label, rain, null, null));
        }
        if (onGlobalThread.getAsBoolean()) {
            // One chunk climate sample for an admin command; inline so the console's reply is captured.
            return onCalendar(c -> search(c, label, rain, WorldSettingsLookup.climate(seed),
                    ChunkClimateSource.climateAt(seed, x, z)));
        }
        CompletableFuture<Reply> out = new CompletableFuture<>();
        scheduler.cpu(() -> {
            try {
                OverworldClimate climate = WorldSettingsLookup.climate(seed);
                ChunkClimate chunk = ChunkClimateSource.climateAt(seed, x, z);
                onCalendar(c -> search(c, label, rain, climate, chunk)).whenComplete((r, e) -> {
                    if (e != null) {
                        out.completeExceptionally(e);
                    } else {
                        out.complete(r);
                    }
                });
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        });
        return out;
    }

    /** The search and the skip, on the global thread. {@code climate == null}: a world without TFC rain. */
    static Reply search(CalendarService cal, String label, boolean rain, OverworldClimate climate, ChunkClimate chunk) {
        CalendarState now = cal.state();
        if (now == null) {
            return Reply.error("time.not_loaded");
        }
        long ticks;
        if (climate == null) {
            ticks = TimeCommand.weatherSkip(rain, now.calendarTicks(), 0f, t -> -1f);
        } else {
            float rainfall = climate.instantRainfall(chunk, now.calendarTicks(), now.daysInMonth());
            ticks = TimeCommand.weatherSkip(rain, now.calendarTicks(), rainfall, climate::rainIntensity);
        }
        if (ticks < 0) {
            return Reply.ok("time.weather.not_found", label, TimeCommand.WEATHER_SEARCH_LIMIT);
        }
        CalendarService.Skipped moved = cal.skip(st -> ticks);
        return moved == null ? Reply.error("time.not_loaded")
                : Reply.ok("time.set.done", label, moved.ticks(),
                        CalendarFormat.dateTime(moved.state().calendarTicks(), moved.state().daysInMonth()));
    }

    /** Runs {@code work} on the calendar's thread: inline when already there, else through the global scheduler. */
    private CompletableFuture<Reply> onCalendar(Function<CalendarService, Reply> work) {
        CalendarService cal = calendar.get();
        if (cal == null) {
            return CompletableFuture.completedFuture(Reply.error("command.not_enabled"));
        }
        CompletableFuture<Reply> out = new CompletableFuture<>();
        Runnable apply = () -> {
            try {
                out.complete(work.apply(cal));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        };
        if (onGlobalThread.getAsBoolean()) {
            apply.run();
        } else {
            scheduler.global(apply);
        }
        return out;
    }
}
