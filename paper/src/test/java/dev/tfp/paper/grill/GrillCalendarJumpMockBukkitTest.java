/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.grill;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.firepit.PaperFirepitEffects;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-34: the grill's burn-out callback follows the calendar clock. A calendar jump past the fuel (no server ticks)
 * fires the callback at the next sweep; the server-tick task fires later and does nothing.
 */
class GrillCalendarJumpMockBukkitTest extends MockServerTest {
    private final List<Runnable> delayed = new ArrayList<>();
    private final List<Runnable> region = new ArrayList<>();
    private final List<Runnable> sweeps = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(1000);
    private final List<Block> called = new ArrayList<>();

    /** Region tasks wait for {@code region}, delayed ones for {@code delayed}, the global repeating one for a sweep. */
    private Scheduler scheduler() {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "region" -> {
                    region.add((Runnable) args[args.length - 1]);
                    yield null;
                }
                case "regionDelayed" -> {
                    delayed.add((Runnable) args[args.length - 1]);
                    yield handle();
                }
                case "globalRepeating" -> {
                    sweeps.add((Runnable) args[2]);
                    yield handle();
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static Scheduler.TaskHandle handle() {
        boolean[] cancelled = new boolean[1];
        return new Scheduler.TaskHandle() {
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

    private void sweep() {
        sweeps.forEach(Runnable::run);
        while (!region.isEmpty()) {
            region.removeFirst().run();
        }
    }

    private void serverTicks() {
        List<Runnable> now = new ArrayList<>(delayed);
        delayed.clear();
        now.forEach(Runnable::run);
    }

    @Test
    void aCalendarJumpPastTheFuelFiresTheCallbackAtTheNextSweepOnly() {
        World world = server.addSimpleWorld("w");
        Block block = world.getBlockAt(0, 64, 0);
        block.getChunk().load();
        
        PaperFirepitEffects pit = new PaperFirepitEffects(scheduler(), new FakeCustomBlocks(), () -> null, clock::get,
            called::add);
        pit.start();
        PaperGrillEffects effects = new PaperGrillEffects(scheduler(), new FakeCustomBlocks(), () -> null, pit,
            (p, b) -> { }, () -> null);
        effects.callbackIn(block, 400);

        sweep();
        assertThat(called).as("the clock has not moved: nothing is due").isEmpty();

        clock.addAndGet(5000); // /tfp time add: the calendar jumps, the server-tick task stays queued
        sweep();
        assertThat(called).containsExactly(block);

        serverTicks();
        sweep();
        assertThat(called).as("each burn-out fires once").hasSize(1);
    }

    @Test
    void theServerTickCallbackStillFiresWhenTheCalendarRunsNormally() {
        World world = server.addSimpleWorld("w2");
        Block block = world.getBlockAt(0, 64, 0);
        block.getChunk().load();
        
        PaperFirepitEffects pit = new PaperFirepitEffects(scheduler(), new FakeCustomBlocks(), () -> null, clock::get,
            called::add);
        pit.start();
        PaperGrillEffects effects = new PaperGrillEffects(scheduler(), new FakeCustomBlocks(), () -> null, pit,
            (p, b) -> { }, () -> null);
        effects.callbackIn(block, 400);

        clock.addAndGet(400);
        serverTicks();
        assertThat(called).containsExactly(block);
        sweep();
        assertThat(called).hasSize(1);
    }
}
