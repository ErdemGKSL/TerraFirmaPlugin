/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.itest.CaseResult;
import dev.tfp.itest.CaseRunner;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.ready.ReadyGate;
import dev.tfp.paper.ready.ReadyGate.Signal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** M1-BOOT-04 through the runner: the ready gate must open within N s of the JVM start. */
class ReadyCaseTest {
    static SelfTestContext ctx(ReadyGate gate, long jvmStart) {
        return new SelfTestContext() {
            @Override
            public TfpPlugin tfp() {
                throw new AssertionError("not used");
            }

            @Override
            public ReadyGate readyGate() {
                return gate;
            }

            @Override
            public long jvmStartMillis() {
                return jvmStart;
            }
        };
    }

    static CaseResult run(ReadyGate gate, long jvmStart, Duration limit) {
        return new CaseRunner(l -> { }).runAll(List.of(new ReadyCase(limit)), ctx(gate, jvmStart)).getFirst();
    }

    @Test
    void passesWhenTheGateOpensInTime() {
        ReadyGate gate = new ReadyGate();
        gate.signal(Signal.REGISTRIES_LOADED);
        gate.waive(Signal.BETTERMODEL_MODELS_READY, "BetterModel not installed");
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            gate.signal(Signal.CRAFTENGINE_RELOADED);
        });
        CaseResult r = run(gate, System.currentTimeMillis(), Duration.ofSeconds(10));
        assertThat(r.status()).isEqualTo(CaseResult.Status.PASSED);
        assertThat(r.message()).contains("ready (degraded: BetterModel not installed)", "s after JVM start");
    }

    @Test
    void failsWithThePendingSignalsWhenTheLimitSinceStartupPasses() {
        ReadyGate gate = new ReadyGate();
        gate.signal(Signal.REGISTRIES_LOADED);
        // the JVM started 9.8 s ago and the limit is 10 s: about 200 ms are left
        long t0 = System.nanoTime();
        CaseResult r = run(gate, System.currentTimeMillis() - 9_800, Duration.ofSeconds(10));
        assertThat(r.status()).isEqualTo(CaseResult.Status.FAILED);
        assertThat(r.message()).contains("not ready 10 s after startup", "CraftEngine has loaded its configuration");
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(5_000);
    }

    @Test
    void aFailedGateFailsTheCase() {
        ReadyGate gate = new ReadyGate();
        gate.fail(Signal.REGISTRIES_LOADED, new IllegalArgumentException("bad json"));
        CaseResult r = run(gate, System.currentTimeMillis(), Duration.ofSeconds(10));
        assertThat(r.status()).isEqualTo(CaseResult.Status.FAILED);
        assertThat(r.message()).contains("ready gate failed", "REGISTRIES_LOADED");
    }
}
