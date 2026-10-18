/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.spawn;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ChunkClimate;

/**
 * Reads the TFC spawn golden {@code spawn_rules.tsv}, exported by a scratch script from TFC at c3b4d9536. The header comment
 * records the file counts the export saw; the rows are one per biome spawner entry, in the biome JSON's order.
 */
final class SpawnTsv {
    static final String RESOURCE = "/dev/tfp/core/spawn/spawn_rules.tsv";

    /** One data row: the biome entry and, for a TFC species, its gate. */
    record Row(SpawnEntry entry, FaunaRule rule) {
    }

    private final Map<String, String> header;
    private final List<Row> rows;

    private SpawnTsv(Map<String, String> header, List<Row> rows) {
        this.header = header;
        this.rows = rows;
    }

    static SpawnTsv load() {
        try (InputStream in = SpawnTsv.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing " + RESOURCE);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static SpawnTsv parse(String text) {
        Map<String, String> header = new LinkedHashMap<>();
        List<Row> rows = new ArrayList<>();
        String[] columns = null;
        for (String line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("#")) {
                for (String pair : line.substring(1).trim().split("\\s+")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        header.put(pair.substring(0, eq), pair.substring(eq + 1));
                    }
                }
                continue;
            }
            String[] cells = line.split("\t", -1);
            if (columns == null) {
                columns = cells;
                continue;
            }
            rows.add(row(columns, cells));
        }
        return new SpawnTsv(header, rows);
    }

    private static Row row(String[] columns, String[] cells) {
        Map<String, String> c = new LinkedHashMap<>();
        for (int i = 0; i < columns.length; i++) {
            c.put(columns[i], cells[i]);
        }
        SpawnEntry entry = new SpawnEntry(c.get("biome"), SpawnCategory.fromSerialized(c.get("category")), c.get("type"),
                Integer.parseInt(c.get("weight")), Integer.parseInt(c.get("min_count")), Integer.parseInt(c.get("max_count")));
        if (!"1".equals(c.get("gated"))) {
            return new Row(entry, null);
        }
        return new Row(entry, new FaunaRule(
                Integer.parseInt(c.get("chance")),
                Integer.parseInt(c.get("distance_below_sea_level")),
                Float.parseFloat(c.get("min_temperature")),
                Float.parseFloat(c.get("max_temperature")),
                Float.parseFloat(c.get("min_groundwater")),
                Float.parseFloat(c.get("max_groundwater")),
                Integer.parseInt(c.get("min_forest")),
                Integer.parseInt(c.get("max_forest")),
                Boolean.parseBoolean(c.get("fuzzy")),
                Boolean.parseBoolean(c.get("ignore_rivers")),
                Integer.parseInt(c.get("max_brightness")),
                months(c.get("months"))));
    }

    private static List<Month> months(String cell) {
        List<Month> months = new ArrayList<>();
        for (String name : cell.split(";")) {
            if (!name.isEmpty()) {
                months.add(Month.valueOf(name.toUpperCase(Locale.ROOT)));
            }
        }
        return months;
    }

    /** The header value for {@code key}, such as {@code spawn_entries}. */
    int headerInt(String key) {
        return Integer.parseInt(header.get(key));
    }

    List<Row> rows() {
        return rows;
    }

    /** The gate of every TFC species that appears in the file, by namespaced type. */
    Map<String, FaunaRule> rules() {
        Map<String, FaunaRule> rules = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.rule() != null) {
                rules.putIfAbsent(row.entry().type(), row.rule());
            }
        }
        return rules;
    }

    /** The candidate list of one biome and category, in file order. */
    List<SpawnEntry> candidates(String biome, SpawnCategory category) {
        List<SpawnEntry> out = new ArrayList<>();
        for (Row row : rows) {
            if (row.entry().biome().equals(biome) && row.entry().category() == category) {
                out.add(row.entry());
            }
        }
        return out;
    }

    static ChunkClimate chunk(float temperature, float rain, float variance, float base) {
        return new ChunkClimate(temperature, rain, variance, base);
    }
}
