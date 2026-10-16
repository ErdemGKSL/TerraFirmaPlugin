/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.showcase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonParser;
import dev.tfp.core.showcase.Showcase.Cell;
import dev.tfp.core.showcase.Showcase.Family;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** M1-SVC-05: family selection and the showcase grid layout. */
class ShowcaseTest {
    @Test
    void familiesByIdPrefix() {
        assertThat(Showcase.familyOf("tfp:rock/raw/granite")).contains(Family.ROCKS);
        assertThat(Showcase.familyOf("tfp:rock/mossy_cobble/chalk")).contains(Family.ROCKS);
        assertThat(Showcase.familyOf("tfp:ore/rich_hematite/basalt")).contains(Family.ORES);
        assertThat(Showcase.familyOf("tfp:ore/diamond/gneiss")).contains(Family.ORES);
        assertThat(Showcase.familyOf("tfp:dirt/alfisol")).contains(Family.SOILS);
        assertThat(Showcase.familyOf("tfp:clay_grass/oxisol")).contains(Family.SOILS);
        assertThat(Showcase.familyOf("tfp:sand/white")).contains(Family.SOILS);
        assertThat(Showcase.familyOf("tfp:wood/log/oak")).isEmpty();
        assertThat(Showcase.familyOf("tfp:aggregate")).isEmpty();
        assertThat(Showcase.familyOf("tfp:rock/")).isEmpty();
        assertThat(Showcase.familyOf("tfp:mud_bricks/loam")).isEmpty();
    }

    @Test
    void familyArguments() {
        assertThat(Family.arguments()).containsExactly("rocks", "soils", "ores");
        assertThat(Family.byArgument("ORES")).contains(Family.ORES);
        assertThat(Family.byArgument(" rocks ")).contains(Family.ROCKS);
        assertThat(Family.byArgument("woods")).isEmpty();
        assertThat(Family.byArgument(null)).isEmpty();
    }

    @Test
    void selectSortsAndDeduplicates() {
        List<String> ids = List.of("tfp:rock/raw/granite", "tfp:ore/diamond/granite", "tfp:rock/raw/basalt",
                "tfp:rock/raw/granite", "tfp:wood/log/oak");
        assertThat(Showcase.select(ids, Family.ROCKS)).containsExactly("tfp:rock/raw/basalt", "tfp:rock/raw/granite");
        assertThat(Showcase.select(ids, Family.ORES)).containsExactly("tfp:ore/diamond/granite");
        assertThat(Showcase.select(ids, Family.SOILS)).isEmpty();
    }

    @Test
    void layoutIsOneRowPerGroupOneColumnPerMember() {
        List<String> ids = List.of("tfp:ore/rich_hematite/granite", "tfp:ore/poor_hematite/basalt",
                "tfp:ore/rich_hematite/basalt", "tfp:ore/poor_hematite/granite", "tfp:ore/poor_hematite/chalk");
        Map<String, Cell> grid = Showcase.layout(ids, 2);
        // rows sorted by group, columns sorted by member
        assertThat(grid).containsExactly(
                Map.entry("tfp:ore/poor_hematite/basalt", new Cell(0, 0)),
                Map.entry("tfp:ore/poor_hematite/chalk", new Cell(2, 0)),
                Map.entry("tfp:ore/poor_hematite/granite", new Cell(4, 0)),
                Map.entry("tfp:ore/rich_hematite/basalt", new Cell(0, 2)),
                Map.entry("tfp:ore/rich_hematite/granite", new Cell(2, 2)));
        assertThat(Showcase.extent(grid)).isEqualTo(new Cell(5, 3));
        assertThat(Showcase.layout(ids, 1).get("tfp:ore/rich_hematite/granite")).isEqualTo(new Cell(1, 1));
    }

    @Test
    void layoutHasNoOverlapsAndIsStableUnderInputOrder() {
        List<String> ids = new java.util.ArrayList<>();
        for (String type : List.of("raw", "hardened", "bricks", "cobble")) {
            for (String rock : List.of("granite", "basalt", "chalk", "shale", "gneiss")) {
                ids.add("tfp:rock/" + type + "/" + rock);
            }
        }
        Map<String, Cell> grid = Showcase.layout(ids, 2);
        assertThat(grid).hasSize(20);
        assertThat(new HashSet<>(grid.values())).hasSize(20);
        assertThat(Showcase.extent(grid)).isEqualTo(new Cell(9, 7));
        java.util.Collections.reverse(ids);
        assertThat(Showcase.layout(ids, 2)).containsExactlyEntriesOf(grid);
        assertThat(Showcase.layout(List.of(), 2)).isEmpty();
        assertThat(Showcase.extent(Map.of())).isEqualTo(new Cell(0, 0));
        assertThatThrownBy(() -> Showcase.layout(ids, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blockIdsFromAnIdMap() {
        var json = JsonParser.parseString("""
                {"comment": "x", "namespaces": {"tfc": "tfp"},
                 "blocks": {"tfc:rock/raw/granite": "tfp:rock/raw/granite", "tfc:aggregate": "tfp:aggregate"},
                 "items": {"tfc:rock/raw/granite": "tfp:rock/raw/granite"}}""");
        assertThat(Showcase.blockIds(json)).containsExactly("tfp:aggregate", "tfp:rock/raw/granite");
        assertThatThrownBy(() -> Showcase.blockIds(JsonParser.parseString("{}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Showcase.blockIds(JsonParser.parseString("[]"))).isInstanceOf(IllegalArgumentException.class);
    }

    /** The shipped datagen output (when generated): every family has members, and the ore grid is ore × rock. */
    @Test
    void generatedIdMapHasEveryFamily() throws IOException {
        String dir = System.getProperty("tfp.contentData");
        Path idMap = dir == null ? null : Path.of(dir, "id_map.json");
        Assumptions.assumeTrue(idMap != null && Files.isRegularFile(idMap), "no generated content/data (run datagen)");
        List<String> ids = Showcase.blockIds(JsonParser.parseString(Files.readString(idMap)));
        for (Family f : Family.values()) {
            assertThat(Showcase.select(ids, f)).as(f.argument()).isNotEmpty();
        }
        Map<String, Cell> ores = Showcase.layout(Showcase.select(ids, Family.ORES), 2);
        Set<Integer> rows = new HashSet<>();
        ores.values().forEach(c -> rows.add(c.dz()));
        assertThat(rows.size()).isGreaterThan(20);
        assertThat(ores.get("tfp:ore/rich_hematite/granite")).isNotNull();
    }
}
