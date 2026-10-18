/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.ClimateReading;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weather.PrecipitationType;
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
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * What falls at each player's position (M2-WTH-01), decide async, apply sync. This publishes the decision only; nothing
 * in the world changes (snow and ice placement is M2-WTH-02).
 * <ol>
 *   <li><b>Throttle (global thread).</b> Every {@value #PERIOD_TICKS} ticks, a task goes to each online player's thread.</li>
 *   <li><b>Capture (player thread).</b> Reads the block position, the world seed, and whether the position can see the
 *       sky (the top block of the world surface is below the player). Only the overworld has the model.</li>
 *   <li><b>Compute (worker pool).</b> {@link ClimateReading#of} gives the temperature and rainfall for the published
 *       {@link ClockSnapshot}, {@link OverworldClimate#rainIntensity} gives the rain at that time, and
 *       {@link PrecipitationType#decide} gives the type. Pure.</li>
 *   <li><b>Apply (player thread).</b> Publishes the type into the player's {@link HudSnapshot}. Decisions from one player
 *       are published in clock order, as {@link dev.tfp.paper.climate.PlayerClimateService} does.</li>
 * </ol>
 */
public final class PrecipitationService implements Listener {
    /** One decision per player per second, as the calendar advances. */
    public static final long PERIOD_TICKS = 20;

    private final GlobalClockHolder clock;
    private final Scheduler scheduler;
    private final HudSnapshotCache hud;
    /** Newest clock sequence published per player. Writers are the player's thread; removed on quit. */
    private final ConcurrentHashMap<UUID, Long> published = new ConcurrentHashMap<>();

    public PrecipitationService(GlobalClockHolder clock, Scheduler scheduler, HudSnapshotCache hud) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.hud = hud;
    }

    /** What the player thread reads: everything the pure decision needs, as plain values. */
    record Capture(UUID player, long worldSeed, boolean overworld, BlockPos pos, boolean skyVisible) {
        static Capture of(Player player) {
            World world = player.getWorld();
            Location at = player.getLocation();
            int x = at.getBlockX();
            int y = at.getBlockY();
            int z = at.getBlockZ();
            boolean overworld = world.getEnvironment() == World.Environment.NORMAL;
            // Vanilla canSeeSky: the position is above the top block of the world surface (the surface heightmap)
            boolean sky = overworld && y > world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE).getY();
            return new Capture(player.getUniqueId(), world.getSeed(), overworld, new BlockPos(x, y, z), sky);
        }
    }

    /** The decision and the clock reading it was made from. */
    record Decision(long clockSequence, PrecipitationType type) {}

    /** The worker-side decision (pure). Not overworld, or no calendar reading yet: NONE. */
    static Decision compute(Capture c, ClockSnapshot clock) {
        if (!c.overworld() || !clock.isSet()) {
            return new Decision(clock.sequence(), PrecipitationType.NONE);
        }
        OverworldClimate climate = OverworldClimate.forWorldSeed(c.worldSeed(), OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        ClimateReading reading = ClimateReading.of(climate, ChunkClimate.NEUTRAL, c.pos(), clock);
        float rainIntensity = climate.rainIntensity(reading.calendarTicks());
        return new Decision(clock.sequence(), PrecipitationType.decide(reading.temperature(), reading.rainfall(), rainIntensity, c.skyVisible()));
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
            }, scheduler.cpuExecutor()).thenAccept(decision -> scheduler.entity(player, () -> publish(player, decision), null));
        }, null);
    }

    /** Player thread: the newest clock reading wins, and only the precipitation field changes. */
    void publish(Player player, Decision decision) {
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        Long last = published.get(id);
        if (last != null && last > decision.clockSequence()) {
            return;
        }
        published.put(id, decision.clockSequence());
        hud.publish(player, hud.snapshot(id).withPrecipitation(decision.type()));
    }

    /**
     * {@code /tfp debug chunk}: the decision for the player's position now. The player's thread captures, a worker
     * decides; the result is not published.
     *
     * @return empty if the player left before the capture ran
     */
    public CompletableFuture<Optional<PrecipitationType>> readNow(Player player) {
        CompletableFuture<Optional<PrecipitationType>> out = new CompletableFuture<>();
        boolean scheduled = scheduler.entity(player, () -> {
            Capture c = Capture.of(player);
            CompletableFuture.supplyAsync(() -> compute(c, clock.get()), scheduler.cpuExecutor())
                    .whenComplete((d, err) -> {
                        if (err != null) {
                            out.completeExceptionally(err);
                        } else {
                            out.complete(Optional.of(d.type()));
                        }
                    });
        }, () -> out.complete(Optional.empty()));
        if (!scheduled) {
            out.complete(Optional.empty());
        }
        return out;
    }

    /**
     * {@code /tfp debug chunk <x> <z>} from the console: the decision at {@code pos} in {@code world}, with no player.
     * The caller reads {@code skyVisible} on the owning region thread; the decision itself is {@link #compute}.
     */
    public CompletableFuture<PrecipitationType> readAt(World world, BlockPos pos, boolean skyVisible) {
        Capture c = new Capture(null, world.getSeed(), world.getEnvironment() == World.Environment.NORMAL, pos, skyVisible);
        return CompletableFuture.supplyAsync(() -> compute(c, clock.get()), scheduler.cpuExecutor())
                .thenApply(Decision::type);
    }

    /** Forgets the ordering state of a player who left. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        published.remove(event.getPlayer().getUniqueId());
    }
}
