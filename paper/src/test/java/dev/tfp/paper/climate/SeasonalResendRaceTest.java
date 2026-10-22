/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/**
 * M2-SEA-02 regression: a computation that started in the old colour phase must not take the resend of the new phase
 * with it. Timeline: a chunk is requested in January; the calendar moves to February and the phase check clears the
 * cooldowns; the stale January computation finishes; the global tick drains; the packet thread misses on the new table
 * and asks again; the February computation finishes. The February resend must be queued.
 */
class SeasonalResendRaceTest {
    private static final int DAYS = ClockSnapshot.DEFAULT_DAYS_IN_MONTH;
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final SeasonalBiomeService.ChunkKey KEY = new SeasonalBiomeService.ChunkKey(WORLD, 3, -2);
    private static final OverworldClimate CLIMATE = OverworldClimate.forWorldSeed(42L, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
    private static final SeasonalBiomeRemap.Terrain TERRAIN = new SeasonalBiomeRemap.Terrain() {
        @Override
        public int biomeId(int blockX, int blockZ) {
            return TfcBiomes.PLAINS;
        }

        @Override
        public SeasonalBiomeRemap.ChunkInputs climates(int chunkX, int chunkZ) {
            return (x, z) -> new ChunkClimate(12f, 300f, 0.6f, 40f);
        }
    };

    /** Cpu tasks wait in a queue until the test runs them; the global repeating task is kept to be run by hand. */
    private static final class ManualScheduler implements Scheduler {
        final ArrayDeque<Runnable> cpu = new ArrayDeque<>();
        Runnable globalTick;

        @Override
        public void region(Location location, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void region(World world, int chunkX, int chunkZ, Runnable task) {
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
            globalTick = task;
            return null;
        }

        @Override
        public void cpu(Runnable task) {
            cpu.add(task);
        }

        @Override
        public Executor cpuExecutor() {
            return cpu::add;
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
        public boolean isOwner(World world, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void shutdown() {
        }
    }

    @Test
    void aStaleComputationDoesNotSuppressTheResendOfTheNewPhase() {
        ManualScheduler scheduler = new ManualScheduler();
        GlobalClockHolder clock = new GlobalClockHolder();
        publishMonth(clock, Month.JANUARY);
        SeasonalBiomeService service = new SeasonalBiomeService(clock, scheduler, id -> null);
        service.registerView(WORLD, new SeasonalBiomeService.WorldView(TERRAIN, CLIMATE, -64, 384));
        service.start(() -> List.<World>of(), () -> List.<Player>of());

        runTicks(scheduler, 1); // tick 1: the phase check publishes January
        service.request(KEY, true); // a table miss in January: computation A starts, its resend is wanted

        publishMonth(clock, Month.FEBRUARY);
        runTicks(scheduler, 20); // tick 21: the phase check publishes February, clears tables and cooldowns
        service.request(KEY, true); // what resendAround does for the chunk in a player's view

        runNext(scheduler); // A (January) finishes after the change: its table is dropped
        runTicks(scheduler, 1); // the global tick drains the queue: the refresh for February goes out (or not)

        service.request(KEY, true); // the packet thread misses on the February table
        while (!scheduler.cpu.isEmpty()) {
            runNext(scheduler);
        }

        assertThat(service.describe())
                .as("the February table is stored and its resend is queued (not refused by a January cooldown)")
                .contains("tables=1")
                .contains("pendingResends=1");
    }

    private static void publishMonth(GlobalClockHolder clock, Month month) {
        long calendar = CalendarTicks.of(CalendarTicks.FIRST_YEAR, month, 1, 0, 0, DAYS);
        clock.publish(calendar, calendar, DAYS, true);
    }

    private static void runTicks(ManualScheduler scheduler, int ticks) {
        for (int i = 0; i < ticks; i++) {
            scheduler.globalTick.run();
        }
    }

    private static void runNext(ManualScheduler scheduler) {
        scheduler.cpu.poll().run();
    }
}
