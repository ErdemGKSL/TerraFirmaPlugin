/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weather.PrecipitationSample;
import dev.tfp.core.weather.PrecipitationSamples;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.weather.cycle.WeatherCycle;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import dev.tfp.paper.weather.cycle.ClimateWeatherCycle;
import dev.tfp.paper.weather.cycle.LocalRain;
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
import org.bukkit.plugin.java.JavaPlugin;

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
 * <p>
 * The weather (PAR-CLIM-20): in a world the climate weather cycle drives ({@link ClimateWeatherCycle#controls}), the
 * world's weather flag applies (TFC {@code rainIntensity = isWeatherEnabled() ? getRain(t) : -1}, so with the flag off
 * nothing falls anywhere). Vanilla's {@code level.isRaining()} gate of {@code isRainingAt} (the rain level above 0.2)
 * applies everywhere, read from the lazy rain level the cycle steps a climate world along ({@link WeatherCycle#levelsAt},
 * the same targets the cycle sets the world's storm flag from): it holds the decision back for the first 21 ticks of a
 * climate rain window, as TFC, without the up-to-one-second lag of the world's storm flag. Overworlds the cycle does not
 * drive, and every overworld without the cycle module (or outside the plugin), keep the climate as their only
 * precipitation source with the flag on, as before.
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
    /** The climate weather cycle, once found (the weather_cycle module is enabled after this service is built). */
    private final Supplier<Optional<ClimateWeatherCycle>> cycle;
    /** Newest sample per player. Writers are the player's thread; removed on quit. */
    private final PrecipitationSamples samples = new PrecipitationSamples();

    public PrecipitationService(GlobalClockHolder clock, Scheduler scheduler, HudSnapshotCache hud) {
        this(clock, scheduler, hud, null, pluginCycle());
    }

    /** Tests: {@code capture} replaces the player-thread read; no climate weather cycle. */
    PrecipitationService(GlobalClockHolder clock, Scheduler scheduler, HudSnapshotCache hud, Function<Player, Capture> capture) {
        this(clock, scheduler, hud, capture, Optional::empty);
    }

    /**
     * Tests: {@code cycle} replaces the plugin's service lookup; a null {@code capture} reads the player with
     * {@link Capture#of}.
     */
    PrecipitationService(GlobalClockHolder clock, Scheduler scheduler, HudSnapshotCache hud, Function<Player, Capture> capture,
            Supplier<Optional<ClimateWeatherCycle>> cycle) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.hud = hud;
        this.cycle = cycle;
        this.capture = capture != null ? capture : p -> Capture.of(p, weatherEnabled(p.getWorld()));
    }

    /**
     * The plugin's climate weather cycle from {@code ModuleServices}, cached once found; empty outside the plugin
     * (tests) and while the weather_cycle module is off or not yet enabled.
     */
    private static Supplier<Optional<ClimateWeatherCycle>> pluginCycle() {
        java.util.concurrent.atomic.AtomicReference<Optional<ClimateWeatherCycle>> found =
                new java.util.concurrent.atomic.AtomicReference<>();
        return () -> {
            Optional<ClimateWeatherCycle> f = found.get();
            if (f != null) {
                return f;
            }
            try {
                Optional<ClimateWeatherCycle> c = JavaPlugin.getPlugin(TfpPlugin.class).moduleServices()
                        .get(ClimateWeatherCycle.class);
                if (c.isPresent()) {
                    found.set(c);
                }
                return c;
            } catch (IllegalArgumentException | IllegalStateException | ClassCastException | NullPointerException e) {
                return Optional.empty(); // not running as the plugin (tests)
            }
        };
    }

    /**
     * TFC {@code WorldTracker.isWeatherEnabled} for {@code world}: the cycle's flag where the cycle drives the world,
     * true elsewhere and without the cycle. Any thread (a map read).
     */
    public boolean weatherEnabled(World world) {
        Optional<ClimateWeatherCycle> c = cycle.get();
        return c.isEmpty() || !c.get().controls(world) || c.get().enabled(world);
    }

    /** The service started last, or empty before the plugin started it. Any thread. */
    public static Optional<PrecipitationService> current() {
        return Optional.ofNullable(current);
    }

    /** What the player thread reads: everything the pure decision needs, as plain values. */
    record Capture(UUID player, long worldSeed, boolean overworld, BlockPos pos, boolean skyVisible,
            boolean weatherEnabled) {
        /** Tests: the weather flag on. */
        Capture(UUID player, long worldSeed, boolean overworld, BlockPos pos, boolean skyVisible) {
            this(player, worldSeed, overworld, pos, skyVisible, true);
        }

        static Capture of(Player player, boolean weatherEnabled) {
            World world = player.getWorld();
            Location at = player.getLocation();
            int x = at.getBlockX();
            int y = at.getBlockY();
            int z = at.getBlockZ();
            boolean overworld = world.getEnvironment() == World.Environment.NORMAL;
            boolean sky = overworld && rainReaches(world, x, y, z);
            return new Capture(player.getUniqueId(), world.getSeed(), overworld, new BlockPos(x, y, z), sky,
                    weatherEnabled);
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
                decide(c.worldSeed(), c.overworld(), c.pos(), c.skyVisible(), c.weatherEnabled(), clock));
    }

    /**
     * The pure decision: vanilla's position gates, TFC's {@code getPrecipitationAt} with the weather flag, then vanilla's
     * {@code level.isRaining()} gate ({@link #climateRaining}). Thread-safe; it samples the climate noise.
     */
    public static PrecipitationType decide(long worldSeed, boolean overworld, BlockPos pos, boolean rainReaches,
            boolean weatherEnabled, ClockSnapshot clock) {
        if (!overworld || !clock.isSet() || !rainReaches) {
            return PrecipitationType.NONE;
        }
        PrecipitationType type = LocalRain.at(worldSeed, pos, clock, weatherEnabled, true);
        if (type == PrecipitationType.NONE) {
            return type;
        }
        return climateRaining(worldSeed, clock.calendarTicks(), weatherEnabled) ? type : PrecipitationType.NONE;
    }

    /**
     * Vanilla {@code Level.isRaining()} in a climate-driven world: the rain level the cycle steps by 0.01 per tick toward
     * the climate's target ({@link WeatherCycle#levelsAt}, thunder left out) is above 0.2. Pure.
     */
    static boolean climateRaining(long worldSeed, long calendarTicks, boolean weatherEnabled) {
        OverworldClimate climate = OverworldClimate.forWorldSeed(worldSeed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        return WeatherCycle.levelsAt(calendarTicks,
                t -> new WeatherCycle.Target(weatherEnabled ? climate.rainIntensity(t) : -1f, false)).isRaining();
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
     * The decision follows TFC: the climate's rain at the calendar time (with the world's weather flag), the instant
     * temperature and the rainfall, behind the rain level gate ({@link #climateRaining}); in a climate-driven world
     * {@code /weather rain} changes nothing (the cycle undoes it, PAR-CLIM-12). Pass the exact position the TFC caller passes. TFC's own callers
     * of {@code level.isRainingAt} at c3b4d9536 (vanilla's callers, such as fire, are not listed):
     * <ul>
     *   <li>{@code pos.above()}: barrel ({@code BarrelBlockEntity}:179), firestarter and flint-and-pyrite
     *       ({@code abovePos}), leaves ({@code TFCLeavesBlock}:84), drying bricks ({@code DryingBricksBlock}:179), the
     *       block-entity tooltip ({@code BlockEntityTooltips}:584, {@code entity.getBlockPos().above()});</li>
     *   <li>{@code pos}: firepit ({@code AbstractFirepitBlockEntity}:88 and 127), weathering blocks
     *       ({@code IWeatheringBlock}:163);</li>
     *   <li>{@code pos.above()} and {@code pos.above(2)}: charcoal forge ({@code CharcoalForgeBlockEntity}:86);</li>
     *   <li>{@code player.blockPosition().above()}: drinking in the rain while looking up ({@code ForgeEventHandler}:772).</li>
     * </ul>
     *
     * @param rainReaches {@link #rainReaches} for the position, read on its region thread
     */
    public PrecipitationType precipitationAt(World world, BlockPos pos, boolean rainReaches) {
        return decide(world.getSeed(), world.getEnvironment() == World.Environment.NORMAL, pos, rainReaches,
                weatherEnabled(world), clock.get());
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
