/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.scraping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** PAR-PROC-05: TFC ScrapingBlock.calculatePoint and the cell under a look ray. */
class ScrapingAimTest {

    @Test
    void straightDownHitsTheCellUnderTheEye() {
        // eye 1.6 above the block at (0.6, 0.1) of the surface, looking straight down
        ScrapingAim.Point p = ScrapingAim.point(0, -1, 0, 0.6, 1.6, 0.1).orElseThrow();
        assertThat(p.x()).isCloseTo(0.6, within(1e-12));
        assertThat(p.z()).isCloseTo(0.1, within(1e-12));
        assertThat(ScrapingAim.cell(p)).isEqualTo(2);
    }

    @Test
    void anyPointOfTheRayGivesTheSamePlanePointAsTFCsHitPoint() {
        // TFC passes the hit on the 1 px top face; we pass the eye. Both lie on the look ray, so the plane point agrees.
        Random r = new Random(5);
        for (int i = 0; i < 1000; i++) {
            double hx = r.nextDouble();
            double hz = r.nextDouble();
            double hy = 1.0 / 16;
            double lx = r.nextDouble() - 0.5;
            double ly = -0.2 - r.nextDouble();
            double lz = r.nextDouble() - 0.5;
            double back = 1 + 4 * r.nextDouble();
            ScrapingAim.Point fromHit = ScrapingAim.point(lx, ly, lz, hx, hy, hz).orElseThrow();
            ScrapingAim.Point fromEye = ScrapingAim.point(lx, ly, lz, hx - lx * back, hy - ly * back, hz - lz * back)
                .orElseThrow();
            assertThat(fromEye.x()).isCloseTo(fromHit.x(), within(1e-9));
            assertThat(fromEye.z()).isCloseTo(fromHit.z(), within(1e-9));
            // TFC's formula on the hit: rayPoint - rayVector * (rayPoint.y / rayVector.y)
            assertThat(fromHit.x()).isCloseTo(hx - lx * (hy / ly), within(1e-12));
            assertThat(fromHit.z()).isCloseTo(hz - lz * (hy / ly), within(1e-12));
        }
    }

    @Test
    void aShallowRayPastTheEdgeLandsOnTheNearestEdgeCell() {
        // TFC: (int) (1.05 * 4) = 4 would mark the next row's first cell; clamped here to column 3 of the same row
        assertThat(ScrapingAim.cell(new ScrapingAim.Point(1.05, 0.3))).isEqualTo(3 + 4);
        assertThat(ScrapingAim.cell(new ScrapingAim.Point(-0.3, 0.9))).isEqualTo(12);
        assertThat(ScrapingAim.cell(new ScrapingAim.Point(1.0, 1.0))).isEqualTo(15);
    }

    @Test
    void aRayParallelToThePlaneHasNoCell() {
        assertThat(ScrapingAim.cell(1, 0, 0, 0.5, 1.6, 0.5)).isEmpty();
        assertThat(ScrapingAim.cell(0, -1, 0, 0.5, 1.6, 0.5)).hasValue(10);
    }
}
