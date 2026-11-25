/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.datagen.DatagenException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-59: the selector's plant pools: sizes, the twigs' reserved tripwire state, the pick order. */
class PlantPoolsTest {
    @Test
    void sizesAndReserve() {
        assertThat(PlantPools.SIZES).containsExactly(Map.entry("sugar_cane", 15), Map.entry("sapling", 8),
            Map.entry("tripwire", 126), Map.entry("kelp", 25));
        assertThat(PlantPools.capacity(PlantPools.TRIPWIRE)).isEqualTo(125);
        assertThat(PlantPools.waterModels()).isEqualTo(24);
        assertThatThrownBy(() -> PlantPools.capacity("solid")).isInstanceOf(DatagenException.class);
    }

    @Test
    void untintedLooksFillTheSmallPoolsFirstTintedOnlyTheTripwire() {
        PlantPools.Allocator a = new PlantPools.Allocator();
        assertThat(a.land(true)).contains(PlantPools.TRIPWIRE);
        for (int i = 0; i < 15; i++) {
            assertThat(a.land(false)).contains(PlantPools.SUGAR_CANE);
        }
        for (int i = 0; i < 8; i++) {
            assertThat(a.land(false)).contains(PlantPools.SAPLING);
        }
        for (int i = 1; i < 125; i++) {
            assertThat(a.land(i % 2 == 0)).contains(PlantPools.TRIPWIRE);
        }
        assertThat(a.land(false)).isEmpty();
        assertThat(a.use()).containsEntry(PlantPools.TRIPWIRE, 125).containsEntry(PlantPools.KELP, 0);
        for (int i = 0; i < 25; i++) {
            assertThat(a.take(PlantPools.KELP)).isTrue();
        }
        assertThat(a.take(PlantPools.KELP)).isFalse();
    }

    @Test
    void appearanceRoundTrips() {
        Map<String, Object> app = PlantPools.appearance(PlantPools.KELP, "tfp_kelp_3");
        assertThat(PlantPools.poolState(app)).contains(List.of("kelp", "tfp_kelp_3"));
        assertThat(PlantPools.poolState(Map.of("auto_state", Map.of("type", "solid", "id", "x")))).isEmpty();
        assertThatThrownBy(() -> PlantPools.appearance("solid", "x")).isInstanceOf(DatagenException.class);
    }
}
