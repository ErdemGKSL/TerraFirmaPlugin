/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.paper.ready.ReadyGate;
import dev.tfp.paper.scheduler.Scheduler;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

/**
 * What an adapter gets from the plugin.
 *
 * @param plugin our plugin (event registration owner)
 * @param logger our logger
 * @param readyGate the ready gate the adapter signals
 * @param scheduler our scheduler; {@code null} until our {@code onEnable()}
 * @param hudCache the shared per-player HUD snapshots
 * @param clock the published global clock (volatile reads; HUD date placeholders)
 * @param pluginsDirectory the server's {@code plugins/} folder (install targets are computed from it)
 */
public record IntegrationContext(Plugin plugin, Logger logger, ReadyGate readyGate, Supplier<Scheduler> scheduler,
        HudSnapshotCache hudCache, GlobalClockHolder clock, Path pluginsDirectory) {
    public IntegrationContext {
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(readyGate, "readyGate");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(hudCache, "hudCache");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(pluginsDirectory, "pluginsDirectory");
    }

    /** The scheduler; throws if called before our {@code onEnable()}. */
    public Scheduler requireScheduler() {
        Scheduler s = scheduler.get();
        if (s == null) {
            throw new IllegalStateException("the scheduler exists only after TerraFirmaPlugin is enabled");
        }
        return s;
    }
}
