/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.visuals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-41: the lit normal, the light the client gives it and the compensating tint per face. */
class OverlayFaceTest {
    /** A flat quad (no tilt, no mirror) is lit at the values PAR-INFRA-37 measured: down 0.40, north 0.74, east 0.50, up 1. */
    @Test
    void flatQuadLightMatchesTheMeasuredDarkening() {
        assertThat(OverlayFace.itemLight(new double[] {0, -1, 0})).isCloseTo(0.40, within(1e-9));
        assertThat(OverlayFace.itemLight(new double[] {0, 0, -1})).isCloseTo(0.7396, within(1e-3));
        assertThat(OverlayFace.itemLight(new double[] {0, 0, 1})).isCloseTo(0.7396, within(1e-3));
        assertThat(OverlayFace.itemLight(new double[] {1, 0, 0})).isCloseTo(0.497, within(1e-3));
        assertThat(OverlayFace.itemLight(new double[] {-1, 0, 0})).isCloseTo(0.497, within(1e-3));
        assertThat(OverlayFace.itemLight(new double[] {0, 1, 0})).isEqualTo(1.0);
    }

    /** The lit normal points out of the face, tilted 22.5 degrees up on the side faces; the down face is lit from above. */
    @Test
    void litNormalPerFace() {
        double s = Math.sin(Math.toRadians(22.5));
        double c = Math.cos(Math.toRadians(22.5));
        Map<OverlayFace, double[]> want = Map.of(
            OverlayFace.NORTH, new double[] {0, s, -c},
            OverlayFace.SOUTH, new double[] {0, s, c},
            OverlayFace.EAST, new double[] {c, s, 0},
            OverlayFace.WEST, new double[] {-c, s, 0},
            OverlayFace.UP, new double[] {0, c, -s},
            OverlayFace.DOWN, new double[] {0, c, s});
        for (OverlayFace f : OverlayFace.values()) {
            assertThat(f.litNormal()).as(f.name()).containsExactly(want.get(f), within(1e-6));
        }
    }

    @Test
    void lightAndTintPerFace() {
        assertThat(OverlayFace.itemLight(OverlayFace.NORTH.litNormal())).isCloseTo(0.8995, within(1e-3));
        assertThat(OverlayFace.itemLight(OverlayFace.EAST.litNormal())).isCloseTo(0.7713, within(1e-3));
        assertThat(OverlayFace.itemLight(OverlayFace.UP.litNormal())).isEqualTo(1.0);
        assertThat(OverlayFace.itemLight(OverlayFace.DOWN.litNormal())).isEqualTo(1.0);
        assertThat(OverlayFace.NORTH.tint()).isEqualTo(0xE3E3E3);
        assertThat(OverlayFace.SOUTH.tint()).isEqualTo(0xE3E3E3);
        assertThat(OverlayFace.EAST.tint()).isEqualTo(0xC6C6C6);
        assertThat(OverlayFace.WEST.tint()).isEqualTo(0xC6C6C6);
        assertThat(OverlayFace.UP.tint()).isEqualTo(0xFFFFFF);
        assertThat(OverlayFace.DOWN.tint()).isEqualTo(0x808080);
    }

    /** Drawn speck brightness (tint x light) equals the vanilla face shade the rock gets, within one tint step. */
    @Test
    void speckToRockRatioIsTfcsOnEveryFace() {
        for (OverlayFace f : OverlayFace.values()) {
            double drawn = (f.tint() & 0xFF) / 255.0 * OverlayFace.itemLight(f.litNormal());
            double rock = (f.shade() & 0xFF) / 255.0;
            assertThat(drawn / rock).as(f.name()).isCloseTo(1.0, within(0.01));
        }
    }

    @Test
    void untiltAndMirror() {
        double h = Math.toRadians(-22.5) / 2;
        for (OverlayFace f : OverlayFace.values()) {
            assertThat(f.rightRotation()).containsExactly(new float[] {(float) Math.sin(h), 0, 0, (float) Math.cos(h)},
                within(1e-7f));
            assertThat(f.scale()).containsExactly(f == OverlayFace.DOWN ? new float[] {1, 1, -1} : new float[] {1, 1, 1});
        }
        // the tilted geometry (model element rotated TILT about x) is flat again after the 180 y turn and the untilt
        double[] corner = {0.5, 0.5, 0};
        double t = Math.toRadians(OverlayFace.TILT_DEGREES);
        double[] tilted = {corner[0], corner[1] * Math.cos(t) - corner[2] * Math.sin(t),
            corner[1] * Math.sin(t) + corner[2] * Math.cos(t)};
        double[] turned = {-tilted[0], tilted[1], -tilted[2]};
        double[] flat = OverlayFace.rotate(OverlayFace.NORTH.rightRotation(), turned);
        assertThat(flat).containsExactly(new double[] {-0.5, 0.5, 0}, within(1e-6));
    }
}
