/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper;

import dev.tfp.core.compat.CompatGate;
import dev.tfp.paper.boot.CompatCheck;
import dev.tfp.paper.boot.TfpCommands;
import dev.tfp.paper.config.ConfigService;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.integration.IntegrationContext;
import dev.tfp.paper.integration.Integrations;
import dev.tfp.paper.ready.ReadyGate;
import dev.tfp.paper.ready.ReadyGate.Signal;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ServerScheduler;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point. Phases follow {@code dependency-bootstrap} §2 / ARCHITECTURE.md §12:
 * <ol>
 *   <li>bootstrap ({@link dev.tfp.paper.boot.TfpBootstrap}): datapack, Brigadier commands;</li>
 *   <li>{@link #onLoad()}: compat gate → config → (M1-INST-*) install content into CE/BetterHud,
 *       config edits → adapters' {@code onLoad} (CraftEngine behaviour registration);</li>
 *   <li>{@link #onEnable()}: refuse if the gate said so; scheduler; ready gate (waive what is missing);
 *       adapters' {@code onEnable} (CraftEngine listeners, BetterModel subscriptions, BetterHud
 *       placeholders); (later) our listeners, packet layer.</li>
 * </ol>
 */
public final class TfpPlugin extends JavaPlugin implements TfpCommands.Handler {
    /** Log the pending ready signals if the gate is still closed this long after enable. */
    private static final long READY_WARN_SECONDS = 60;

    private final ReadyGate readyGate = new ReadyGate();
    private CompatGate.Report compat;
    private ConfigService config;
    private volatile Scheduler scheduler;
    private volatile Integrations integrations;
    private final HudSnapshotCache hudCache = new HudSnapshotCache(() -> {
        Integrations i = integrations;
        return i != null && i.activePlugins().contains("BetterHud");
    });

    @Override
    public void onLoad() {
        try {
            compat = CompatCheck.run(getServer(), getLogger());
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Compatibility check failed; TerraFirmaPlugin will not enable", e);
            compat = null;
            return;
        }
        if (compat.refused()) {
            return;
        }
        config = new ConfigService(getDataPath().resolve("config.yml"), this::defaultConfigText);
        logConfig(config.load());
        // M1-INST-01..03: install our content into CraftEngine/BetterHud, config edits, visual profile.
        // M1-INT-01..04: discover the adapters of the installed plugins; CraftEngine registers its
        // behaviours/functions/conditions here (must happen in onLoad).
        IntegrationContext ctx = new IntegrationContext(this, getLogger(), readyGate, () -> scheduler, hudCache,
                getDataPath().toAbsolutePath().getParent());
        integrations = Integrations.discover(ctx, name -> getServer().getPluginManager().getPlugin(name) != null,
                TfpPlugin.class.getClassLoader());
        integrations.onLoad();
    }

    @Override
    public void onEnable() {
        if (compat == null || compat.refused()) {
            getLogger().severe("TerraFirmaPlugin is disabled: see the compatibility errors above.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        scheduler = new ServerScheduler(this, Math.max(1, Runtime.getRuntime().availableProcessors() - 2));
        if (compat.missing().contains("CraftEngine")) {
            readyGate.waive(Signal.CRAFTENGINE_RELOADED, "CraftEngine not installed");
        }
        if (compat.missing().contains("BetterModel")) {
            readyGate.waive(Signal.BETTERMODEL_MODELS_READY, "BetterModel not installed");
        }
        // CRAFTENGINE_RELOADED is signalled by the CE adapter (M1-INT-02), BETTERMODEL_MODELS_READY by the
        // BetterModel adapter (M1-INT-03), REGISTRIES_LOADED by the registry loader's first swap.
        integrations.onEnable();
        getLogger().info("Integrations: " + (integrations.activePlugins().isEmpty() ? "none"
                : String.join(", ", integrations.activePlugins())));
        readyGate.ready().whenComplete((v, err) -> {
            if (err == null) {
                getLogger().info("TerraFirmaPlugin " + readyGate.describe());
            } else {
                getLogger().log(Level.SEVERE, "TerraFirmaPlugin failed to become ready", err);
            }
        });
        CompletableFuture.runAsync(() -> {
            if (!readyGate.isReady()) {
                getLogger().warning("Not ready after " + READY_WARN_SECONDS + " s, " + readyGate.describe());
            }
        }, CompletableFuture.delayedExecutor(READY_WARN_SECONDS, TimeUnit.SECONDS, scheduler.cpuExecutor()));
    }

    @Override
    public void onDisable() {
        if (integrations != null) {
            integrations.onDisable();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public ReadyGate readyGate() {
        return readyGate;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    /** The client-plugin services (degraded when a plugin is missing). Null until {@link #onLoad()} passed. */
    public Integrations integrations() {
        return integrations;
    }

    /** Null until {@link #onLoad()} passed the compat gate. */
    public ConfigService config() {
        return config;
    }

    @Override
    public String version() {
        StringBuilder sb = new StringBuilder("TerraFirmaPlugin ").append(getPluginMeta().getVersion())
                .append(" on ").append(getServer().getName()).append(' ').append(getServer().getMinecraftVersion())
                .append(", Java ").append(Runtime.version().feature());
        for (String name : new String[] {"CraftEngine", "BetterModel", "BetterHud"}) {
            Plugin p = getServer().getPluginManager().getPlugin(name);
            sb.append("\n  ").append(name).append(": ")
                    .append(p == null ? "not installed" : p.getPluginMeta().getVersion() + (p.isEnabled() ? "" : " (disabled)"));
        }
        sb.append("\n  status: ").append(readyGate.describe());
        return sb.toString();
    }

    @Override
    public CompletableFuture<String> reloadSettings() {
        return CompletableFuture.supplyAsync(() -> {
            ConfigService.Outcome o = config.load();
            logConfig(o);
            if (!o.applied()) {
                return "config.yml not applied: " + String.join("; ", o.warnings());
            }
            return "config.yml reloaded" + (o.warnings().isEmpty() ? "" : " with " + o.warnings().size()
                    + " warning(s), see the console") + ". Profile changes need a restart.";
        }, scheduler.ioExecutor());
    }

    private void logConfig(ConfigService.Outcome o) {
        if (o.created()) {
            getLogger().info("Wrote default config.yml");
        }
        if (!o.added().isEmpty()) {
            getLogger().info("config.yml: added missing sections with defaults: " + String.join(", ", o.added()));
        }
        for (String w : o.warnings()) {
            getLogger().warning("config.yml: " + w);
        }
    }

    private String defaultConfigText() {
        try (InputStream in = getResource("config.yml")) {
            if (in == null) {
                throw new IllegalStateException("config.yml is missing from the plugin jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
