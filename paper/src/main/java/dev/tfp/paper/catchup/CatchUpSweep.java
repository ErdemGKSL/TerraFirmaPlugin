/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.catchup;

import dev.tfp.core.catchup.SweepArea;
import dev.tfp.core.world.ChunkPos;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The visible-area sweep (PAR-INFRA-11): every {@link CatchUpSettings#sweepPeriodTicks} the global scheduler asks each
 * online player's own thread for its chunk; the chunks within {@link CatchUpSettings#sweepRadiusChunks} are handed to
 * their region threads, nearest first, and {@link CatchUpService#sweep} queues the due ones (a chunk seen by several
 * players, or loaded within the period, is caught up once). Unloaded chunks are skipped, never loaded.
 */
public final class CatchUpSweep {
    private final CatchUpService service;
    private final Scheduler scheduler;
    private final Supplier<? extends Collection<? extends Player>> players;
    private volatile Scheduler.TaskHandle task;

    public CatchUpSweep(CatchUpService service, Scheduler scheduler,
            Supplier<? extends Collection<? extends Player>> players) {
        this.service = Objects.requireNonNull(service, "service");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.players = Objects.requireNonNull(players, "players");
    }

    /** Starts the repeating sweep (no-op when the period is 0). */
    public void start() {
        long period = service.settings().sweepPeriodTicks();
        if (period > 0 && task == null) {
            task = scheduler.globalRepeating(period, period, this::runOnce);
        }
    }

    public void stop() {
        Scheduler.TaskHandle t = task;
        task = null;
        if (t != null) {
            t.cancel();
        }
    }

    /** One sweep over every online player. Global thread (any thread). */
    public void runOnce() {
        for (Player player : List.copyOf(players.get())) {
            scheduler.entity(player, () -> sweepAround(player), () -> {
            });
        }
    }

    /** The player's thread: hands the chunks around it to their regions. */
    void sweepAround(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        for (ChunkPos c : SweepArea.around(loc.getBlockX() >> 4, loc.getBlockZ() >> 4,
                service.settings().sweepRadiusChunks())) {
            scheduler.region(world, c.x(), c.z(), () -> service.sweep(world, c.x(), c.z()));
        }
    }
}
