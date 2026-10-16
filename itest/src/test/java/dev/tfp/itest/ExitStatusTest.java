/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

/** M1-TEST-01: when the exit hook decides the server has stopped. */
class ExitStatusTest {
    @Test
    void aStackInsideSystemExitCountsAsStopped() {
        StackTraceElement[] exiting = {
            new StackTraceElement("java.lang.Object", "wait", null, -1),
            new StackTraceElement("java.lang.Shutdown", "exit", null, -1),
            new StackTraceElement("java.lang.Runtime", "exit", null, -1),
            new StackTraceElement("net.minecraft.server.MinecraftServer", "runServer", null, 1)};
        StackTraceElement[] ticking = {
            new StackTraceElement("net.minecraft.server.MinecraftServer", "tickServer", null, 1),
            new StackTraceElement("java.lang.Runtime", "availableProcessors", null, -1)};
        assertThat(ExitStatus.inExit(exiting)).isTrue();
        assertThat(ExitStatus.inExit(new StackTraceElement[] {new StackTraceElement("java.lang.System", "exit", null, -1)}))
                .isTrue();
        assertThat(ExitStatus.inExit(ticking)).isFalse();
        assertThat(ExitStatus.stopped(null)).isTrue();
    }

    @Test
    void waitsForTheServerThreadToEnd() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        Thread server = Thread.ofPlatform().start(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(ExitStatus.waitForStop(server, Duration.ofMillis(200))).isFalse();
        release.countDown();
        assertThat(ExitStatus.waitForStop(server, Duration.ofSeconds(10))).isTrue();
        assertThat(ExitStatus.stopped(server)).isTrue();
    }
}
