/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay.census;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** PAR-WORLD-17: the exposure census on synthetic chunks. */
class ExposureCensusTest {
    static final int MIN_Y = -64;
    static final int HEIGHT = 384;
    static final int ORE = 0;
    static final int ROCK = 1;
    static final boolean[] ORE_ONLY = {true, false};

    /** A chunk of solid stone with the surface at {@code y=200}, so everything below 192 is the cave band. */
    static ArrayChunkView solid() {
        final ArrayChunkView v = new ArrayChunkView(MIN_Y, HEIGHT);
        for (int x = -1; x <= 16; x++) {
            for (int z = -1; z <= 16; z++) {
                v.surface(x, z, 200);
            }
        }
        return v;
    }

    static ArrayChunkView open() {
        final ArrayChunkView v = solid();
        v.fill(-1, MIN_Y, -1, 16, MIN_Y + HEIGHT - 1, 16, ExposureRule.OPEN);
        return v;
    }

    @Test
    void singleOreInAirHasSixFaces() {
        final ChunkCensus c = ExposureCensus.count(open().set(8, 64, 8, ORE), ORE_ONLY);
        assertThat(c.blocks()).isEqualTo(1);
        assertThat(c.exposed()).isEqualTo(1);
        assertThat(c.faces()).isEqualTo(6);
        for (int d = 0; d < 6; d++) {
            assertThat(c.facesByDirection(d)).as(ChunkCensus.DIRECTIONS[d]).isEqualTo(1);
        }
    }

    @Test
    void oreInSolidRockHasNoFaces() {
        final ChunkCensus c = ExposureCensus.count(solid().set(8, 64, 8, ORE), ORE_ONLY);
        assertThat(c.blocks()).isEqualTo(1);
        assertThat(c.exposed()).isZero();
        assertThat(c.faces()).isZero();
        assertThat(c.surfaceExample()).isEqualTo(-1);
    }

    @Test
    void oreNextToRockTargetsIsNotExposedByThem() {
        final ArrayChunkView v = solid().fill(7, 63, 7, 9, 65, 9, ROCK).set(8, 64, 8, ORE);
        final ChunkCensus c = ExposureCensus.count(v, ORE_ONLY);
        assertThat(c.faces()).isZero();
        assertThat(c.targetBlocks(ROCK)).isEqualTo(26);
        assertThat(c.targetExposed(ROCK)).isZero();
    }

    @Test
    void oreUnderWaterHasOneFace() {
        // Water is a fluid: it exposes the face below it.
        final int water = ExposureRule.cell(false, true, false);
        final ArrayChunkView v = solid().set(8, 64, 8, ORE).fill(-1, 65, -1, 16, 70, 16, water);
        final ChunkCensus c = ExposureCensus.count(v, ORE_ONLY);
        assertThat(c.faces()).isEqualTo(1);
        assertThat(c.facesByDirection(1)).isEqualTo(1);
    }

    @Test
    void glassAndLeavesExpose() {
        // Non-occluding, neither air nor fluid: glass, leaves.
        final int glass = ExposureRule.cell(false, false, false);
        assertThat(glass).isEqualTo(ExposureRule.OPEN);
        assertThat(ExposureRule.cell(false, false, true)).isEqualTo(ExposureRule.SOLID);
        assertThat(ExposureRule.cell(true, false, true)).isEqualTo(ExposureRule.OPEN);
        final ArrayChunkView v = solid().set(8, 64, 8, ORE).set(9, 64, 8, glass).set(8, 64, 7, glass);
        final ChunkCensus c = ExposureCensus.count(v, ORE_ONLY);
        assertThat(c.exposed()).isEqualTo(1);
        assertThat(c.faces()).isEqualTo(2);
        assertThat(c.facesByDirection(5)).as("east").isEqualTo(1);
        assertThat(c.facesByDirection(2)).as("north").isEqualTo(1);
    }

    @Test
    void neighboursAcrossTheChunkEdgeComeFromTheBorder() {
        final ArrayChunkView v = solid()
                .set(15, 10, 0, ORE).set(16, 10, 0, ExposureRule.OPEN) // east of an edge ore: the next chunk's air
                .set(0, 20, 15, ORE).set(0, 20, 16, ExposureRule.OPEN) // south edge
                .set(0, 30, 0, ORE).set(-1, 30, 0, ExposureRule.OPEN).set(0, 30, -1, ExposureRule.OPEN) // corner column
                .set(16, 40, 5, ORE).set(16, 41, 5, ExposureRule.OPEN); // an ore in the border belongs to the next chunk
        final ChunkCensus c = ExposureCensus.count(v, ORE_ONLY);
        assertThat(c.blocks()).isEqualTo(3);
        assertThat(c.faces()).isEqualTo(4);
        assertThat(c.facesByDirection(5)).as("east").isEqualTo(1);
        assertThat(c.facesByDirection(3)).as("south").isEqualTo(1);
        assertThat(c.facesByDirection(4)).as("west").isEqualTo(1);
        assertThat(c.facesByDirection(2)).as("north").isEqualTo(1);
        assertThat(c.facesByDirection(1)).as("up: the border ore is not ours").isZero();
    }

    @Test
    void columnOfOreAlongTheEdgeCountsEachFaceOnce() {
        final ArrayChunkView v = solid();
        for (int y = 0; y < 10; y++) {
            v.set(15, y, 15, ORE).set(16, y, 15, ExposureRule.OPEN).set(15, y, 16, ExposureRule.OPEN);
        }
        final ChunkCensus c = ExposureCensus.count(v, ORE_ONLY);
        assertThat(c.blocks()).isEqualTo(10);
        assertThat(c.faces()).isEqualTo(20);
    }

    @Test
    void depthBandsFollowTheSurface() {
        final ArrayChunkView v = solid();
        for (int x = -1; x <= 16; x++) {
            for (int z = -1; z <= 16; z++) {
                v.surface(x, z, 70);
            }
        }
        v.set(3, 64, 3, ORE).set(3, 65, 3, ExposureRule.OPEN) // open cell at 65 >= 70 - 8: surface outcrop
                .set(5, 61, 5, ORE).set(5, 62, 5, ExposureRule.OPEN) // 62 is the band's lowest y
                .set(7, 20, 7, ORE).set(7, 21, 7, ExposureRule.OPEN) // deep: cave
                .set(9, 60, 9, ORE).set(9, 61, 9, ExposureRule.OPEN); // 61 < 62: cave
        final ChunkCensus c = ExposureCensus.count(v, ORE_ONLY);
        assertThat(c.faces()).isEqualTo(4);
        assertThat(c.facesSurface()).isEqualTo(2);
        assertThat(c.facesCave()).isEqualTo(2);
        assertThat(c.exposedSurface()).isEqualTo(2);
        assertThat(c.exposedCave()).isEqualTo(2);
        final int ex = c.surfaceExample();
        assertThat(ChunkCensus.unpackX(ex)).isEqualTo(3);
        assertThat(ChunkCensus.unpackY(ex)).isEqualTo(64);
        assertThat(ChunkCensus.unpackZ(ex)).isEqualTo(3);
    }

    @Test
    void worldBoundsBelowSolidAboveOpen() {
        final ArrayChunkView v = solid().set(1, MIN_Y, 1, ORE).set(2, MIN_Y + HEIGHT - 1, 2, ORE);
        final ChunkCensus c = ExposureCensus.count(v, ORE_ONLY);
        assertThat(c.faces()).isEqualTo(1);
        assertThat(c.facesByDirection(1)).isEqualTo(1);
    }

    @Test
    void uncountedTargetsOnlyInPerTarget() {
        final ArrayChunkView v = open().set(2, 0, 2, ROCK).set(4, 0, 4, ORE);
        final ChunkCensus c = ExposureCensus.count(v, ORE_ONLY);
        assertThat(c.blocks()).isEqualTo(1);
        assertThat(c.faces()).isEqualTo(6);
        assertThat(c.targetBlocks(ROCK)).isEqualTo(1);
        assertThat(c.targetFaces(ROCK)).isEqualTo(6);
        assertThat(c.targetFaces(ORE)).isEqualTo(6);
    }

    @Test
    void unknownTargetIsRefused() {
        final ArrayChunkView v = solid().set(1, 1, 1, 5);
        assertThatThrownBy(() -> ExposureCensus.count(v, ORE_ONLY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void packRoundTripsNegativeY() {
        for (int y : new int[] {-64, -1, 0, 319}) {
            final int p = ChunkCensus.pack(15, y, 7);
            assertThat(ChunkCensus.unpackX(p)).isEqualTo(15);
            assertThat(ChunkCensus.unpackY(p)).isEqualTo(y);
            assertThat(ChunkCensus.unpackZ(p)).isEqualTo(7);
        }
    }
}
