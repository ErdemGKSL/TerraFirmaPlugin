/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rendered;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;

/**
 * PAR-INFRA-32: wires {@link ServerTimeMining} (break time of server-mined custom blocks in server ticks, independent
 * of the client's swing rate). The driver comes from {@link MiningDrivers}, filled by the CraftEngine adapter; without
 * CraftEngine the listener does nothing.
 */
public final class RenderedMiningModule implements FeatureModule {
    public static final String ID = "rendered_mining";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Scheduler scheduler = context.scheduler();
        ServerTimeMining mining = new ServerTimeMining(MiningDrivers::current,
                (player, tick, retired) -> scheduler.entityRepeating(player, 1L, 1L, tick, retired));
        context.services().provide(ServerTimeMining.class, mining);
        context.listen(mining);
    }
}
