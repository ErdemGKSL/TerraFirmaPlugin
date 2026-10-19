/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.fluid;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.river.Flow;
import org.junit.jupiter.api.Test;

/**
 * Cases of TFC's {@code RiverWaterFluid} (src/main/java/net/dries007/tfc/common/fluids/RiverWaterFluid.java @ c3b4d9536) and
 * of the flow vector table in src/main/java/net/dries007/tfc/world/river/Flow.java @ c3b4d9536. Vectors are compared with a
 * tolerance because {@code Mth.sin} and {@code Mth.cos} are table lookups.
 */
class RiverWaterRulesTest {

    private static final double TOL = 1e-3;

    /** TFC {@code getAmount} returns 8 for every river water state. */
    @Test
    void amountIsAlwaysEight() {
        assertThat(RiverWaterRules.AMOUNT).isEqualTo(8);
    }

    /** TFC {@code isSame}: river water, salt water (source and flowing) and vanilla water are one family. Lava is not. */
    @Test
    void familyIsRiverSaltAndVanillaWater() {
        assertThat(RiverWaterRules.isSame("tfc:river_water")).isTrue();
        assertThat(RiverWaterRules.isSame("tfc:salt_water")).isTrue();
        assertThat(RiverWaterRules.isSame("tfc:flowing_salt_water")).isTrue();
        assertThat(RiverWaterRules.isSame("minecraft:water")).isTrue();
        assertThat(RiverWaterRules.isSame("minecraft:flowing_water")).isTrue();
        assertThat(RiverWaterRules.isSame("minecraft:lava")).isFalse();
        assertThat(RiverWaterRules.isSame("tfc:spring_water")).isFalse();
        assertThat(RiverWaterRules.isSame("tfc:metal/copper")).isFalse();
    }

    /** TFC {@code getHeight}: a block with the same fluid above is full, otherwise it keeps its own height. */
    @Test
    void heightIsFullWhenTheFluidIsAbove() {
        assertThat(RiverWaterRules.height(true, 0.8f)).isEqualTo(1f);
        assertThat(RiverWaterRules.height(false, 0.8f)).isEqualTo(0.8f);
    }

    /** TFC {@code getSource}: the lerp of four equal flows is that flow, and of four {@code NONE} is {@code NONE}. */
    @Test
    void sourceFlowOfEqualNeighboursIsThatFlow() {
        assertThat(RiverWaterRules.sourceFlow(Flow.NONE, Flow.NONE, Flow.NONE, Flow.NONE)).isEqualTo(Flow.NONE);
        assertThat(RiverWaterRules.sourceFlow(Flow.EEE, Flow.EEE, Flow.EEE, Flow.EEE)).isEqualTo(Flow.EEE);
        assertThat(RiverWaterRules.sourceFlow(Flow.NNN, Flow.NNN, Flow.NNN, Flow.NNN)).isEqualTo(Flow.NNN);
    }

    /** TFC {@code getSource} is deterministic: the same four neighbours always give the same flow. */
    @Test
    void sourceFlowIsRepeatable() {
        Flow first = RiverWaterRules.sourceFlow(Flow.NNE, Flow.SSW, Flow.EEE, Flow.NONE);
        for (int i = 0; i < 100; i++) {
            assertThat(RiverWaterRules.sourceFlow(Flow.NNE, Flow.SSW, Flow.EEE, Flow.NONE)).isEqualTo(first);
        }
    }

    /** TFC {@code getFlow}: {@code Flow.EEE} is angle 0, the vector (1, 0, 0). */
    @Test
    void eastFlowPointsEast() {
        RiverWaterRules.Vector v = RiverWaterRules.flowVector(Flow.EEE, false, false);
        assertThat(v.x()).isCloseTo(1, org.assertj.core.data.Offset.offset(TOL));
        assertThat(v.y()).isCloseTo(0, org.assertj.core.data.Offset.offset(TOL));
        assertThat(v.z()).isCloseTo(0, org.assertj.core.data.Offset.offset(TOL));
    }

    /** TFC's table: {@code Flow.NNN} is angle pi/2 with the z component {@code -sin}, so it points to -z (north). */
    @Test
    void northFlowPointsNorth() {
        RiverWaterRules.Vector v = RiverWaterRules.flowVector(Flow.NNN, false, false);
        assertThat(v.x()).isCloseTo(0, org.assertj.core.data.Offset.offset(TOL));
        assertThat(v.z()).isCloseTo(-1, org.assertj.core.data.Offset.offset(TOL));
    }

    /**
     * TFC {@code getFlow}, falling past a solid face: the horizontal unit vector (1, 0, 0) gets a -6 y step, and the result is
     * normalized, so (1, -6, 0) / sqrt(37).
     */
    @Test
    void fallingPastASolidFaceTiltsDown() {
        RiverWaterRules.Vector v = RiverWaterRules.flowVector(Flow.EEE, true, true);
        double length = Math.sqrt(37);
        assertThat(v.x()).isCloseTo(1 / length, org.assertj.core.data.Offset.offset(TOL));
        assertThat(v.y()).isCloseTo(-6 / length, org.assertj.core.data.Offset.offset(TOL));
        assertThat(v.z()).isCloseTo(0, org.assertj.core.data.Offset.offset(TOL));
    }

    /** TFC {@code getFlow}: falling with no solid face is the same as not falling. */
    @Test
    void fallingWithoutASolidFaceIsLevel() {
        RiverWaterRules.Vector v = RiverWaterRules.flowVector(Flow.EEE, true, false);
        assertThat(v.x()).isCloseTo(1, org.assertj.core.data.Offset.offset(TOL));
        assertThat(v.y()).isCloseTo(0, org.assertj.core.data.Offset.offset(TOL));
    }

    /**
     * TFC's table gives {@code Flow.NONE} the sixteenth eighth-turn, angle 2 pi, which is east. A stored flowing block with
     * no flow still has a direction, as in TFC.
     */
    @Test
    void noneFlowStillPointsEast() {
        RiverWaterRules.Vector v = RiverWaterRules.flowVector(Flow.NONE, false, false);
        assertThat(v.x()).isCloseTo(1, org.assertj.core.data.Offset.offset(TOL));
        assertThat(Math.abs(v.z())).isLessThan(TOL);
    }

    /** Vanilla {@code Vec3.normalize}: a vector shorter than 1e-4 is zero, not a division by a tiny length. */
    @Test
    void normalizeOfTheZeroVectorIsZero() {
        assertThat(new RiverWaterRules.Vector(0, 0, 0).normalize()).isEqualTo(new RiverWaterRules.Vector(0, 0, 0));
        assertThat(new RiverWaterRules.Vector(1e-6, 0, 0).normalize()).isEqualTo(new RiverWaterRules.Vector(0, 0, 0));
    }
}
