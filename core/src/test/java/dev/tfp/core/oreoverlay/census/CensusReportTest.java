/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay.census;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-17: aggregation of the census over an area. */
class CensusReportTest {
    static final List<String> TARGETS = List.of("tfp:ore/normal_native_gold/granite", "tfp:ore/rich_native_gold/granite",
            "tfp:rock/raw/granite");
    static final boolean[] COUNTED = {true, true, false};

    /** A chunk with {@code n} single gold ores in open air (6 faces each, all in the surface band) and one rock. */
    static ChunkCensus chunk(int n) {
        final ArrayChunkView v = ExposureCensusTest.open();
        for (int x = -1; x <= 16; x++) {
            for (int z = -1; z <= 16; z++) {
                v.surface(x, z, 70);
            }
        }
        for (int i = 0; i < n; i++) {
            v.set(i % 8 * 2, 66, i / 8 * 2, i % 2);
        }
        v.set(15, 0, 15, 2);
        return ExposureCensus.count(v, COUNTED);
    }

    static CensusReport uniform(int size, int perChunk) {
        final ChunkCensus c = chunk(perChunk);
        final List<ChunkCensus> grid = new ArrayList<>();
        for (int i = 0; i < size * size; i++) {
            grid.add(c);
        }
        return new CensusReport("tfc", TARGETS, COUNTED, -5, 3, size, grid);
    }

    @Test
    void percentilesAreNearestRank() {
        final int[] v = new int[100];
        for (int i = 0; i < 100; i++) {
            v[i] = 100 - i;
        }
        assertThat(CensusReport.percentile(v, 0.5)).isEqualTo(50);
        assertThat(CensusReport.percentile(v, 0.95)).isEqualTo(95);
        assertThat(CensusReport.percentile(v, 1.0)).isEqualTo(100);
        assertThat(CensusReport.percentile(new int[0], 0.5)).isZero();
        assertThat(CensusReport.percentile(new int[] {7}, 0.95)).isEqualTo(7);
    }

    @Test
    void radiusTotalsSumTheSquare() {
        final CensusReport r = uniform(21, 1);
        final int[] faces = r.perChunk(ChunkCensus::faces);
        assertThat(faces[0]).isEqualTo(6);
        assertThat(r.radiusTotals(faces, 4, 1)).hasSize(CensusReport.SAMPLES).containsOnly(81 * 6);
        assertThat(r.radiusTotals(faces, 10, 1)).containsOnly(441 * 6);
        assertThat(r.radiusTotals(faces, 11, 1)).isNull();
    }

    @Test
    void radiusTotalsSeeOneHotChunk() {
        final List<ChunkCensus> grid = new ArrayList<>();
        final ChunkCensus cold = chunk(0);
        for (int i = 0; i < 81; i++) {
            grid.add(i == 40 ? chunk(10) : cold);
        }
        final CensusReport r = new CensusReport("tfc", TARGETS, COUNTED, 0, 0, 9, grid);
        // 9x9 area, radius 4: the only square is the whole area, which holds the hot chunk.
        assertThat(r.radiusTotals(r.perChunk(ChunkCensus::faces), 4, 7)).containsOnly(60);
        assertThat(r.radiusTotals(r.perChunk(ChunkCensus::faces), 6, 7)).isNull();
    }

    @Test
    void jsonHasTheTablesTheSpikeDocNeeds() {
        final CensusReport r = uniform(9, 4);
        final JsonObject j = r.toJson(id -> id.replaceAll("/[a-z_]+$", ""), 3, 0.5);
        assertThat(j.getAsJsonObject("per_chunk").getAsJsonObject("faces").get("p95").getAsInt()).isEqualTo(24);
        assertThat(j.getAsJsonObject("totals").get("blocks").getAsLong()).isEqualTo(81 * 4);
        assertThat(j.getAsJsonObject("totals").get("faces_surface").getAsLong()).isEqualTo(81 * 24);
        assertThat(j.getAsJsonObject("fractions").get("faces_per_exposed").getAsDouble()).isEqualTo(6.0);
        assertThat(j.getAsJsonObject("radius_faces").getAsJsonObject("gated_4").get("p50").getAsInt()).isEqualTo(81 * 24);
        assertThat(j.getAsJsonObject("radius_faces").get("view_distance_6").isJsonNull()).isTrue();
        // One row per ore grade with the rock folded away; the uncounted rock keeps its own row, after the ores.
        assertThat(j.getAsJsonArray("per_target")).hasSize(3);
        assertThat(j.getAsJsonArray("per_target").get(2).getAsJsonObject().get("counted").getAsBoolean()).isFalse();
        final JsonObject gold = j.getAsJsonArray("per_target").get(0).getAsJsonObject();
        assertThat(gold.get("id").getAsString()).isEqualTo("tfp:ore/normal_native_gold");
        assertThat(gold.get("blocks").getAsLong()).isEqualTo(81 * 2);
        final JsonObject example = j.getAsJsonObject("exposed_example");
        assertThat(example.get("x").getAsInt()).isEqualTo(-5 * 16);
        assertThat(example.get("y").getAsInt()).isEqualTo(66);
        assertThat(example.get("z").getAsInt()).isEqualTo(3 * 16);
        // Estimate: surface faces 24 + max(cave 0, 4 blocks x 0.5) = 26.
        assertThat(j.getAsJsonObject("estimate").getAsJsonObject("per_chunk").get("p50").getAsInt()).isEqualTo(26);
        assertThat(j.getAsJsonObject("per_chunk_raw").getAsJsonArray("blocks")).hasSize(81);
    }

    @Test
    void caveFacesPerBlockOfAProxy() {
        final ArrayChunkView v = ExposureCensusTest.solid();
        v.set(1, 0, 1, 0).set(1, 1, 1, ExposureRule.OPEN).set(3, 0, 3, 0);
        final ChunkCensus c = ExposureCensus.count(v, COUNTED);
        final CensusReport r = new CensusReport("vanilla", TARGETS, COUNTED, 0, 0, 1, List.of(c));
        assertThat(r.caveFacesPerBlock()).isEqualTo(0.5);
        assertThat(r.estimatedFaces(2.0)).containsExactly(4);
    }
}
