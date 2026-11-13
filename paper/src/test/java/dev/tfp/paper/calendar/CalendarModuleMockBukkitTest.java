/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarCodec;
import dev.tfp.core.calendar.CalendarConfig;
import dev.tfp.core.calendar.CalendarState;
import dev.tfp.core.calendar.SleepSkip;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import io.papermc.paper.block.bed.BedEnterAction;
import io.papermc.paper.block.bed.BedRuleResult;
import io.papermc.paper.event.world.WorldGameRuleChangeEvent;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-CLIM-01 on MockBukkit: vanilla's night skip advances the calendar to 5 am (TFC {@code onPlayersFinishedSleeping}
 * through {@code skipForwardBy}), the world's day time and daylight rule follow the calendar, beds follow TFC's window,
 * the daylight rule cannot be changed by others, and the config file shapes a new calendar.
 */
@SuppressWarnings("deprecation") // BedEnterResult, as the module reads it
class CalendarModuleMockBukkitTest extends MockServerTest {
    private static final Logger LOG = Logger.getLogger("calendar-test");
    /** June 1, 1000 (day 40), 21:00. */
    private static final long NINE_PM = 40L * 24_000 + 21_000;

    private WorldMock world;
    private GlobalClockHolder clock;
    private final AtomicBoolean online = new AtomicBoolean(true);
    private final AtomicBoolean onGlobal = new AtomicBoolean(true);
    private final List<Runnable> globalTasks = new ArrayList<>();
    private CalendarService service;
    private DaylightSync sync;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("world");
        clock = new GlobalClockHolder();
        service = new CalendarService(clock, () -> world, online::get, LOG);
        sync = new DaylightSync(() -> List.<World>of(world));
        CalendarModule module = new CalendarModule(service, sync, clock, scheduler(), Messages.fromJar(), LOG,
                onGlobal::get);
        server.getPluginManager().registerEvents(module, plugin);
    }

    private Scheduler scheduler() {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("global")) {
                        globalTasks.add((Runnable) args[0]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /** Stores a calendar at {@code calendarTicks} in the world and lets the service load it. */
    private void loadAt(long calendarTicks) {
        CalendarState s = new CalendarState(100_000L, calendarTicks, 0, 24, 8, false);
        world.getPersistentDataContainer().set(CalendarService.KEY, PersistentDataType.BYTE_ARRAY,
                CalendarCodec.CODEC.toBytes(s));
        service.tick();
    }

    @Test
    void loadHoldsTheWorldToTheCalendar() {
        loadAt(NINE_PM);
        assertThat(world.getFullTime()).isEqualTo(NINE_PM + 18_000);
        assertThat(world.getGameRuleValue(DaylightSync.DAYLIGHT)).isTrue();
        service.tick(); // one second: 20 player ticks = 16 calendar ticks (+ carry)
        assertThat(world.getFullTime()).isEqualTo(service.state().calendarTicks() + 18_000);
        assertThat(service.state().calendarTicks()).isEqualTo(NINE_PM + 16);
    }

    @Test
    void emptyServerStopsTheCalendarAndTheSun() {
        loadAt(NINE_PM);
        online.set(false);
        service.tick();
        assertThat(service.state().running()).isFalse();
        assertThat(service.state().calendarTicks()).isEqualTo(NINE_PM);
        assertThat(world.getGameRuleValue(DaylightSync.DAYLIGHT)).isFalse();
        assertThat(world.getFullTime()).isEqualTo(NINE_PM + 18_000);
        online.set(true);
        service.tick();
        assertThat(world.getGameRuleValue(DaylightSync.DAYLIGHT)).isTrue();
    }

    @Test
    void timeStopOffRunsTheCalendarWithNobodyOnline() {
        service.configure(new CalendarConfig(8, 40, 24, false));
        loadAt(NINE_PM);
        online.set(false);
        service.tick();
        assertThat(service.state().running()).isTrue();
        assertThat(service.state().calendarTicks()).isEqualTo(NINE_PM + 16);
        assertThat(world.getGameRuleValue(DaylightSync.DAYLIGHT)).isTrue();
    }

    @Test
    void nightSkipAdvancesTheCalendarToFiveAm() {
        loadAt(NINE_PM);
        long before = service.state().playerTicks();
        TimeSkipEvent event = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.NIGHT_SKIP, 9_000);
        server.getPluginManager().callEvent(event);

        long wake = 41L * 24_000 + 5_000; // June 2, 5 am
        assertThat(service.state().calendarTicks()).isEqualTo(wake);
        assertThat(service.state().playerTicks()).isEqualTo(before + 8_000 * 24 / 20); // TFC skipForwardBy
        assertThat(clock.get().calendarTicks()).isEqualTo(wake); // published: the HUD date follows
        // vanilla adds the amount to its day time after the event: it must land on the calendar
        assertThat(world.getFullTime() + event.getSkipAmount()).isEqualTo(SleepSkip.vanillaDayTime(wake));
        assertThat(event.isCancelled()).isFalse(); // vanilla still wakes the players
    }

    @Test
    void nightSkipOffTheGlobalThreadQueuesTheSkip() {
        loadAt(NINE_PM);
        onGlobal.set(false);
        TimeSkipEvent event = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.NIGHT_SKIP, 9_000);
        server.getPluginManager().callEvent(event);
        long wake = 41L * 24_000 + 5_000;
        assertThat(world.getFullTime() + event.getSkipAmount()).isEqualTo(SleepSkip.vanillaDayTime(wake));
        assertThat(service.state().calendarTicks()).isEqualTo(NINE_PM); // not yet: global thread only
        assertThat(globalTasks).hasSize(1);
        globalTasks.getFirst().run();
        assertThat(service.state().calendarTicks()).isEqualTo(wake);
    }

    @Test
    void vanillaTimeCommandMovesTheCalendarForwardOnly() {
        loadAt(NINE_PM);
        TimeSkipEvent add = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.COMMAND, 1_000);
        server.getPluginManager().callEvent(add);
        assertThat(service.state().calendarTicks()).isEqualTo(NINE_PM + 1_000);
        TimeSkipEvent back = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.COMMAND, -2_000);
        server.getPluginManager().callEvent(back);
        assertThat(service.state().calendarTicks()).isEqualTo(NINE_PM + 1_000 + 22_000);
        assertThat(world.getFullTime() + back.getSkipAmount())
                .isEqualTo(SleepSkip.vanillaDayTime(service.state().calendarTicks()));
    }

    @Test
    void ownAndOtherWorldSkipsAreIgnored() {
        loadAt(NINE_PM);
        TimeSkipEvent custom = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.CUSTOM, 5_000);
        server.getPluginManager().callEvent(custom);
        WorldMock nether = new WorldMock();
        nether.setEnvironment(World.Environment.NETHER);
        server.getPluginManager().callEvent(new TimeSkipEvent(nether, TimeSkipEvent.SkipReason.NIGHT_SKIP, 5_000));
        assertThat(service.state().calendarTicks()).isEqualTo(NINE_PM);
        assertThat(custom.getSkipAmount()).isEqualTo(5_000);
    }

    @Test
    void bedsFollowTfcsWindow() {
        PlayerMock player = server.addPlayer();
        player.teleport(world.getSpawnLocation());
        loadAt(40L * 24_000 + 12_000); // noon
        PlayerBedEnterEvent noon = bed(player, PlayerBedEnterEvent.BedEnterResult.OK);
        server.getPluginManager().callEvent(noon);
        assertThat(noon.useBed()).isEqualTo(Event.Result.DENY);

        loadAgainAt(40L * 24_000 + 19_500); // 7:30 pm: vanilla night, TFC not yet
        PlayerBedEnterEvent early = bed(player, PlayerBedEnterEvent.BedEnterResult.OK);
        server.getPluginManager().callEvent(early);
        assertThat(early.useBed()).isEqualTo(Event.Result.DENY);

        loadAgainAt(40L * 24_000 + 20_000); // 8 pm
        PlayerBedEnterEvent night = bed(player, PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_NOW);
        server.getPluginManager().callEvent(night);
        assertThat(night.useBed()).isEqualTo(Event.Result.ALLOW);

        PlayerBedEnterEvent unsafe = bed(player, PlayerBedEnterEvent.BedEnterResult.NOT_SAFE);
        server.getPluginManager().callEvent(unsafe);
        assertThat(unsafe.useBed()).isEqualTo(Event.Result.DEFAULT); // only the time verdict is TFC's

        PlayerBedEnterEvent denied = bed(player, PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_NOW);
        denied.setUseBed(Event.Result.DENY); // another plugin's refusal stands
        server.getPluginManager().callEvent(denied);
        assertThat(denied.useBed()).isEqualTo(Event.Result.DENY);
    }

    private PlayerBedEnterEvent bed(PlayerMock player, PlayerBedEnterEvent.BedEnterResult result) {
        BedRuleResult canSleep = result == PlayerBedEnterEvent.BedEnterResult.OK ? BedRuleResult.ALLOWED
                : result == PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_NOW ? BedRuleResult.TOO_MUCH_LIGHT
                : BedRuleResult.ALLOWED;
        BedEnterAction action = (BedEnterAction) Proxy.newProxyInstance(BedEnterAction.class.getClassLoader(),
                new Class<?>[] {BedEnterAction.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "canSleep", "canSetSpawn" -> canSleep;
                    default -> null;
                });
        return new PlayerBedEnterEvent(player, world.getBlockAt(0, 64, 0), result, action);
    }

    private void loadAgainAt(long calendarTicks) {
        clock.publish(100_000L, calendarTicks, 8, true);
    }

    @Test
    void daylightRuleCannotBeChangedByOthers() {
        loadAt(NINE_PM);
        PlayerMock op = server.addPlayer();
        WorldGameRuleChangeEvent change = new WorldGameRuleChangeEvent(world, op, DaylightSync.DAYLIGHT, "false");
        server.getPluginManager().callEvent(change);
        assertThat(change.isCancelled()).isTrue();
        assertThat(op.nextMessage()).contains("/tfp time");
    }

    @Test
    void configFileIsWrittenAndShapesANewCalendar(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("plugin").resolve(CalendarModule.FILE);
        assertThat(CalendarModule.loadConfig(file, LOG)).isEqualTo(CalendarConfig.DEFAULTS);
        assertThat(file).exists();
        Files.writeString(file, Files.readString(file).replace("defaultMonthLength: 8", "defaultMonthLength: 30")
                .replace("defaultCalendarStartDay: 40", "defaultCalendarStartDay: -1"), StandardCharsets.UTF_8);
        CalendarConfig c = CalendarModule.loadConfig(file, LOG);
        assertThat(c).isEqualTo(new CalendarConfig(30, 40, 24, true)); // -1 refused, TFC's default kept

        service.configure(c);
        service.tick(); // no stored calendar: a new one from the config
        assertThat(service.state().daysInMonth()).isEqualTo(30);
        assertThat(world.getFullTime()).isEqualTo(SleepSkip.vanillaDayTime(40L * 24_000 + 6_000));
    }
}
