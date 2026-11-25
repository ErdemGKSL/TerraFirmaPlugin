/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prey;

import java.util.function.LongSupplier;
import org.bukkit.Bukkit;

/**
 * The server tick the animal sensing runs on (PAR-FAUNA-33): the sensor period ({@code seenAt}), the shared section scans
 * ({@link NearbyScan}), the shared sightings ({@link Sightings}) and the {@link PathBudget}. Vanilla's sensors and goal
 * selector count game ticks, not calendar time. The calendar's player ticks stand still while no player is online (TFC's
 * arrested calendar), so stamps taken from them would freeze every reading and cache, and one tick's path budget would
 * never refill. TFC's own timestamps (flee expiry, panic, breeding, temptation cooldown) stay on the calendar.
 *
 * <p>{@code Bukkit.getCurrentTick()}; where a server does not support it, 50 ms buckets of {@link System#nanoTime}.
 * Thread-safe.
 */
public final class Ticks {
    private static volatile boolean serverTicks = true;
    private static volatile LongSupplier source = Ticks::server;

    private Ticks() {
    }

    public static long now() {
        return source.getAsLong();
    }

    private static long server() {
        if (serverTicks) {
            try {
                return Bukkit.getCurrentTick();
            } catch (RuntimeException | LinkageError unsupported) {
                serverTicks = false;
            }
        }
        return System.nanoTime() / 50_000_000L;
    }

    /** Which source {@link #now} reads ("server ticks" or "50 ms buckets"), for the load tests. */
    public static String source() {
        return serverTicks ? "server ticks" : "50 ms buckets";
    }

    /** Test seam: the tick source; null restores the server's. */
    public static void forTest(LongSupplier ticks) {
        source = ticks == null ? Ticks::server : ticks;
    }
}
