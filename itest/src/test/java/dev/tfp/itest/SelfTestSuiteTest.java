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
    void readyGateRunsFirstNamesAreUniqueAndPendingCasesSayWhy() {
        List<SelfTestCase> cases = SelfTestSuite.cases(Duration.ofSeconds(180));
        assertThat(cases.getFirst()).isInstanceOf(ReadyCase.class);
        assertThat(cases.getFirst().gate()).isTrue();
        assertThat(cases.getFirst().name()).contains("180 s");
        assertThat(cases).extracting(SelfTestCase::name).doesNotHaveDuplicates();
        assertThat(cases).filteredOn(c -> c.skipReason().isPresent()).extracting(SelfTestCase::name)
                .containsExactly("CraftEngine test block places, breaks and persists (M1-INT-02)",
                        "BetterModel example bovine spawns (M1-INT-03)");
        assertThat(cases).filteredOn(c -> c.skipReason().isEmpty()).hasSize(5)
                .allSatisfy(c -> assertThat(c.timeout()).isPositive());
    }
}
