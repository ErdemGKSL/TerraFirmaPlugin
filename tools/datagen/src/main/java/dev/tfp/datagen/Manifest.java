/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * {@code .datagen-manifest.json}: the TFC commit, an aggregate hash per input tree (every file datagen read,
 * grouped by its {@code resources/<data|assets>/<ns>/<kind>} directory) and per output tree. No timestamps, so
 * the same inputs give a byte-identical manifest.
 */
final class Manifest {
    static final String FILE = ".datagen-manifest.json";
    /** Bump when the output format changes in a way readers must know about. */
    static final int FORMAT = 1;

    private Manifest() {
    }

    static String build(String commit, Map<String, String> inputHashes, SortedMap<String, byte[]> outputs) {
        JsonObject root = new JsonObject();
        root.addProperty("generator", "tools/datagen");
        root.addProperty("format", FORMAT);
        root.addProperty("tfc_commit", commit);
        Map<String, String> outputHashes = new TreeMap<>();
        outputs.forEach((p, b) -> {
            if (!p.equals(FILE)) {
                outputHashes.put(p, TfcCheckout.sha256(b));
            }
        });
        root.add("inputs", groups(inputHashes, Manifest::inputGroup));
        root.addProperty("inputs_sha256", aggregate(inputHashes));
        root.add("outputs", groups(outputHashes, Manifest::outputGroup));
        root.addProperty("outputs_sha256", aggregate(outputHashes));
        return Json.write(root);
    }

    private static JsonObject groups(Map<String, String> hashes, java.util.function.Function<String, String> key) {
        Map<String, Map<String, String>> grouped = new TreeMap<>();
        hashes.forEach((p, h) -> grouped.computeIfAbsent(key.apply(p), k -> new TreeMap<>()).put(p, h));
        JsonObject o = new JsonObject();
        grouped.forEach((g, files) -> {
            JsonObject e = new JsonObject();
            e.addProperty("files", files.size());
            e.addProperty("sha256", aggregate(files));
            o.add(g, e);
        });
        return o;
    }

    /** {@code src/main/resources/assets/tfc/textures/...} → {@code src/main/resources/assets/tfc/textures}. */
    static String inputGroup(String path) {
        String[] s = path.split("/");
        for (int i = 0; i < s.length; i++) {
            if (s[i].equals("resources")) {
                return String.join("/", java.util.Arrays.copyOfRange(s, 0, Math.min(s.length - 1, i + 4)));
            }
        }
        return path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : ".";
    }

    /** Output files grouped by their first three directories. */
    static String outputGroup(String path) {
        String[] s = path.split("/");
        int n = Math.min(s.length - 1, 3);
        return n == 0 ? "." : String.join("/", java.util.Arrays.copyOfRange(s, 0, n));
    }

    /** sha256 over "path\tsha256\n" lines in path order. */
    static String aggregate(Map<String, String> hashes) {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(hashes).forEach((p, h) -> sb.append(p).append('\t').append(h).append('\n'));
        return TfcCheckout.sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
