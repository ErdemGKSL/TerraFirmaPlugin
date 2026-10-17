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
import java.nio.file.Path;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;
import kr.toxicity.hud.api.BetterHud;
import kr.toxicity.hud.api.BetterHudAPI;
import kr.toxicity.hud.api.listener.HudListener;
import kr.toxicity.hud.api.placeholder.HudPlaceholder;
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
        PlaceholderContainer<String> texts = api.getPlaceholderManager().getStringContainer();
        GlobalClockHolder clock = ctx.clock();
        for (Map.Entry<String, Function<ClockSnapshot, String>> e : HudClockValues.TEXTS.entrySet()) {
            Function<ClockSnapshot, String> read = e.getValue();
            // Reads the published clock only: no world, no snapshot cache, the same value for every player.
            texts.addPlaceholder(e.getKey(), HudPlaceholder.<String>of((args, reason) -> player -> read.apply(clock.get())));
        }
        registered = true;
        Path pack = BetterHudPackTarget.packFolder(ctx.pluginsDirectory());
        ctx.logger().info("BetterHud: registered " + HudValues.NUMBERS.size() + " number placeholder(s), "
                + HudClockValues.TEXTS.size() + " text placeholder(s) and " + HudValues.LISTENERS.size()
                + " listener(s); pack folder " + pack
                + (BetterHudPackTarget.optedOut(ctx.pluginsDirectory()) ? " (disabled by the owner: packs/-tfp exists)" : ""));
    }

    /** One of our number placeholders: reads {@code cache} by the player's uuid, nothing else. */
    static HudPlaceholder<Number> numberPlaceholder(HudSnapshotCache cache, ToDoubleFunction<HudSnapshot> read) {
        return HudPlaceholder.<Number>of((args, reason) -> player -> read.applyAsDouble(cache.snapshot(player.uuid())));
    }

    @Override
    public void onDisable() {
        // BetterHud has no API to remove placeholders; they read the cache, which simply stops changing.
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
            if (HudValues.NUMBERS.containsKey(name)) {
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
            if (HudClockValues.TEXTS.containsKey(name)) {
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
