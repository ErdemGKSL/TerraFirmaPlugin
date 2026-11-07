/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/QuernBlock.java @ c3b4d9536 (the shapes and getPlayerSelection)
 */
package dev.tfp.core.quern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.quern.QuernAim.Place;
import dev.tfp.core.quern.QuernAim.Point;
import dev.tfp.core.quern.QuernAim.Situation;
import org.junit.jupiter.api.Test;

/** PAR-PROC-04: TFC's selection places from a look ray. */
class QuernAimTest {
    static final Situation EMPTY_HANDED = new Situation(true, false, true, true, false);
    static final Situation HOLDING = new Situation(true, false, false, true, false);
    static final Situation NO_HANDSTONE = new Situation(false, false, true, true, false);
    static final Situation HOLDING_HANDSTONE = new Situation(false, false, false, true, true);

    /** Looks straight down from above at pixel (x, z). */
    static Place down(double px, double pz, Situation s) {
        return QuernAim.aim(px / 16, 2.5, pz / 16, 0, -1, 0, s);
    }

    @Test
    void shapesAreTfcs() {
        assertThat(QuernAim.BASE).isEqualTo(new QuernAim.Box(0, 0, 0, 1, 10 / 16.0, 1));
        assertThat(QuernAim.HANDSTONE.maxY()).isCloseTo(13.76 / 16, within(1e-12));
        assertThat(QuernAim.HANDLE.maxY()).isCloseTo(16.24 / 16, within(1e-12));
        assertThat(QuernAim.shape(false)).containsExactly(QuernAim.BASE);
        assertThat(QuernAim.shape(true)).containsExactly(QuernAim.BASE, QuernAim.HANDSTONE, QuernAim.HANDLE);
    }

    @Test
    void traceFindsTheNearestFace() {
        Point top = QuernAim.trace(0.5, 2, 0.5, 0, -1, 0, QuernAim.FULL_SHAPE).orElseThrow();
        assertThat(top.y()).isCloseTo(13.76 / 16, within(1e-12));
        Point base = QuernAim.trace(0.05, 2, 0.05, 0, -1, 0, QuernAim.FULL_SHAPE).orElseThrow();
        assertThat(base.y()).isCloseTo(10 / 16.0, within(1e-12));
        Point side = QuernAim.trace(-1, 0.3, 0.5, 1, 0, 0, QuernAim.BASE_SHAPE).orElseThrow();
        assertThat(side.x()).isZero();
        assertThat(QuernAim.trace(0.5, 2, 0.5, 0, 1, 0, QuernAim.FULL_SHAPE)).as("looking away").isEmpty();
        assertThat(QuernAim.trace(0.5, 2, 0.5, 0, 0, 0, QuernAim.FULL_SHAPE)).isEmpty();
        assertThat(QuernAim.trace(2, 0.5, 0.5, 0, 0, 1, QuernAim.FULL_SHAPE)).as("parallel outside").isEmpty();
    }

    @Test
    void emptyHandWithAHandstone() {
        assertThat(down(4.8, 4.8, EMPTY_HANDED)).as("the handle top").isEqualTo(Place.HANDLE);
        assertThat(down(8, 8, EMPTY_HANDED)).as("the handstone top (input slot empty)").isEqualTo(Place.HANDSTONE);
        assertThat(down(1, 1, EMPTY_HANDED)).as("the bare base corner").isEqualTo(Place.BASE);
        Situation withInput = new Situation(true, false, true, false, false);
        // the handstone top is at 13.76 px, inside the input slot box's 0.01 inflation
        assertThat(down(8, 8, withInput)).isEqualTo(Place.INPUT_SLOT);
    }

    @Test
    void holdingAnythingWithAHandstoneIsTheInputSlot() {
        // TFC: !isGrinding && !held.isEmpty() || ...: anywhere but the handle
        assertThat(down(8, 8, HOLDING)).isEqualTo(Place.INPUT_SLOT);
        assertThat(down(1, 1, HOLDING)).isEqualTo(Place.INPUT_SLOT);
        assertThat(down(4.8, 4.8, HOLDING)).isEqualTo(Place.HANDLE);
    }

    @Test
    void withoutAHandstone() {
        assertThat(down(8, 8, NO_HANDSTONE)).as("only a handstone in hand selects the handstone").isEqualTo(Place.BASE);
        assertThat(down(8, 8, HOLDING_HANDSTONE)).as("base top inside the inflated handstone box").isEqualTo(Place.HANDSTONE);
        assertThat(down(1, 1, HOLDING_HANDSTONE)).isEqualTo(Place.BASE);
        assertThat(down(4.8, 4.8, NO_HANDSTONE)).as("no handle to hit").isEqualTo(Place.BASE);
    }

    @Test
    void grindingSkipsTheHandleAndInput() {
        Situation grinding = new Situation(true, true, false, false, false);
        assertThat(down(4.8, 4.8, grinding)).as("the handle top is above the handstone box").isEqualTo(Place.BASE);
        assertThat(down(8, 8, grinding)).isEqualTo(Place.INPUT_SLOT); // input not empty and inside its box
        assertThat(QuernAim.place(null, EMPTY_HANDED)).as("a ray missing TFC's outline").isEqualTo(Place.BASE);
    }
}
