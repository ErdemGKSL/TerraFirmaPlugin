/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import dev.tfp.paper.integration.HudPlaceholders;
import dev.tfp.paper.integration.HudReloader;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.integration.Integration;
import dev.tfp.paper.integration.IntegrationContext;
import dev.tfp.paper.player.MountHudService.MountReading;
import java.nio.file.Path;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;
import kr.toxicity.hud.api.BetterHud;
import kr.toxicity.hud.api.BetterHudAPI;
import kr.toxicity.hud.api.listener.HudListener;
import kr.toxicity.hud.api.placeholder.HudPlaceholder;
import kr.toxicity.hud.api.player.HudPlayer;
import kr.toxicity.hud.api.placeholder.PlaceholderContainer;
import kr.toxicity.hud.api.plugin.ReloadState;

/**
 * BetterHud adapter base (M1-INT-04, {@code betterhud} skill; API as in the compiled M0-BH-01 spike):
 * registers our number placeholders and bar listeners in {@code onEnable}, before BetterHud's first YAML
 * parse (at ServerLoad, or inside CraftEngine's pack build), because an unknown placeholder makes the
 * element fail to load. Every function only reads the per-player {@link HudSnapshotCache}; snapshots are
 * published by features from the player's own thread. Registrations survive {@code /hud reload}.
 *
 * <p>Not a ready-gate condition (ARCHITECTURE.md §12: ready = CraftEngine ∧ BetterModel ∧ registries);
 * {@link #isRegistered()} reports the registration instead.
 */
public final class BetterHudIntegration implements Integration, HudPlaceholders, HudReloader {
    private final IntegrationContext ctx;
    private volatile boolean registered;

    BetterHudIntegration(IntegrationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String pluginName() {
        return BetterHudProvider.PLUGIN;
    }

    @Override
    public void onEnable() {
        BetterHud api = BetterHudAPI.inst();
        HudSnapshotCache cache = ctx.hudCache();
        PlaceholderContainer<Number> numbers = api.getPlaceholderManager().getNumberContainer();
        for (Map.Entry<String, ToDoubleFunction<HudSnapshot>> e : HudValues.NUMBERS.entrySet()) {
            numbers.addPlaceholder(e.getKey(), numberPlaceholder(cache, e.getValue()));
        }
        for (Map.Entry<String, ToDoubleFunction<HudSnapshot>> e : HudValues.LISTENERS.entrySet()) {
            ToDoubleFunction<HudSnapshot> read = e.getValue();
            HudListener listener = player -> read.applyAsDouble(cache.snapshot(player.uuid()));
            api.getListenerManager().addListener(e.getKey(), yaml -> event -> listener);
        }
        // PAR-HUD-19: the bar states (absorption, glow, overheat level) and the yellow surplus bar's listener.
        for (Map.Entry<String, ToDoubleFunction<HudSnapshot>> e : HudValues.STATES.entrySet()) {
            numbers.addPlaceholder(e.getKey(), numberPlaceholder(cache, e.getValue()));
        }
        for (Map.Entry<String, ToDoubleFunction<HudSnapshot>> e : HudValues.STATE_LISTENERS.entrySet()) {
            ToDoubleFunction<HudSnapshot> read = e.getValue();
            HudListener listener = player -> read.applyAsDouble(cache.snapshot(player.uuid()));
            api.getListenerManager().addListener(e.getKey(), yaml -> event -> listener);
        }
        // PAR-HUD-09: the ridden animal's health, read from the rider's published mount reading only.
        for (Map.Entry<String, ToDoubleFunction<MountReading>> e : HudMountValues.NUMBERS.entrySet()) {
            ToDoubleFunction<MountReading> read = e.getValue();
            numbers.addPlaceholder(e.getKey(), HudPlaceholder.<Number>of((args, reason) -> player -> read.applyAsDouble(HudMountValues.READINGS.apply(player.uuid()))));
        }
        for (Map.Entry<String, ToDoubleFunction<MountReading>> e : HudMountValues.LISTENERS.entrySet()) {
            ToDoubleFunction<MountReading> read = e.getValue();
            HudListener listener = player -> read.applyAsDouble(HudMountValues.READINGS.apply(player.uuid()));
            api.getListenerManager().addListener(e.getKey(), yaml -> event -> listener);
        }
        PlaceholderContainer<String> texts = api.getPlaceholderManager().getStringContainer();
        GlobalClockHolder clock = ctx.clock();
        for (Map.Entry<String, BiFunction<ClockSnapshot, HudSnapshot, String>> e : HudClockValues.TEXTS.entrySet()) {
            texts.addPlaceholder(e.getKey(), clockPlaceholder(clock, cache, e.getValue()));
        }
        // PAR-HUD-17: the health bar text in the configured style (the published HudStyles, never the config file).
        for (Map.Entry<String, Function<HudSnapshot, String>> e : HudValues.TEXTS.entrySet()) {
            Function<HudSnapshot, String> read = e.getValue();
            texts.addPlaceholder(e.getKey(), HudPlaceholder.<String>of((args, reason) -> player -> read.apply(cache.snapshot(player.uuid()))));
        }
        for (Map.Entry<String, Function<HudSnapshot, String>> e : HudClimateValues.TEXTS.entrySet()) {
            Function<HudSnapshot, String> read = e.getValue();
            // The player's published snapshot only (M2-CLIM-03): the climate service writes it from the player's thread.
            texts.addPlaceholder(e.getKey(), HudPlaceholder.<String>of((args, reason) -> player -> read.apply(cache.snapshot(player.uuid()))));
        }
        // PAR-HUD-10: a held-heat write redraws the player's HUD at once instead of at BetterHud's next update.
        cache.pusher(BetterHudIntegration::push);
        registered = true;
        Path pack = BetterHudPackTarget.packFolder(ctx.pluginsDirectory());
        ctx.logger().info("BetterHud: registered " + (HudValues.NUMBERS.size() + HudValues.STATES.size() + HudMountValues.NUMBERS.size()) + " number placeholder(s), "
                + (HudClockValues.TEXTS.size() + HudClimateValues.TEXTS.size() + HudValues.TEXTS.size()) + " text placeholder(s) and " + (HudValues.LISTENERS.size() + HudValues.STATE_LISTENERS.size() + HudMountValues.LISTENERS.size())
                + " listener(s); pack folder " + pack
                + (BetterHudPackTarget.optedOut(ctx.pluginsDirectory()) ? " (disabled by the owner: packs/-tfp exists)" : ""));
    }

    /**
     * PAR-HUD-16: one of our clock text placeholders: reads the published clock and {@code cache} by the viewer's uuid
     * (the season follows the hemisphere published there), never the world or a Bukkit object.
     */
    static HudPlaceholder<String> clockPlaceholder(GlobalClockHolder clock, HudSnapshotCache cache,
            BiFunction<ClockSnapshot, HudSnapshot, String> read) {
        return HudPlaceholder.<String>of((args, reason) -> player -> read.apply(clock.get(), cache.snapshot(player.uuid())));
    }

    /** One of our number placeholders: reads {@code cache} by the player's uuid, nothing else. */
    static HudPlaceholder<Number> numberPlaceholder(HudSnapshotCache cache, ToDoubleFunction<HudSnapshot> read) {
        return HudPlaceholder.<Number>of((args, reason) -> player -> read.applyAsDouble(cache.snapshot(player.uuid())));
    }

    /**
     * PAR-HUD-10: redraws {@code player}'s HUD now ({@link HudPlayer#update()}: the placeholders read the published
     * snapshot, the boss-bar line is sent). BetterHud runs the same method from its own async timer every tick, and it is
     * synchronized, so calling it from the player's thread is safe. Nothing happens for a player BetterHud does not know
     * (not joined yet, or already quit).
     */
    static void push(UUID player) {
        HudPlayer hud = BetterHudAPI.inst().getPlayerManager().getHudPlayer(player);
        if (hud != null) {
            hud.update();
        }
    }

    @Override
    public void onDisable() {
        // BetterHud has no API to remove placeholders; they read the cache, which simply stops changing.
        ctx.hudCache().pusher(null);
        registered = false;
    }

    // ---- HudPlaceholders: through BetterHud's registry, not our table ---------------------------------

    @Override
    public Set<String> registered() {
        if (!registered) {
            return Set.of();
        }
        Set<String> out = new TreeSet<>();
        for (String name : BetterHudAPI.inst().getPlaceholderManager().getNumberContainer().getAllPlaceholders().keySet()) {
            if (HudValues.NUMBERS.containsKey(name) || HudValues.STATES.containsKey(name)
                    || HudMountValues.NUMBERS.containsKey(name)) {
                out.add(name);
            }
        }
        return out;
    }

    @Override
    public OptionalDouble evaluate(String name, UUID player) {
        if (!registered) {
            return OptionalDouble.empty();
        }
        return PlaceholderProbe.evaluate(BetterHudAPI.inst().getPlaceholderManager().getNumberContainer().getAllPlaceholders(),
                name, player);
    }

    @Override
    public Set<String> registeredText() {
        if (!registered) {
            return Set.of();
        }
        Set<String> out = new TreeSet<>();
        for (String name : BetterHudAPI.inst().getPlaceholderManager().getStringContainer().getAllPlaceholders().keySet()) {
            if (HudClockValues.TEXTS.containsKey(name) || HudClimateValues.TEXTS.containsKey(name)
                    || HudValues.TEXTS.containsKey(name)) {
                out.add(name);
            }
        }
        return out;
    }

    @Override
    public Optional<String> evaluateText(String name, UUID player) {
        if (!registered) {
            return Optional.empty();
        }
        return PlaceholderProbe.value(BetterHudAPI.inst().getPlaceholderManager().getStringContainer().getAllPlaceholders(),
                name, player).map(String::valueOf);
    }

    public boolean isRegistered() {
        return registered;
    }

    // ---- HudReloader: M1-INST-02, the first-start config edits ---------------------------------------

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean isReloading() {
        return BetterHudAPI.inst().isOnReload();
    }

    @Override
    public boolean reload() {
        ReloadState state = BetterHudAPI.inst().reload();
        if (state instanceof ReloadState.Failure f) {
            ctx.logger().log(Level.WARNING, "BetterHud reload failed", f.throwable());
        }
        return state instanceof ReloadState.Success;
    }

    /** {@code plugins/BetterHud/packs/tfp}, the installer's target (M1-INST-01). */
    public Path packFolder() {
        return BetterHudPackTarget.packFolder(ctx.pluginsDirectory());
    }
}
