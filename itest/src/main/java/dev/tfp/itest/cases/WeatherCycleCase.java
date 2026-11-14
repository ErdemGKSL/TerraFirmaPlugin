/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.weather.cycle.WeatherCycle;
import dev.tfp.core.world.BlockPos;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.calendar.CalendarService;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.weather.PrecipitationService;
import dev.tfp.paper.weather.cycle.ClimateWeatherCycle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * {@code weather.cycle} (PAR-CLIM-20, for PAR-CLIM-12): the climate drives the self-test world's weather on a real
 * server, as TFC's {@code ServerLevelMixin} replaces {@code advanceWeatherCycle}.
 * <ol>
 *   <li>From the climate alone ({@link ClimateWeatherCycle#target}), the case finds the next calendar time inside a
 *       climate rain window and a later one outside every window, each holding for a minute of calendar time, and moves
 *       the calendar there through {@link CalendarService#skip}.</li>
 *   <li>Within a few syncs (one per 20 ticks) {@code World.hasStorm()} must follow the target, and the vanilla weather
 *       rule ({@code doWeatherCycle} / {@code advance_weather}) must read false.</li>
 *   <li>In the rain window: a vanilla {@code weather clear} ({@code setStorm(false)}) is undone by the next sync; with
 *       the weather flag off the storm ends and {@link PrecipitationService} reads the flag off (NONE at a high open
 *       cell); the flag is then restored and the storm comes back.</li>
 * </ol>
 * The self-test world has vanilla terrain unless the run uses our generator; then the module's cycle leaves it alone and
 * the case runs its own {@link ClimateWeatherCycle} over that world (the same class, no players), checks the flag
 * through the pure {@link PrecipitationService#decide}, and restores the vanilla weather rule afterwards. The calendar
 * only moves forward; the weather flag is restored to what it was.
 */
public final class WeatherCycleCase implements SelfTestCase {
    private static final Duration STEP = Duration.ofSeconds(60);
    /** A few syncs: one per 20 ticks, plus the worker hop. */
    private static final Duration SYNC = Duration.ofSeconds(5);
    /** How far ahead the search looks: two years of the default 8-day months. */
    private static final long SEARCH_TICKS = 2L * 12 * 8 * 24_000;
    private static final long SEARCH_STEP = 200;
    /** The target must hold over this much calendar time after the chosen tick (the calendar runs during the case). */
    private static final long HOLD_TICKS = 1_200;

    @Override
    public String name() {
        return "weather.cycle: the storm follows the climate at a rain and a clear calendar time, the weather rule is "
                + "off, weather clear is undone, the weather flag clears it and reaches PrecipitationService (PAR-CLIM-20)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(8 * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        return OnThread.script("weather-cycle", () -> {
            ClimateWeatherCycle module = tfp.moduleServices().get(ClimateWeatherCycle.class)
                    .orElseThrow(() -> new AssertionError("no ClimateWeatherCycle in ModuleServices (weather_cycle module)"));
            PrecipitationService precipitation = tfp.moduleServices().get(PrecipitationService.class)
                    .orElseThrow(() -> new AssertionError("no PrecipitationService in ModuleServices"));
            World world = Bukkit.getWorlds().getFirst();
            check(world.getEnvironment() == World.Environment.NORMAL, "the first world is " + world.getEnvironment());
            if (!OnThread.poll(Duration.ofSeconds(20), () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            check(tfp.calendar() != null, "no calendar service");
            Scheduler s = tfp.scheduler();
            List<String> log = new ArrayList<>();
            if (module.controls(world)) {
                log.add("TFC world: the module's cycle");
                return run(tfp, module, precipitation, world, log);
            }
            // A vanilla-terrain self-test world: the module leaves it alone, so the case runs the same class with this
            // world as its TFC world (no players), and puts the vanilla rule back afterwards.
            log.add("vanilla-terrain world: a case-owned cycle over " + world.getName());
            ClimateWeatherCycle own = new ClimateWeatherCycle(tfp.clock(), s, w -> w.getUID().equals(world.getUID()),
                    () -> List.of(world), List::of);
            Boolean ruleBefore = OnThread.global(s, STEP, () -> ruleValue(world));
            own.start();
            try {
                return run(tfp, own, null, world, log);
            } finally {
                own.stop();
                OnThread.global(s, STEP, () -> {
                    restoreRule(world, ruleBefore);
                    world.getPersistentDataContainer().remove(ClimateWeatherCycle.ENABLED_KEY);
                    return null;
                });
            }
        });
    }

    /** Runs the scenario with the weather flag on, then puts the flag back to what it was. */
    private static String run(TfpPlugin tfp, ClimateWeatherCycle cycle, PrecipitationService precipitation, World world,
            List<String> log) throws Exception {
        boolean flagBefore = cycle.enabled(world);
        if (!flagBefore) {
            cycle.setEnabled(world, true);
        }
        try {
            return scenario(tfp, cycle, precipitation, world, log);
        } finally {
            cycle.setEnabled(world, flagBefore);
        }
    }

    /**
     * @param precipitation the plugin's service when {@code cycle} is the module's; null for a case-owned cycle (the pure
     *                      decision is checked with that cycle's flag instead)
     */
    private static String scenario(TfpPlugin tfp, ClimateWeatherCycle cycle, PrecipitationService precipitation,
            World world, List<String> log) throws Exception {
        Scheduler s = tfp.scheduler();
        long seed = world.getSeed();
        long now = tfp.clock().get().calendarTicks();
        long rain = find(seed, now + 1, true).orElseThrow(() -> new AssertionError(
                "no climate rain window within " + SEARCH_TICKS + " calendar ticks of " + now));
        long clear = find(seed, rain + HOLD_TICKS + 1, false).orElseThrow(() -> new AssertionError(
                "no clear calendar time within " + SEARCH_TICKS + " calendar ticks of " + rain));

        // Rain window: the storm follows; the vanilla rule is off.
        moveTo(tfp, s, rain);
        awaitStorm(s, world, true, "in the rain window at calendar " + rain);
        Boolean rule = OnThread.global(s, STEP, () -> ruleValue(world));
        check(rule == null || !rule, "the vanilla weather rule reads " + rule + " in a TFC world");
        log.add("calendar " + rain + ": storm (rule " + rule + ")");

        // A vanilla weather clear is undone by the next sync.
        OnThread.global(s, STEP, () -> {
            world.setStorm(false);
            return null;
        });
        awaitStorm(s, world, true, "after weather clear in the rain window");
        log.add("weather clear undone");

        // The weather flag off clears the sky and reaches PrecipitationService; restored, the storm comes back.
        cycle.setEnabled(world, false);
        try {
            awaitStorm(s, world, false, "with the weather flag off");
            Location spawn = world.getSpawnLocation();
            BlockPos high = new BlockPos(spawn.getBlockX(), world.getMaxHeight() - 2, spawn.getBlockZ());
            PrecipitationType off;
            if (precipitation != null) {
                check(!precipitation.weatherEnabled(world), "PrecipitationService reads the weather flag on");
                off = precipitation.precipitationAt(world, high, true);
            } else {
                off = PrecipitationService.decide(seed, true, high, true, cycle.enabled(world), tfp.clock().get());
            }
            check(off == PrecipitationType.NONE, "PrecipitationService gives " + off + " with the weather flag off");
            log.add("flag off: clear, precipitation NONE");
        } finally {
            cycle.setEnabled(world, true);
        }
        awaitStorm(s, world, true, "with the weather flag back on");
        log.add("flag on: storm again");

        // Outside every window: the storm ends.
        moveTo(tfp, s, clear);
        awaitStorm(s, world, false, "outside the rain windows at calendar " + clear);
        log.add("calendar " + clear + ": clear");
        return String.join("; ", log);
    }

    /** Polls the world's storm flag on the global thread until it is {@code want}. */
    private static void awaitStorm(Scheduler s, World world, boolean want, String when) throws Exception {
        long deadline = System.nanoTime() + SYNC.toNanos();
        while (true) {
            boolean storm = OnThread.global(s, STEP, world::hasStorm);
            if (storm == want) {
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("World.hasStorm() is " + storm + " " + when + " after " + SYNC.toSeconds()
                        + " s");
            }
            Thread.sleep(100);
        }
    }

    /**
     * The first calendar time from {@code from} whose climate target is {@code raining} for {@link #HOLD_TICKS} after
     * it (with the weather flag on).
     */
    private static OptionalLong find(long seed, long from, boolean raining) {
        for (long t = from; t < from + SEARCH_TICKS; t += SEARCH_STEP) {
            if (holds(seed, t, raining)) {
                return OptionalLong.of(t);
            }
        }
        return OptionalLong.empty();
    }

    private static boolean holds(long seed, long t, boolean raining) {
        for (long u = t; u <= t + HOLD_TICKS; u += 20) {
            WeatherCycle.Target target = ClimateWeatherCycle.target(seed, at(u), true);
            if (target.raining() != raining) {
                return false;
            }
        }
        return true;
    }

    private static ClockSnapshot at(long calendarTicks) {
        return new ClockSnapshot(1, calendarTicks, calendarTicks, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true);
    }

    /** The vanilla weather cycle rule's value, or null when this version has neither name. Global thread. */
    @SuppressWarnings({"unchecked", "deprecation", "removal"})
    private static Boolean ruleValue(World world) {
        for (GameRule<?> rule : GameRule.values()) {
            if (rule.getType() != Boolean.class) {
                continue;
            }
            String key = rule.getKey().getKey();
            String name = rule.getName();
            if (key.equals("advance_weather") || name.equals("advance_weather")
                    || key.equalsIgnoreCase("doWeatherCycle") || name.equals("doWeatherCycle")) {
                return world.getGameRuleValue((GameRule<Boolean>) rule);
            }
        }
        return null;
    }

    /** Puts the vanilla weather rule back (global thread). */
    @SuppressWarnings({"unchecked", "deprecation", "removal"})
    private static void restoreRule(World world, Boolean value) {
        if (value == null) {
            return;
        }
        for (GameRule<?> rule : GameRule.values()) {
            String key = rule.getKey().getKey();
            String name = rule.getName();
            if (rule.getType() == Boolean.class && (key.equals("advance_weather") || name.equals("advance_weather")
                    || key.equalsIgnoreCase("doWeatherCycle") || name.equals("doWeatherCycle"))) {
                world.setGameRule((GameRule<Boolean>) rule, value);
                return;
            }
        }
    }

    /** Moves the calendar forward to {@code target} on the global thread. */
    private static void moveTo(TfpPlugin tfp, Scheduler s, long target) throws Exception {
        long now = tfp.clock().get().calendarTicks();
        check(target > now, "the calendar already passed " + target + " (now " + now + ")");
        long delta = target - now;
        check(delta <= Integer.MAX_VALUE, "skip of " + delta + " ticks is too long");
        CalendarService.Skipped skipped = OnThread.global(s, STEP,
                () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.TICKS, (int) delta)));
        check(skipped != null, "the calendar is not loaded");
        long after = tfp.clock().get().calendarTicks();
        check(after >= target && after <= target + HOLD_TICKS,
                "the calendar is at " + after + " after the skip to " + target);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
