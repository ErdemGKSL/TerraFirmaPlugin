/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plantslow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.leaves.SlowBlocks;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlantSlowProviderTest {
    private final PlantSlowProvider provider = new PlantSlowProvider();

    @Test
    void answersForPlantIdsAndNotForOthers() {
        provider.load(Map.of("athyrium_fern", 0.7f, "goldenrod", 1.0f));
        assertThat(provider.factor(BlockStateRef.of("tfp:plant/athyrium_fern")).getAsDouble()).isCloseTo(0.76, within(1e-6));
        assertThat(provider.factor(BlockStateRef.of("tfp:plant/goldenrod")).getAsDouble()).isCloseTo(1.0, within(1e-6));
        assertThat(provider.factor(BlockStateRef.of("tfp:plant/unknown"))).isEmpty();
        assertThat(provider.factor(BlockStateRef.of("tfp:rock/hardened/granite"))).isEmpty();
        assertThat(provider.factor(BlockStateRef.of("tfp:berry_bush/dead"))).isEmpty();
    }

    @Test
    void nothingSlowsBeforeTheTableIsLoaded() {
        assertThat(provider.factor(BlockStateRef.of("tfp:plant/athyrium_fern"))).isEmpty();
    }

    @Test
    void berryBushesSlowByStage() {
        BlockStateRef bush = BlockStateRef.of("tfp:berry_bush/blueberry").with("stage", "2");
        assertThat(provider.factor(bush).getAsDouble()).isCloseTo(0.4, within(1e-6));
        assertThat(provider.factor(bush.with("stage", "0")).getAsDouble()).isCloseTo(0.8, within(1e-6));
    }

    @Test
    void thatchSlowsOutOfWaterOnly() {
        assertThat(provider.factor(BlockStateRef.of("tfp:thatch")).getAsDouble()).isCloseTo(0.6, within(1e-6));
        assertThat(provider.factor(BlockStateRef.of("tfp:thatch").with("fluid", "water")).getAsDouble()).isEqualTo(1.0);
    }

    @Test
    void theSlowestAnswerWinsInTheService() {
        provider.load(Map.of("athyrium_fern", 0.7f));
        SlowBlocks slow = new SlowBlocks();
        slow.register(provider);
        assertThat(slow.factor(BlockStateRef.of("tfp:plant/athyrium_fern"))).isCloseTo(0.76f, within(1e-6f));
    }
}
