/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * An immutable set of loot tables by id. {@link #load} reads the datapack layout {@code <root>/<namespace>/loot_table/**.json}
 * (the data root the plugin jar ships as {@code content/data}); a table that fails to parse is reported in
 * {@link #errors()} and left out, the others load.
 */
public record LootTables(Map<String, LootTable> tables, List<String> errors) {
    public static final LootTables EMPTY = new LootTables(Map.of(), List.of());

    public LootTables {
        tables = Map.copyOf(tables);
        errors = List.copyOf(errors);
    }

    public Optional<LootTable> get(String id) {
        return Optional.ofNullable(tables.get(id));
    }

    /** The table of a block: {@code ns:path} → {@code ns:blocks/path}. */
    public Optional<LootTable> forBlock(String blockId) {
        return get(prefixed(blockId, "blocks/"));
    }

    /** The table of an entity type: {@code ns:path} → {@code ns:entities/path}. */
    public Optional<LootTable> forEntity(String entityTypeId) {
        return get(prefixed(entityTypeId, "entities/"));
    }

    public int size() {
        return tables.size();
    }

    private static String prefixed(String id, String folder) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft:" + folder + id : id.substring(0, colon + 1) + folder + id.substring(colon + 1);
    }

    /** Loads every {@code <ns>/loot_table/**.json} under {@code dataRoot}; a missing root or folder gives no tables. */
    public static LootTables load(Path dataRoot) {
        Map<String, LootTable> out = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        if (!Files.isDirectory(dataRoot)) {
            return EMPTY;
        }
        try (Stream<Path> namespaces = Files.list(dataRoot)) {
            for (Path ns : namespaces.filter(Files::isDirectory).sorted().toList()) {
                Path folder = ns.resolve("loot_table");
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                String namespace = name(ns);
                try (Stream<Path> files = Files.walk(folder)) {
                    for (Path file : files.filter(f -> name(f).endsWith(".json")).sorted().toList()) {
                        String rel = folder.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                        String id = namespace + ":" + rel.substring(0, rel.length() - ".json".length());
                        try {
                            out.put(id, LootTableParser.parse(id, Files.readString(file, StandardCharsets.UTF_8)));
                        } catch (LootTableParser.LootParseException e) {
                            errors.add(e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read loot tables under " + dataRoot, e);
        }
        return new LootTables(out, errors);
    }

    private static String name(Path p) {
        Path f = p.getFileName();
        String s = f == null ? "" : f.toString();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
