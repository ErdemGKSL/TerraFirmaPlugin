/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/tree/ForestConfig.java @ c3b4d9536 (the codecs: field names and defaults),
 * with the codecs of RandomTreeConfig, OverlayTreeConfig, StackedTreeConfig, KrummholzConfig, TrunkConfig, RootConfig and
 * TreePlacementConfig in the same package, src/main/java/net/dries007/tfc/world/placement/ClimatePlacement.java (CODEC) and
 * src/main/java/net/dries007/tfc/world/Codecs.java
 * (BLOCK_TO_WEIGHTED_BLOCKSTATE, weightedCodec)
 */
package dev.tfp.core.worldgen.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.worldgen.ore.Weighted;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Reads the tree data datagen exports from TFC: the forest tag {@code data/tfp/tags/worldgen/configured_feature/forest_trees.json},
 * the forest entries and tree features under {@code data/tfp/worldgen/configured_feature/tree/} and the structure templates
 * under {@code data/tfp/worldgen/tree_structure/}. Pure; throws {@link IllegalArgumentException} on data that does not follow
 * TFC's codecs or uses a part that is not ported (the mangrove's special root placer). Not thread-safe while loading.
 */
public final class TreeData {
    /** The forest entry tag of {@code tfc:forest}, below a data root. */
    public static final String FOREST_TAG = "tfp/tags/worldgen/configured_feature/forest_trees.json";
    /** The configured tree features, below a data root. */
    public static final String FEATURE_DIR = "tfp/worldgen/configured_feature/";
    /** The tree structure templates, below a data root. */
    public static final String STRUCTURE_DIR = "tfp/worldgen/tree_structure/";
    private static final String NS = "tfp:";

    private final Function<String, Optional<String>> read;
    private final Map<String, TreeFeature> features = new HashMap<>();
    private final Map<String, TreeTemplate> templates = new HashMap<>();

    private TreeData(Function<String, Optional<String>> read) {
        this.read = read;
    }

    /**
     * The entries of the forest tag, in tag order, with their features and templates.
     *
     * @param read the text of a file below the data root, or empty when it is missing
     */
    public static List<ForestEntry> forestEntries(Function<String, Optional<String>> read) {
        final TreeData data = new TreeData(read);
        final List<ForestEntry> out = new ArrayList<>();
        for (JsonElement e : array(data.json(FOREST_TAG), "values")) {
            out.add(data.entry(e.getAsString()));
        }
        return List.copyOf(out);
    }

    /** One template by id ({@code tfp:oak/1}), for tests and tools. */
    public static TreeTemplate template(Function<String, Optional<String>> read, String id) {
        return new TreeData(read).template(id);
    }

    /** A forest entry ({@code tfp:forest_entry}) by id. */
    ForestEntry entry(String id) {
        final JsonObject root = json(featurePath(id));
        expectType(root, "tfp:forest_entry", id);
        final JsonObject c = object(root, "config");
        return new ForestEntry(id, climate(object(c, "climate")),
            feature(string(c, "normal_tree")), feature(string(c, "dead_tree")),
            optionalString(c, "old_growth_tree").map(this::feature), optionalString(c, "krummholz").map(this::feature),
            optionalInt(c, "old_growth_chance", ForestEntry.DEFAULT_OLD_GROWTH_CHANCE),
            optionalInt(c, "spoiler_old_growth_chance", ForestEntry.DEFAULT_SPOILER_OLD_GROWTH_CHANCE),
            optionalInt(c, "dead_chance", ForestEntry.DEFAULT_DEAD_CHANCE),
            c.has("floating") && c.get("floating").getAsBoolean());
    }

    /** A tree feature by id, read once. */
    TreeFeature feature(String id) {
        final TreeFeature cached = features.get(id);
        if (cached != null) {
            return cached;
        }
        final JsonObject root = json(featurePath(id));
        final JsonObject c = object(root, "config");
        final Wood wood = woodOf(id);
        final TreeFeature out = switch (string(root, "type")) {
            case "tfp:random_tree" -> {
                final List<TreeTemplate> structures = new ArrayList<>();
                for (JsonElement e : array(c, "structures")) {
                    structures.add(template(e.getAsString()));
                }
                yield new TreeFeature.RandomTree(wood, structures, trunk(c), placement(object(c, "placement")), roots(c));
            }
            case "tfp:overlay_tree" -> new TreeFeature.OverlayTree(wood, template(string(c, "base")), template(string(c, "overlay")),
                trunk(c), c.has("overlay_integrity") ? c.get("overlay_integrity").getAsFloat() : 0.5f,
                placement(object(c, "placement")), roots(c));
            case "tfp:stacked_tree" -> {
                final List<TreeFeature.Layer> layers = new ArrayList<>();
                for (JsonElement e : array(c, "layers")) {
                    final JsonObject l = e.getAsJsonObject();
                    final List<TreeTemplate> ts = new ArrayList<>();
                    for (JsonElement t : array(l, "templates")) {
                        ts.add(template(t.getAsString()));
                    }
                    layers.add(new TreeFeature.Layer(ts, l.get("min_count").getAsInt(), l.get("max_count").getAsInt()));
                }
                yield new TreeFeature.StackedTree(wood, layers, trunk(c).orElseThrow(() -> new IllegalArgumentException(
                    "stacked tree without trunk: " + id)), placement(object(c, "placement")), roots(c));
            }
            case "tfp:krummholz" -> {
                final JsonElement h = c.get("height");
                final int min;
                final int max;
                if (h.isJsonPrimitive()) {
                    min = max = h.getAsInt();
                } else {
                    final JsonObject ho = h.getAsJsonObject();
                    final String type = string(ho, "type");
                    if (type.equals("constant") || type.equals("minecraft:constant")) {
                        min = max = ho.get("value").getAsInt();
                    } else if (type.equals("uniform") || type.equals("minecraft:uniform")) {
                        min = ho.get("min_inclusive").getAsInt();
                        max = ho.get("max_inclusive").getAsInt();
                    } else {
                        throw new IllegalArgumentException("unsupported krummholz height provider " + type + " in " + id);
                    }
                }
                if (min == max) {
                    // A ConstantInt draws nothing; TFC's krummholz all use uniform heights, so the feature only models those
                    throw new IllegalArgumentException("constant krummholz height is not supported: " + id);
                }
                yield new TreeFeature.Krummholz(woodOf(string(c, "block")), min, max,
                    c.has("spawns_on_stone") && c.get("spawns_on_stone").getAsBoolean(),
                    c.has("spawns_on_gravel") && c.get("spawns_on_gravel").getAsBoolean());
            }
            default -> throw new IllegalArgumentException("unsupported tree feature type " + string(root, "type") + " in " + id);
        };
        features.put(id, out);
        return out;
    }

    /** A structure template by id ({@code tfp:<tree>/<n>}), read once. */
    TreeTemplate template(String id) {
        final TreeTemplate cached = templates.get(id);
        if (cached != null) {
            return cached;
        }
        final JsonObject root = json(STRUCTURE_DIR + path(id) + ".json");
        final JsonArray size = array(root, "size");
        final JsonArray palette = array(root, "palette");
        final List<TreeTemplate.Block> paletteBlocks = new ArrayList<>();
        for (JsonElement e : palette) {
            final JsonObject state = e.getAsJsonObject();
            final String name = string(state, "Name");
            final JsonObject props = state.has("Properties") ? state.getAsJsonObject("Properties") : new JsonObject();
            final TreeBlock.Kind kind;
            if (name.startsWith(NS + "wood/log/")) {
                kind = TreeBlock.Kind.LOG;
            } else if (name.startsWith(NS + "wood/wood/")) {
                kind = TreeBlock.Kind.WOOD;
            } else if (name.startsWith(NS + "wood/leaves/")) {
                kind = TreeBlock.Kind.LEAVES;
            } else {
                throw new IllegalArgumentException("unsupported block " + name + " in template " + id);
            }
            final Wood wood = Wood.byName(name.substring(name.lastIndexOf('/') + 1));
            final TreeBlock.Axis axis = props.has("axis") ? TreeBlock.Axis.valueOf(props.get("axis").getAsString().toUpperCase(Locale.ROOT))
                : TreeBlock.Axis.Y;
            final BranchDirection branch = props.has("branch_direction")
                ? BranchDirection.byName(props.get("branch_direction").getAsString()) : BranchDirection.NONE;
            paletteBlocks.add(new TreeTemplate.Block(0, 0, 0, kind, wood, axis, branch));
        }
        final List<TreeTemplate.Block> blocks = new ArrayList<>();
        for (JsonElement e : array(root, "blocks")) {
            final JsonArray b = e.getAsJsonArray();
            final TreeTemplate.Block s = paletteBlocks.get(b.get(3).getAsInt());
            blocks.add(new TreeTemplate.Block(b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt(), s.kind(), s.wood(), s.axis(),
                s.branch()));
        }
        final TreeTemplate out = TreeTemplate.of(id, size.get(0).getAsInt(), size.get(1).getAsInt(), size.get(2).getAsInt(), blocks);
        templates.put(id, out);
        return out;
    }

    private static ForestEntry.Climate climate(JsonObject c) {
        return new ForestEntry.Climate(
            optionalFloat(c, "min_temperature", Float.NEGATIVE_INFINITY), optionalFloat(c, "max_temperature", Float.POSITIVE_INFINITY),
            optionalFloat(c, "min_groundwater", Float.NEGATIVE_INFINITY), optionalFloat(c, "max_groundwater", Float.POSITIVE_INFINITY),
            optionalFloat(c, "min_rain_variance", -1f), optionalFloat(c, "max_rain_variance", 1f),
            c.has("rain_variance_absolute") && c.get("rain_variance_absolute").getAsBoolean(),
            optionalInt(c, "min_elevation", -64), optionalInt(c, "max_elevation", 320));
    }

    private static Optional<TreeFeature.Trunk> trunk(JsonObject c) {
        if (!c.has("trunk")) {
            return Optional.empty();
        }
        final JsonObject t = c.getAsJsonObject("trunk");
        final JsonObject state = object(t, "state");
        final String name = string(state, "Name");
        if (!name.startsWith(NS + "wood/log/")) {
            throw new IllegalArgumentException("unsupported trunk block " + name);
        }
        final JsonObject props = state.has("Properties") ? state.getAsJsonObject("Properties") : new JsonObject();
        final TreeBlock.Axis axis = props.has("axis") ? TreeBlock.Axis.valueOf(props.get("axis").getAsString().toUpperCase(Locale.ROOT))
            : TreeBlock.Axis.Y;
        return Optional.of(new TreeFeature.Trunk(Wood.byName(name.substring(name.lastIndexOf('/') + 1)), axis,
            t.get("min_height").getAsInt(), t.get("max_height").getAsInt(), t.get("wide").getAsBoolean()));
    }

    private static TreeFeature.Placement placement(JsonObject p) {
        final TreeFeature.GroundType ground = p.has("ground_type")
            ? TreeFeature.GroundType.valueOf(p.get("ground_type").getAsString().toUpperCase(Locale.ROOT)) : TreeFeature.GroundType.NORMAL;
        return new TreeFeature.Placement(p.get("width").getAsInt(), p.get("height").getAsInt(), ground);
    }

    /**
     * {@code RootConfig}: the replace map keyed by the ground kind ({@code grass}, {@code dirt}, {@code mud}). TFC lists one
     * entry per soil that keeps the soil; the terrain has no soil variants, so the entries of one kind must agree.
     */
    private static Optional<TreeFeature.Roots> roots(JsonObject c) {
        if (!c.has("root_system")) {
            return Optional.empty();
        }
        final JsonObject r = c.getAsJsonObject("root_system");
        if (r.has("special_placer")) {
            throw new IllegalArgumentException("the special root placer is not ported");
        }
        final Map<TreeGround, String> signature = new EnumMap<>(TreeGround.class);
        final Map<TreeGround, Weighted<TreeBlock.Kind>> blocks = new EnumMap<>(TreeGround.class);
        for (JsonElement e : array(r, "blocks")) {
            final JsonObject entry = e.getAsJsonObject();
            final List<TreeBlock.Kind> kinds = new ArrayList<>();
            final List<Double> weights = new ArrayList<>();
            for (JsonElement w : array(entry, "with")) {
                final JsonObject wo = w.getAsJsonObject();
                kinds.add(rootKind(string(wo, "block")));
                weights.add(wo.has("weight") ? wo.get("weight").getAsDouble() : 1.0);
            }
            final String sig = kinds + "/" + weights;
            for (JsonElement rep : array(entry, "replace")) {
                final TreeGround ground = rootGround(rep.getAsString());
                final String previous = signature.putIfAbsent(ground, sig);
                if (previous != null && !previous.equals(sig)) {
                    throw new IllegalArgumentException("roots replace " + ground + " differently per soil: " + previous + " vs " + sig);
                }
                blocks.put(ground, Weighted.of(kinds, weights));
            }
        }
        return Optional.of(new TreeFeature.Roots(blocks, optionalInt(r, "width", 4), optionalInt(r, "height", 3),
            optionalInt(r, "tries", 5), r.has("required") && r.get("required").getAsBoolean()));
    }

    private static TreeGround rootGround(String id) {
        if (id.startsWith(NS + "grass/")) {
            return TreeGround.GRASS;
        } else if (id.startsWith(NS + "dirt/")) {
            return TreeGround.DIRT;
        } else if (id.startsWith(NS + "mud/")) {
            return TreeGround.MUD;
        }
        throw new IllegalArgumentException("unsupported root replace block " + id);
    }

    private static TreeBlock.Kind rootKind(String id) {
        if (id.startsWith(NS + "rooted_dirt/")) {
            return TreeBlock.Kind.ROOTED_DIRT;
        } else if (id.startsWith(NS + "muddy_roots/")) {
            return TreeBlock.Kind.MUDDY_ROOTS;
        }
        throw new IllegalArgumentException("unsupported root block " + id);
    }

    /**
     * The wood a feature id names: the longest wood name that the last path segment starts with ({@code tfp:tree/douglas_fir_large},
     * {@code tfp:plant/aspen_krummholz}).
     */
    static Wood woodOf(String id) {
        final String last = id.substring(id.lastIndexOf('/') + 1);
        Wood best = null;
        for (Wood wood : Wood.values()) {
            final String n = wood.serializedName();
            if ((last.equals(n) || last.startsWith(n + "_")) && (best == null || n.length() > best.serializedName().length())) {
                best = wood;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("no wood in feature id " + id);
        }
        return best;
    }

    private static String featurePath(String id) {
        return FEATURE_DIR + path(id) + ".json";
    }

    private static String path(String id) {
        if (!id.startsWith(NS)) {
            throw new IllegalArgumentException("not a " + NS + " id: " + id);
        }
        return id.substring(NS.length());
    }

    private JsonObject json(String path) {
        final String text = read.apply(path).orElseThrow(() -> new IllegalArgumentException(
            "tree data " + path + " is missing (run tools/datagen)"));
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static void expectType(JsonObject root, String type, String id) {
        if (!type.equals(string(root, "type"))) {
            throw new IllegalArgumentException(id + " is not a " + type);
        }
    }

    private static String string(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing string " + key);
        }
        return o.get(key).getAsString();
    }

    private static Optional<String> optionalString(JsonObject o, String key) {
        return o.has(key) ? Optional.of(o.get(key).getAsString()) : Optional.empty();
    }

    private static int optionalInt(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    private static float optionalFloat(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }

    private static JsonObject object(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonObject()) {
            throw new IllegalArgumentException("missing object " + key);
        }
        return o.getAsJsonObject(key);
    }

    private static JsonArray array(JsonObject o, String key) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            throw new IllegalArgumentException("missing array " + key);
        }
        return o.getAsJsonArray(key);
    }
}
