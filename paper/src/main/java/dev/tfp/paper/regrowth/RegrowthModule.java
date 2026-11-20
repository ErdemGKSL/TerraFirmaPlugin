/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 */
package dev.tfp.paper.regrowth;

import dev.tfp.core.catchup.SweepArea;
import dev.tfp.core.world.ChunkPos;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.ModifierExtensions;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * PAR-FARM-11: plants that spread over time (the lazy sweep of grasses and tall plants, like {@code GrassModule}), flower cuttings from
 * shearing a flower and the {@code tfc:flower_cutting} crafting modifier. A chunk is swept shortly after it loads and every
 * {@value #PERIOD_TICKS} ticks around each player. Must be listed after the tags and recipe modules.
 */
public final class RegrowthModule implements FeatureModule {
    public static final String ID = "regrowth";
    static final long PERIOD_TICKS = 600;
    static final int RADIUS = 3;
    static final int DEFAULT_RANDOM_TICK_SPEED = 3;

    private volatile Scheduler.TaskHandle task;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Scheduler scheduler = context.scheduler();
        Tags tags = context.services().require(Tags.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        PlantSweeper sweeper = new PlantSweeper(blocks, tags, scheduler, RegrowthModule::randomTickSpeed, () -> context.clock().get());
        blocks.onChunkLoad(chunk -> {
            World world = chunk.getWorld();
            long delay = 60 + ThreadLocalRandom.current().nextInt(200);
            scheduler.regionDelayed(world, chunk.getX(), chunk.getZ(), delay, () -> sweeper.sweepChunk(world, chunk.getX(), chunk.getZ()));
        });
        task = scheduler.globalRepeating(PERIOD_TICKS, PERIOD_TICKS, () -> {
            for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
                scheduler.entity(player, () -> {
                    Location loc = player.getLocation();
                    World world = loc.getWorld();
                    if (world == null) {
                        return;
                    }
                    for (ChunkPos c : SweepArea.around(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, RADIUS)) {
                        scheduler.region(world, c.x(), c.z(), () -> sweeper.sweepChunk(world, c.x(), c.z()));
                    }
                }, () -> {
                });
            }
        });
        context.listen(new CuttingListener(blocks, context.integrations().customItems(), tags));
        context.services().get(Recipes.class).ifPresent(r -> r.extend(ModifierExtensions.Kind.FLOWER_CUTTING,
            (stack, input, inputs, modifier) -> CuttingPdc.read(input)
                .flatMap(id -> context.integrations().customItems().create(id, 2))));
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle t = task;
        task = null;
        if (t != null) {
            t.cancel();
        }
    }

    @SuppressWarnings("removal")
    private static int randomTickSpeed(World world) {
        try {
            Integer value = world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
            return value == null ? DEFAULT_RANDOM_TICK_SPEED : value;
        } catch (LinkageError | RuntimeException e) {
            // the game rule API differs between 1.21.x and 26.x: use the vanilla default there
            return DEFAULT_RANDOM_TICK_SPEED;
        }
    }
}
