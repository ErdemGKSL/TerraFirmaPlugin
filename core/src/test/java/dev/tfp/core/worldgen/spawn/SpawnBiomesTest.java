/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The spawnable table resolves every TFC name (class load fails otherwise) and matches the count at c3b4d9536. */
class SpawnBiomesTest {
    @Test
    void theTableLoadsAndCountsTheRegistrations() {
        assertThat(SpawnBiomes.count()).isEqualTo(93);
    }
}
