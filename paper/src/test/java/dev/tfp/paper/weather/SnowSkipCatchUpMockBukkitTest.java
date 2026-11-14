/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weather.SnowCatchUp;
import dev.tfp.core.world.ChunkPos;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-CLIM-18: a chunk that stays loaded across a calendar skip gets the snow catch-up of the skipped window, once, and
 * is stamped at the skip's end; a skip of at most 4 000 calendar ticks, the calendar's own advance and an unloaded chunk
 * get nothing. Region tasks are queued and pumped by the test; the worker pool only collects the plan jobs (the plan
 * itself is {@code SnowCatchUp}'s, tested in core).
 */
class SnowSkipCatchUpMockBukkitTest extends MockServerTest {
    /** One month at the default 8-day month. */
    private static final long MONTH = 8L * 24_000L;

    private final GlobalClockHolder clock = new GlobalClockHolder();
    private final QueueScheduler scheduler = new QueueScheduler();
    private World world;
    private SnowService snow;

    @BeforeEach
    void setUpWorld() {
        world = server.addSimpleWorld("tfp_snow_skip");
        snow = new SnowService(clock, scheduler);
    }

    private void publish(long calendarTicks) {
        clock.publish(calendarTicks, calendarTicks, 8, true);
    }

    /** Loads the chunk the way the server does (event, then the region pass) and forgets the load's own plan job. */
    private Chunk load(int cx, int cz) {
        Chunk chunk = world.getChunkAt(cx, cz);
        for (int[] c : new int[][] {{8, 8}, {3, 11}}) {
            world.getBlockAt(cx * 16 + c[0], 64, cz * 16 + c[1]).setType(Material.STONE);
        }
        snow.onChunkLoad(new ChunkLoadEvent(chunk, false));
        scheduler.pumpRegions();
        scheduler.plans.clear();
        return chunk;
    }

    private static long stamp(Chunk chunk) {
        return ChunkSnowTime.read(chunk.getPersistentDataContainer()).lastCalendarTick();
    }

    @Test
    void aLoadedChunkCatchesUpAMonthSkipAndIsStampedAtItsEnd() {
        publish(1_000L);
        Chunk chunk = load(1, 2);
        assertThat(stamp(chunk)).isEqualTo(1_000L);
        assertThat(snow.loadedChunks(world)).containsExactly(ChunkPos.key(1, 2));
        snow.observeClock(); // the first reading
        long dueBefore = snow.stats().due.sum();

        publish(1_000L + MONTH); // /tfp time add months 1
        snow.observeClock();
        assertThat(scheduler.regions).hasSize(1);
        scheduler.pumpRegions();

        assertThat(scheduler.plans).as("one catch-up plan on the worker pool").hasSize(1);
        assertThat(snow.stats().due.sum()).isEqualTo(dueBefore + 1);
        assertThat(stamp(chunk)).isEqualTo(1_000L + MONTH);

        // The next round sees only the calendar's own advance: no second pass.
        publish(1_000L + MONTH + SnowService.PERIOD_TICKS);
        snow.observeClock();
        assertThat(scheduler.regions).isEmpty();
    }

    @Test
    void aSkipOfAtMostTheThresholdPlansNothing() {
        publish(1_000L);
        Chunk chunk = load(0, 0);
        snow.observeClock();

        publish(1_000L + SnowCatchUp.SAMPLE_INTERVAL); // TFC: timeSinceTick > 4000 is strict
        snow.observeClock();
        scheduler.pumpRegions();
        assertThat(scheduler.plans).isEmpty();
        assertThat(stamp(chunk)).isEqualTo(1_000L);

        publish(1_000L + SnowCatchUp.SAMPLE_INTERVAL + 3_000L);
        snow.observeClock();
        scheduler.pumpRegions();
        assertThat(scheduler.plans).isEmpty();
    }

    @Test
    void anUnloadedChunkIsNotTouchedBySkips() {
        publish(1_000L);
        Chunk chunk = load(3, 3);
        snow.observeClock();
        snow.onChunkUnload(new ChunkUnloadEvent(chunk));
        assertThat(snow.loadedChunks(world)).isEmpty();
        long unloadStamp = stamp(chunk);

        publish(1_000L + MONTH);
        snow.observeClock();
        assertThat(scheduler.regions).isEmpty();
        assertThat(stamp(chunk)).isEqualTo(unloadStamp);
    }

    @Test
    void theDetectorReportsOnlyJumpsPastTheThreshold() {
        SnowSkipCatchUp skips = new SnowSkipCatchUp();
        assertThat(skips.observe(ClockSnapshot.UNSET)).isNull();
        assertThat(skips.observe(new ClockSnapshot(1, 10_000L, 10_000L, 8, true))).as("first reading").isNull();
        assertThat(skips.observe(new ClockSnapshot(2, 10_020L, 10_020L, 8, true))).as("normal advance").isNull();
        assertThat(skips.observe(new ClockSnapshot(3, 30_000L, 30_000L, 8, true)))
                .isEqualTo(new SnowSkipCatchUp.Skip(10_020L, 30_000L));
        assertThat(skips.observe(new ClockSnapshot(4, 20_000L, 20_000L, 8, true))).as("a step back").isNull();
        assertThat(skips.observe(new ClockSnapshot(5, 24_001L, 24_001L, 8, true)))
                .isEqualTo(new SnowSkipCatchUp.Skip(20_000L, 24_001L));
    }

    @Test
    void theWindowStartsAtTheLaterOfTheStampAndTheSkip() {
        PersistentDataContainer data = world.getChunkAt(5, 5).getPersistentDataContainer();
        // Loaded at 1 000 and live-sampled until the skip at 50 000: only the skip is caught up.
        new ChunkSnowTime(1_000L).write(data);
        SnowCatchUpSchedule.Load load = SnowSkipCatchUp.onSkip(data, new SnowSkipCatchUp.Skip(50_000L, 50_000L + MONTH));
        assertThat(load).isEqualTo(new SnowCatchUpSchedule.Load(true, 50_000L, 50_000L + MONTH));
        assertThat(ChunkSnowTime.read(data)).isEqualTo(new ChunkSnowTime(50_000L + MONTH));

        // A stamp newer than the skip's start (the chunk loaded inside it) shortens the window.
        new ChunkSnowTime(60_000L).write(data);
        load = SnowSkipCatchUp.onSkip(data, new SnowSkipCatchUp.Skip(50_000L, 62_000L));
        assertThat(load.catchUp()).isFalse();
        assertThat(ChunkSnowTime.read(data)).isEqualTo(new ChunkSnowTime(62_000L));

        // No stamp: the window is the skip.
        ChunkSnowTime.never().write(data);
        load = SnowSkipCatchUp.onSkip(data, new SnowSkipCatchUp.Skip(50_000L, 60_000L));
        assertThat(load).isEqualTo(new SnowCatchUpSchedule.Load(true, 50_000L, 60_000L));
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
