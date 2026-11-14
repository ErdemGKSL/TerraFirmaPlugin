/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weather.SeaIce;
import dev.tfp.core.weather.SnowCatchUp;
import dev.tfp.core.weather.SnowRule;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.ChunkPos;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Entity;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-CLIM-21: with the world's weather flag off SnowService neither accumulates nor melts snow, live or caught up, as
 * TFC's {@code WeatherHelpers.onTickChunk} returning at {@code !isWeatherEnabled}: a cold wet site in a rain window and a
 * warm site get no event, a snapshot taken with the flag off decides no change (forced catch-up steps included), and a
 * load, a skip or an unload with the flag off plans nothing and leaves the chunk's stamp alone. Turning the flag off
 * stamps the loaded chunks; turning it back on catches them up from there.
 */
class SnowServiceWeatherFlagTest extends MockServerTest {
    private static final long MONTH = SnowCatchUp.MONTH_WINDOW;
    private static final long YEAR = 12 * MONTH;

    private final GlobalClockHolder clock = new GlobalClockHolder();
    private final QueueScheduler scheduler = new QueueScheduler();
    private final AtomicBoolean weather = new AtomicBoolean(true);
    private World world;
    private SnowService snow;

    @BeforeEach
    void setUpWorld() {
        world = server.addSimpleWorld("tfp_snow_flag");
        snow = new SnowService(clock, scheduler, w -> weather.get());
    }

    private static ClockSnapshot at(long calendarTicks) {
        return new ClockSnapshot(1, calendarTicks, calendarTicks, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true);
    }

    private void publish(long calendarTicks) {
        clock.publish(calendarTicks, calendarTicks, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true);
    }

    /** The first column and calendar tick (one year, 4 000-tick steps) where the climate gives {@code event}. */
    private static long[] find(long seed, SnowRule.Event event) {
        for (int i = 0; i < 441; i++) {
            BlockPos pos = new BlockPos((i % 21 - 10) * 2_000, 70, (i / 21 - 10) * 2_000);
            for (long t = 4_000; t < YEAR; t += 4_000) {
                if (SnowService.liveEvent(seed, pos, at(t), true) == event) {
                    return new long[] {pos.x(), pos.z(), t};
                }
            }
        }
        return null;
    }

    @Test
    void aColdWetSiteInARainWindowAndAWarmSiteGetNoEventWithTheFlagOff() {
        long seed = world.getSeed();
        long[] cold = find(seed, SnowRule.Event.ACCUMULATE);
        assertThat(cold).as("a cold site precipitating at some tick of the year").isNotNull();
        BlockPos c = new BlockPos((int) cold[0], 70, (int) cold[1]);
        assertThat(SnowService.liveEvent(seed, c, at(cold[2]), true)).isEqualTo(SnowRule.Event.ACCUMULATE);
        assertThat(SnowService.liveEvent(seed, c, at(cold[2]), false)).isEqualTo(SnowRule.Event.NONE);

        long[] warm = find(seed, SnowRule.Event.MELT);
        assertThat(warm).as("a site above 2 degrees at some tick of the year").isNotNull();
        BlockPos w = new BlockPos((int) warm[0], 70, (int) warm[1]);
        assertThat(SnowService.liveEvent(seed, w, at(warm[2]), true)).isEqualTo(SnowRule.Event.MELT);
        assertThat(SnowService.liveEvent(seed, w, at(warm[2]), false)).isEqualTo(SnowRule.Event.NONE);
    }

    /**
     * MockBukkit's block data with {@code isFaceSturdy} answered (MockBukkit does not implement it): full faces for
     * solid blocks, none otherwise. Everything else, and every interface of the block's class, is the mock's.
     */
    private static BlockData sturdy(Material material) {
        BlockData real = material.createBlockData();
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> c = real.getClass(); c != null; c = c.getSuperclass()) {
            collect(c, interfaces);
        }
        if (material == Material.SNOW) {
            interfaces.add(Snow.class); // MockBukkit's snow block data is not a Snow: one layer
        }
        return (BlockData) Proxy.newProxyInstance(SnowServiceWeatherFlagTest.class.getClassLoader(),
                interfaces.toArray(Class<?>[]::new), (p, m, a) -> {
                    if (m.getName().equals("isFaceSturdy")) {
                        return material.isSolid();
                    }
                    switch (m.getName()) {
                        case "getLayers", "getMinimumLayers" -> {
                            return 1;
                        }
                        case "getMaximumLayers" -> {
                            return SnowRule.MAX_SNOW_LAYERS;
                        }
                        default -> { }
                    }
                    try {
                        return m.invoke(real, a);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static void collect(Class<?> c, Set<Class<?>> out) {
        for (Class<?> i : c.getInterfaces()) {
            if (out.add(i)) {
                collect(i, out);
            }
        }
    }

    /** A column of stone up to y 64 and {@code surfaceBlock} at the surface (y 65), open to the sky. */
    private static SnowService.SnowSample sample(Material surfaceBlock, SnowRule.Event forced, boolean weatherOn) {
        BlockPos surface = new BlockPos(0, 65, 0);
        Map<BlockPos, SnowBlock> blocks = new java.util.HashMap<>();
        for (int y = 40; y <= 64; y++) {
            blocks.put(new BlockPos(0, y, 0), SnowBlock.of(sturdy(Material.STONE)));
        }
        blocks.put(surface, SnowBlock.of(sturdy(surfaceBlock)));
        blocks.put(surface.offset(0, 1, 0), SnowBlock.of(sturdy(Material.AIR)));
        return new SnowService.SnowSample(surface, Map.copyOf(blocks), Map.of(), new boolean[] {true, true, true}, false,
                1L, 7L, 0L, forced, weatherOn);
    }

    @Test
    void aSnapshotTakenWithTheFlagOffDecidesNoAccumulationAndNoMelt() {
        publish(10_000L);
        SeaIce pattern = SeaIce.forLevelSeed(1L);
        // The same catch-up steps decide changes with the flag on...
        assertThat(snow.decide(sample(Material.AIR, SnowRule.Event.ACCUMULATE, true), pattern)).isNotEmpty();
        assertThat(snow.decide(sample(Material.SNOW, SnowRule.Event.MELT, true), pattern)).isNotEmpty();
        // ...and nothing with it off
        assertThat(snow.decide(sample(Material.AIR, SnowRule.Event.ACCUMULATE, false), pattern)).isEmpty();
        assertThat(snow.decide(sample(Material.SNOW, SnowRule.Event.MELT, false), pattern)).isEmpty();
        // A live step with the flag off decides nothing either
        assertThat(snow.decide(sample(Material.AIR, null, false), pattern)).isEmpty();
        assertThat(snow.decide(sample(Material.SNOW, null, false), pattern)).isEmpty();
    }

    private Chunk loadChunk(int cx, int cz) {
        Chunk chunk = world.getChunkAt(cx, cz);
        for (int[] c : new int[][] {{8, 8}, {3, 11}}) {
            world.getBlockAt(cx * 16 + c[0], 64, cz * 16 + c[1]).setType(Material.STONE);
        }
        snow.onChunkLoad(new ChunkLoadEvent(chunk, false));
        scheduler.pumpRegions();
        return chunk;
    }

    private static long stamp(Chunk chunk) {
        return ChunkSnowTime.read(chunk.getPersistentDataContainer()).lastCalendarTick();
    }

    @Test
    void withTheFlagOffALoadASkipAndAnUnloadPlanNothingAndKeepTheStamp() {
        publish(1_000L + 3 * MONTH); // the stamp is three months old: a load with the flag on would catch up
        weather.set(false);
        Chunk chunk = world.getChunkAt(2, 2);
        new ChunkSnowTime(1_000L).write(chunk.getPersistentDataContainer());
        loadChunk(2, 2);
        assertThat(scheduler.plans).as("no catch-up plan on load").isEmpty();
        assertThat(stamp(chunk)).isEqualTo(1_000L);
        assertThat(snow.loadedChunks(world)).containsExactly(ChunkPos.key(2, 2));

        snow.observeClock();
        publish(1_000L + 4 * MONTH); // a calendar skip
        snow.observeClock();
        scheduler.pumpRegions();
        assertThat(scheduler.plans).as("no catch-up plan on a skip").isEmpty();
        assertThat(stamp(chunk)).isEqualTo(1_000L);

        snow.onChunkUnload(new ChunkUnloadEvent(chunk));
        assertThat(stamp(chunk)).as("no stamp on unload").isEqualTo(1_000L);
    }

    @Test
    void turningTheFlagOffStampsTheLoadedChunksAndTurningItOnCatchesThemUp() {
        publish(1_000L);
        Chunk chunk = loadChunk(4, 1);
        scheduler.plans.clear();
        assertThat(stamp(chunk)).isEqualTo(1_000L);
        snow.observeClock();

        publish(50_000L); // live-sampled until here
        weather.set(false);
        snow.observeClock();
        scheduler.pumpRegions();
        assertThat(stamp(chunk)).as("stamped when the flag turned off").isEqualTo(50_000L);
        assertThat(scheduler.plans).isEmpty();

        publish(50_000L + 2 * MONTH);
        snow.observeClock();
        scheduler.pumpRegions();
        assertThat(scheduler.plans).as("nothing while off").isEmpty();

        weather.set(true);
        snow.observeClock();
        scheduler.pumpRegions();
        assertThat(scheduler.plans).as("one catch-up of the off time when it turns back on").hasSize(1);
        assertThat(stamp(chunk)).isEqualTo(50_000L + 2 * MONTH);

        // The next round sees no change of the flag
        snow.observeClock();
        assertThat(scheduler.regions).isEmpty();
    }

    @Test
    void aFlagToggledBackBeforeItsPassDoesNothing() {
        publish(1_000L);
        Chunk chunk = loadChunk(0, 3);
        snow.observeClock();
        publish(9_000L);
        weather.set(false);
        snow.observeClock();
        weather.set(true); // before the region pass ran
        scheduler.pumpRegions();
        assertThat(stamp(chunk)).isEqualTo(1_000L);
    }

    /** Region tasks are queued for {@link #pumpRegions}; worker jobs are collected and never run. */
    private static final class QueueScheduler implements Scheduler {
        final Deque<Runnable> regions = new ArrayDeque<>();
        final List<Runnable> plans = new ArrayList<>();

        void pumpRegions() {
            Runnable r;
            while ((r = regions.poll()) != null) {
                r.run();
            }
        }

        @Override
        public void region(World world, int chunkX, int chunkZ, Runnable task) {
            regions.add(task);
        }

        @Override
        public boolean isOwner(World world, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public Executor cpuExecutor() {
            return plans::add;
        }

        @Override
        public void cpu(Runnable task) {
            plans.add(task);
        }

        @Override
        public void region(Location location, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle regionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
                Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entity(Entity entity, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityRepeating(Entity entity, long initialDelayTicks, long periodTicks, Runnable task,
                Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void global(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> io(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Executor ioExecutor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
        }
    }
}
