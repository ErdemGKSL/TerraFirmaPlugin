/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.itest.cases.ReadyCase;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The registered case list (M1-TEST-01). */
class SelfTestSuiteTest {
    @Test
    void readyGateRunsFirstNamesAreUniqueAndEveryCaseRuns() {
        List<SelfTestCase> cases = SelfTestSuite.cases(Duration.ofSeconds(180));
        assertThat(cases.getFirst()).isInstanceOf(ReadyCase.class);
        assertThat(cases.getFirst().gate()).isTrue();
        assertThat(cases.getFirst().name()).contains("180 s");
        assertThat(cases).extracting(SelfTestCase::name).doesNotHaveDuplicates();
        // Missing prerequisites are decided at run time (SelfTestCase.Skip), so nothing is skipped up front.
        assertThat(cases).allSatisfy(c -> {
            assertThat(c.skipReason()).isEmpty();
            assertThat(c.timeout()).isPositive();
        });
        assertThat(cases).extracting(SelfTestCase::name).anyMatch(n -> n.startsWith("ce.pack_issues"))
                .anyMatch(n -> n.startsWith("ce.block_roundtrip")).anyMatch(n -> n.startsWith("bm.spawn_persist"))
                .anyMatch(n -> n.startsWith("bh.placeholder")).anyMatch(n -> n.startsWith("packet.noop_measured"))
                .anyMatch(n -> n.startsWith("inst.profile_restart")).anyMatch(n -> n.startsWith("inst.suppression")).anyMatch(n -> n.startsWith("calendar.persist"))
                .anyMatch(n -> n.startsWith("worldgen.terrain_generates"))
                .anyMatch(n -> n.startsWith("metal.run_rules")).anyMatch(n -> n.startsWith("animal.spawn_persist"))
                .anyMatch(n -> n.startsWith("itemdata.pdc_roundtrip"));
        assertThat(cases).hasSize(17);
    }
}
