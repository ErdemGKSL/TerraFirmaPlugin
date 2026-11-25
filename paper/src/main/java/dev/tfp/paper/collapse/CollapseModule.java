/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.collapse;

import dev.tfp.paper.falling.BukkitFallWorld;
import dev.tfp.paper.falling.FallingBlocks;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * PAR-PHYS-02: mining collapses. Provides {@link CollapseService} and {@link CollapseSupports} (the support table
 * PAR-PHYS-04 fills) and registers {@link CollapseListener}. Needs {@code Tags}, {@code Recipes}, {@code LootService} and
 * {@link FallingBlocks}: must come after {@code TagsModule}, {@code RecipeModule}, {@code LootModule} and
 * {@code FallingModule} in the service file.
 */
public final class CollapseModule implements FeatureModule {
    public static final String ID = "collapse";

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
        CollapseWorld world = new BukkitCollapseWorld(tags, recipes::index, context.integrations().customBlocks(), fall,
                falling);
        CollapseSupports supports = new CollapseSupports(() -> context.registryService().registries());
        CollapseConfig config = CollapseConfig.parse(
                yaml(context.config().file()).getConfigurationSection(CollapseConfig.SECTION),
                warning -> context.logger().warning(warning));
        CollapseService service = new CollapseService(context::regions, world, context.scheduler(),
                context.scheduler().cpuExecutor(), new CommitBudget(CollapseService.BUDGET_NANOS, System::nanoTime),
                supports, config);
        context.services().provide(CollapseSupports.class, supports);
        context.services().provide(CollapseService.class, service);
        context.listen(new CollapseListener(service));
    }

    private static YamlConfiguration yaml(Path file) {
        return file == null || !Files.isRegularFile(file) ? new YamlConfiguration()
                : YamlConfiguration.loadConfiguration(file.toFile());
    }
}
