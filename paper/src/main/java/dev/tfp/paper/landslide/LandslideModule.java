/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.landslide;

import dev.tfp.paper.collapse.CollapseSupports;
import dev.tfp.paper.falling.BukkitFallWorld;
import dev.tfp.paper.falling.FallingBlocks;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;

/**
 * PAR-PHYS-03: landslides and gravity soils. Provides {@link LandslideService} and registers {@link LandslideListener}.
 * Needs {@code Tags}, {@code Recipes}, {@code LootService} and {@link FallingBlocks}: must come after
 * {@code TagsModule}, {@code RecipeModule}, {@code LootModule} and {@code FallingModule} in the service file.
 */
public final class LandslideModule implements FeatureModule {
    public static final String ID = "landslide";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        Recipes recipes = context.services().require(Recipes.class);
        LootService loot = context.services().require(LootService.class);
        FallingBlocks falling = context.services().require(FallingBlocks.class);
        BukkitFallWorld fall = new BukkitFallWorld(tags, context.integrations().customBlocks(),
                context.integrations().blockVisuals(), loot);
        LandslideWorld world = new BukkitLandslideWorld(tags, recipes::index, context.integrations().customBlocks(),
                fall, falling);
        // PAR-PHYS-04: support beams hold landslide blocks up (TFC Support.isSupported); the collapse module comes later in
        // the service file, so the same loaded definitions are read through a table of our own
        CollapseSupports supports = new CollapseSupports(() -> context.registryService().registries());
        LandslideService service = new LandslideService(context::regions, world, context.scheduler(),
                context.scheduler().cpuExecutor(), new CommitBudget(LandslideService.BUDGET_NANOS, System::nanoTime),
                supports);
        context.services().provide(LandslideService.class, service);
        context.listen(new LandslideListener(service));
    }
}
