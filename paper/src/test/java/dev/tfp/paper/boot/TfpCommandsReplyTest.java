/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.blockdata.PdcChunkDataStore;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.weather.PrecipitationService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-38: a console reply never blocks a tick thread that the reply's future may need. The "same thread"
 * is a fake executor whose queue only drains after {@link TfpCommands#reply} returns, as Paper's main thread
 * only runs a region task after the command returns.
 */
class TfpCommandsReplyTest {
    private static final Messages MESSAGES = BootstrapTest.MESSAGES;

    private static CommandSender console(List<String> out) {
        return Fakes.source(Set.of(), out).getSender();
    }

    @Test
    void consoleOnTickThreadDoesNotBlockOnAFutureItsOwnThreadCompletes() {
        List<String> out = new CopyOnWriteArrayList<>();
        Queue<Runnable> sameThread = new ArrayDeque<>();
        CompletableFuture<TfpCommands.Reply> future = new CompletableFuture<>();
        sameThread.add(() -> future.complete(TfpCommands.Reply.ok("command.version", "1.0")));

        long start = System.nanoTime();
        TfpCommands.reply(console(out), MESSAGES, future, () -> false);
        long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(tookMillis < TfpCommands.CONSOLE_WAIT_MILLIS / 4, "reply blocked for " + tookMillis + " ms");
        assertTrue(out.isEmpty(), "no reply before the task ran: " + out);
        while (!sameThread.isEmpty()) {
            sameThread.poll().run();
        }
        assertEquals(1, out.size(), "exactly one reply: " + out);
    }

    @Test
    void consoleOnTickThreadFailureIsRepliedOnceLater() {
        List<String> out = new CopyOnWriteArrayList<>();
        CompletableFuture<TfpCommands.Reply> future = new CompletableFuture<>();
        TfpCommands.reply(console(out), MESSAGES, future, () -> false);
        assertTrue(out.isEmpty());
        future.completeExceptionally(new IllegalStateException("boom"));
        assertEquals(1, out.size(), out.toString());
        assertTrue(out.get(0).contains("boom"), out.toString());
    }

    @Test
    void finishedFutureIsRepliedBeforeReturnEvenOnATickThread() {
        List<String> out = new CopyOnWriteArrayList<>();
        TfpCommands.reply(console(out), MESSAGES,
                CompletableFuture.completedFuture(TfpCommands.Reply.ok("command.version", "1.0")), () -> false);
        assertEquals(1, out.size(), out.toString());

        out.clear();
        TfpCommands.reply(console(out), MESSAGES,
                CompletableFuture.failedFuture(new IllegalStateException("bad")), () -> false);
        assertEquals(1, out.size(), out.toString());
        assertTrue(out.get(0).contains("bad"), out.toString());
    }

    @Test
    void consoleOffTickThreadStillWaitsForAnotherThreadsReply() {
        List<String> out = new CopyOnWriteArrayList<>();
        CompletableFuture<TfpCommands.Reply> future = new CompletableFuture<>();
        Thread worker = Thread.ofPlatform().start(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            future.complete(TfpCommands.Reply.ok("command.version", "1.0"));
        });
        TfpCommands.reply(console(out), MESSAGES, future, () -> true);
        assertEquals(1, out.size(), "the reply is sent before reply returns (RCON captures it): " + out);
        future.join();
        assertEquals(1, out.size(), "and not a second time: " + out);
        try {
            worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * PAR-CLIM-17: {@code /tfp debug chunk <x> <z> [y]} from the console on Paper's main thread, which owns the column's
     * region, answers before the command returns (RCON captures it), and the precipitation goes through vanilla's rain
     * gates. Runs the real {@link dev.tfp.paper.TfpPlugin.ConsoleChunkReadout} through the real command tree.
     */
    @Nested
    final class ConsoleChunkReadoutOnMockServer extends MockServerTest {
        private static final int X = 8;
        private static final int Z = 8;
        private static final int GROUND = 64;
        private static final int ROOF = 70;

        private final List<String> logged = new CopyOnWriteArrayList<>();
        private final GlobalClockHolder clock = new GlobalClockHolder();
        private org.bukkit.World world;
        private Scheduler scheduler;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            world = server.addSimpleWorld("world");
            for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                world.getBlockAt(X, y, Z).setType(y <= GROUND ? Material.STONE : Material.AIR);
            }
            scheduler = scheduler(() -> org.bukkit.Bukkit.isPrimaryThread());
        }

        /** A scheduler whose owner test is {@code owner}; region tasks are not expected on the inline path. */
        private Scheduler scheduler(BooleanSupplier owner) {
            return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
                    (p, m, a) -> switch (m.getName()) {
                        case "isOwner" -> owner.getAsBoolean();
                        case "cpuExecutor" -> (java.util.concurrent.Executor) Runnable::run;
                        case "region" -> {
                            ((Runnable) a[a.length - 1]).run();
                            yield null;
                        }
                        case "hashCode" -> System.identityHashCode(p);
                        case "equals" -> p == a[0];
                        case "toString" -> "test scheduler";
                        default -> throw new UnsupportedOperationException(m.getName());
                    });
        }

        private TfpPlugin.ConsoleChunkReadout readout() {
            return new TfpPlugin.ConsoleChunkReadout(new PdcChunkDataStore(), MESSAGES, clock, true,
                    new PrecipitationService(clock, scheduler, null));
        }

        /** A console command tree whose {@code debugChunkAt} is the plugin's readout, as {@code TfpPlugin} wires it. */
        private CommandDispatcher<CommandSourceStack> dispatcher() {
            Logger log = loggerInto(logged);
            return BootstrapTest.dispatcher(new BootstrapTest.FakeHandler() {
                @Override
                public CompletableFuture<TfpCommands.Reply> debugChunkAt(int x, int z) {
                    return readout().read(world, x, null, z, scheduler, log);
                }

                @Override
                public CompletableFuture<TfpCommands.Reply> debugChunkAt(int x, int y, int z) {
                    return readout().read(world, x, y, z, scheduler, log);
                }
            });
        }

        /** Publishes the first calendar time at which open sky over the column gets rain or snow. */
        private PrecipitationType publishPrecipitatingTime() {
            PrecipitationService service = new PrecipitationService(clock, scheduler, null);
            dev.tfp.core.world.BlockPos pos = new dev.tfp.core.world.BlockPos(X, GROUND + 1, Z);
            for (long tick = 0; tick < 20L * 2_304_000L; tick += 1_000) {
                clock.publish(tick, tick, 8, true);
                PrecipitationType type = service.precipitationAt(world, pos, true);
                if (type != PrecipitationType.NONE) {
                    return type;
                }
            }
            throw new AssertionError("no rain or snow over the column in 20 years");
        }

        private List<String> console(String command) throws CommandSyntaxException {
            List<String> out = new CopyOnWriteArrayList<>();
            assertTrue(org.bukkit.Bukkit.isPrimaryThread());
            assertFalse(TfpCommands.callerMayWait(), "the primary thread never waits for a reply");
            dispatcher().execute(command, Fakes.source(BootstrapTest.ADMIN, out));
            return out;
        }

        @Test
        void consoleOnThePrimaryThreadGetsThePrecipitationLineBeforeTheCommandReturns() throws CommandSyntaxException {
            PrecipitationType open = publishPrecipitatingTime();
            ((org.mockbukkit.mockbukkit.block.BlockMock) world.getBlockAt(X, GROUND + 1, Z)).setLightFromSky((byte) 15);

            List<String> out = console("tfp debug chunk " + X + " " + Z);

            assertTrue(out.stream().anyMatch(l -> l.contains("position 8 8, y " + (GROUND + 1) + " (open to the sky)")),
                    out.toString());
            assertTrue(out.stream().anyMatch(l -> l.contains("climate now: temperature")), out.toString());
            assertTrue(out.stream().anyMatch(l -> l.contains("precipitation here: " + open.name())), out.toString());
            assertTrue(logged.isEmpty(), "the inline path is not a hand-off: " + logged);
        }

        @Test
        void consoleReadoutUnderARoofReadsNone() throws CommandSyntaxException {
            PrecipitationType open = publishPrecipitatingTime();
            world.getBlockAt(X, ROOF, Z).setType(Material.GLASS);
            ((org.mockbukkit.mockbukkit.block.BlockMock) world.getBlockAt(X, GROUND + 1, Z)).setLightFromSky((byte) 15);
            ((org.mockbukkit.mockbukkit.block.BlockMock) world.getBlockAt(X, ROOF + 1, Z)).setLightFromSky((byte) 15);

            List<String> under = console("tfp debug chunk " + X + " " + Z + " " + (GROUND + 1));
            assertTrue(under.stream().anyMatch(l -> l.contains("(sheltered:")), under.toString());
            assertTrue(under.stream().anyMatch(l -> l.contains("precipitation here: NONE")), under.toString());

            List<String> above = console("tfp debug chunk " + X + " " + Z + " " + (ROOF + 1));
            assertTrue(above.stream().anyMatch(l -> l.contains("precipitation here: " + open.name())), above.toString());
        }

        @Test
        void consoleReadoutWithoutYUnderLeavesReadsNone() throws CommandSyntaxException {
            publishPrecipitatingTime();
            world.getBlockAt(X, ROOF, Z).setType(Material.OAK_LEAVES);
            ((org.mockbukkit.mockbukkit.block.BlockMock) world.getBlockAt(X, GROUND + 1, Z)).setLightFromSky((byte) 15);

            List<String> out = console("tfp debug chunk " + X + " " + Z);

            assertTrue(out.stream().anyMatch(l -> l.contains("y " + (GROUND + 1) + " (sheltered:")), out.toString());
            assertTrue(out.stream().anyMatch(l -> l.contains("precipitation here: NONE")), out.toString());
        }

        @Test
        void unownedColumnIsReadOnItsRegionAndSaysSoAndLogs() {
            publishPrecipitatingTime();
            scheduler = scheduler(() -> false);
            CompletableFuture<TfpCommands.Reply> reply = readout().read(world, X, GROUND + 1, Z, scheduler,
                    loggerInto(logged));
            assertTrue(reply.isDone(), "the test scheduler runs region and worker steps inline");
            assertTrue(reply.join().details().stream().anyMatch(l -> l.startsWith("(decided after the command returned")),
                    reply.join().details().toString());
            assertEquals(1, logged.size(), logged.toString());
        }

        private static Logger loggerInto(List<String> sink) {
            Logger log = Logger.getAnonymousLogger();
            log.setUseParentHandlers(false);
            log.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    sink.add(record.getMessage());
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
            return log;
        }
    }

    /** {@link TfpCommands#callerMayWait()} on MockBukkit: the server's primary thread may not wait, others may. */
    @Nested
    final class OnMockServer extends MockServerTest {
        @Test
        void primaryThreadMayNotWaitOtherThreadsMay() {
            assertFalse(TfpCommands.callerMayWait());
            assertTrue(offMainThread(TfpCommands::callerMayWait));
        }
    }
}
