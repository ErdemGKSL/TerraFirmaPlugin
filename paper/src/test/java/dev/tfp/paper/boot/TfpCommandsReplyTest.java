/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
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
