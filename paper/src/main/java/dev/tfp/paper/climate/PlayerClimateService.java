/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.ClimateReading;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * The player's ambient temperature for the HUD (M2-CLIM-03), decide async, apply sync:
 * <ol>
 *   <li><b>Throttle (global thread).</b> Every {@value #PERIOD_TICKS} ticks, for each online player, a task goes to that
 *       player's own thread. A player who has not moved still gets a fresh reading, because the calendar moves the
 *       daily temperature swing.</li>
 *   <li><b>Capture (player thread).</b> Reads the block position and the world seed, and nothing else. Only
 *       the overworld has the model; other dimensions publish an unknown reading.</li>
 *   <li><b>Compute (worker pool).</b> {@link ClimateReading#of} is pure: {@link OverworldClimate} with the published
 *       {@link ClockSnapshot} and the chunk's {@link ChunkClimate}, read from the seed by {@link ChunkClimateSource}.</li>
 *   <li><b>Apply (player thread).</b> Publishes the value into the player's {@link HudSnapshot} in the
 *       {@link HudSnapshotCache}, which the BetterHud placeholder {@code tfp_ambient_temp} reads from BetterHud's
 *       thread. Readings from one player are published in clock order: an older reading that finishes late is dropped.</li>
 * </ol>
 * The placeholder itself never touches the world; this service is the only writer of the ambient field.
 */
public final class PlayerClimateService implements Listener {
    /** The throttle: one reading per player per second, as the calendar advances. */
    public static final long PERIOD_TICKS = 20;

    private final GlobalClockHolder clock;
    private final Scheduler scheduler;
    private final HudSnapshotCache hud;
    /** Newest clock sequence published per player. Writers are the player's thread; removed on quit. */
    private final ConcurrentHashMap<UUID, Long> published = new ConcurrentHashMap<>();

    public PlayerClimateService(GlobalClockHolder clock, Scheduler scheduler, HudSnapshotCache hud) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.hud = hud;
    }

    /** What the player thread reads: everything the pure computation needs, as plain values. */
    record Capture(UUID player, long worldSeed, boolean overworld, BlockPos pos) {
        static Capture of(Player player) {
            World world = player.getWorld();
            Location at = player.getLocation();
            return new Capture(player.getUniqueId(), world.getSeed(), world.getEnvironment() == World.Environment.NORMAL,
                    new BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ()));
        }
    }

    /** The worker-side computation (pure): the overworld model of the captured world at the captured position. */
    static ClimateReading compute(Capture c, ClockSnapshot clock) {
        if (!c.overworld()) {
            return ClimateReading.unknown(clock.sequence(), clock.calendarTicks());
        }
        OverworldClimate climate = OverworldClimate.forWorldSeed(c.worldSeed(), OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        ChunkClimate chunk = ChunkClimateSource.climateAt(c.worldSeed(), c.pos().x(), c.pos().z());
        return ClimateReading.of(climate, chunk, c.pos(), clock);
    }

    /** Starts the throttle on the global thread. Call once, after the scheduler exists. */
    public void start(Supplier<Collection<? extends Player>> online) {
        scheduler.globalRepeating(1, PERIOD_TICKS, () -> {
            if (!hud.isActive()) {
                return; // nothing renders the value: no work
            }
            for (Player player : online.get()) {
                refresh(player);
            }
        });
    }

    /** Global or any thread: hops to the player's thread to capture. */
    void refresh(Player player) {
        scheduler.entity(player, () -> {
            Capture c = Capture.of(player);
            CompletableFuture.supplyAsync(() -> {
                ThreadAsserts.workerThread();
                return compute(c, clock.get());
            }, scheduler.cpuExecutor()).thenAccept(reading -> scheduler.entity(player, () -> publish(player, reading), null));
        }, null);
    }

    /** Player thread: the newest clock reading wins, and only the ambient field changes. */
    void publish(Player player, ClimateReading reading) {
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        Long last = published.get(id);
        if (last != null && last > reading.clockSequence()) {
            return;
        }
        published.put(id, reading.clockSequence());
        hud.publish(player, hud.snapshot(id).withAmbientTemperature(reading.temperature()));
    }

    /**
     * {@code /tfp debug chunk}: the reading for the player's position now. The player's thread captures, a worker
     * computes; the result is not published, so the command shows what the HUD value would be right now.
     *
     * @return empty if the player left before the capture ran
     */
    public CompletableFuture<Optional<ClimateReading>> readNow(Player player) {
        CompletableFuture<Optional<ClimateReading>> out = new CompletableFuture<>();
        boolean scheduled = scheduler.entity(player, () -> {
            Capture c = Capture.of(player);
            CompletableFuture.supplyAsync(() -> compute(c, clock.get()), scheduler.cpuExecutor())
                    .whenComplete((r, err) -> {
                        if (err != null) {
                            out.completeExceptionally(err);
                        } else {
                            out.complete(Optional.of(r));
                        }
                    });
        }, () -> out.complete(Optional.empty()));
        if (!scheduled) {
            out.complete(Optional.empty());
        }
        return out;
    }

    /**
     * {@code /tfp debug chunk <x> <z>} from the console: the reading at {@code pos} in {@code world}, with no player.
     * The caller reads the position's block data on the owning region thread; this only computes (the capture has no
     * player, so {@link Capture#player()} is {@code null}).
     */
    public CompletableFuture<ClimateReading> readAt(World world, BlockPos pos) {
        Capture c = new Capture(null, world.getSeed(), world.getEnvironment() == World.Environment.NORMAL, pos);
        return CompletableFuture.supplyAsync(() -> compute(c, clock.get()), scheduler.cpuExecutor());
    }

    /** Forgets the ordering state of a player who left. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        published.remove(event.getPlayer().getUniqueId());
    }
}
