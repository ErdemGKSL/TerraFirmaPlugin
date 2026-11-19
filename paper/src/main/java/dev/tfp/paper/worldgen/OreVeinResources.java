/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.worldgen.ore.ClusterVeinConfig;
import dev.tfp.core.worldgen.ore.DiscVeinConfig;
import dev.tfp.core.worldgen.ore.VeinBlock;
import dev.tfp.core.worldgen.ore.VeinConfig;
import dev.tfp.core.worldgen.ore.VeinData;
import dev.tfp.core.worldgen.ore.Weighted;
import dev.tfp.paper.data.RegistryService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The ore vein types of the shipped data (M2-GEN-10, PAR-WORLD-08): the veins of the vein tag, in tag order, read from the
 * datagen output in the jar ({@code content/data/tfp/...}), and the veins of the biome vein tags.
 *
 * <p>The generator places {@link #fromJar}: the main tag's types that it can write today ({@link #placeable}). The other
 * types are ported in {@code core} ({@link #loadAll}, {@link #biomeVeins}, {@link #kaolin}) and wait for the terrain to give
 * them what they need (PAR-WORLD-08's follow-up): blocks that are not ores, the ground height, the lava, and the biome tags.
 */
final class OreVeinResources {
    private OreVeinResources() {}

    /** The veins the generator places, from the plugin jar: {@link #placeable} of {@link #loadAll}. */
    static List<VeinConfig> fromJar(ClassLoader loader) {
        return placeable(loadAll(jar(loader)));
    }

    /** Reads files below the data root of the plugin jar. */
    static Function<String, Optional<String>> jar(ClassLoader loader) {
        return path -> {
            try (InputStream in = loader.getResourceAsStream(RegistryService.JAR_DATA_ROOT + "/" + path)) {
                return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + path + " from the plugin jar", e);
            }
        };
    }

    /**
     * Pure: the cluster veins of the vein tag, in tag order.
     *
     * @param read the text of a file below the data root, or empty when it is missing
     * @throws IllegalStateException when the tag or a vein it names is missing
     */
    static List<ClusterVeinConfig> load(Function<String, Optional<String>> read) {
        final List<ClusterVeinConfig> out = new ArrayList<>();
        for (VeinConfig config : loadAll(read)) {
            if (config instanceof ClusterVeinConfig c) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    /** Pure: every vein of the vein tag (cluster, disc and pipe veins), in tag order. */
    static List<VeinConfig> loadAll(Function<String, Optional<String>> read) {
        return tag(read, VeinData.VEIN_TAG);
    }

    /**
     * Pure: the veins of the biome vein tags that replace rock ({@link VeinData#BIOME_VEIN_TAGS}: the second pass of the
     * montane veins, the marble belt of the collisional mountains, the river gems), by tag, for
     * {@link dev.tfp.core.worldgen.ore.OreVeins}.
     */
    static Map<String, List<VeinConfig>> biomeVeins(Function<String, Optional<String>> read) {
        final Map<String, List<VeinConfig>> out = new LinkedHashMap<>();
        for (String key : VeinData.BIOME_VEIN_TAGS) {
            out.put(key, tag(read, VeinData.BIOME_VEIN_TAG_DIR + key + ".json"));
        }
        return out;
    }

    /** Pure: the kaolin disc of the kaolin biome tag, for {@link dev.tfp.core.worldgen.ore.KaolinDisc}. */
    static DiscVeinConfig kaolin(Function<String, Optional<String>> read) {
        for (VeinConfig config : tag(read, VeinData.BIOME_VEIN_TAG_DIR + VeinData.KAOLIN_TAG + ".json")) {
            if (config instanceof DiscVeinConfig disc && disc.kaolin()) {
                return disc;
            }
        }
        throw new IllegalStateException("the kaolin vein tag holds no kaolin disc");
    }

    /**
     * The types the terrain can write today: those that need no view of the world (not projected, not near lava) and whose
     * blocks are all ores or deposits. A type that writes rock, gravel or a plain block (the dikes, the gravel disc) is held
     * back whole, because the veins after it must see its blocks, and the terrain's palette holds only rocks and ores.
     */
    static List<VeinConfig> placeable(List<VeinConfig> all) {
        final List<VeinConfig> out = new ArrayList<>();
        for (VeinConfig config : all) {
            if (!config.needsWorld() && !config.blocks().isEmpty() && onlyOres(config)) {
                out.add(config);
            }
        }
        return List.copyOf(out);
    }

    private static boolean onlyOres(VeinConfig config) {
        for (Weighted<VeinBlock> weighted : config.blocks().values()) {
            for (VeinBlock block : weighted.values()) {
                if (block.kind() == VeinBlock.Kind.OTHER) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<VeinConfig> tag(Function<String, Optional<String>> read, String tagPath) {
        final List<VeinConfig> out = new ArrayList<>();
        for (String name : VeinData.tagOrder(json(read, tagPath))) {
            out.add(VeinData.parseAny(name, json(read, VeinData.FEATURE_DIR + name + ".json")));
        }
        return List.copyOf(out);
    }

    private static JsonObject json(Function<String, Optional<String>> read, String path) {
        final String text = read.apply(path).orElseThrow(() -> new IllegalStateException(
            "ore vein data " + path + " is missing (run tools/datagen)"));
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
