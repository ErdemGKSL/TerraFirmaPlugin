/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import dev.tfp.core.registry.DataLoadException;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * What the exported data refers to outside itself, found by running core's own {@link DataLoader} over the
 * exported {@code data/} tree with a table that accepts and records every lookup. So the reference set is exactly
 * what the runtime loader (and ContentDataLoadTest) will check, with no second schema walker to keep in sync.
 *
 * <p>Load errors are ignored here: datagen only collects; ContentDataLoadTest reports them.
 *
 * @param ids  registry ({@code item}, {@code block}, {@code fluid}, {@code entity_type}, {@code food_trait}) → ids
 *             looked up in the id table (i.e. not defined by the data itself)
 * @param tags tag registry → tags referenced but not defined by the data
 */
record DataReferences(SortedMap<String, SortedSet<String>> ids, SortedMap<String, SortedSet<String>> tags) {
    static DataReferences collect(OutputTree out) {
        SortedMap<String, SortedSet<String>> ids = new TreeMap<>();
        SortedMap<String, SortedSet<String>> tags = new TreeMap<>();
        KnownIds recorder = new KnownIds() {
            @Override
            public boolean hasId(String registry, String id) {
                ids.computeIfAbsent(registry, r -> new TreeSet<>()).add(id);
                return true;
            }

            @Override
            public boolean hasExternalTag(String registry, String tag) {
                tags.computeIfAbsent(registry, r -> new TreeSet<>()).add(tag);
                return true;
            }
        };
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("tfp-datagen-refs");
            Path root = tmp.resolve("data");
            for (Map.Entry<String, byte[]> e : out.files().entrySet()) {
                if (e.getKey().startsWith("data/")) {
                    Path p = root.resolve(e.getKey().substring("data/".length()));
                    Files.createDirectories(p.getParent());
                    Files.write(p, e.getValue());
                }
            }
            try {
                DataLoader.standard(recorder).load(List.of(root));
            } catch (DataLoadException ignored) {
                // reported by ContentDataLoadTest
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            delete(tmp);
        }
        return new DataReferences(ids, tags);
    }

    /** Ids of {@code registry} outside {@code ourNamespace}, sorted. */
    SortedSet<String> externalIds(String registry, String ourNamespace) {
        SortedSet<String> out = new TreeSet<>();
        for (String id : ids.getOrDefault(registry, new TreeSet<>())) {
            if (!id.startsWith(ourNamespace + ":")) {
                out.add(id);
            }
        }
        return out;
    }

    private static void delete(Path p) {
        if (p == null) {
            return;
        }
        try (Stream<Path> s = Files.walk(p)) {
            for (Path f : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(f);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
