/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.pan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PanDepositsTest {
    @Test
    void soundEverySixteenTicksLeft() {
        List<Integer> due = java.util.stream.IntStream.rangeClosed(0, 120).filter(PanDeposits::soundDue).boxed().toList();
        assertThat(due).containsExactly(8, 24, 40, 56, 72, 88, 104);
    }

    @Test
    void lookup() {
        PanDeposits d = PanDeposits.of(Map.of("tfp:deposit/native_copper/granite", "tfp:deposit/native_copper_granite"));
        assertThat(d.isDeposit("tfp:deposit/native_copper/granite")).isTrue();
        assertThat(d.isDeposit("minecraft:dirt")).isFalse();
        assertThat(d.isDeposit(null)).isFalse();
        assertThat(d.lootTable("tfp:deposit/native_copper/granite")).contains("tfp:deposit/native_copper_granite");
        assertThat(d.lootTable("minecraft:dirt")).isEmpty();
        assertThat(PanDeposits.modelKey("tfp:deposit/native_copper/granite")).isEqualTo("native_copper/granite");
    }

    @Test
    void realDepositData() throws java.io.IOException {
        String configured = System.getProperty("tfp.contentData");
        Path data = Path.of(configured == null ? "content/generated/content/data" : configured);
        assumeTrue(Files.isDirectory(data), "no generated content at " + data.toAbsolutePath());
        PanDeposits d = PanDeposits.fromRegistry(DataLoader.standard(KnownIds.fromDataRoot(data)).load(List.of(data)));
        assertThat(d.size()).isEqualTo(84);
        assertThat(d.lootTable("tfp:deposit/cassiterite/slate")).contains("tfp:deposit/cassiterite_slate");
    }
}
