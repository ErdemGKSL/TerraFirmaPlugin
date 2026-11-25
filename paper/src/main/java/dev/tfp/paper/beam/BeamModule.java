/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.beam;

import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.pipeline.CommitBudget;
import org.bukkit.event.HandlerList;

/**
 * PAR-PHYS-04: support beams. Provides {@link BeamService} and registers {@link BeamListener} (placing the support item,
 * neighbour changes). The collapse and landslide support tables read the horizontal beams by id from TFC's loaded support
 * definition ({@code CollapseSupports}), so they need nothing from here. Acts only on the {@code tfp:wood/*_support/*}
 * CraftEngine blocks and the {@code tfp:wood/support/*} items.
 */
public final class BeamModule implements FeatureModule {
    public static final String ID = "beam";

    private volatile BeamListener listener;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        BeamService service = new BeamService(context::regions, context.integrations().customBlocks(),
            context.integrations().customItems(), context.scheduler().cpuExecutor(),
            new CommitBudget(BeamService.BUDGET_NANOS, System::nanoTime), BeamWorld.STURDY_UP);
        context.services().provide(BeamService.class, service);
        BeamListener l = new BeamListener(service, context.scheduler(), context.integrations().customItems());
        context.listen(l);
        listener = l;
    }

    @Override
    public void disable() {
        BeamListener l = listener;
        listener = null;
        if (l != null) {
            HandlerList.unregisterAll(l);
        }
    }
}
