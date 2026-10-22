/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.module;

import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.paper.blockdata.ChunkDataStore;
import dev.tfp.paper.config.ConfigService;
import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.integration.IntegrationContext;
import dev.tfp.paper.integration.Integrations;
import dev.tfp.paper.packet.PacketHandlerRegistry;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.pipeline.BlockAccess;
import dev.tfp.paper.pipeline.BlockChange;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.player.PlayerDataStore;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * What a {@link FeatureModule} gets from the plugin when it is enabled (PAR-INFRA-01). Every accessor is non-null on
 * a running server. Feature code reaches CraftEngine/BetterModel/BetterHud only through {@link #integrations()}.
 */
public interface ModuleContext {
    JavaPlugin plugin();

    Logger logger();

    /** Region/entity/global/async scheduling; never use the BukkitScheduler. */
    Scheduler scheduler();

    /** The client-plugin services (degraded implementations when a plugin is missing). */
    Integrations integrations();

    /** The context the integration adapters were built with (ready gate, HUD cache, food definitions). */
    IntegrationContext integrationContext();

    /** Our registries; {@code registryService().registries()} is empty until the first load completed. */
    RegistryService registryService();

    ConfigService config();

    Messages messages();

    PlayerDataStore playerData();

    ChunkDataStore chunkData();

    /** The global calendar clock; take one snapshot per operation. */
    GlobalClockHolder clock();

    HudSnapshotCache hudCache();

    /** Outgoing/incoming packet handlers; register before the layer is installed (that is, in {@code enable}). */
    PacketHandlerRegistry packets();

    /** Services the plugin and earlier modules provide; provide your own here. */
    ModuleServices services();

    /** Registers {@code listener} for this plugin. */
    default void listen(Listener listener) {
        plugin().getServer().getPluginManager().registerEvents(listener, plugin());
    }

    /** The region executor of {@code world} for an {@link AsyncDecisionPipeline} ("decide async, apply sync"). */
    default RegionExecutor regions(World world) {
        return new SchedulerRegions(world, scheduler());
    }

    /**
     * A "decide async, apply sync" pipeline for one job kind in {@code world}: snapshots on the owner thread,
     * {@code decide} on the CPU pool, changes applied by the region commit queue under {@code budget}.
     */
    default <S, N> AsyncDecisionPipeline<S, N> pipeline(String name, World world, BlockAccess<S> blocks,
            CommitBudget budget, AsyncDecisionPipeline.SnapshotSource<N> snapshots,
            Function<N, List<BlockChange<S>>> decide) {
        return new AsyncDecisionPipeline<>(name, regions(world), blocks, scheduler().cpuExecutor(), budget, snapshots,
                decide);
    }

    /** The plain implementation {@code TfpPlugin} builds (and tests fill with what they need). */
    record Of(JavaPlugin plugin, Logger logger, Scheduler scheduler, Integrations integrations,
            IntegrationContext integrationContext, RegistryService registryService, ConfigService config,
            Messages messages, PlayerDataStore playerData, ChunkDataStore chunkData, GlobalClockHolder clock,
            HudSnapshotCache hudCache, PacketHandlerRegistry packets, ModuleServices services)
            implements ModuleContext {
    }
}
