/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import dev.tfp.core.calendar.Season;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.paper.packet.SeasonalBiomeSource;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import dev.tfp.paper.worldgen.TfcChunkGenerator;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;

/**
 * M2-SEA-02: the seasonal biome tables behind the packet rewrite, decide async, apply sync.
 * <ol>
 *   <li><b>Snapshot.</b> Events only record plain values: the TFC worlds (terrain view, climate model, height; on world
 *       load), each player's world (join, world change), and the chunks that load or unload.</li>
 *   <li><b>Decide (worker pool).</b> {@link SeasonalBiomeRemap#compute} builds a chunk's {@link SeasonalChunkTable} for
 *       the current season from the seed alone: on chunk load, on a packet-thread miss, and for the chunks around every
 *       player at a season change.</li>
 *   <li><b>Apply (Netty thread, per player).</b> {@code SeasonalBiomeHandler} looks the table up ({@link #table}) and
 *       rewrites the outgoing packet only; the world is never changed.</li>
 *   <li><b>Resend (region thread, rate limited).</b> A miss or a season change queues the chunk in a {@link ResendLimiter}
 *       ({@value #RESENDS_PER_TICK} chunks per tick, {@value #RESEND_COOLDOWN_TICKS}-tick cooldown per chunk). The global
 *       tick drains it into {@code World.refreshChunk} on the chunk's region thread, which sends the chunk again and so
 *       through the rewrite.</li>
 * </ol>
 * The season is checked every {@value #SEASON_CHECK_TICKS} ticks from the published clock. At a change the tables are
 * dropped (they hold one season), the cooldowns are cleared and the chunks in every player's view are recomputed and
 * resent, nearest first.
 */
public final class SeasonalBiomeService implements Listener, SeasonalBiomeSource {
    /** Chunk resends per server tick. */
    public static final int RESENDS_PER_TICK = 8;
    /** A chunk is resent at most once per this many ticks (except at a season change). */
    public static final long RESEND_COOLDOWN_TICKS = 100;
    /** How often the season is compared with the clock. */
    public static final long SEASON_CHECK_TICKS = 20;
    /** Bound of the table cache; over it the cache is dropped (chunk unloads keep it well below). */
    static final int MAX_TABLES = 32_768;

    /** A TFC world as the workers need it: pure, captured once. */
    record WorldView(SeasonalBiomeRemap.Terrain terrain, OverworldClimate climate, int minY, int height) {
    }

    /** A chunk of a world. */
    record ChunkKey(UUID world, int x, int z) {
    }

    private final GlobalClockHolder clock;
    private final Scheduler scheduler;
    private final Function<UUID, World> worldById;
    private final ConcurrentHashMap<UUID, WorldView> worlds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> playerWorld = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkKey, SeasonalChunkTable> tables = new ConcurrentHashMap<>();
    private final Set<ChunkKey> inflight = ConcurrentHashMap.newKeySet();
    private final Set<ChunkKey> wantResend = ConcurrentHashMap.newKeySet();
    private final ResendLimiter<ChunkKey> limiter = new ResendLimiter<>(RESENDS_PER_TICK, RESEND_COOLDOWN_TICKS);
    private final AtomicLong tick = new AtomicLong();
    private final AtomicLong computed = new AtomicLong();
    private final AtomicLong resent = new AtomicLong();
    private volatile Season season;
    private volatile int daysInMonth = ClockSnapshot.DEFAULT_DAYS_IN_MONTH;
    private volatile Consumer<UUID> quitHook = id -> { };

    public SeasonalBiomeService(GlobalClockHolder clock, Scheduler scheduler, Function<UUID, World> worldById) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.worldById = worldById;
    }

    /** Called with a player's id when they quit (the packet handler drops its per-user ids). */
    public void onQuit(Consumer<UUID> hook) {
        this.quitHook = hook;
    }

    /** Starts the global tick: season check and resend drain. Call once. */
    public void start(Supplier<Collection<? extends World>> loadedWorlds, Supplier<Collection<? extends Player>> online) {
        scheduler.globalRepeating(1, 1, () -> {
            long now = tick.incrementAndGet();
            if (now % SEASON_CHECK_TICKS == 1) {
                for (World w : loadedWorlds.get()) {
                    register(w);
                }
                checkSeason(online.get());
            }
            for (ChunkKey key : limiter.drain(now)) {
                World world = worldById.apply(key.world());
                if (world != null) {
                    scheduler.region(world, key.x(), key.z(), () -> {
                        if (world.isChunkLoaded(key.x(), key.z())) {
                            world.refreshChunk(key.x(), key.z());
                            resent.incrementAndGet();
                        }
                    });
                }
            }
        });
    }

    /** Global thread: publishes a new season (or month length) and recomputes and resends the chunks near players. */
    void checkSeason(Collection<? extends Player> online) {
        ClockSnapshot c = clock.get();
        Season now = SeasonalBiomeRemap.seasonOf(c);
        if (now == null || (now == season && c.daysInMonth() == daysInMonth)) {
            return;
        }
        daysInMonth = c.daysInMonth();
        season = now;
        tables.clear();
        limiter.clearCooldowns();
        for (Player player : online) {
            scheduler.entity(player, () -> resendAround(player), null);
        }
    }

    /** Player thread: queues the chunks in the player's view, nearest first. */
    private void resendAround(Player player) {
        ThreadAsserts.owned(player);
        UUID world = player.getWorld().getUID();
        if (!worlds.containsKey(world)) {
            return;
        }
        int cx = player.getLocation().getBlockX() >> 4;
        int cz = player.getLocation().getBlockZ() >> 4;
        int radius = Math.max(2, player.getSendViewDistance());
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                        request(new ChunkKey(world, cx + dx, cz + dz), true);
                    }
                }
            }
        }
    }

    /** Remembers a world of our generator. Any thread that may read the world's generator (global, main). */
    void register(World world) {
        if (worlds.containsKey(world.getUID()) || world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        ChunkGenerator generator = world.getGenerator();
        if (generator instanceof TfcChunkGenerator tfc) {
            long seed = world.getSeed();
            worlds.put(world.getUID(), new WorldView(tfc.seasonalTerrain(() -> seed),
                    OverworldClimate.forWorldSeed(seed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE),
                    world.getMinHeight(), world.getMaxHeight() - world.getMinHeight()));
        }
    }

    /** Queues the table computation of a chunk, and its resend when {@code resend}. Any thread; never blocks. */
    void request(ChunkKey key, boolean resend) {
        WorldView view = worlds.get(key.world());
        Season s = season;
        if (view == null || s == null) {
            return;
        }
        if (resend) {
            wantResend.add(key);
        }
        if (!inflight.add(key)) {
            return; // the running computation picks up the resend flag
        }
        int days = daysInMonth;
        try {
            scheduler.cpuExecutor().execute(() -> {
                try {
                    SeasonalChunkTable table = SeasonalBiomeRemap.compute(view.terrain(), view.climate(), key.x(), key.z(),
                            view.minY(), view.height(), s, days);
                    computed.incrementAndGet();
                    if (s == season) {
                        if (tables.size() >= MAX_TABLES) {
                            tables.clear();
                        }
                        tables.put(key, table);
                    }
                } finally {
                    inflight.remove(key);
                    if (wantResend.remove(key)) {
                        limiter.offer(key, tick.get());
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            inflight.remove(key);
            wantResend.remove(key);
        }
    }

    @Override
    public boolean active(UUID player) {
        return season != null && playerWorld.containsKey(player);
    }

    @Override
    public SeasonalChunkTable table(UUID player, int chunkX, int chunkZ) {
        UUID world = playerWorld.get(player);
        Season s = season;
        if (world == null || s == null) {
            return null;
        }
        ChunkKey key = new ChunkKey(world, chunkX, chunkZ);
        SeasonalChunkTable table = tables.get(key);
        if (table != null && table.season() == s) {
            return table;
        }
        request(key, true);
        return null;
    }

    @Override
    public void biomesChanged(UUID player, int chunkX, int chunkZ) {
        UUID world = playerWorld.get(player);
        if (world != null) {
            ChunkKey key = new ChunkKey(world, chunkX, chunkZ);
            if (tables.containsKey(key)) {
                limiter.offer(key, tick.get());
            } else {
                request(key, true);
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        register(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        request(new ChunkKey(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ()), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        tables.remove(new ChunkKey(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        track(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        track(event.getPlayer());
    }

    @EventHandler
    public void onQuitEvent(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        playerWorld.remove(id);
        quitHook.accept(id);
    }

    private void track(Player player) {
        World world = player.getWorld();
        register(world);
        if (worlds.containsKey(world.getUID())) {
            playerWorld.put(player.getUniqueId(), world.getUID());
        } else {
            playerWorld.remove(player.getUniqueId());
        }
    }

    /** For {@code /tfp debug}: the current season, tables cached, computed, waiting and resent chunks. */
    public String describe() {
        return "season=" + season + " worlds=" + worlds.size() + " tables=" + tables.size() + " computed=" + computed.get()
                + " pendingResends=" + limiter.pending() + " resent=" + resent.get();
    }
}
