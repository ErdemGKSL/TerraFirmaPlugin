/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarCodec;
import dev.tfp.core.calendar.CalendarState;
import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-CLIM-13 on MockBukkit: {@code /tfp time set dayLength|monthLength|rain|clear} change the calendar on its thread,
 * persist the rate with it, and answer with TFC's texts.
 */
class TimeRateCommandsTest extends MockServerTest {
    private static final Logger LOG = Logger.getLogger("time-rate-test");
    private static final long NOON = 966_000L + 6_000L;

    private WorldMock world;
    private GlobalClockHolder clock;
    private CalendarService service;
    private final AtomicBoolean onGlobal = new AtomicBoolean(true);
    private final List<Runnable> globalTasks = new ArrayList<>();
    private final List<Runnable> cpuTasks = new ArrayList<>();
    private TimeRateCommands commands;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("world");
        clock = new GlobalClockHolder();
        service = new CalendarService(clock, () -> world, () -> true, LOG);
        commands = new TimeRateCommands(() -> service, scheduler(), onGlobal::get);
    }

    private Scheduler scheduler() {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "global" -> {
                        globalTasks.add((Runnable) args[0]);
                        yield null;
                    }
                    case "cpu" -> {
                        cpuTasks.add((Runnable) args[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private void loadAt(long calendarTicks) {
        CalendarState s = new CalendarState(100_000L, calendarTicks, 0, 24, 8, false);
        world.getPersistentDataContainer().set(CalendarService.KEY, PersistentDataType.BYTE_ARRAY,
                CalendarCodec.CODEC.toBytes(s));
        service.tick();
    }

    private static Reply now(java.util.concurrent.CompletableFuture<Reply> f) {
        assertThat(f).isDone();
        return f.join();
    }

    @Test
    void notLoadedAnswersSo() {
        assertThat(now(commands.setDayLength(20)).key()).isEqualTo("time.not_loaded");
        assertThat(now(commands.setMonthLength(30)).key()).isEqualTo("time.not_loaded");
        assertThat(new TimeRateCommands(() -> null, scheduler(), () -> true).setDayLength(20).join().key())
                .isEqualTo("command.not_enabled");
    }

    /** dayLength: the rate changes, nothing else moves, it is saved, and a reloaded calendar keeps it (TFC NBT). */
    @Test
    void dayLengthIsAppliedAndPersisted() {
        loadAt(NOON);
        Reply r = now(commands.setDayLength(20));
        assertThat(r.ok()).isTrue();
        assertThat(r.key()).isEqualTo("time.set.day_length");
        assertThat(r.args()).containsExactly(20);
        assertThat(service.state().dayLengthMinutes()).isEqualTo(20);
        assertThat(service.state().calendarTicks()).isEqualTo(NOON);
        service.tick(); // vanilla rate: 20 player ticks = 20 calendar ticks
        assertThat(service.state().calendarTicks()).isEqualTo(NOON + 20);

        Reply off = now(commands.setDayLength(0));
        assertThat(off.key()).isEqualTo("time.set.day_length_disabled");
        long frozen = service.state().calendarTicks();
        long players = service.state().playerTicks();
        service.tick();
        assertThat(service.state().calendarTicks()).isEqualTo(frozen);
        assertThat(service.state().playerTicks()).isEqualTo(players + 20);

        // persisted: a new service on the same world loads the disabled rate
        CalendarService reloaded = new CalendarService(new GlobalClockHolder(), () -> world, () -> true, LOG);
        reloaded.tick();
        assertThat(reloaded.state().dayLengthMinutes()).isZero();
        assertThat(reloaded.state().calendarTicks()).isEqualTo(frozen);
    }

    /** The disabled rate holds the daylight rule off although the calendar runs (the sun stops). */
    @Test
    void disabledRateStopsTheSun() {
        CalendarState running = new CalendarState(0, NOON, 0, 0, 8, true);
        assertThat(CalendarModule.forDaylight(running).running()).isFalse();
        CalendarState normal = running.withDayLength(24);
        assertThat(CalendarModule.forDaylight(normal)).isSameAs(normal);
    }

    /** monthLength: TFC's setMonthLength on the calendar's state, published and saved. */
    @Test
    void monthLengthMovesTheDate() {
        loadAt(NOON);
        Reply r = now(commands.setMonthLength(30));
        assertThat(r.key()).isEqualTo("time.set.month_length");
        // TFC multiplies the day index floor(12000 / 192000 * 30) = 1 by one HOUR: noon becomes 13:00 (kept as TFC)
        assertThat(r.args()).containsExactly(30, "June 1, 1000 13:00");
        assertThat(service.state().daysInMonth()).isEqualTo(30);
        assertThat(service.state().calendarTicks()).isEqualTo(5L * 30 * 24_000 + 1_000 + 12_000);
        assertThat(clock.get().daysInMonth()).isEqualTo(30);
        CalendarState saved = CalendarCodec.CODEC.fromBytes(
                world.getPersistentDataContainer().get(CalendarService.KEY, PersistentDataType.BYTE_ARRAY));
        assertThat(saved.daysInMonth()).isEqualTo(30);
    }

    /** rain / clear: the calendar moves by TFC's search at the sender's position, or stays when nothing is found. */
    @Test
    void weatherSearchSkipsLikeTfc() {
        loadAt(NOON);
        Location at = new Location(world, 1_000, 64, -2_000);
        long seed = world.getSeed();
        OverworldClimate climate = OverworldClimate.forWorldSeed(seed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        ChunkClimate chunk = ChunkClimateSource.climateAt(seed, 1_000, -2_000);
        for (boolean rain : new boolean[] {true, false}) {
            long start = service.state().calendarTicks();
            long expected = TimeCommand.weatherSkip(rain, start,
                    climate.instantRainfall(chunk, start, 8), climate::rainIntensity);
            Reply r = now(commands.skipToWeather(at, rain));
            if (expected < 0) {
                assertThat(r.key()).isEqualTo("time.weather.not_found");
                assertThat(service.state().calendarTicks()).isEqualTo(start);
            } else {
                assertThat(r.key()).isEqualTo("time.set.done");
                assertThat(r.args().get(1)).isEqualTo(expected);
                assertThat(service.state().calendarTicks()).isEqualTo(start + expected);
            }
        }
    }

    /** Outside an overworld TFC's model has no rain: rain is never found, clear holds now (skip 0). */
    @Test
    void noRainOutsideTheOverworld() {
        loadAt(NOON);
        World nether = server.addSimpleWorld("nether");
        ((WorldMock) nether).setEnvironment(World.Environment.NETHER);
        Location at = new Location(nether, 0, 64, 0);
        assertThat(now(commands.skipToWeather(at, true)).key()).isEqualTo("time.weather.not_found");
        Reply clear = now(commands.skipToWeather(at, false));
        assertThat(clear.key()).isEqualTo("time.set.done");
        assertThat(clear.args().get(1)).isEqualTo(0L);
        assertThat(service.state().calendarTicks()).isEqualTo(NOON);
    }

    /** Off the calendar's thread: the chunk climate is sampled on a worker, the search and skip on the global thread. */
    @Test
    void offThreadHopsToTheGlobalThread() {
        loadAt(NOON);
        onGlobal.set(false);
        var day = commands.setDayLength(1440);
        assertThat(day).isNotDone();
        assertThat(globalTasks).hasSize(1);
        globalTasks.remove(0).run();
        assertThat(day.join().args()).containsExactly(1440);
        assertThat(service.state().dayLengthMinutes()).isEqualTo(1440);

        var weather = commands.skipToWeather(null, false); // console form: the calendar world's spawn
        assertThat(cpuTasks).hasSize(1);
        cpuTasks.remove(0).run();
        assertThat(globalTasks).hasSize(1);
        globalTasks.remove(0).run();
        assertThat(weather).isDone();
        assertThat(weather.join().ok()).isTrue();
    }
}
