/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/CalendarEventHandler.java @ c3b4d9536 (onPlayerStartSleeping,
 * onPlayersFinishedSleeping), src/main/java/net/dries007/tfc/common/commands/TFCCommands.java @ c3b4d9536
 * (the disabled doDaylightCycle game rule)
 */
package dev.tfp.paper.calendar;

import dev.tfp.core.calendar.CalendarConfig;
import dev.tfp.core.calendar.CalendarState;
import dev.tfp.core.calendar.SleepSkip;
import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.world.TimeSkipEvent;
import io.papermc.paper.event.world.WorldGameRuleChangeEvent;

/**
 * PAR-CLIM-01: calendar configuration, sleep skipping and the vanilla day time held to the calendar.
 * <ul>
 *   <li><b>Config.</b> {@value #FILE} in the plugin folder holds TFC's four calendar keys under TFC's names
 *       ({@link CalendarConfig}); written with the defaults when missing, read at enable, before the calendar's first
 *       global tick creates or loads the calendar.</li>
 *   <li><b>Day time.</b> {@link DaylightSync} on every published calendar state (global thread).</li>
 *   <li><b>Sleep.</b> TFC lets a bed be used from 8 pm to 5 am calendar time ({@link PlayerBedEnterEvent}, the player's
 *       thread, reading the published clock). When vanilla skips the night ({@link TimeSkipEvent} {@code NIGHT_SKIP},
 *       fired because the daylight rule is on while the calendar runs), the calendar skips to the next 5 am through
 *       {@link CalendarService#skip} (TFC {@code skipForwardBy}: calendar ticks plus the matching player ticks), and the
 *       event's amount is set so vanilla lands on the calendar's new day time. The amount is measured from the world time
 *       read before the skip, and the event's world is held there until vanilla applies it (PAR-CLIM-25): 26.x
 *       {@code /time set <marker>} adds it to the time read before the event and fails with "already at time marker"
 *       on 0, 1.21.11 adds it to the time read after.</li>
 *   <li><b>Vanilla {@code /time}.</b> A {@code COMMAND} skip moves the calendar forward the same way (backward sets go to
 *       the next same time of day), so the world and the calendar never part. TFC replaces {@code /time} instead.</li>
 *   <li><b>Daylight rule.</b> TFC disables {@code /gamerule doDaylightCycle}; a change of the rule that we did not make
 *       is cancelled with a message pointing at {@code /tfp time}.</li>
 * </ul>
 */
public final class CalendarModule implements FeatureModule, Listener {
    public static final String ID = "calendar";
    /** The configuration file in the plugin folder. */
    public static final String FILE = "calendar.yml";
    /** Vanilla's "you can only sleep at night" text, shown when TFC's window refuses a bed vanilla would allow. */
    static final String NO_SLEEP_KEY = "block.minecraft.bed.no_sleep";
    /** The 26.x API's world-less time-skip event (absent on 1.21.x). */
    static final String CLOCK_SKIP_EVENT = "org.bukkit.event.world.ClockTimeSkipEvent";

    static final String DEFAULT_TEXT = """
            # TerraFirmaPlugin calendar (TFC's calendar options, under TFC's names).
            # defaultMonthLength, defaultCalendarStartDay and defaultCalendarDayLength only shape the calendar of a NEW
            # world, as in TFC; an existing world keeps its stored month and day length. Changes need a restart.
            #
            # defaultMonthLength: the number of days in a month (at least 1).
            # defaultCalendarStartDay: the start date, in days after January 1, 1000 (at least 0). The default
            #   (5 * daysInMonth) = 40 starts at June 1, 1000 with the default month length. TFC also accepts -1;
            #   we do not.
            # defaultCalendarDayLength: the length of an in-game day in minutes (at least 1). 24 makes one in-game
            #   hour one minute of real time at 20 TPS.
            # enableTimeStopWhenServerEmpty: stop time while no player is online, so food does not decay, the
            #   calendar does not advance and the sun does not move. Disable at your own risk.
            defaultMonthLength: 8
            defaultCalendarStartDay: 40
            defaultCalendarDayLength: 24
            enableTimeStopWhenServerEmpty: true
            """;

    private volatile CalendarService service;
    private volatile DaylightSync sync;
    private volatile GlobalClockHolder clock;
    private volatile Scheduler scheduler;
    private volatile Messages messages;
    private volatile Logger logger;
    private volatile BooleanSupplier onGlobalThread = () -> true;
    private volatile Supplier<? extends List<World>> worlds = org.bukkit.Bukkit::getWorlds;
    private final AtomicBoolean unknownReasonLogged = new AtomicBoolean();

    public CalendarModule() {
    }

    /** For tests: the module wired without a {@link ModuleContext}. */
    CalendarModule(CalendarService service, DaylightSync sync, GlobalClockHolder clock, Scheduler scheduler,
            Messages messages, Logger logger, BooleanSupplier onGlobalThread) {
        this.service = service;
        this.sync = sync;
        this.clock = clock;
        this.scheduler = scheduler;
        this.messages = messages;
        this.logger = logger;
        this.onGlobalThread = onGlobalThread;
        service.onChange(st -> sync.apply(forDaylight(st)));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) throws IOException {
        CalendarService cal = ctx.plugin() instanceof TfpPlugin tfp ? tfp.calendar() : null;
        if (cal == null) {
            throw new IllegalStateException("no calendar service");
        }
        logger = ctx.logger();
        clock = ctx.clock();
        scheduler = ctx.scheduler();
        messages = ctx.messages();
        onGlobalThread = org.bukkit.Bukkit::isGlobalTickThread;
        cal.configure(loadConfig(ctx.plugin().getDataFolder().toPath().resolve(FILE), logger));
        Supplier<List<World>> serverWorlds = () -> ctx.plugin().getServer().getWorlds();
        worlds = serverWorlds;
        DaylightSync s = new DaylightSync(serverWorlds);
        if (DaylightSync.DAYLIGHT == null) {
            logger.warning("Calendar: no daylight game rule on this server version; the sun is held by the time alone");
        }
        sync = s;
        service = cal;
        cal.onChange(st -> s.apply(forDaylight(st)));
        ctx.listen(this);
        listenForClockSkips(ctx);
    }

    /**
     * PAR-CLIM-13: with TFC's disabled calendar tick rate ({@code /tfp time set dayLength disabled}) the calendar runs
     * but its time stands still, so the daylight rule is held off as if it were arrested (the sun stops, TFC's "daylight
     * cycle has been disabled").
     */
    static CalendarState forDaylight(CalendarState st) {
        return st.rateDisabled() && st.running() ? st.withRunning(false) : st;
    }

    /** PAR-CLIM-22: on the 26.x API, also handle the world-less {@code ClockTimeSkipEvent} ({@link #onClockTimeSkip}). */
    private void listenForClockSkips(ModuleContext ctx) {
        Class<? extends Event> clockEvent;
        try {
            clockEvent = Class.forName(CLOCK_SKIP_EVENT, false, TimeSkipEvent.class.getClassLoader())
                    .asSubclass(Event.class);
        } catch (ClassNotFoundException | ClassCastException e) {
            return; // 1.21.x: TimeSkipEvent is the only time-skip event
        }
        ctx.plugin().getServer().getPluginManager().registerEvent(clockEvent, this, EventPriority.HIGH,
                (listener, event) -> onClockTimeSkip(event), ctx.plugin(), true);
        logger.fine("Calendar: listening for " + CLOCK_SKIP_EVENT);
    }

    @Override
    public void disable() {
        CalendarService cal = service;
        if (cal != null) {
            cal.onChange(st -> { });
        }
    }

    /** Reads {@code file}, writing it with the defaults first when missing. A broken file gives TFC's defaults. */
    static CalendarConfig loadConfig(Path file, Logger logger) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, DEFAULT_TEXT, StandardCharsets.UTF_8);
            logger.info("Wrote default " + FILE);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException e) {
            logger.log(Level.SEVERE, FILE + " is not valid YAML; using TFC's calendar defaults", e);
            return CalendarConfig.DEFAULTS;
        }
        List<String> warnings = new ArrayList<>();
        CalendarConfig config = CalendarConfig.parse(yaml.getValues(false), warnings);
        for (String w : warnings) {
            logger.warning(FILE + ": " + w);
        }
        return config;
    }

    /**
     * TFC {@code onPlayerStartSleeping}: beds work from 8 pm to 5 am calendar time only. The player's thread. Not
     * {@code ignoreCancelled}: Paper reports vanilla's own time refusal as a cancelled event, which TFC's window may
     * overturn; a denial by another plugin ({@code useBed() == DENY}) is kept.
     */
    @SuppressWarnings("deprecation") // BedEnterResult: the one form both 1.21.4 and 1.21.11+ offer
    @EventHandler(priority = EventPriority.HIGH)
    public void onBedEnter(PlayerBedEnterEvent event) {
        PlayerBedEnterEvent.BedEnterResult result = event.getBedEnterResult();
        if (event.useBed() == Event.Result.DENY) {
            return;
        }
        if (result != PlayerBedEnterEvent.BedEnterResult.OK
                && result != PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_NOW) {
            return; // TFC only replaces vanilla's time verdict
        }
        if (event.getPlayer().getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        GlobalClockHolder c = clock;
        ClockSnapshot now = c == null ? null : c.get();
        if (now == null || !now.isSet()) {
            return;
        }
        if (SleepSkip.canSleepAt(now.calendarTicks())) {
            if (result == PlayerBedEnterEvent.BedEnterResult.NOT_POSSIBLE_NOW) {
                event.setUseBed(Event.Result.ALLOW);
            }
        } else if (result == PlayerBedEnterEvent.BedEnterResult.OK) {
            event.setUseBed(Event.Result.DENY); // vanilla shows no text for a plugin denial
            event.getPlayer().sendActionBar(Component.translatable(NO_SLEEP_KEY));
        }
    }

    /**
     * Vanilla's night skip and {@code /time} become calendar skips; our own time changes ({@code CUSTOM}) and other
     * worlds are left alone. The calendar is written on the global thread only: where the event fires elsewhere, the
     * skip is queued there and the amount is predicted from the published clock. The reason is read through
     * {@link TimeSkipReasons} (PAR-CLIM-22): the 26.x API moved {@code getSkipReason()} to {@code ClockTimeSkipEvent}
     * with a different return type.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        skip(TimeSkipReasons.of(event), event.getWorld(), event.getSkipAmount(), event::setSkipAmount);
    }

    /**
     * PAR-CLIM-22: the world-less {@code ClockTimeSkipEvent} of the 26.x API, which Paper fires for {@code /time}
     * instead of {@code TimeSkipEvent} when {@code time.affects-all-worlds} is on in {@code paper-global.yml}. The
     * shared clock is measured against the first overworld-type world, as the calendar's own world. Registered by
     * {@link #enable} only where that class exists.
     */
    void onClockTimeSkip(Event event) {
        if (event instanceof TimeSkipEvent || (event instanceof Cancellable c && c.isCancelled())
                || !TimeSkipReasons.hasAmount(event)) {
            return;
        }
        World world = firstNormalWorld();
        if (world == null) {
            return;
        }
        skip(TimeSkipReasons.of(event), world, TimeSkipReasons.amount(event),
                amount -> TimeSkipReasons.setAmount(event, amount));
    }

    private World firstNormalWorld() {
        for (World w : worlds.get()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                return w;
            }
        }
        return null;
    }

    private void skip(TimeSkipReasons.Reason reason, World world, long skipAmount, LongConsumer setSkipAmount) {
        DaylightSync s = sync;
        CalendarService cal = service;
        if (s == null || cal == null || s.applying() || world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        if (reason == TimeSkipReasons.Reason.UNKNOWN) {
            Logger log = logger;
            if (unknownReasonLogged.compareAndSet(false, true) && log != null) {
                log.warning("Calendar: a time skip with an unknown reason was left alone (treated as CUSTOM)");
            }
            return;
        }
        if (reason == TimeSkipReasons.Reason.CUSTOM) {
            return;
        }
        boolean night = reason == TimeSkipReasons.Reason.NIGHT_SKIP;
        TimeCommand.Delta delta;
        if (night) {
            delta = st -> SleepSkip.calendarTicksSlept(st.calendarTicks());
        } else {
            long calendarSkip = SleepSkip.calendarSkipForVanilla(skipAmount);
            delta = st -> calendarSkip;
        }
        if (onGlobalThread.getAsBoolean()) {
            long before = world.getFullTime();
            CalendarService.Skipped moved = cal.skip(delta);
            if (moved == null) {
                return; // not loaded yet: vanilla's skip stands, the first sync will hold the world to the calendar
            }
            // PAR-CLIM-25: the skip's DaylightSync already moved the world. Put the event's world back to the time
            // vanilla read, so the amount is non-zero (26.x /time set <marker> fails on 0) and lands on the calendar
            // whether vanilla adds it to the time read before the event (26.x) or after it (1.21.11).
            if (world.getFullTime() != before) {
                world.setFullTime(before);
            }
            setSkipAmount.accept(SleepSkip.vanillaDayTime(moved.state().calendarTicks()) - before);
            return;
        }
        ClockSnapshot now = clock.get();
        if (!now.isSet()) {
            return;
        }
        long predicted = now.calendarTicks() + (night
                ? SleepSkip.calendarTicksSlept(now.calendarTicks())
                : SleepSkip.calendarSkipForVanilla(skipAmount));
        setSkipAmount.accept(SleepSkip.vanillaDayTime(predicted) - world.getFullTime());
        scheduler.global(() -> cal.skip(delta));
    }

    /** TFC disables {@code /gamerule doDaylightCycle}: the calendar owns the rule. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGameRuleChange(WorldGameRuleChangeEvent event) {
        DaylightSync s = sync;
        if (s == null || s.applying() || !DaylightSync.isDaylightRule(event.getGameRule())
                || event.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        event.setCancelled(true);
        if (event.getCommandSender() != null) {
            Messages m = messages;
            String text = m == null ? "The calendar controls this game rule; use /tfp time."
                    : m.get(event.getCommandSender(), "calendar.daylight_rule.disabled", event.getGameRule().getName());
            event.getCommandSender().sendMessage(text);
        }
    }

    /** The state the calendar holds now (tests, diagnostics). */
    CalendarState state() {
        CalendarService cal = service;
        return cal == null ? null : cal.state();
    }
}
