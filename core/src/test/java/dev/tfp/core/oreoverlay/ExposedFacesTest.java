/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.oreoverlay.census.ExposureRule;
import dev.tfp.core.visuals.OverlayFace;
import org.junit.jupiter.api.Test;

class ExposedFacesTest {
    private static final int SOLID = ExposureRule.SOLID;
    private static final int OPEN = ExposureRule.OPEN;

    @Test
    void buriedOreHasNoFaceButStaysInTheTable() {
        OreOverlayTable t = new TestWorld(SOLID).set(5, 10, 5, 3).table(0, 0);
        assertThat(t.size()).isEqualTo(1);
        assertThat(t.faceCount()).isZero();
        assertThat(t.exposedOres()).isZero();
        int i = t.find(5, 10, 5);
        assertThat(i).isZero();
        assertThat(t.kind(i)).isEqualTo(3);
        assertThat(t.find(5, 11, 5)).isEqualTo(-1);
    }

    @Test
    void eachFaceIsExposedByTheOpenCellInItsDirection() {
        for (OverlayFace f : OverlayFace.values()) {
            OreOverlayTable t = new TestWorld(SOLID).set(5, 10, 5, 0).set(5 + f.dx(), 10 + f.dy(), 5 + f.dz(), OPEN)
                    .table(0, 0);
            int i = t.find(5, 10, 5);
            assertThat(t.mask(i)).as(f.name()).isEqualTo(1 << f.ordinal());
            assertThat(t.faceCount()).isEqualTo(1);
        }
    }

    @Test
    void neighbouringOresHideEachOther() {
        OreOverlayTable t = new TestWorld(OPEN).set(5, 10, 5, 1).set(6, 10, 5, 2).table(0, 0);
        assertThat(t.mask(t.find(5, 10, 5))).isEqualTo(0x3F & ~(1 << OverlayFace.EAST.ordinal()));
        assertThat(t.mask(t.find(6, 10, 5))).isEqualTo(0x3F & ~(1 << OverlayFace.WEST.ordinal()));
        assertThat(t.faceCount()).isEqualTo(10);
    }

    @Test
    void fluidsAndNonOccludingStatesExpose() {
        int water = ExposureRule.cell(false, true, false);
        int glass = ExposureRule.cell(false, false, false);
        int stone = ExposureRule.cell(false, false, true);
        assertThat(water).isEqualTo(OPEN);
        assertThat(glass).isEqualTo(OPEN);
        assertThat(stone).isEqualTo(SOLID);
        OreOverlayTable t = new TestWorld(SOLID).set(5, 10, 5, 0).set(5, 11, 5, water).set(5, 9, 5, glass)
                .set(4, 10, 5, stone).table(0, 0);
        assertThat(t.mask(0)).isEqualTo((1 << OverlayFace.UP.ordinal()) | (1 << OverlayFace.DOWN.ordinal()));
    }

    @Test
    void chunkEdgeFacesComeFromTheBorder() {
        TestWorld w = new TestWorld(SOLID).set(15, 10, 0, 0).set(16, 10, 0, OPEN).set(15, 10, -1, OPEN);
        OreSnapshot s = w.snapshot(0, 0);
        assertThat(ExposedFaces.borderNeeds(s)).containsExactlyInAnyOrder(OverlayKeys.block(16, 10, 0),
                OverlayKeys.block(15, 10, -1));
        OreOverlayTable withBorder = ExposedFaces.compute(s, w);
        assertThat(withBorder.mask(0)).isEqualTo((1 << OverlayFace.EAST.ordinal()) | (1 << OverlayFace.NORTH.ordinal()));
        OreOverlayTable unknownBorder = ExposedFaces.compute(s, CellLookup.NONE);
        assertThat(unknownBorder.mask(0)).as("an unknown border cell hides the face").isZero();
    }

    @Test
    void worldLimitsTopIsSkyBottomIsHidden() {
        OreOverlayTable t = new TestWorld(SOLID).set(1, TestWorld.MAX_Y - 1, 1, 0).set(2, TestWorld.MIN_Y, 2, 0)
                .table(0, 0);
        assertThat(t.mask(t.find(1, TestWorld.MAX_Y - 1, 1))).isEqualTo(1 << OverlayFace.UP.ordinal());
        assertThat(t.mask(t.find(2, TestWorld.MIN_Y, 2))).isZero();
    }

    @Test
    void columnarHostOreShowsTheSameOverlayOnAllSixFaces() {
        // TFC's ore_column model uses overlay = overlay_end; the overlay kind does not depend on the face.
        OreOverlayTable t = new TestWorld(OPEN).set(3, 5, 3, 9).table(0, 0);
        assertThat(t.mask(0)).isEqualTo(0x3F);
        assertThat(t.kind(0)).isEqualTo(9);
    }

    @Test
    void outcropsAreFacesWithinEightBlocksOfTheSurface() {
        TestWorld w = new TestWorld(SOLID).set(4, 20, 4, 0).set(4, 21, 4, OPEN).set(4, 10, 6, 0).set(4, 10, 7, OPEN)
                .surface(4, 4, 28).surface(4, 7, 28);
        OreOverlayTable t = w.table(0, 0);
        assertThat(t.outcrop(t.find(4, 20, 4))).as("open cell y 21 >= 28 - 8").isEqualTo(1 << OverlayFace.UP.ordinal());
        assertThat(t.outcrop(t.find(4, 10, 6))).as("cave face 18 below the surface").isZero();
        assertThat(t.outcropFaceCount()).isEqualTo(1);
        assertThat(ExposedFaces.outcrop(20, 28)).isTrue();
        assertThat(ExposedFaces.outcrop(19, 28)).isFalse();
        assertThat(ExposedFaces.outcrop(50, CellLookup.NO_SURFACE)).isFalse();
    }

    @Test
    void patchOfANeighbourChangeMatchesAFullBuild() {
        TestWorld w = new TestWorld(SOLID).set(5, 10, 5, 0);
        OreOverlayTable before = w.table(0, 0);
        w.set(5, 10, 6, OPEN);
        long[] changed = {OverlayKeys.block(5, 10, 6)};
        OreOverlayTable patched = ExposedFaces.patch(before, TestWorld.MAX_Y, w, changed);
        assertThat(patched.sameAs(w.table(0, 0))).isTrue();
        assertThat(patched.mask(0)).isEqualTo(1 << OverlayFace.SOUTH.ordinal());
    }

    @Test
    void opposite() {
        assertThat(ExposedFaces.opposite(OverlayFace.UP)).isEqualTo(OverlayFace.DOWN);
        assertThat(ExposedFaces.opposite(OverlayFace.NORTH)).isEqualTo(OverlayFace.SOUTH);
        assertThat(ExposedFaces.opposite(OverlayFace.EAST)).isEqualTo(OverlayFace.WEST);
    }

    @Test
    void keysRoundTrip() {
        long b = OverlayKeys.block(-30000000, -64, 29999999);
        assertThat(OverlayKeys.blockX(b)).isEqualTo(-30000000);
        assertThat(OverlayKeys.blockY(b)).isEqualTo(-64);
        assertThat(OverlayKeys.blockZ(b)).isEqualTo(29999999);
        long c = OverlayKeys.chunk(-5, 7);
        assertThat(OverlayKeys.chunkX(c)).isEqualTo(-5);
        assertThat(OverlayKeys.chunkZ(c)).isEqualTo(7);
        int l = OverlayKeys.local(-1, -64, 17, -64);
        assertThat(OverlayKeys.localX(l)).isEqualTo(15);
        assertThat(OverlayKeys.localZ(l)).isEqualTo(1);
        assertThat(OverlayKeys.localY(l, -64)).isEqualTo(-64);
        int f = OverlayKeys.face(l, 5);
        assertThat(OverlayKeys.faceLocal(f)).isEqualTo(l);
        assertThat(OverlayKeys.faceOrdinal(f)).isEqualTo(5);
    }
}
