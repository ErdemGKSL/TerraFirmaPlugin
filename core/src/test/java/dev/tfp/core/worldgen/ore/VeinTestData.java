/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.ore;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** The vein data datagen exports (content/generated/content/data), read through {@link VeinData}. */
final class VeinTestData {
    private VeinTestData() {}

    static Path dataRoot() {
        final String configured = System.getProperty("tfp.contentData");
        final Path root = configured != null ? Path.of(configured) : Path.of("..", "content", "generated", "content", "data");
        assumeTrue(Files.isDirectory(root.resolve(VeinData.FEATURE_DIR)), "no generated vein data at " + root.toAbsolutePath() + " (run tools/datagen)");
        return root;
    }

    static JsonObject json(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every cluster vein type by name, in file name order. */
    static Map<String, ClusterVeinConfig> clusterConfigs() {
        final Map<String, ClusterVeinConfig> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(dataRoot().resolve(VeinData.FEATURE_DIR))) {
            for (Path file : files.sorted().toList()) {
                final String name = file.getFileName().toString().replace(".json", "");
                VeinData.parse(name, json(file)).ifPresent(c -> out.put(name, c));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** The cluster vein types of the vein tag, in tag order (what the generator uses). */
    static List<ClusterVeinConfig> tagOrdered() {
        final Map<String, ClusterVeinConfig> all = clusterConfigs();
        final List<ClusterVeinConfig> out = new ArrayList<>();
        for (String name : VeinData.tagOrder(json(dataRoot().resolve(VeinData.VEIN_TAG)))) {
            if (all.containsKey(name)) {
                out.add(all.get(name));
            }
        }
        return out;
    }

    /** The first replaced block of a vein type's first entry. */
    static String firstRock(String name) {
        final JsonObject root = json(dataRoot().resolve(VeinData.FEATURE_DIR + name + ".json"));
        return root.getAsJsonObject("config").getAsJsonArray("blocks").get(0).getAsJsonObject()
            .getAsJsonArray("replace").get(0).getAsString();
    }

    /** The 21 raw rock ids, as the generator passes them. */
    static List<String> rockIds() {
        return dev.tfp.core.worldgen.chunk.PlaceholderRocks.names().stream().map(n -> "tfp:rock/raw/" + n).toList();
    }
}
