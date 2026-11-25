/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.grass;

import dev.tfp.core.catchup.SweepArea;
import dev.tfp.core.world.ChunkPos;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * PAR-GATHER-11: the lazy sweep of grass spread and death. A chunk is swept shortly after it loads and every
 * {@value #PERIOD_TICKS} ticks for the chunks within {@value #RADIUS} chunks of each player ({@link GrassSweeper}).
 */
public final class GrassModule implements FeatureModule {
    public static final String ID = "grass";
    static final long PERIOD_TICKS = 600;
    static final int RADIUS = 3;

    private volatile Scheduler.TaskHandle task;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Scheduler scheduler = context.scheduler();
        CustomBlocks blocks = context.integrations().customBlocks();
        GrassSweeper sweeper = new GrassSweeper(blocks, scheduler, GrassModule::randomTickSpeed)
            .withRisingRocks(new RisingRocks(blocks, () -> context.clock().get()));
        blocks.onChunkLoad(chunk -> {
            World world = chunk.getWorld();
            long delay = 40 + ThreadLocalRandom.current().nextInt(200);
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
            return value == null ? GrassSweepRules.RANDOM_TICK_SPEED : value;
        } catch (LinkageError | RuntimeException e) {
            // the game rule API differs between 1.21.x and 26.x: use the vanilla default there
            return GrassSweepRules.RANDOM_TICK_SPEED;
        }
    }
}
