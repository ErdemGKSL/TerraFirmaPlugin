/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.lifecycle;

import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.packet.PacketHandler;
import dev.tfp.paper.packet.PacketLayers;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * PAR-WORLD-63: the plant stage looks at packet time (PAR-WORLD-61's rule). Reads {@link PlantStageTable} on the CPU pool,
 * resolves it to server ids once CraftEngine has loaded and after every CraftEngine reload (which {@code /tfp reload}
 * triggers), tracks the TFC overworld of each player and, when PacketEvents is installed, registers
 * {@code dev.tfp.paper.packet.plantstage.PlantStagePacketHandler} reflectively (only the packet layer links PacketEvents).
 *
 * <p>Season changes reach the client through chunk resends: the seasonal biome service resends the chunks in view at each
 * month change (M2-SEA-02, its rate limiter), and every resent or newly loaded chunk carries the current look.
 */
public final class PlantStageModule implements FeatureModule {
    public static final String ID = "plant_stage";
    /** The PacketEvents-linked handler, in the packet layer. */
    static final String PACKET_HANDLER = "dev.tfp.paper.packet.plantstage.PlantStagePacketHandler";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) throws Exception {
        if (context.plugin().getServer().getPluginManager().getPlugin(PacketLayers.PACKETEVENTS) == null) {
            context.logger().info("PacketEvents not installed: plants keep their blooming look all year");
            return;
        }
        PlantStageService service = new PlantStageService(context.clock(), context.logger());
        context.services().provide(PlantStageSource.class, service);
        context.listen(service);
        context.packets().register((PacketHandler) Class.forName(PACKET_HANDLER)
                .getConstructor(PlantStageSource.class, java.util.logging.Logger.class)
                .newInstance(service, context.logger()));
        BlockVisuals visuals = context.integrations().blockVisuals();
        visuals.onReload(() -> context.scheduler().cpu(() -> service.resolve(visuals)));
        ClassLoader loader = context.plugin().getClass().getClassLoader();
        CompletableFuture.supplyAsync(() -> PlantStageTable.read(RegistryService.jarDataRoot(loader).resolve(PlantStageTable.FILE)),
                context.scheduler().cpuExecutor()).whenComplete((table, err) -> {
                    if (err != null) {
                        context.logger().log(Level.WARNING, "Cannot load the plant stage table; plants keep their blooming look", err);
                        return;
                    }
                    service.table(table);
                    service.resolve(visuals);
                });
    }
}
