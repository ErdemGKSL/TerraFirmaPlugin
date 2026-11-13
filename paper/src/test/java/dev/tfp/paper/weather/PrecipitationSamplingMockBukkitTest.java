/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weather.PrecipitationSample;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-CLIM-02 on a fake clock: the 20-tick throttle samples every online player whether or not a HUD renders it, the
 * sample follows the published calendar reading, the newest reading wins, a quit forgets the sample, and the position
 * query other systems use agrees with the player samples. A forced climate (a known seed, a high cold column in winter
 * and a low warm column in summer) shows both snow and rain.
 */
class PrecipitationSamplingMockBukkitTest extends MockServerTest {
    private static final long SEED = 42L;

    /** Entity tasks run inline on the main thread and are queued from elsewhere; CPU tasks run on a tfp worker. */
    private final class FakeScheduler implements Scheduler {
        final Deque<Runnable> mainQueue = new ArrayDeque<>();
        Runnable repeating;
        long period;

        void pump() {
            Runnable r;
            while ((r = mainQueue.poll()) != null) {
                r.run();
            }
        }

        @Override
        public boolean entity(Entity entity, Runnable task, Runnable retired) {
            if (server.isPrimaryThread()) {
                task.run();
            } else {
                synchronized (mainQueue) {
                    mainQueue.add(task);
                }
            }
            return true;
        }

        @Override
        public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
            repeating = task;
            period = periodTicks;
            return null;
        }

        @Override
        public Executor cpuExecutor() {
            return r -> {
                Thread t = new Thread(r, "tfp-cpu-test");
                t.start();
                try {
                    t.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            };
        }

        @Override
        public void cpu(Runnable task) {
            cpuExecutor().execute(task);
        }

        // Not used by the service.
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

    private FakeScheduler scheduler;
    private GlobalClockHolder clock;
    private boolean hudActive;
    private HudSnapshotCache hud;
    /** The forced capture per player: position and whether rain reaches it. */
    private final Map<UUID, PrecipitationService.Capture> forced = new HashMap<>();
    private PrecipitationService service;
    private final List<Player> online = new ArrayList<>();

    @BeforeEach
    void setUp() {
        scheduler = new FakeScheduler();
        clock = new GlobalClockHolder();
        hud = new HudSnapshotCache(() -> hudActive);
        Function<Player, PrecipitationService.Capture> capture = p -> forced.get(p.getUniqueId());
        service = new PrecipitationService(clock, scheduler, hud, capture);
        service.start(() -> online);
    }

    private PlayerMock join(BlockPos pos, boolean sky) {
        PlayerMock p = server.addPlayer();
        forced.put(p.getUniqueId(), new PrecipitationService.Capture(p.getUniqueId(), SEED, true, pos, sky));
        online.add(p);
        return p;
    }

    /** One throttle period on the fake clock: advance 20 ticks, publish, run the global task, apply the results. */
    private ClockSnapshot period(long calendarTicks) {
        ClockSnapshot snap = clock.publish(calendarTicks, calendarTicks, 8, true);
        scheduler.repeating.run();
        scheduler.pump();
        return snap;
    }

    @Test
    void theThrottleSamplesEveryPlayerEachPeriodOnTheFakeClockWithoutAHud() {
        assertThat(scheduler.period).isEqualTo(PrecipitationService.PERIOD_TICKS).isEqualTo(20L);
        assertThat(PrecipitationService.current()).contains(service);
        PlayerMock a = join(new BlockPos(0, 120, 0), true);
        PlayerMock b = join(new BlockPos(500, 80, -300), false);
        assertThat(service.sampleOf(a.getUniqueId())).isEmpty();

        hudActive = false;
        for (long t = 1_000; t < 1_000 + 20 * 50; t += 20) {
            ClockSnapshot snap = period(t);
            PrecipitationSample sa = service.sampleOf(a.getUniqueId()).orElseThrow();
            PrecipitationSample sb = service.sampleOf(b.getUniqueId()).orElseThrow();
            assertThat(sa.clockSequence()).isEqualTo(snap.sequence());
            assertThat(sa.calendarTicks()).isEqualTo(t);
            assertThat(sa.pos()).isEqualTo(new BlockPos(0, 120, 0));
            assertThat(sa).isEqualTo(PrecipitationService.compute(forced.get(a.getUniqueId()), snap));
            assertThat(sb.type()).isEqualTo(PrecipitationType.NONE); // sheltered
            assertThat(sb.clockSequence()).isEqualTo(snap.sequence());
        }
        // No HUD renders the value: nothing was written to the HUD cache
        assertThat(hud.size()).isZero();
    }

    @Test
    void withAHudTheSnapshotCarriesTheSampledType() {
        hudActive = true;
        PlayerMock a = join(new BlockPos(0, 64, 0), true);
        for (long t = 0; t < 2_304_000L; t += 96_000) {
            period(t);
            PrecipitationType sampled = service.sampleOf(a.getUniqueId()).orElseThrow().type();
            assertThat(hud.snapshot(a.getUniqueId()).precipitation()).isEqualTo(sampled);
        }
    }

    @Test
    void anOlderDecisionNeverReplacesANewerOne() {
        PlayerMock a = join(new BlockPos(0, 120, 0), true);
        ClockSnapshot first = period(100);
        ClockSnapshot second = period(120);
        PrecipitationSample late = new PrecipitationSample(first.sequence(), 100, new BlockPos(0, 120, 0), PrecipitationType.SNOW);
        service.apply(a, late);
        assertThat(service.sampleOf(a.getUniqueId()).orElseThrow().clockSequence()).isEqualTo(second.sequence());
    }

    @Test
    void aQuitForgetsTheSample() {
        PlayerMock a = join(new BlockPos(0, 120, 0), true);
        period(100);
        assertThat(service.sampleOf(a.getUniqueId())).isPresent();
        service.onQuit(new PlayerQuitEvent(a, net.kyori.adventure.text.Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertThat(service.sampleOf(a.getUniqueId())).isEmpty();
    }

    @Test
    void thePositionQueryAgreesWithThePlayerSamples() {
        World world = server.createWorld(new WorldCreator("tfp_precip").seed(SEED).environment(World.Environment.NORMAL));
        World nether = server.createWorld(new WorldCreator("tfp_precip_nether").seed(SEED).environment(World.Environment.NETHER));
        BlockPos pos = new BlockPos(0, 120, 0);
        PlayerMock a = join(pos, true);
        for (long t = 0; t < 2_304_000L; t += 24_000) {
            period(t);
            PrecipitationType sampled = service.sampleOf(a.getUniqueId()).orElseThrow().type();
            assertThat(service.precipitationAt(world, pos, true)).isEqualTo(sampled);
            assertThat(service.isRainingAt(world, pos, true)).isEqualTo(sampled == PrecipitationType.RAIN);
            assertThat(service.precipitationAt(world, pos, false)).isEqualTo(PrecipitationType.NONE);
            assertThat(service.precipitationAt(nether, pos, true)).isEqualTo(PrecipitationType.NONE);
        }
    }

    @Test
    void noCalendarReadingYetGivesNone() {
        World world = server.createWorld(new WorldCreator("tfp_precip_unset").seed(SEED));
        assertThat(service.precipitationAt(world, new BlockPos(0, 64, 0), true)).isEqualTo(PrecipitationType.NONE);
    }

    @Test
    void aForcedColdSiteSnowsAndAForcedWarmSiteRains() {
        // weather.precip_sample's forced climate: the same seed and column, a high cold player and a low warm player,
        // over one year of calendar time. Rain events are shared (one rain intensity per time), so whenever the warm
        // site rains there must be times the cold site snows, and the cold site must never rain while the warm one snows.
        PlayerMock cold = join(new BlockPos(0, 255, 0), true);
        PlayerMock warm = join(new BlockPos(0, 63, 0), true);
        int snow = 0;
        int rain = 0;
        for (long t = 0; t < 8 * 12 * 24_000L; t += 2_000) {
            period(t);
            PrecipitationType c = service.sampleOf(cold.getUniqueId()).orElseThrow().type();
            PrecipitationType w = service.sampleOf(warm.getUniqueId()).orElseThrow().type();
            if (c == PrecipitationType.SNOW) {
                snow++;
            }
            if (w == PrecipitationType.RAIN) {
                rain++;
            }
            assertThat(c == PrecipitationType.RAIN && w == PrecipitationType.SNOW).isFalse();
        }
        assertThat(snow).isPositive();
        assertThat(rain).isPositive();
    }
}
