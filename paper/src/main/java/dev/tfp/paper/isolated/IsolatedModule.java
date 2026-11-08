/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.isolated;

import dev.tfp.core.isolated.IsolatedRule;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.tags.Tags;

/**
 * PAR-PHYS-06: the isolated raw rock check. Provides {@link IsolatedService} and registers {@link IsolatedListener}.
 * Tag membership comes from {@link Tags} ({@code #tfp:breaks_when_isolated}); collision from the live server state
 * ({@code Block.isPassable}: an empty collision shape; for a CraftEngine block the shape of its hitbox carrier). Must
 * come after {@code TagsModule} and {@code LootModule} in the service file.
 */
public final class IsolatedModule implements FeatureModule {
    public static final String ID = "isolated";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        LootService loot = context.services().require(LootService.class);
        CustomBlocks custom = context.integrations().customBlocks();
        IsolatedService service = new IsolatedService(context::regions,
                block -> tags.is(block, IsolatedRule.BREAKS_WHEN_ISOLATED), block -> !block.isPassable(),
                new LootPopper(loot, custom, context.logger()), context.scheduler().cpuExecutor(),
                new CommitBudget(IsolatedService.BUDGET_NANOS, System::nanoTime));
        context.services().provide(IsolatedService.class, service);
        context.listen(new IsolatedListener(service, context.scheduler()));
    }
}
