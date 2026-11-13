/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.piston;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.pipeline.CommitBudget;

/** PAR-DATA-16: provides {@link PistonFluidService} and registers {@link PistonFluidListener}. */
public final class PistonFluidModule implements FeatureModule {
    public static final String ID = "piston_fluid";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        PistonFluidService service = new PistonFluidService(context::regions, context.integrations().customBlocks(),
                context.scheduler().cpuExecutor(),
                new CommitBudget(PistonFluidService.BUDGET_NANOS, System::nanoTime));
        context.services().provide(PistonFluidService.class, service);
        context.listen(new PistonFluidListener(service));
    }
}
