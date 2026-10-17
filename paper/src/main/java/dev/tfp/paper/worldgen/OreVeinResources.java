/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.worldgen.ore.ClusterVeinConfig;
import dev.tfp.core.worldgen.ore.VeinData;
import dev.tfp.paper.data.RegistryService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The ore vein types the generator places (M2-GEN-10): the cluster veins of the vein tag, in tag order, read from the
 * datagen output shipped in the jar ({@code content/data/tfp/...}). Disc and pipe veins are not ported yet and are skipped.
 */
final class OreVeinResources {
    private OreVeinResources() {}

    /** Reads the veins from the plugin jar. */
    static List<ClusterVeinConfig> fromJar(ClassLoader loader) {
        return load(path -> {
            try (InputStream in = loader.getResourceAsStream(RegistryService.JAR_DATA_ROOT + "/" + path)) {
                return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + path + " from the plugin jar", e);
            }
        });
    }

    /**
     * Pure: the cluster veins of the vein tag, in tag order.
     *
     * @param read the text of a file below the data root, or empty when it is missing
     * @throws IllegalStateException when the tag or a vein it names is missing
     */
    static List<ClusterVeinConfig> load(Function<String, Optional<String>> read) {
        final JsonObject tag = json(read, VeinData.VEIN_TAG);
        final List<ClusterVeinConfig> out = new ArrayList<>();
        for (String name : VeinData.tagOrder(tag)) {
            VeinData.parse(name, json(read, VeinData.FEATURE_DIR + name + ".json")).ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    private static JsonObject json(Function<String, Optional<String>> read, String path) {
        final String text = read.apply(path).orElseThrow(() -> new IllegalStateException(
            "ore vein data " + path + " is missing (run tools/datagen)"));
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
