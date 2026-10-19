/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The complete datagen output, held in memory and sorted by path, then written in one go. Writing first
 * deletes every path datagen owns, so stale files never survive a regeneration.
 */
public final class OutputTree {
    /** Top-level entries of the content directory that datagen owns (everything else is left alone). */
    public static final List<String> OWNED = List.of(
        "data", "craftengine", "betterhud", "visuals", "datapack", "advancements", "guide", "reports", Manifest.FILE,
        "third-party.json");

    private final SortedMap<String, byte[]> files = new TreeMap<>();

    public synchronized void put(String path, byte[] bytes) {
        if (path.startsWith("/") || path.contains("\\") || path.contains("..")) {
            throw new IllegalArgumentException("bad output path " + path);
        }
        String top = path.contains("/") ? path.substring(0, path.indexOf('/')) : path;
        if (!OWNED.contains(top)) {
            throw new IllegalArgumentException("output path outside datagen-owned roots: " + path);
        }
        byte[] old = files.put(path, bytes);
        if (old != null && !Arrays.equals(old, bytes)) {
            throw new DatagenException("two different outputs for " + path);
        }
    }

    public void put(String path, String text) {
        put(path, text.getBytes(StandardCharsets.UTF_8));
    }

    /** The bytes written at {@code path}, or null. */
    public synchronized byte[] get(String path) {
        return files.get(path);
    }

    public synchronized boolean contains(String path) {
        return files.containsKey(path);
    }

    public synchronized SortedMap<String, byte[]> files() {
        return new TreeMap<>(files);
    }

    public void writeTo(Path dir) {
        try {
            Files.createDirectories(dir);
            for (String owned : OWNED) {
                deleteRecursively(dir.resolve(owned));
            }
            for (Map.Entry<String, byte[]> e : files().entrySet()) {
                Path p = dir.resolve(e.getKey());
                Files.createDirectories(p.getParent());
                Files.write(p, e.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Differences between this tree and the owned part of {@code dir}; empty when up to date. */
    public List<String> diff(Path dir) {
        SortedMap<String, byte[]> expected = files();
        SortedMap<String, byte[]> actual = readOwned(dir);
        List<String> out = new ArrayList<>();
        for (String p : new TreeSet<>(union(expected.keySet(), actual.keySet()))) {
            byte[] e = expected.get(p);
            byte[] a = actual.get(p);
            if (a == null) {
                out.add("missing: " + p);
            } else if (e == null) {
                out.add("stale:   " + p);
            } else if (!Arrays.equals(e, a)) {
                out.add("changed: " + p);
            }
        }
        return out;
    }

    static SortedMap<String, byte[]> readOwned(Path dir) {
        SortedMap<String, byte[]> out = new TreeMap<>();
        for (String owned : OWNED) {
            Path p = dir.resolve(owned);
            if (!Files.exists(p)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(p)) {
                for (Path f : s.filter(Files::isRegularFile).toList()) {
                    out.put(dir.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return out;
    }

    private static TreeSet<String> union(java.util.Set<String> a, java.util.Set<String> b) {
        TreeSet<String> s = new TreeSet<>(a);
        s.addAll(b);
        return s;
    }

    private static void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) {
            return;
        }
        try (Stream<Path> s = Files.walk(p)) {
            for (Path f : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(f);
            }
        }
    }
}
