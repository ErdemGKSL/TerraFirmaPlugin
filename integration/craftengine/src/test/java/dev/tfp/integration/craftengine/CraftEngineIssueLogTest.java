/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.IntegrationProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class CraftEngineIssueLogTest {
    @Test
    void countsOnlyOurPackAndKeepsRecentLines() {
        CraftEngineIssueLog log = new CraftEngineIssueLog(CraftEngineIntegration.NAMESPACE);
        log.accept("INFO", "Loaded 4512 blocks in 812ms");
        log.accept("WARN", "Found 2 issue(s) in file 'resources/default/configuration/blocks.yml':");
        log.accept("WARN", "Found 3 issue(s) in file 'resources/tfp/configuration/blocks.yml':");
        log.accept("WARN", "Unknown template tfp:block_state/slab");
        assertThat(log.total()).isEqualTo(4);
        assertThat(log.recent()).hasSize(2);
    }

    @Test
    void keepsAtMostFiftyRecentLines() {
        CraftEngineIssueLog log = new CraftEngineIssueLog("tfp");
        for (int i = 0; i < 120; i++) {
            log.accept("WARN", "Unknown block tfp:b" + i);
        }
        assertThat(log.total()).isEqualTo(120);
        assertThat(log.recent()).hasSize(50);
        assertThat(log.recent().getLast().message()).endsWith("tfp:b119");
    }

    /** The service file names the provider; loading it needs no CraftEngine class (none is on this classpath). */
    @Test
    void providerIsDiscoverableWithoutCraftEngine() {
        List<IntegrationProvider> found = new ArrayList<>();
        ServiceLoader.load(IntegrationProvider.class, getClass().getClassLoader()).forEach(found::add);
        assertThat(found).extracting(IntegrationProvider::pluginName).contains(CraftEngineProvider.PLUGIN);
    }
}
