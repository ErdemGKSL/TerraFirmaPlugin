/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/tfc/fuel/*.json @ c3b4d9536 (every file's duration, temperature and purity)
 */
package dev.tfp.core.fuel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * M3-HEAT-02 golden values: TFC's fuel data files at c3b4d9536, read from {@link #TABLE} (exported from
 * {@code src/generated/resources/data/tfc/tfc/fuel/**} by a scratch script outside the repo). Each row is one data file with
 * its id, duration, temperature, purity (TFC default 1.0) and the ingredient kind and value, which is data glue that lives
 * with the item data and is not part of {@link FuelTable}.
 *
 * <p>The checks: the row count is TFC's recursive file count, every Java entry matches its row's duration, temperature and
 * purity, and the TFC fuels with no Java entry are exactly the ids listed in {@link #OPEN_GAPS}.
 */
class FuelTableGoldenTest {
    static final String TABLE = "dev/tfp/core/fuel/fuel_table.tsv";

    /** The TFC {@code fuel/} file count (recursive) at c3b4d9536: 47 top level, 2 groundcover, 1 ore. */
    static final int TFC_FILE_COUNT = 50;

    /** The TFC fuel ids the Java {@link FuelTable} does not have yet, one per line in {@code open_gaps.txt}. */
    static final String OPEN_GAPS = "dev/tfp/core/fuel/open_gaps.txt";

    private static List<Map<String, String>> rows;

    @BeforeAll
    static void load() throws IOException {
        rows = readTable();
    }

    @Test
    void rowCountMatchesTfcFileCount() {
        assertThat(rows).hasSize(TFC_FILE_COUNT);
        final Set<String> ids = new HashSet<>();
        for (Map<String, String> row : rows) {
            assertThat(ids.add(row.get("id"))).as("duplicate id %s", row.get("id")).isTrue();
        }
    }

    @Test
    void ingredientsAreItemsOrTags() {
        for (Map<String, String> row : rows) {
            assertThat(row.get("ingredient_kind")).as(row.get("id")).isIn("item", "tag");
            assertThat(row.get("ingredient")).as(row.get("id")).isNotBlank();
        }
    }

    @Test
    void everyJavaEntryMatchesItsRow() {
        int checked = 0;
        for (Map<String, String> row : rows) {
            final String id = row.get("id");
            final Optional<Fuel> fuel = FuelTable.byId(id);
            if (fuel.isEmpty()) {
                continue;
            }
            assertThat(fuel.get().duration()).as(id).isEqualTo(Integer.parseInt(row.get("duration")));
            assertThat(fuel.get().temperature()).as(id).isEqualTo(Float.parseFloat(row.get("temperature")));
            assertThat(fuel.get().purity()).as(id).isEqualTo(Float.parseFloat(row.get("purity")));
            checked++;
        }
        assertThat(checked).isEqualTo(FuelTable.ALL.size());
    }

    @Test
    void openGapsAreExact() throws IOException {
        final Set<String> tfcIds = new TreeSet<>();
        for (Map<String, String> row : rows) {
            tfcIds.add(row.get("id"));
        }
        final Set<String> javaIds = new TreeSet<>();
        for (Fuel fuel : FuelTable.ALL) {
            javaIds.add(fuel.id());
        }
        assertThat(javaIds).as("Java entries with no TFC file").isSubsetOf(tfcIds);

        final Set<String> missing = new TreeSet<>(tfcIds);
        missing.removeAll(javaIds);
        assertThat(missing).isEqualTo(openGaps());
    }

    @Test
    void lookupByIdFindsTfcEntries() {
        assertThat(FuelTable.byId("coal")).contains(new Fuel("coal", 2000, 1415f, 1f));
        assertThat(FuelTable.byId("groundcover/pinecone")).contains(new Fuel("groundcover/pinecone", 220, 150f, 0.15f));
        assertThat(FuelTable.byId("nope")).isEqualTo(Optional.empty());
    }

    @Test
    void recordRejectsImpossibleValues() {
        assertThatThrownBy(() -> new Fuel("x", -1, 1f, 1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Fuel("x", 1, -1f, 1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Fuel("x", 1, 1f, 1.5f)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Map<String, String>> readTable() throws IOException {
        try (InputStream in = FuelTableGoldenTest.class.getClassLoader().getResourceAsStream(TABLE)) {
            assertThat(in).as("resource %s", TABLE).isNotNull();
            final List<String> lines = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    lines.add(line);
                }
            }
            final String[] header = lines.get(0).split("\t");
            final List<Map<String, String>> out = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                final String[] cols = line.split("\t", -1);
                assertThat(cols).as(line).hasSize(header.length);
                final Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < header.length; i++) {
                    row.put(header[i], cols[i]);
                }
                out.add(row);
            }
            return out;
        }
    }

    private static Set<String> openGaps() throws IOException {
        try (InputStream in = FuelTableGoldenTest.class.getClassLoader().getResourceAsStream(OPEN_GAPS)) {
            assertThat(in).as("resource %s", OPEN_GAPS).isNotNull();
            final Set<String> out = new TreeSet<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    out.add(line.trim());
                }
            }
            return out;
        }
    }
}
