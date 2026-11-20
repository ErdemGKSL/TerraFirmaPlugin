/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pluck;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.CarrierTable;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-20: TFC's one-hour plucking cooldown, and every pluckable species has a carrier. */
class PluckModuleTest {
    @Test
    void cooldownIsOneCalendarHour() {
        assertThat(PluckModule.remaining(0, 5_000)).isZero();
        assertThat(PluckModule.remaining(1_000, 1_500)).isEqualTo(500);
        assertThat(PluckModule.remaining(1_000, 2_000)).isZero();
        assertThat(PluckModule.remaining(1_000, 9_999)).isZero();
    }

    @Test
    void everyPluckableSpeciesIsOneOfOurAnimals() {
        for (String id : PluckModule.PLUCKABLE) {
            assertThat(CarrierTable.get().find(id)).as(id).isPresent();
        }
    }
}
