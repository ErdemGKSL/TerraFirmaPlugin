/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.spawn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.calendar.Month;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-04: spawn_table.json parsing with TFC's codec defaults, the chunk plan, and the shipped file against the TFC golden. */
class SpawnTablesTest {
    private static final String SAMPLE = """
            {"biomes": {"plains": {"probability": 0.1,
                "creature": [["tfc:cow", 1, 1, 4], ["tfc:deer", 3, 2, 2]]}},
             "fauna": {"tfc:cow": {"min_temperature": -10.0, "min_groundwater": 250.0, "max_forest": 3},
                       "tfc:salmon": {"chance": 10, "fuzzy": true, "months": ["june", "july"], "max_brightness": 4,
                                      "distance_below_sea_level": 0}}}
            """;

    @Test
    void parsesWithTfcDefaults() {
        SpawnTables t = SpawnTables.parse(JsonParser.parseString(SAMPLE).getAsJsonObject());
        FaunaRule cow = t.fauna().get("tfc:cow");
        assertThat(cow.chance()).isEqualTo(1);
        assertThat(cow.distanceBelowSeaLevel()).isEqualTo(-1);
        assertThat(cow.minTemperature()).isEqualTo(-10f);
        assertThat(cow.maxTemperature()).isEqualTo(Float.POSITIVE_INFINITY);
        assertThat(cow.minGroundwater()).isEqualTo(250f);
        assertThat(cow.maxGroundwater()).isEqualTo(Float.POSITIVE_INFINITY);
        assertThat(cow.minForest()).isZero();
        assertThat(cow.maxForest()).isEqualTo(3);
        assertThat(cow.fuzzy()).isFalse();
        assertThat(cow.maxBrightness()).isEqualTo(-1);
        assertThat(cow.months()).isEmpty();
        FaunaRule salmon = t.fauna().get("tfc:salmon");
        assertThat(salmon.chance()).isEqualTo(10);
        assertThat(salmon.fuzzy()).isTrue();
        assertThat(salmon.months()).containsExactly(Month.JUNE, Month.JULY);
        assertThat(salmon.maxBrightness()).isEqualTo(4);
        assertThat(salmon.distanceBelowSeaLevel()).isZero();
        SpawnTables.BiomeSpawns plains = t.biome("plains").orElseThrow();
        assertThat(plains.probability()).isEqualTo(0.1f);
        assertThat(plains.of(SpawnCategory.CREATURE)).extracting(SpawnEntry::type).containsExactly("tfc:cow", "tfc:deer");
        assertThat(plains.of(SpawnCategory.WATER_CREATURE)).isEmpty();
    }

    @Test
    void newChunkPlanFollowsTheProbabilityLoopAndScattersInsideTheChunk() {
        SpawnTables.BiomeSpawns plains = SpawnTables.parse(JsonParser.parseString(SAMPLE).getAsJsonObject()).biome("plains").orElseThrow();
        // a draw of 0.05 stays under 0.1, then 0.09 after one step (0.1 - 0.01), so the loop ends only at the group cap
        assertThat(ChunkSpawns.forNewChunk(plains, new FixedRandom(0, 0.5f))).isEmpty();
        List<ChunkSpawns.Member> members = ChunkSpawns.forNewChunk(plains, new FixedRandom(3, 0.0f));
        assertThat(members).isNotEmpty().allSatisfy(m -> {
            assertThat(m.x()).isBetween(0, 15);
            assertThat(m.z()).isBetween(0, 15);
        });
        assertThat(ChunkSpawns.topUp(plains, new FixedRandom(0, 0.0f))).hasSize(1).first()
                .satisfies(m -> assertThat(m.entry().type()).isEqualTo("tfc:cow"));
    }

    @Test
    void groupSizeStaysInTheEntryRange() {
        SpawnEntry e = new SpawnEntry("plains", SpawnCategory.CREATURE, "tfc:cow", 1, 1, 4);
        for (int i = 0; i < 12; i++) {
            assertThat(ChunkSpawns.group(e, new FixedRandom(i, 0f))).hasSizeBetween(1, 4);
        }
    }

    /** The file datagen ships equals TFC's exported golden: every biome entry and every gate (skipped when content is not generated). */
    @Test
    void shippedTableMatchesTheGolden() throws IOException {
        Path file = Path.of("..", "content", "generated", "content", "data", "tfp", "data_maps", "spawn_table.json");
        assumeTrue(Files.exists(file), "content/generated is not built");
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        SpawnTables t = SpawnTables.parse(root);
        SpawnTsv golden = SpawnTsv.load();
        List<String> problems = new ArrayList<>();
        for (SpawnTsv.Row row : golden.rows()) {
            if (!row.entry().isTfc()) {
                continue;
            }
            SpawnEntry want = row.entry();
            List<SpawnEntry> have = t.biome(want.biome()).map(b -> b.of(want.category())).orElse(List.of());
            if (!have.contains(want)) {
                problems.add("missing " + want);
            }
            if (!row.rule().equals(t.fauna().get(want.type()))) {
                problems.add("gate of " + want.type() + ": " + t.fauna().get(want.type()) + " != " + row.rule());
            }
        }
        assertThat(problems).as(Arrays.toString(problems.stream().limit(5).toArray())).isEmpty();
        long tfcEntries = t.biomes().values().stream().mapToLong(b -> b.entries().values().stream().mapToLong(List::size).sum()).sum();
        assertThat(tfcEntries).isEqualTo(5039);
        assertThat(t.fauna()).hasSize(77);
    }
}
