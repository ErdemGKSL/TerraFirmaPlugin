/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (slots, limits),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java @ c3b4d9536 (getStateForPlacement)
 */
package dev.tfp.core.anvil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** PAR-METAL-03: the anvil block's slots, tiers, placement facing, conversion rule, state codec and display poses. */
class AnvilBlockRulesTest {

    @Test
    void slotsAndLimitsAreTfcs() {
        assertThat(AnvilSlots.COUNT).isEqualTo(4);
        assertThat(AnvilSlots.BY_HAND_EXTRACT).containsExactly(0, 1);
        assertThat(AnvilSlots.BY_HAND_INSERT).containsExactly(3, 0, 1);
        assertThat(AnvilSlots.limit(AnvilSlots.CATALYST)).isEqualTo(64);
        for (int s : new int[] {AnvilSlots.INPUT_MAIN, AnvilSlots.INPUT_SECOND, AnvilSlots.HAMMER}) {
            assertThat(AnvilSlots.limit(s)).isEqualTo(1);
        }
    }

    @Test
    void tiersFollowTheBlockId() {
        assertThat(AnvilTiers.tier("tfp:rock/anvil/granite")).isEqualTo(OptionalInt.of(0));
        assertThat(AnvilTiers.tier("tfp:metal/anvil/copper")).isEqualTo(OptionalInt.of(1));
        assertThat(AnvilTiers.tier("tfp:metal/anvil/bronze")).isEqualTo(OptionalInt.of(2));
        assertThat(AnvilTiers.tier("tfp:metal/anvil/wrought_iron")).isEqualTo(OptionalInt.of(3));
        assertThat(AnvilTiers.tier("tfp:metal/anvil/steel")).isEqualTo(OptionalInt.of(4));
        assertThat(AnvilTiers.tier("tfp:metal/anvil/black_steel")).isEqualTo(OptionalInt.of(5));
        assertThat(AnvilTiers.tier("tfp:metal/anvil/red_steel")).isEqualTo(OptionalInt.of(6));
        assertThat(AnvilTiers.tier("tfp:metal/anvil/gold")).isEmpty();
        assertThat(AnvilTiers.tier("tfp:rock/anvil/")).isEmpty();
        assertThat(AnvilTiers.tier("tfp:quern")).isEmpty();
        assertThat(AnvilTiers.METAL_LEVELS).hasSize(9);
    }

    @Test
    void placementFacesTheClockwiseOfTheLookDirection() {
        // vanilla yaw: 0 south, 90 west, 180 north, 270 (-90) east
        assertThat(AnvilPlacement.horizontalDirection(0)).isEqualTo("south");
        assertThat(AnvilPlacement.horizontalDirection(90)).isEqualTo("west");
        assertThat(AnvilPlacement.horizontalDirection(180)).isEqualTo("north");
        assertThat(AnvilPlacement.horizontalDirection(-90)).isEqualTo("east");
        assertThat(AnvilPlacement.horizontalDirection(44.9)).isEqualTo("south");
        assertThat(AnvilPlacement.horizontalDirection(45.1)).isEqualTo("west");
        assertThat(AnvilPlacement.horizontalDirection(-179)).isEqualTo("north");
        // TFC: getHorizontalDirection().getClockWise()
        assertThat(AnvilPlacement.facing(0)).isEqualTo("west");
        assertThat(AnvilPlacement.facing(90)).isEqualTo("north");
        assertThat(AnvilPlacement.facing(180)).isEqualTo("east");
        assertThat(AnvilPlacement.facing(-90)).isEqualTo("south");
        assertThat(AnvilPlacement.axis("east")).isEqualTo("x");
        assertThat(AnvilPlacement.axis("west")).isEqualTo("x");
        assertThat(AnvilPlacement.axis("north")).isEqualTo("z");
        assertThatThrownBy(() -> AnvilPlacement.clockWise("up")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conversionNeedsAHammerNoChiselTheTopFaceAndAir() {
        assertThat(AnvilPlacement.convertsToAnvil(true, false, true, true)).isTrue();
        assertThat(AnvilPlacement.convertsToAnvil(false, false, true, true)).isFalse();
        assertThat(AnvilPlacement.convertsToAnvil(true, true, true, true)).isFalse();
        assertThat(AnvilPlacement.convertsToAnvil(true, false, false, true)).isFalse();
        assertThat(AnvilPlacement.convertsToAnvil(true, false, true, false)).isFalse();
        assertThat(AnvilPlacement.IGNEOUS).hasSize(7);
    }

    @Test
    void stateRoundTrips() {
        AnvilState s = new AnvilState("tfc:anvil/copper/rod");
        assertThat(AnvilState.CODEC.fromBytes(AnvilState.CODEC.toBytes(s))).isEqualTo(s);
        assertThat(AnvilState.CODEC.fromBytes(AnvilState.CODEC.toBytes(AnvilState.FRESH))).isEqualTo(AnvilState.FRESH);
        assertThatThrownBy(() -> new AnvilState(null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** TFC's pose stack multiplied out as 4x4 matrices, independent of the closed form in {@link AnvilVisuals}. */
    private static double[] tfc(int slot, int rotation, double[] p) {
        double[][] m = identity();
        m = mul(m, translate(0.5, AnvilVisuals.displayY(1), 0.5));
        m = mul(m, scale(0.3));
        m = mul(m, rotX(90));
        m = mul(m, rotZ(90.0 * rotation + 270));
        m = mul(m, translate(-0.4, 0, 0));
        if (slot == AnvilSlots.HAMMER) {
            return apply(m, p);
        }
        m = mul(m, translate(1.15, 0, 0));
        if (slot == AnvilSlots.INPUT_MAIN) {
            return apply(m, p);
        }
        m = mul(m, translate(0.4, 0, -0.05));
        if (slot == AnvilSlots.INPUT_SECOND) {
            return apply(m, p);
        }
        m = mul(m, translate(-0.9, -0.25, 0.05));
        m = mul(m, scale(0.6));
        return apply(m, p);
    }

    /** The display's transformation: translate(position + translation) * rotate(quaternion) * scale. */
    private static double[] ours(int slot, int rotation, double[] p) {
        AnvilVisuals.Pose pose = AnvilVisuals.pose(slot, rotation);
        double[] q = pose.rotation();
        double[] v = {p[0] * pose.scale(), p[1] * pose.scale(), p[2] * pose.scale()};
        double[] r = quat(q, v);
        return new double[] {0.5 + pose.tx() + r[0], AnvilVisuals.displayY(1) + pose.ty() + r[1], 0.5 + pose.tz() + r[2]};
    }

    private static double[] quat(double[] q, double[] v) {
        double x = q[0];
        double y = q[1];
        double z = q[2];
        double w = q[3];
        double tx = 2 * (y * v[2] - z * v[1]);
        double ty = 2 * (z * v[0] - x * v[2]);
        double tz = 2 * (x * v[1] - y * v[0]);
        return new double[] {v[0] + w * tx + (y * tz - z * ty), v[1] + w * ty + (z * tx - x * tz),
            v[2] + w * tz + (x * ty - y * tx)};
    }

    @Test
    void displayPosesEqualTfcsPoseStack() {
        double[][] points = {{0, 0, 0}, {0.5, 0, 0}, {0, 0.5, 0}, {0, 0, 0.5}, {0.3, -0.2, 0.1}};
        for (int slot = 0; slot < AnvilSlots.COUNT; slot++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                for (double[] p : points) {
                    double[] a = tfc(slot, rotation, p);
                    double[] b = ours(slot, rotation, p);
                    for (int i = 0; i < 3; i++) {
                        assertThat(b[i]).as("slot %d rotation %d point %s axis %d", slot, rotation, java.util.Arrays.toString(p), i)
                            .isCloseTo(a[i], within(1e-6));
                    }
                }
            }
        }
    }

    @Test
    void heightsAndFacingRotations() {
        assertThat(AnvilVisuals.displayY(0)).isEqualTo(0.003125 + 0.875);
        assertThat(AnvilVisuals.displayY(1)).isEqualTo(0.003125 + 0.6875);
        assertThat(AnvilVisuals.displayY(6)).isEqualTo(AnvilVisuals.displayY(1));
        assertThat(AnvilVisuals.rotation("south")).isZero();
        assertThat(AnvilVisuals.rotation("west")).isEqualTo(1);
        assertThat(AnvilVisuals.rotation("north")).isEqualTo(2);
        assertThat(AnvilVisuals.rotation("east")).isEqualTo(3);
        assertThat(AnvilVisuals.rotation("")).isZero();
        assertThat(AnvilVisuals.pose(7, 0)).isNull();
        assertThat(AnvilVisuals.pose(AnvilSlots.CATALYST, 0).scale()).isEqualTo(0.3f * 0.6f);
    }

    // ---- 4x4 helpers --------------------------------------------------------------------------------

    private static double[][] identity() {
        return new double[][] {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}};
    }

    private static double[][] translate(double x, double y, double z) {
        return new double[][] {{1, 0, 0, x}, {0, 1, 0, y}, {0, 0, 1, z}, {0, 0, 0, 1}};
    }

    private static double[][] scale(double s) {
        return new double[][] {{s, 0, 0, 0}, {0, s, 0, 0}, {0, 0, s, 0}, {0, 0, 0, 1}};
    }

    private static double[][] rotX(double deg) {
        double c = Math.cos(Math.toRadians(deg));
        double s = Math.sin(Math.toRadians(deg));
        return new double[][] {{1, 0, 0, 0}, {0, c, -s, 0}, {0, s, c, 0}, {0, 0, 0, 1}};
    }

    private static double[][] rotZ(double deg) {
        double c = Math.cos(Math.toRadians(deg));
        double s = Math.sin(Math.toRadians(deg));
        return new double[][] {{c, -s, 0, 0}, {s, c, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}};
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                for (int k = 0; k < 4; k++) {
                    r[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return r;
    }

    private static double[] apply(double[][] m, double[] p) {
        double[] r = new double[3];
        for (int i = 0; i < 3; i++) {
            r[i] = m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3];
        }
        return r;
    }
}
