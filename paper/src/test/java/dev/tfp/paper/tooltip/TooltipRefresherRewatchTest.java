/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import static dev.tfp.paper.tooltip.NbtStacks.TEXT;
import static dev.tfp.paper.tooltip.NbtStacks.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-INFRA-05 in-game rejection 2026-10-22: after a death the refresher stopped for good (a cooling ingot kept its
 * 900 C text). Paper's entity scheduler ends a repeating task and runs its retired callback when the task fires while
 * the dead player is removed from the world; nothing re-armed the watch. The respawn and world-change handlers do now.
 */
class TooltipRefresherRewatchTest extends MockServerTest {
    /** One {@code entityRepeating} registration: its task, its retired callback and whether it was cancelled. */
    record Repeating(Runnable task, Runnable retired, boolean[] cancelled) {
    }

    private final List<Repeating> repeating = new ArrayList<>();
    private final AtomicInteger decisions = new AtomicInteger();

    private Scheduler recordingScheduler() {
        Executor direct = r -> {
            decisions.incrementAndGet();
            r.run();
        };
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "entityRepeating" -> {
                        boolean[] cancelled = new boolean[1];
                        repeating.add(new Repeating((Runnable) args[3], (Runnable) args[4], cancelled));
                        yield new Scheduler.TaskHandle() {
                            @Override
                            public void cancel() {
                                cancelled[0] = true;
                            }

                            @Override
                            public boolean isCancelled() {
                                return cancelled[0];
                            }
                        };
                    }
                    case "cpuExecutor" -> direct;
                    case "entity" -> {
                        ((Runnable) args[1]).run();
                        yield true;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private TooltipRefresher refresher(Supplier<ClockSnapshot> clock) {
        TooltipPipeline p = new TooltipPipeline(() -> clock(0L), null);
        p.add(new HeatTooltipContributor(() -> new ItemHeatDefinitions(Map.of()), HeatConfig.DEFAULT,
                HeatDisplayStyle.COLOR, TEXT));
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        return new TooltipRefresher(recordingScheduler(), p, clock, s -> Optional.empty(), FoodPdc.DEFAULT_TRAITS, log);
    }

    @Test
    void aWatchRetiredWhileThePlayerWasDeadIsReArmedOnRespawn() {
        PlayerMock player = server.addPlayer();
        TooltipRefresher r = refresher(() -> clock(100L));
        r.watch(player);
        assertThat(r.watching(player.getUniqueId())).isTrue();
        assertThat(repeating).hasSize(1);

        // Paper: the period fired while the dead player was out of the world, so the task ended as retired.
        repeating.get(0).retired().run();
        assertThat(r.watching(player.getUniqueId())).as("the retired task drops the watch").isFalse();

        r.onRespawn(new PlayerRespawnEvent(player, player.getLocation(), false, false, false,
                PlayerRespawnEvent.RespawnReason.DEATH));
        assertThat(r.watching(player.getUniqueId())).as("re-armed by the respawn").isTrue();
        assertThat(repeating).hasSize(2);

        repeating.get(1).task().run();
        assertThat(decisions).as("the new task decides again").hasValue(1);
    }

    @Test
    void aRespawnWithTheOldTaskStillAliveReplacesIt() {
        PlayerMock player = server.addPlayer();
        TooltipRefresher r = refresher(() -> clock(100L));
        r.watch(player);
        r.onChangedWorld(new PlayerChangedWorldEvent(player, player.getWorld()));
        assertThat(repeating).hasSize(2);
        assertThat(repeating.get(0).cancelled()[0]).as("one task per player").isTrue();
        assertThat(repeating.get(1).cancelled()[0]).isFalse();
        assertThat(r.watching(player.getUniqueId())).isTrue();
    }

    @Test
    void aThrowingClockReleasesTheInFlightFlag() {
        PlayerMock player = server.addPlayer();
        AtomicReference<RuntimeException> failure = new AtomicReference<>(new IllegalStateException("clock"));
        TooltipRefresher r = refresher(() -> {
            RuntimeException e = failure.get();
            if (e != null) {
                throw e;
            }
            return clock(100L);
        });
        r.watch(player);
        Runnable period = repeating.get(0).task();
        assertThatCode(period::run).doesNotThrowAnyException();
        assertThatCode(period::run).doesNotThrowAnyException();
        assertThat(decisions).hasValue(0);

        failure.set(null);
        period.run();
        assertThat(decisions).as("the flag was released, so the next period decides").hasValue(1);
    }
}
