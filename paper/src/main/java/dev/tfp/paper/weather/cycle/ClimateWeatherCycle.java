/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/mixin/ServerLevelMixin.java @ c3b4d9536 (advanceWeatherCycle replaced)
 * Also: src/main/java/net/dries007/tfc/util/tracker/WeatherHelpers.java @ c3b4d9536 (advanceWeatherCycle),
 *       src/main/java/net/dries007/tfc/util/tracker/WorldTracker.java @ c3b4d9536 (weatherEnabled),
 *       src/main/java/net/dries007/tfc/mixin/LevelMixin.java @ c3b4d9536 and
 *       src/main/java/net/dries007/tfc/client/ClimateRenderCache.java @ c3b4d9536 (local rain level per player)
 */
package dev.tfp.paper.weather.cycle;

import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.weather.cycle.WeatherCycle;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * PAR-CLIM-12: the climate drives the weather of every TFC overworld, as TFC's {@code ServerLevelMixin} replaces vanilla's
 * {@code advanceWeatherCycle} with {@code WeatherHelpers.advanceWeatherCycle}.
 * <ol>
 *   <li><b>World (global thread, every {@value #PERIOD_TICKS} ticks).</b> Holds the vanilla weather rule
 *       ({@code doWeatherCycle} / {@code advance_weather}) off, snapshots each TFC world (seed, weather flag), decides
 *       the {@link WeatherCycle.Target} on a worker and sets the world's storm and thunder flags on the global thread
 *       when they differ. Vanilla's own {@code advanceWeatherCycle} keeps stepping the rain and thunder levels by 0.01
 *       per tick toward those flags (that part runs whatever the rule says), which is exactly TFC's stepping, and sends
 *       the level packets. A {@code /weather} command or another plugin's change is undone on the next sync.</li>
 *   <li><b>Players (player thread, then a worker).</b> Substitute for TFC's client mixins ({@code LevelMixin.getRainLevel}
 *       through {@code ClimateRenderCache}): each player's own weather is set to {@link WeatherType#DOWNFALL} where the
 *       climate precipitates at the player's position and {@link WeatherType#CLEAR} where it does not. Paper steps that
 *       player's rain level 0.01 per tick toward it, as TFC's client does. Leaving a TFC world resets it.
 *       Besides the timer, a join, respawn, world change and teleport re-decide one tick after the event (after Paper's
 *       {@code sendLevelInfo}), so a player who arrives at a dry site starts fading out at once rather than up to 20
 *       ticks later. A join, respawn and world change also re-send the decision even when it did not change: the client
 *       starts that level at rain level 0, and Paper only sends its per-player rain level while it moves, so a player
 *       who stays DOWNFALL at level 1 would otherwise get no rain at all.</li>
 * </ol>
 * The world flags ({@link #apply}) and the players' weather ({@link #refresh}) are both started by one {@link #sync}
 * but are not ordered against each other. That is harmless: a player's own weather is a plugin weather, and Paper's
 * storm toggle and {@code sendLevelInfo} only change a player's weather when no plugin weather is set
 * ({@code ServerPlayer.setPlayerWeather(type, false)} returns early), so the world's storm never undoes it.
 * Worlds that are not TFC overworlds keep vanilla's weather (their climate model does not support rain, PAR-CLIM-11).
 * The weather flag ({@code /tfp weather}, PAR-CLIM-07; TFC {@code WorldTracker.weatherEnabled}, default true) is kept in
 * the world's persistent data.
 */
public final class ClimateWeatherCycle implements Listener {
    /** One decision per second; the levels themselves move every tick (vanilla). */
    public static final long PERIOD_TICKS = 20;
    /** World PDC: the weather flag, absent = true. */
    public static final NamespacedKey ENABLED_KEY = new NamespacedKey("tfp", "weather_enabled");

    private final GlobalClockHolder clock;
    private final Scheduler scheduler;
    private final Predicate<World> tfcWorld;
    private final Supplier<? extends Collection<? extends World>> worlds;
    private final Supplier<? extends Collection<? extends Player>> players;
    /** The weather flag per world, read once from the world's PDC on the global thread ({@link #loadEnabled}). */
    private final Map<UUID, Boolean> enabled = new ConcurrentHashMap<>();
    /** Players whose own weather this service set. */
    private final Set<UUID> managed = ConcurrentHashMap.newKeySet();
    private final GameRule<Boolean> weatherRule = weatherRule();
    private volatile Scheduler.TaskHandle task;

    /**
     * @param tfcWorld which worlds are TFC overworlds (our generator); only they get the climate weather
     */
    public ClimateWeatherCycle(GlobalClockHolder clock, Scheduler scheduler, Predicate<World> tfcWorld,
            Supplier<? extends Collection<? extends World>> worlds,
            Supplier<? extends Collection<? extends Player>> players) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.tfcWorld = tfcWorld;
        this.worlds = worlds;
        this.players = players;
    }

    /** Starts the once-per-second sync on the global thread (its first run also loads every world's weather flag). */
    public void start() {
        task = scheduler.globalRepeating(1, PERIOD_TICKS, this::sync);
    }

    /** Ticks between a join, respawn, world change or teleport and its re-decision (after Paper's sendLevelInfo). */
    static final long EVENT_DELAY_TICKS = 1;

    /** Stops the sync. The worlds keep the rule off: they are TFC worlds. */
    public void stop() {
        Scheduler.TaskHandle t = task;
        if (t != null) {
            t.cancel();
        }
    }

    /** Whether {@code world} gets the climate weather (a TFC overworld). Any thread. */
    public boolean controls(World world) {
        return tfcWorld.test(world);
    }

    /**
     * TFC {@code WorldTracker.isWeatherEnabled}: the world's weather flag (default true). Any thread: it reads the value
     * the global thread loaded from the world PDC ({@link #loadEnabled}, every sync) or the last {@link #setEnabled};
     * until the first sync after a world loads it reads true.
     */
    public boolean enabled(World world) {
        return enabled.getOrDefault(world.getUID(), Boolean.TRUE);
    }

    /** Global thread: reads the world's weather flag from its PDC once (a {@link #setEnabled} before that wins). */
    boolean loadEnabled(World world) {
        return enabled.computeIfAbsent(world.getUID(), id -> world.getPersistentDataContainer()
                .getOrDefault(ENABLED_KEY, PersistentDataType.BOOLEAN, Boolean.TRUE));
    }

    /**
     * TFC {@code WorldTracker.setWeatherEnabled} (for {@code /tfp weather}, PAR-CLIM-07, and {@code /tfp time set
     * rain|clear}, PAR-CLIM-13). Stored in the world's PDC on the global thread and applied by the next sync. Any thread.
     */
    public void setEnabled(World world, boolean on) {
        enabled.put(world.getUID(), on);
        scheduler.global(() -> world.getPersistentDataContainer().set(ENABLED_KEY, PersistentDataType.BOOLEAN, on));
    }

    /** The climate's target for {@code world} now (worker side of the sync; pure). */
    public static WeatherCycle.Target target(long worldSeed, ClockSnapshot clock, boolean weatherEnabled) {
        OverworldClimate climate = OverworldClimate.forWorldSeed(worldSeed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        return WeatherCycle.target(climate, clock.calendarTicks(), weatherEnabled);
    }

    /** The world snapshot the sync decides on. */
    record WorldCapture(World world, long seed, boolean weatherEnabled) {
    }

    /** Global thread, once per period: the worlds, then every online player. */
    void sync() {
        List<World> tfcWorlds = new ArrayList<>();
        for (World world : worlds.get()) {
            if (tfcWorld.test(world)) {
                loadEnabled(world);
                tfcWorlds.add(world);
            }
        }
        ClockSnapshot now = clock.get();
        if (!now.isSet()) {
            return;
        }
        List<WorldCapture> captures = new ArrayList<>();
        for (World world : tfcWorlds) {
            holdRuleOff(world);
            captures.add(new WorldCapture(world, world.getSeed(), enabled(world)));
        }
        if (!captures.isEmpty()) {
            CompletableFuture.supplyAsync(() -> decide(captures, now), scheduler.cpuExecutor())
                    .thenAccept(targets -> scheduler.global(() -> {
                        for (int i = 0; i < captures.size(); i++) {
                            apply(captures.get(i).world(), targets.get(i));
                        }
                    }));
        }
        for (Player player : players.get()) {
            refresh(player, false);
        }
    }

    /** Worker: one target per captured world. */
    static List<WeatherCycle.Target> decide(List<WorldCapture> captures, ClockSnapshot now) {
        List<WeatherCycle.Target> out = new ArrayList<>(captures.size());
        for (WorldCapture c : captures) {
            out.add(target(c.seed(), now, c.weatherEnabled()));
        }
        return out;
    }

    /** Global thread: sets the world's flags where they differ from the climate; vanilla steps the levels. */
    static void apply(World world, WeatherCycle.Target target) {
        if (world.hasStorm() != target.raining()) {
            world.setStorm(target.raining());
        }
        if (world.isThundering() != target.thunder()) {
            world.setThundering(target.thunder());
        }
    }

    /** Global thread: vanilla's random weather cycle stays off in a TFC world (TFC cancels advanceWeatherCycle). */
    void holdRuleOff(World world) {
        if (weatherRule != null && Boolean.TRUE.equals(world.getGameRuleValue(weatherRule))) {
            world.setGameRule(weatherRule, false);
        }
    }

    /** The captured player state the local decision needs. */
    record PlayerCapture(UUID world, long seed, BlockPos pos, boolean weatherEnabled) {
    }

    /** Any thread: decides the player's own weather on their thread and a worker; sets it only when it changed. */
    void refresh(Player player) {
        refresh(player, false);
    }

    /**
     * Any thread: decides the player's own weather on their thread and a worker.
     *
     * @param resend set it even when it did not change (the client has a fresh level at rain level 0)
     */
    void refresh(Player player, boolean resend) {
        scheduler.entity(player, () -> {
            World world = player.getWorld();
            if (!tfcWorld.test(world)) {
                release(player);
                return;
            }
            Location at = player.getLocation();
            PlayerCapture c = new PlayerCapture(world.getUID(), world.getSeed(),
                    new BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ()), enabled(world));
            ClockSnapshot now = clock.get();
            CompletableFuture.supplyAsync(() -> localWeather(c, now), scheduler.cpuExecutor())
                    .thenAccept(type -> scheduler.entity(player, () -> applyPlayer(player, c.world(), type, resend),
                            null));
        }, null);
    }

    /** Worker: what the player sees, from the climate at their position (TFC's client decision; no sky gate). */
    static WeatherType localWeather(PlayerCapture c, ClockSnapshot now) {
        PrecipitationType type = LocalRain.at(c.seed(), c.pos(), now, c.weatherEnabled(), true);
        return type == PrecipitationType.NONE ? WeatherType.CLEAR : WeatherType.DOWNFALL;
    }

    /**
     * Player thread: sets the player's own weather when it changed (or {@code resend}) and they are still in the captured
     * world. Paper sends the client an instant rain level with it (CLEAR: level 0, DOWNFALL: level 1) and then its own
     * per-player level, which steps 0.01 per tick from where it was; so a player whose level is already at the target
     * keeps it, and one coming from the other side fades over up to 100 ticks, as TFC's {@code ClimateRenderCache}.
     */
    void applyPlayer(Player player, UUID world, WeatherType want, boolean resend) {
        if (!player.isOnline() || !player.getWorld().getUID().equals(world)) {
            return;
        }
        managed.add(player.getUniqueId());
        if (resend || player.getPlayerWeather() != want) {
            player.setPlayerWeather(want);
        }
    }

    /** Player thread, {@link #EVENT_DELAY_TICKS} after an event: re-decides (after Paper's own sendLevelInfo). */
    void refreshSoon(Player player, boolean resend) {
        scheduler.entityDelayed(player, EVENT_DELAY_TICKS, () -> refresh(player, resend), null);
    }

    /** Player thread: gives a player outside a TFC world back to the world's weather, if this service set theirs. */
    void release(Player player) {
        if (managed.remove(player.getUniqueId())) {
            player.resetPlayerWeather();
        }
    }

    /** A join decides one tick later, re-sending it (a fresh client level starts at rain level 0). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refreshSoon(event.getPlayer(), true);
    }

    /** A respawn re-decides one tick later and re-sends it (the client may build a new level). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        refreshSoon(event.getPlayer(), true);
    }

    /**
     * A world change re-decides one tick later (a TFC world sets the local weather, another world releases it) and
     * re-sends it: the client builds a new level at rain level 0.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        refreshSoon(event.getPlayer(), true);
    }

    /**
     * A teleport re-decides one tick later, at the destination, without waiting for the timer. Not re-sent: the client
     * keeps its level, and re-sending an unchanged decision would flash the instant level over Paper's ramp.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        refreshSoon(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        managed.remove(event.getPlayer().getUniqueId());
    }

    /** Whether this service set {@code player}'s own weather. */
    boolean manages(UUID player) {
        return managed.contains(player);
    }

    /** The vanilla weather cycle rule: {@code advance_weather} on 1.21.11+ / 26.x, {@code doWeatherCycle} before. */
    @SuppressWarnings({"unchecked", "deprecation", "removal"})
    static GameRule<Boolean> weatherRule() {
        for (GameRule<?> rule : GameRule.values()) {
            if (rule.getType() != Boolean.class) {
                continue;
            }
            String key = rule.getKey().getKey();
            String name = rule.getName();
            if (key.equals("advance_weather") || name.equals("advance_weather")
                    || key.equalsIgnoreCase("doWeatherCycle") || name.equals("doWeatherCycle")) {
                return (GameRule<Boolean>) rule;
            }
        }
        return null;
    }
}
