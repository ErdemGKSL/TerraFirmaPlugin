/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.grill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** PAR-HEAT-08: TFC {@code GrillBlock.getSlotForSelection} and {@code SLOT_CENTERS}. */
class GrillSlotsTest {
    @Test
    void centresAreTfcsSlotCentres() {
        // bounds: render shape grown by 0.01, centre x/z of the bound, y 0.625
        assertThat(GrillSlots.center(4).x()).isCloseTo(0.3, within(1e-9));
        assertThat(GrillSlots.center(4).y()).isEqualTo(0.625);
        assertThat(GrillSlots.center(4).z()).isCloseTo(0.3, within(1e-9));
        assertThat(GrillSlots.center(5).x()).isCloseTo(0.3, within(1e-9));
        assertThat(GrillSlots.center(5).z()).isCloseTo(0.7, within(1e-9));
        assertThat(GrillSlots.center(6).x()).isCloseTo(0.7, within(1e-9));
        assertThat(GrillSlots.center(6).z()).isCloseTo(0.3, within(1e-9));
        assertThat(GrillSlots.center(7).x()).isCloseTo(0.7, within(1e-9));
        assertThat(GrillSlots.center(8).x()).isCloseTo(0.5, within(1e-9));
        assertThatThrownBy(() -> GrillSlots.center(3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aClickOnTheTopPicksTheSlotUnderIt() {
        for (int slot = 4; slot <= 8; slot++) {
            GrillSlots.Point c = GrillSlots.center(slot);
            // TFC's grill top (GRILL_SHAPE, 11 px)
            assertThat(GrillSlots.slotAt(c.x(), GrillSlots.TOP, c.z())).as("slot " + slot).isEqualTo(slot);
        }
        // the 0.65 high slots (4 and 8) start at 0.64, the 0.6 high ones at 0.59
        assertThat(GrillSlots.slotAt(0.3, 0.64, 0.3)).isEqualTo(4);
        assertThat(GrillSlots.slotAt(0.3, 0.63, 0.3)).isEqualTo(-1);
        assertThat(GrillSlots.slotAt(0.7, 0.595, 0.7)).isEqualTo(7);
        // between the slots, outside the bounds, on the max face (exclusive)
        assertThat(GrillSlots.slotAt(0.5, 0.65, 0.25)).isEqualTo(-1);
        assertThat(GrillSlots.slotAt(0.3, 0.71, 0.3)).isEqualTo(-1);
        assertThat(GrillSlots.slotAt(0.1, 0.65, 0.1)).isEqualTo(-1);
        // the corner where the bounds of 4 and 8 overlap: the lower slot wins
        assertThat(GrillSlots.slotAt(0.4, 0.68, 0.4)).isEqualTo(4);
    }

    @Test
    void aClickFromAboveOnTheRimLandsOnTheBasePlateBelowTheLogHeight() {
        assertThat(GrillSlots.topHitY(0.5, 0.5)).isEqualTo(11 / 16.0);
        assertThat(GrillSlots.topHitY(2 / 16.0, 14 / 16.0)).isEqualTo(11 / 16.0);
        assertThat(GrillSlots.topHitY(0.05, 0.5)).isEqualTo(2 / 16.0);
        assertThat(GrillSlots.topHitY(0.5, 0.95)).isEqualTo(2 / 16.0);
        assertThat(GrillSlots.topHitY(0.05, 0.05)).isLessThan(GrillSlots.LOG_INSERT_BELOW);
        assertThat(GrillSlots.slotAt(0.05, GrillSlots.topHitY(0.05, 0.05), 0.05)).isEqualTo(-1);
    }

    @Test
    void renderConstants() {
        assertThat(GrillSlots.ITEM_SCALE).isEqualTo(0.3f);
        assertThat(GrillSlots.ITEM_LIFT).isEqualTo(0.003125);
        assertThat(GrillSlots.LOG_INSERT_BELOW).isEqualTo(0.6);
        assertThat(GrillSlots.BOUNDS).hasSize(5);
    }
}
