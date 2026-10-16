/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.integration.Integration;
import dev.tfp.paper.integration.IntegrationContext;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import kr.toxicity.hud.api.BetterHud;
import kr.toxicity.hud.api.BetterHudAPI;
import kr.toxicity.hud.api.listener.HudListener;
import kr.toxicity.hud.api.placeholder.HudPlaceholder;
import kr.toxicity.hud.api.placeholder.PlaceholderContainer;

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
public final class BetterHudIntegration implements Integration {
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
            ToDoubleFunction<HudSnapshot> read = e.getValue();
            numbers.addPlaceholder(e.getKey(),
                    HudPlaceholder.<Number>of((args, reason) -> player -> read.applyAsDouble(cache.snapshot(player.uuid()))));
        }
        for (Map.Entry<String, ToDoubleFunction<HudSnapshot>> e : HudValues.LISTENERS.entrySet()) {
            ToDoubleFunction<HudSnapshot> read = e.getValue();
            HudListener listener = player -> read.applyAsDouble(cache.snapshot(player.uuid()));
            api.getListenerManager().addListener(e.getKey(), yaml -> event -> listener);
        }
        registered = true;
        Path pack = BetterHudPackTarget.packFolder(ctx.pluginsDirectory());
        ctx.logger().info("BetterHud: registered " + HudValues.NUMBERS.size() + " placeholder(s) and "
                + HudValues.LISTENERS.size() + " listener(s); pack folder " + pack
                + (BetterHudPackTarget.optedOut(ctx.pluginsDirectory()) ? " (disabled by the owner: packs/-tfp exists)" : ""));
    }

    @Override
    public void onDisable() {
        // BetterHud has no API to remove placeholders; they read the cache, which simply stops changing.
        registered = false;
    }

    public boolean isRegistered() {
        return registered;
    }

    /** {@code plugins/BetterHud/packs/tfp}, the installer's target (M1-INST-01). */
    public Path packFolder() {
        return BetterHudPackTarget.packFolder(ctx.pluginsDirectory());
    }
}
