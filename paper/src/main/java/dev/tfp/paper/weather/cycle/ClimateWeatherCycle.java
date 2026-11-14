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
import org.bukkit.event.player.PlayerQuitEvent;
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
 *       player's rain level 0.01 per tick toward it, as TFC's client does. Leaving a TFC world resets it.</li>
 * </ol>
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
    /** The weather flag per world, read once from the world's PDC. */
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

    /** Starts the once-per-second sync on the global thread. */
    public void start() {
        task = scheduler.globalRepeating(1, PERIOD_TICKS, this::sync);
    }

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

    /** TFC {@code WorldTracker.isWeatherEnabled}: the world's weather flag (default true). Any thread. */
    public boolean enabled(World world) {
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
        ClockSnapshot now = clock.get();
        if (!now.isSet()) {
            return;
        }
        List<WorldCapture> captures = new ArrayList<>();
        for (World world : worlds.get()) {
            if (!tfcWorld.test(world)) {
                continue;
            }
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
            refresh(player);
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

    /** Any thread: decides the player's own weather on their thread and a worker. */
    void refresh(Player player) {
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
                    .thenAccept(type -> scheduler.entity(player, () -> applyPlayer(player, c.world(), type), null));
        }, null);
    }

    /** Worker: what the player sees, from the climate at their position (TFC's client decision; no sky gate). */
    static WeatherType localWeather(PlayerCapture c, ClockSnapshot now) {
        PrecipitationType type = LocalRain.at(c.seed(), c.pos(), now, c.weatherEnabled(), true);
        return type == PrecipitationType.NONE ? WeatherType.CLEAR : WeatherType.DOWNFALL;
    }

    /** Player thread: sets the player's own weather when it changed and they are still in the captured world. */
    void applyPlayer(Player player, UUID world, WeatherType want) {
        if (!player.isOnline() || !player.getWorld().getUID().equals(world)) {
            return;
        }
        managed.add(player.getUniqueId());
        if (player.getPlayerWeather() != want) {
            player.setPlayerWeather(want);
        }
    }

    /** Player thread: gives a player outside a TFC world back to the world's weather, if this service set theirs. */
    void release(Player player) {
        if (managed.remove(player.getUniqueId())) {
            player.resetPlayerWeather();
        }
    }

    /** A world change re-decides at once (a TFC world sets the local weather, another world releases it). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        refresh(event.getPlayer());
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
