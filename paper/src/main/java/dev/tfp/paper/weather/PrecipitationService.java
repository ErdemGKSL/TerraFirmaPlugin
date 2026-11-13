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
import dev.tfp.core.weather.PrecipitationSample;
import dev.tfp.core.weather.PrecipitationSamples;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * What falls at each player's position and at any block position (M2-WTH-01, PAR-CLIM-02): TFC's
 * {@code WeatherHelpers.getPrecipitationAt} behind vanilla's {@code Level.isRainingAt} gates, decide async, apply sync.
 * This publishes decisions only; nothing in the world changes here (snow and ice are {@link SnowService}).
 * <ol>
 *   <li><b>Throttle (global thread).</b> Every {@value #PERIOD_TICKS} ticks, a task goes to each online player's thread,
 *       whether or not a HUD renders the value: other systems read the samples too.</li>
 *   <li><b>Capture (player thread).</b> Reads the block position, the world seed, and whether rain reaches the position
 *       ({@link #rainReaches}). Only the overworld has the model.</li>
 *   <li><b>Compute (worker pool).</b> {@link ClimateReading#of} gives the temperature and rainfall for the published
 *       {@link ClockSnapshot}, {@link OverworldClimate#rainIntensity} gives the rain at that time, and
 *       {@link PrecipitationType#decide} gives the type. Pure.</li>
 *   <li><b>Apply (player thread).</b> Stores the sample ({@link #sampleOf}), newest clock reading wins, and publishes the
 *       type into the player's {@link HudSnapshot} when a HUD is active.</li>
 * </ol>
 * Other systems (barrel rain fill, drinking in the rain, fire dousing, firestarter odds) read a player's sample with
 * {@link #sampleOf}, or decide for any block with {@link #precipitationAt} / {@link #isRainingAt} (pure, from their own
 * worker step) after reading {@link #rainReaches} on the block's region thread. They find the service through
 * {@link #current()}.
 */
public final class PrecipitationService implements Listener {
    /** One decision per player per second, as the calendar advances. */
    public static final long PERIOD_TICKS = 20;

    /** The started service, for systems that are wired without a reference to it. */
    private static volatile PrecipitationService current;

    private final GlobalClockHolder clock;
    private final Scheduler scheduler;
    private final HudSnapshotCache hud;
    private final Function<Player, Capture> capture;
    /** Newest sample per player. Writers are the player's thread; removed on quit. */
    private final PrecipitationSamples samples = new PrecipitationSamples();

    public PrecipitationService(GlobalClockHolder clock, Scheduler scheduler, HudSnapshotCache hud) {
        this(clock, scheduler, hud, Capture::of);
    }

    /** Tests: {@code capture} replaces the player-thread read. */
    PrecipitationService(GlobalClockHolder clock, Scheduler scheduler, HudSnapshotCache hud, Function<Player, Capture> capture) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.hud = hud;
        this.capture = capture;
    }

    /** The service started last, or empty before the plugin started it. Any thread. */
    public static Optional<PrecipitationService> current() {
        return Optional.ofNullable(current);
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
            boolean sky = overworld && rainReaches(world, x, y, z);
            return new Capture(player.getUniqueId(), world.getSeed(), overworld, new BlockPos(x, y, z), sky);
        }
    }

    /**
     * Vanilla's position gates of {@code Level.isRainingAt}, which TFC keeps in front of its climate call: the position
     * sees the sky (full sky light, {@code canSeeSky}) and lies above the column's {@code MOTION_BLOCKING} top (so tall
     * grass and flowers do not shelter, leaves, glass and water do). Call on the thread that owns the position's region.
     */
    public static boolean rainReaches(World world, int x, int y, int z) {
        if (y > world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING)) {
            return world.getBlockAt(x, y, z).getLightFromSky() >= 15;
        }
        return false;
    }

    /** The worker-side decision (pure). Not overworld, or no calendar reading yet: NONE. */
    static PrecipitationSample compute(Capture c, ClockSnapshot clock) {
        return new PrecipitationSample(clock.sequence(), clock.calendarTicks(), c.pos(),
                decide(c.worldSeed(), c.overworld(), c.pos(), c.skyVisible(), clock));
    }

    private static PrecipitationType decide(long worldSeed, boolean overworld, BlockPos pos, boolean rainReaches,
            ClockSnapshot clock) {
        if (!overworld || !clock.isSet() || !rainReaches) {
            return PrecipitationType.NONE;
        }
        OverworldClimate climate = OverworldClimate.forWorldSeed(worldSeed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        ChunkClimate chunk = ChunkClimateSource.climateAt(worldSeed, pos.x(), pos.z());
        ClimateReading reading = ClimateReading.of(climate, chunk, pos, clock);
        float rainIntensity = climate.rainIntensity(reading.calendarTicks());
        return PrecipitationType.decide(reading.temperature(), reading.rainfall(), rainIntensity, true);
    }

    /** Starts the throttle on the global thread. Call once, after the scheduler exists. */
    public void start(Supplier<Collection<? extends Player>> online) {
        current = this;
        scheduler.globalRepeating(1, PERIOD_TICKS, () -> sampleAll(online.get()));
    }

    /** Global thread, once per period: one sample per online player. */
    void sampleAll(Collection<? extends Player> players) {
        for (Player player : players) {
            refresh(player);
        }
    }

    /** Global or any thread: hops to the player's thread to capture. */
    void refresh(Player player) {
        scheduler.entity(player, () -> {
            Capture c = capture.apply(player);
            CompletableFuture.supplyAsync(() -> {
                ThreadAsserts.workerThread();
                return compute(c, clock.get());
            }, scheduler.cpuExecutor()).thenAccept(sample -> scheduler.entity(player, () -> apply(player, sample), null));
        }, null);
    }

    /** Player thread: the newest clock reading wins; the HUD gets only the precipitation field. */
    void apply(Player player, PrecipitationSample sample) {
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        if (!samples.offer(id, sample)) {
            return;
        }
        if (hud.isActive()) {
            hud.publish(player, hud.snapshot(id).withPrecipitation(sample.type()));
        }
    }

    /**
     * The player's newest sample: what fell at their position at most {@value #PERIOD_TICKS} ticks ago (plus the time to
     * decide). Any thread.
     *
     * @return empty before the first sample, and after the player left
     */
    public Optional<PrecipitationSample> sampleOf(UUID player) {
        return samples.latest(player);
    }

    /**
     * TFC {@code getPrecipitationAt} with vanilla's gates, decided now for any position: NONE outside the overworld,
     * before the first calendar reading, or when rain does not reach the position. Pure and thread-safe; meant for the
     * worker step of a decide-async system (it samples the climate noise).
     * <p>
     * The decision follows TFC: it reads only the climate's rain at the calendar time, the instant temperature and the
     * rainfall, never the vanilla rain flag, so {@code /weather rain} alone does not make it RAIN or SNOW (coupling the
     * vanilla weather to the climate is PAR-CLIM-12). Pass the exact position the TFC caller passes: {@code pos.above()}
     * for the barrel, firestarter and flint-and-pyrite, {@code pos} for the firepit, {@code pos.above()} and
     * {@code above(2)} for the forge.
     *
     * @param rainReaches {@link #rainReaches} for the position, read on its region thread
     */
    public PrecipitationType precipitationAt(World world, BlockPos pos, boolean rainReaches) {
        return decide(world.getSeed(), world.getEnvironment() == World.Environment.NORMAL, pos, rainReaches, clock.get());
    }

    /** Vanilla {@code Level.isRainingAt} as TFC makes it: rain (not snow) falls at the position. Thread-safe. */
    public boolean isRainingAt(World world, BlockPos pos, boolean rainReaches) {
        return precipitationAt(world, pos, rainReaches) == PrecipitationType.RAIN;
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
            Capture c = capture.apply(player);
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
     * The caller reads {@code skyVisible} on the owning region thread; the decision runs on a worker.
     */
    public CompletableFuture<PrecipitationType> readAt(World world, BlockPos pos, boolean skyVisible) {
        return CompletableFuture.supplyAsync(() -> precipitationAt(world, pos, skyVisible), scheduler.cpuExecutor());
    }

    /** Forgets the sample of a player who left. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
    }
}
