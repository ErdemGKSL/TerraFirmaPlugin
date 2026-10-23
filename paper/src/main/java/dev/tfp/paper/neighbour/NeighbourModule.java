/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.neighbour;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.pipeline.CommitBudget;

/**
 * PAR-INFRA-10: provides {@link NeighbourService} and registers {@link NeighbourListener}. Rules are added by the
 * feature modules after it in the service file ({@code services().require(NeighbourService.class).register(...)}).
 */
public final class NeighbourModule implements FeatureModule {
    public static final String ID = "neighbour";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        NeighbourService service = new NeighbourService(context::regions, context.integrations().customBlocks(),
                context.scheduler().cpuExecutor(), new CommitBudget(NeighbourService.BUDGET_NANOS, System::nanoTime));
        context.services().provide(NeighbourService.class, service);
        context.listen(new NeighbourListener(service));
    }
}
