/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.core.catchup.SweepArea;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.plant.PlantGrowth;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.ChunkPos;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.worldgen.WorldSettingsLookup;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * PAR-FARM-25: registers the lazy column growth ({@link ColumnSweeper}) of the weeping, twisting and kelp plants: swept when a
 * chunk loads and around each player every {@value #PERIOD_TICKS} ticks. PAR-FARM-29 adds the vines, giant kelp flowers and
 * saguaros to the same sweep (vines only grow above 0 C at the instant temperature) and the survival of the growing shapes
 * ({@link ShapeSurvivalQueue}). Must be listed after the tags module.
 */
public final class GrowthModule implements FeatureModule {
    public static final String ID = "plant_growth";
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
        CustomBlocks blocks = context.integrations().customBlocks();
        Tags tags = context.services().require(Tags.class);
        context.listen(new ShapeSurvivalListener(blocks, new ShapeSurvivalQueue(blocks, tags::is, scheduler)));
        Warmth warmth = new Warmth(context);
        ColumnSweeper sweeper = new ColumnSweeper(blocks, scheduler, GrowthModule::randomTickSpeed, tags::is, warmth::samples);
        blocks.onChunkLoad(chunk -> {
            World world = chunk.getWorld();
            long delay = 100 + ThreadLocalRandom.current().nextInt(300);
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
            return value == null ? DEFAULT_RANDOM_TICK_SPEED : value;
        } catch (LinkageError | RuntimeException e) {
            return DEFAULT_RANDOM_TICK_SPEED;
        }
    }

    /**
     * {@code TFCVineBlock.randomTick}: {@link PlantGrowth#vineMayGrow} of the instant temperature at the vine, sampled at
     * {@link ShapeGrowth#TEMPERATURE_SAMPLES} instants over the time the chunk was away. No set calendar: always warm.
     */
    private static final class Warmth {
        private final ModuleContext context;
        private final Map<Long, ChunkClimate> chunks = new ConcurrentHashMap<>();

        Warmth(ModuleContext context) {
            this.context = context;
        }

        int samples(World world, int x, int y, int z, long elapsedTicks) {
            ClockSnapshot clock = context.clock().get();
            if (!clock.isSet() || world.getEnvironment() != World.Environment.NORMAL) {
                return ShapeGrowth.TEMPERATURE_SAMPLES;
            }
            var climate = WorldSettingsLookup.climate(world);
            long key = world.getSeed() * 31 + ((long) (x >> 4) << 32 ^ (z >> 4) & 0xFFFFFFFFL);
            if (chunks.size() > 512) {
                chunks.clear();
            }
            ChunkClimate chunk = chunks.computeIfAbsent(key, k -> ChunkClimateSource.climateAt(world.getSeed(), x, z));
            int warm = 0;
            for (int i = 0; i < ShapeGrowth.TEMPERATURE_SAMPLES; i++) {
                long t = Math.max(0, clock.calendarTicks() - elapsedTicks * i / (ShapeGrowth.TEMPERATURE_SAMPLES - 1));
                if (PlantGrowth.vineMayGrow(climate.instantTemperature(new BlockPos(x, y, z), chunk, t, clock.daysInMonth()))) {
                    warm++;
                }
            }
            return warm;
        }
    }
}
