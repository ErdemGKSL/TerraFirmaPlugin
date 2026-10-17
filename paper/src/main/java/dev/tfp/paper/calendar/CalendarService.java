/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/calendar/ServerCalendar.java @ c3b4d9536
 */
package dev.tfp.paper.calendar;

import dev.tfp.core.calendar.CalendarCodec;
import dev.tfp.core.calendar.CalendarState;
import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.persist.PersistException;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The global clock service (M2-CAL-01): owns the {@link CalendarState}, advances it on the global region
 * scheduler and publishes each step to the {@link GlobalClockHolder} (volatile, lock-free reads anywhere).
 *
 * <ul>
 *   <li><b>Thread.</b> {@link #tick()} and {@link #save()} run on the global region thread (Paper: the main
 *       thread); {@link #shutdown()} runs from {@code onDisable} after the server stopped scheduling. The state
 *       lives only here; everyone else reads the published snapshot.</li>
 *   <li><b>Start.</b> Worlds are not loaded when we enable (load: STARTUP), so the first global tick reads the
 *       state from the first world's PDC ({@link #KEY}). A missing key means a new calendar:
 *       {@link CalendarState#defaults()} (TFC {@code resetToDefault}), saved at once.</li>
 *   <li><b>Running.</b> The calendar runs while at least one player is online (TFC {@code arePlayersLoggedOn}).
 *       {@link #setRunWhenEmpty} overrides that (self-test; a config option later).</li>
 *   <li><b>Advance.</b> Every {@value #PERIOD_TICKS} server ticks by {@value #PERIOD_TICKS} player ticks.</li>
 *   <li><b>Persistence.</b> Every {@value #SAVE_EVERY_RUNS} advances, and at shutdown; the server writes the world
 *       PDC with {@code level.dat}. Data from a newer plugin version is left untouched (no writes this run);
 *       unreadable data is copied to {@link #UNREADABLE_KEY} and replaced by the defaults.</li>
 * </ul>
 */
public final class CalendarService {
    /** World PDC key of the encoded calendar ({@link CalendarCodec}). */
    public static final NamespacedKey KEY = new NamespacedKey("tfp", "calendar");
    /** Where an unreadable calendar blob is kept for inspection. */
    public static final NamespacedKey UNREADABLE_KEY = new NamespacedKey("tfp", "calendar_unreadable");
    /** Advance period in server ticks (once per second). */
    public static final long PERIOD_TICKS = 20;
    /** Save every this many advances (60 s). */
    static final long SAVE_EVERY_RUNS = 60;

    private final GlobalClockHolder clock;
    private final Supplier<World> worldSource;
    private final BooleanSupplier playersOnline;
    private final Logger logger;

    /** Null until loaded. Written on the global thread only; volatile for the shutdown save and diagnostics. */
    private volatile CalendarState state;
    private volatile World world;
    private volatile boolean runWhenEmpty;
    /** The PDC holds data from a newer version: never overwrite it. */
    private volatile boolean readOnly;
    private long runs;
    private volatile Scheduler.TaskHandle task;

    /**
     * @param worldSource   the world that stores the calendar (the first world), or null if none is loaded yet
     * @param playersOnline true while at least one player is online
     */
    public CalendarService(GlobalClockHolder clock, Supplier<World> worldSource, BooleanSupplier playersOnline,
            Logger logger) {
        this.clock = clock;
        this.worldSource = worldSource;
        this.playersOnline = playersOnline;
        this.logger = logger;
    }

    /** Schedules the advance task on the global region scheduler. */
    public void start(Scheduler scheduler) {
        task = scheduler.globalRepeating(1, PERIOD_TICKS, this::tick);
    }

    /** One step: load on first call, otherwise advance, publish, and save periodically. Global thread. */
    void tick() {
        CalendarState s = state;
        if (s == null) {
            if (!load()) {
                return;
            }
            state.publishTo(clock);
            return;
        }
        boolean running = runWhenEmpty || playersOnline.getAsBoolean();
        s = s.withRunning(running).advance(PERIOD_TICKS);
        state = s;
        s.publishTo(clock);
        if (++runs % SAVE_EVERY_RUNS == 0) {
            save();
        }
    }

    private boolean load() {
        World w = worldSource.get();
        if (w == null) {
            return false;
        }
        world = w;
        PersistentDataContainer pdc = w.getPersistentDataContainer();
        byte[] bytes;
        try {
            bytes = pdc.get(KEY, PersistentDataType.BYTE_ARRAY);
        } catch (IllegalArgumentException e) { // a value of another type under our key
            bytes = null;
            logger.severe("World " + w.getName() + ": " + KEY + " is not a byte array; starting a new calendar");
        }
        if (bytes == null) {
            state = CalendarState.defaults();
            logger.info("New calendar in world " + w.getName() + " (TFC defaults)");
            save();
            return true;
        }
        try {
            state = CalendarCodec.CODEC.fromBytes(bytes);
        } catch (PersistException.NewerVersion e) {
            readOnly = true;
            state = CalendarState.defaults();
            logger.log(Level.SEVERE, "World " + w.getName() + ": the calendar was saved by a newer TerraFirmaPlugin; "
                    + "running on defaults and NOT saving it this run", e);
        } catch (PersistException e) {
            pdc.set(UNREADABLE_KEY, PersistentDataType.BYTE_ARRAY, bytes);
            state = CalendarState.defaults();
            logger.log(Level.SEVERE, "World " + w.getName() + ": unreadable calendar (kept as " + UNREADABLE_KEY
                    + "); starting a new one", e);
            save();
        }
        return true;
    }

    /**
     * Writes the state to the world PDC. Global thread (or at shutdown).
     *
     * @return false if nothing was written (not loaded yet, or newer data must be kept)
     */
    public boolean save() {
        CalendarState s = state;
        World w = world;
        if (s == null || w == null || readOnly) {
            return false;
        }
        w.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE_ARRAY, CalendarCodec.CODEC.toBytes(s));
        return true;
    }

    /** Stops advancing and saves; from {@code onDisable} (worlds are saved after plugins disable). */
    public void shutdown() {
        Scheduler.TaskHandle t = task;
        if (t != null) {
            t.cancel();
        }
        try {
            save();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Cannot save the calendar", e);
        }
    }

    /** The result of {@link #skip}: the calendar ticks moved and the state after the move. */
    public record Skipped(long ticks, CalendarState state) {
    }

    /**
     * {@code /tfp time}: moves the calendar forward by the ticks {@code delta} computes from the current state (TFC
     * {@code skipForwardBy}), whether or not it runs. Publishes and saves at once. Global thread only.
     *
     * @return the move, or null before the first load
     */
    public Skipped skip(TimeCommand.Delta delta) {
        CalendarState s = state;
        if (s == null) {
            return null;
        }
        long ticks = delta.ticksFrom(s);
        CalendarState next = s.skipForward(ticks);
        state = next;
        next.publishTo(clock);
        save();
        return new Skipped(ticks, next);
    }

    /** Runs the calendar even with no player online; returns the previous value. */
    public boolean setRunWhenEmpty(boolean value) {
        boolean previous = runWhenEmpty;
        runWhenEmpty = value;
        return previous;
    }

    /** The world holding the calendar, or null before the first load. */
    public World world() {
        return world;
    }

    /** The current state (null before the first load). Diagnostics; features read the clock snapshot. */
    public CalendarState state() {
        return state;
    }
}
