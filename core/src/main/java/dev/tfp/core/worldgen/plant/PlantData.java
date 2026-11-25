/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/placement/ClimatePlacement.java @ c3b4d9536 (CODEC: field names and defaults),
 * with the codecs of IntertidalPlacement, ShallowWaterPlacement, WouldSurviveWithFluidPredicate, RandomPropertyProvider,
 * BlockConfig (world/feature) and NoisyMultipleFeature's SimpleRandomFeatureConfiguration, and Minecraft 1.21's codecs of
 * RarityFilter, CountPlacement, NoiseBasedCountPlacement, HeightmapPlacement, BlockPredicateFilter, WouldSurvivePredicate,
 * RandomPatchConfiguration (defaults 128, 7, 3) and SimpleBlockConfiguration; PAR-WORLD-13: the codecs of CreepingPlantConfig,
 * ColumnPlantConfig, BlockConfig, NearFluidPlacement, BoundedCarvingMaskPlacement, CenterOrDistanceToPlacement and
 * DryReplaceablePredicate, and Minecraft 1.21's HeightRangePlacement (uniform heights), MatchingBlocksPredicate,
 * DualNoiseProvider and BlockStateConfiguration
 */
package dev.tfp.core.worldgen.plant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.tree.ForestType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Reads the plant, wild crop and coral features datagen exports from TFC ({@code data/tfp/worldgen/{placed,configured}_feature}
 * and the placed feature and block tags) into {@link PlantFeature}s. A feature that uses a part the port does not run (a
 * plant type whose survival is not ported, a feature type such as vines or kelp, a modifier such as {@code tfc:atoll}) is
 * left out and listed in {@link #skipped()} with the reason; malformed data throws {@link IllegalArgumentException}. Not
 * thread-safe while loading.
 */
public final class PlantData {
    public static final String PLACED_DIR = "tfp/worldgen/placed_feature/";
    public static final String CONFIGURED_DIR = "tfp/worldgen/configured_feature/";
    public static final String PLACED_TAG_DIR = "tfp/tags/worldgen/placed_feature/";
    /** The biome feature lists of the decoration steps, below {@link #PLACED_TAG_DIR}. */
    public static final String SURFACE_DECORATION = "in_biome/surface_decoration/";
    public static final String LARGE_FEATURES = "in_biome/large_features/";

    /** A part of a feature that the port does not run. */
    static final class Unsupported extends RuntimeException {
        Unsupported(String message) {
            super(message, null, false, false);
        }
    }

    private final Function<String, Optional<String>> read;
    private final Map<String, PlantFeature.Placed> placed = new HashMap<>();
    private final Map<String, String> skipped = new LinkedHashMap<>();

    private PlantData(Function<String, Optional<String>> read) {
        this.read = read;
    }

    /** @param read the text of a file below the data root, or empty when it is missing */
    public static PlantData of(Function<String, Optional<String>> read) {
        return new PlantData(read);
    }

    /** The features left out, by placed feature id, with the reason. */
    public Map<String, String> skipped() {
        return skipped;
    }

    /**
     * The supported placed features of a biome's decoration step, in the order of its tag, nested tags expanded. Missing
     * lists are empty.
     *
     * @param stepDir {@link #SURFACE_DECORATION} or {@link #LARGE_FEATURES}
     * @param biome   the biome's name ({@code plains})
     */
    public List<PlantFeature.Placed> biomeFeatures(String stepDir, String biome) {
        final List<PlantFeature.Placed> out = new ArrayList<>();
        for (String id : tagValues(PlantState.NS + stepDir + biome)) {
            final PlantFeature.Placed p = supported(id);
            if (p != null) {
                out.add(p);
            }
        }
        return List.copyOf(out);
    }

    /** A placed feature by id, or null (recorded in {@link #skipped()}) when it uses a part the port does not run. */
    public PlantFeature.Placed supported(String id) {
        if (skipped.containsKey(id)) {
            return null;
        }
        try {
            return placed(id);
        } catch (Unsupported e) {
            skipped.put(id, e.getMessage());
            return null;
        }
    }

    /** The values of a placed feature tag ({@code tfp:feature/land_plants}), nested tags expanded in place. */
    public List<String> tagValues(String tag) {
        final List<String> out = new ArrayList<>();
        final Optional<String> text = read.apply(PLACED_TAG_DIR + path(tag) + ".json");
        if (text.isEmpty()) {
            return out;
        }
        for (JsonElement e : array(parse(text.get(), tag), "values")) {
            final String value = e.isJsonObject() ? string(e.getAsJsonObject(), "id") : e.getAsString();
            if (value.startsWith("#")) {
                out.addAll(tagValues(value.substring(1)));
            } else {
                out.add(value);
            }
        }
        return out;
    }

    /**
     * The block ids of a block tag ({@code tfp:single_block_replaceable}), nested tags expanded; a tag the data does not define
     * (a vanilla or common tag without TFC members) is empty.
     */
    public Set<String> blockTag(String tag) {
        final Set<String> out = new HashSet<>();
        final int colon = tag.indexOf(':');
        final Optional<String> text = read.apply(tag.substring(0, colon) + "/tags/block/" + tag.substring(colon + 1) + ".json");
        if (text.isEmpty()) {
            return out;
        }
        for (JsonElement e : array(parse(text.get(), tag), "values")) {
            final String value = e.isJsonObject() ? string(e.getAsJsonObject(), "id") : e.getAsString();
            if (value.startsWith("#")) {
                out.addAll(blockTag(value.substring(1)));
            } else {
                out.add(value);
            }
        }
        return out;
    }

    /** A placed feature by id; throws {@link Unsupported} for a part the port does not run. */
    PlantFeature.Placed placed(String id) {
        final PlantFeature.Placed cached = placed.get(id);
        if (cached != null) {
            return cached;
        }
        final JsonObject root = json(PLACED_DIR + path(id) + ".json", id);
        final List<PlantFeature.Modifier> modifiers = new ArrayList<>();
        for (JsonElement e : array(root, "placement")) {
            modifiers.add(modifier(e.getAsJsonObject()));
        }
        final JsonElement feature = root.get("feature");
        if (feature == null || !feature.isJsonPrimitive()) {
            throw new Unsupported("inline configured feature in " + id);
        }
        final PlantFeature.Placed out = new PlantFeature.Placed(id, modifiers, configured(feature.getAsString()));
        placed.put(id, out);
        return out;
    }

    private PlantFeature.Configured configured(String id) {
        final JsonObject root = json(CONFIGURED_DIR + path(id) + ".json", id);
        final String type = type(root);
        final JsonObject c = root.has("config") ? root.getAsJsonObject("config") : new JsonObject();
        return switch (type) {
            case "minecraft:random_patch", "tfp:dynamic_random_patch" -> {
                final JsonElement inner = c.get("feature");
                if (inner == null || !inner.isJsonPrimitive()) {
                    throw new Unsupported("inline patch feature in " + id);
                }
                yield new PlantFeature.Patch(type.equals("tfp:dynamic_random_patch"), optionalInt(c, "tries", 128),
                    optionalInt(c, "xz_spread", 7), optionalInt(c, "y_spread", 3), placed(inner.getAsString()));
            }
            case "minecraft:simple_block", "tfp:block_with_fluid" -> {
                final JsonObject provider = object(c, "to_place");
                final String providerType = type(provider);
                final boolean random = switch (providerType) {
                    case "minecraft:dual_noise_provider" -> {
                        // One state for every noise value: DualNoiseProvider draws nothing and always gives it
                        final JsonArray states = array(provider, "states");
                        for (JsonElement e : states) {
                            if (!e.equals(states.get(0))) {
                                throw new Unsupported("dual noise provider of several states in " + id);
                            }
                        }
                        yield false;
                    }
                    case "tfp:random_property" -> {
                        if (!"age".equals(string(provider, "property"))) {
                            throw new Unsupported("random property " + string(provider, "property") + " in " + id);
                        }
                        yield true;
                    }
                    case "minecraft:simple_state_provider" -> false;
                    default -> throw new Unsupported("state provider " + providerType + " in " + id);
                };
                final JsonObject stateJson = providerType.equals("minecraft:dual_noise_provider")
                    ? array(provider, "states").get(0).getAsJsonObject() : object(provider, "state");
                yield new PlantFeature.SimpleBlock(state(stateJson), random, type.equals("tfp:block_with_fluid"));
            }
            case "tfp:tall_plant" -> {
                final PlantState s = blockState(string(c, "block"));
                if (s.kind() != PlantState.Kind.PLANT || !s.type().isTall()) {
                    throw new IllegalArgumentException("tall_plant needs a tall plant: " + id);
                }
                yield new PlantFeature.TallPlant(s);
            }
            case "tfp:emergent_plant", "tfp:submerged_tall_plant" -> {
                final PlantState s = blockState(string(c, "block"));
                if (s.kind() != PlantState.Kind.PLANT || s.type().fluidProperty() == null || !s.type().isTall()) {
                    throw new IllegalArgumentException(type + " needs a tall water plant: " + id);
                }
                yield type.equals("tfp:emergent_plant") ? new PlantFeature.EmergentPlant(s) : new PlantFeature.SubmergedTallPlant(s);
            }
            case "tfp:tall_wild_crop" -> {
                final PlantState s = blockState(string(c, "block"));
                if (WildCrop.of(s.block()) != WildCrop.DOUBLE) {
                    throw new IllegalArgumentException("tall_wild_crop needs a double wild crop: " + id);
                }
                yield new PlantFeature.TallWildCrop(s);
            }
            case "tfp:spreading_crop" -> {
                final PlantState s = blockState(string(c, "block"));
                if (WildCrop.of(s.block()) != WildCrop.SPREADING) {
                    throw new IllegalArgumentException("spreading_crop needs a spreading wild crop: " + id);
                }
                yield new PlantFeature.SpreadingCrop(s);
            }
            case "tfp:noisy_multiple" -> {
                final JsonElement features = c.get("features");
                final List<String> ids = new ArrayList<>();
                if (features != null && features.isJsonPrimitive() && features.getAsString().startsWith("#")) {
                    ids.addAll(tagValues(features.getAsString().substring(1)));
                } else if (features != null && features.isJsonArray()) {
                    for (JsonElement e : features.getAsJsonArray()) {
                        ids.add(e.getAsString());
                    }
                } else {
                    throw new IllegalArgumentException("noisy_multiple needs features: " + id);
                }
                final List<PlantFeature.Placed> list = new ArrayList<>();
                for (String inner : ids) {
                    list.add(placed(inner));
                }
                yield new PlantFeature.NoisyMultiple(list);
            }
            case "tfp:creeping_plant", "tfp:creeping_ocean_plant" -> {
                final PlantState block = blockState(string(c, "block"));
                final boolean ocean = type.equals("tfp:creeping_ocean_plant");
                if (block.kind() != PlantState.Kind.PLANT || !creeping(block.type(), ocean)) {
                    throw new IllegalArgumentException(type + " needs a creeping plant: " + id);
                }
                yield new PlantFeature.Creeping(block.withAge(-1), c.get("radius").getAsInt(), c.get("height").getAsInt(),
                    c.get("tide_height").getAsInt(), optionalFloat(c, "integrity", 1f), ocean);
            }
            case "tfp:epiphyte_plant", "tfp:rotatable_water_plant" -> {
                final PlantState block = blockState(string(c, "block"));
                final boolean rotatable = type.equals("tfp:rotatable_water_plant");
                final PlantType expected = rotatable ? PlantType.OCEAN_ROTATABLE : PlantType.EPIPHYTE;
                if (block.kind() != PlantState.Kind.PLANT || block.type() != expected) {
                    throw new IllegalArgumentException(type + " needs a " + expected + " plant: " + id);
                }
                yield new PlantFeature.Attached(block.withAge(-1), rotatable);
            }
            case "tfp:twisting_vines", "tfp:weeping_vines", "tfp:kelp" -> {
                final PlantFeature.ColumnKind kind = switch (type) {
                    case "tfp:twisting_vines" -> PlantFeature.ColumnKind.TWISTING;
                    case "tfp:weeping_vines" -> PlantFeature.ColumnKind.WEEPING;
                    default -> PlantFeature.ColumnKind.KELP;
                };
                final PlantState body = blockState(string(c, "body"));
                final PlantState head = blockState(string(c, "head"));
                if (body.kind() != PlantState.Kind.PLANT || head.kind() != PlantState.Kind.PLANT) {
                    throw new IllegalArgumentException(type + " needs plant blocks: " + id);
                }
                // The body blocks have no age; the head's is drawn when it is placed
                yield new PlantFeature.Column(kind, body.withAge(-1), head.withAge(0), c.get("tries").getAsInt(),
                    c.get("radius").getAsInt(), c.get("min_height").getAsInt(), c.get("max_height").getAsInt());
            }
            case "tfp:vines" -> {
                final PlantState block = state(object(c, "state"));
                if (block.kind() != PlantState.Kind.PLANT || block.type() != PlantType.VINE) {
                    throw new IllegalArgumentException("vines needs a vine: " + id);
                }
                yield new PlantFeature.Vines(block.withAge(-1).withExtra(0));
            }
            case "tfp:kelp_tree" -> {
                final String flower = string(c, "block");
                final PlantState state = blockState(flower);
                if (state.type() != PlantType.KELP_TREE_FLOWER) {
                    throw new IllegalArgumentException("kelp_tree needs a kelp tree flower: " + id);
                }
                // Plant.transform: giant_kelp_flower grows on giant_kelp_plant
                final String body = flower.replace("_flower", "_plant");
                yield new PlantFeature.GiantKelp(state.withAge(0), blockState(body).withAge(-1).withExtra(0));
            }
            case "tfp:branching_cactus" -> {
                final String head = string(c, "block");
                final PlantState state = blockState(head);
                if (state.type() != PlantType.BRANCHING_CACTUS_TOP) {
                    throw new IllegalArgumentException("branching_cactus needs a growing branching cactus: " + id);
                }
                // Plant.transform and secondTransform: saguaro grows saguaro_plant and flowers saguaro_fruit;
                // GROWS_BRANCHES (bit 6) is true by default
                yield new PlantFeature.BranchingCactus(state.withAge(-1).withExtra(64), blockState(head + "_plant").withAge(-1),
                    blockState(head + "_fruit").withAge(-1));
            }
            case "tfp:coral_tree" -> new PlantFeature.Coral(Corals.Shape.TREE);
            case "tfp:coral_mushroom" -> new PlantFeature.Coral(Corals.Shape.MUSHROOM);
            case "tfp:coral_claw" -> new PlantFeature.Coral(Corals.Shape.CLAW);
            default -> throw new Unsupported("feature type " + type + " (" + id + ")");
        };
    }

    private PlantFeature.Modifier modifier(JsonObject m) {
        final String type = type(m);
        return switch (type) {
            case "minecraft:rarity_filter" -> new PlantFeature.Rarity(optionalInt(m, "chance", 1));
            case "minecraft:in_square" -> new PlantFeature.InSquare();
            case "minecraft:heightmap" -> switch (string(m, "heightmap")) {
                case "OCEAN_FLOOR_WG" -> new PlantFeature.Heightmap(true);
                case "WORLD_SURFACE_WG" -> new PlantFeature.Heightmap(false);
                default -> throw new Unsupported("heightmap " + string(m, "heightmap"));
            };
            case "tfp:climate" -> new PlantFeature.Climate(climate(m), forestTypes(m));
            case "minecraft:height_range" -> heightRange(object(m, "height"));
            case "tfp:near_fluid" -> new PlantFeature.NearFluid(optionalInt(m, "radius", 2), fluids(m));
            case "tfp:carving_mask" -> {
                if (!"air".equals(string(m, "step"))) {
                    throw new Unsupported("carving mask step " + string(m, "step"));
                }
                yield new PlantFeature.CarvingMask(m.has("min_y") ? absolute(object(m, "min_y")) : Integer.MIN_VALUE,
                    m.has("max_y") ? absolute(object(m, "max_y")) : Integer.MAX_VALUE);
            }
            case "tfp:atoll" -> {
                if (m.has("center") && m.get("center").getAsBoolean()) {
                    throw new Unsupported("atoll center placement");
                }
                yield new PlantFeature.Atoll(optionalFloat(m, "min_easing", 0f), optionalFloat(m, "max_easing", 1f));
            }
            case "minecraft:count" -> count(m.get("count"));
            case "minecraft:noise_based_count" -> new PlantFeature.NoiseCount(m.get("noise_to_count_ratio").getAsInt(),
                m.get("noise_factor").getAsDouble(), m.has("noise_offset") ? m.get("noise_offset").getAsDouble() : 0.0);
            case "minecraft:block_predicate_filter" -> new PlantFeature.Filter(predicate(object(m, "predicate")));
            case "tfp:intertidal" -> new PlantFeature.Intertidal(m.get("min_elevation").getAsInt(), m.get("max_elevation").getAsInt());
            case "tfp:no_solid_neighbors" -> new PlantFeature.NoSolidNeighbors();
            case "tfp:shallow_water" -> new PlantFeature.ShallowWater(optionalInt(m, "min_depth", 0), optionalInt(m, "max_depth", 5));
            default -> throw new Unsupported("placement " + type);
        };
    }

    private static PlantFeature.Count count(JsonElement count) {
        if (count == null) {
            throw new IllegalArgumentException("count without a count");
        }
        if (count.isJsonPrimitive()) {
            return new PlantFeature.Count(count.getAsInt(), count.getAsInt());
        }
        final JsonObject o = count.getAsJsonObject();
        final String type = type(o);
        final JsonObject v = o.has("value") ? o.getAsJsonObject("value") : o;
        return switch (type) {
            case "minecraft:constant" -> new PlantFeature.Count(v.get("value").getAsInt(), v.get("value").getAsInt());
            case "minecraft:uniform" -> new PlantFeature.Count(v.get("min_inclusive").getAsInt(), v.get("max_inclusive").getAsInt());
            default -> throw new Unsupported("count provider " + type);
        };
    }

    private PlantFeature.Predicate predicate(JsonObject p) {
        final String type = type(p);
        if (p.has("offset")) {
            throw new Unsupported("predicate offset in " + type);
        }
        return switch (type) {
            case "tfp:air_or_empty_fluid" -> new PlantFeature.AirOrEmptyFluid();
            case "tfp:replaceable" -> new PlantFeature.Replaceable();
            case "minecraft:would_survive" -> new PlantFeature.WouldSurvive(state(object(p, "state")));
            case "tfp:would_survive_with_fluid" -> new PlantFeature.WouldSurviveWithFluid(state(object(p, "state")));
            case "tfp:dry_replaceable" -> new PlantFeature.DryReplaceable();
            case "minecraft:matching_blocks" -> {
                final JsonElement blocks = p.get("blocks");
                if (blocks == null || !blocks.isJsonArray() || blocks.getAsJsonArray().size() != 1
                    || !"tfp:fluid/salt_water".equals(blocks.getAsJsonArray().get(0).getAsString())) {
                    throw new Unsupported("matching blocks " + blocks);
                }
                yield new PlantFeature.SaltWaterBlock();
            }
            default -> throw new Unsupported("block predicate " + type);
        };
    }

    /** Whether a plant type is placed by {@code tfc:creeping_plant} or (ocean) {@code tfc:creeping_ocean_plant}. */
    private static boolean creeping(PlantType type, boolean ocean) {
        return ocean ? type == PlantType.OCEAN_CREEPING || type == PlantType.OCEAN_ROCK_CREEPING
            : type == PlantType.CREEPING || type == PlantType.CREEPING_STONE;
    }

    /** {@code ClimatePlacement.CODEC}'s {@code forest_types}: empty when absent. */
    static Set<ForestType> forestTypes(JsonObject m) {
        final Set<ForestType> out = EnumSet.noneOf(ForestType.class);
        if (m.has("forest_types")) {
            for (JsonElement e : m.getAsJsonArray("forest_types")) {
                out.add(ForestType.valueOf(e.getAsString().toUpperCase(Locale.ROOT)));
            }
        }
        return out;
    }

    /** {@code minecraft:height_range} of a uniform height between absolute anchors. */
    private static PlantFeature.HeightRange heightRange(JsonObject height) {
        if (!"minecraft:uniform".equals(type(height))) {
            throw new Unsupported("height provider " + type(height));
        }
        return new PlantFeature.HeightRange(absolute(object(height, "min_inclusive")), absolute(object(height, "max_inclusive")));
    }

    /** A {@code VerticalAnchor} that is {@code {"absolute": y}}. */
    private static int absolute(JsonObject anchor) {
        if (!anchor.has("absolute")) {
            throw new Unsupported("vertical anchor " + anchor);
        }
        return anchor.get("absolute").getAsInt();
    }

    /** {@code NearFluidPlacement}'s fluids: the ones the terrain has (salt and fresh water; spring water is never there). */
    private static Set<PlantState.Fluid> fluids(JsonObject m) {
        final Set<PlantState.Fluid> out = EnumSet.noneOf(PlantState.Fluid.class);
        if (!m.has("fluids")) {
            out.add(PlantState.Fluid.WATER);
            out.add(PlantState.Fluid.SALT_WATER);
            return out;
        }
        for (JsonElement e : m.getAsJsonArray("fluids")) {
            switch (e.getAsString()) {
                case "minecraft:water" -> out.add(PlantState.Fluid.WATER);
                case "tfp:salt_water" -> out.add(PlantState.Fluid.SALT_WATER);
                case "tfp:spring_water" -> { }
                default -> throw new Unsupported("near fluid " + e.getAsString());
            }
        }
        return out;
    }

    /** {@code ClimatePlacement.CODEC}: absent fields take the codec's defaults ({@code forest_types}: {@link #forestTypes}). */
    static ClimateFilter climate(JsonObject m) {
        return new ClimateFilter(optionalFloat(m, "min_temperature", Float.NEGATIVE_INFINITY),
            optionalFloat(m, "max_temperature", Float.POSITIVE_INFINITY),
            optionalFloat(m, "min_groundwater", Float.NEGATIVE_INFINITY),
            optionalFloat(m, "max_groundwater", Float.POSITIVE_INFINITY),
            optionalFloat(m, "min_rain_variance", -1f), optionalFloat(m, "max_rain_variance", 1f),
            m.has("rain_variance_absolute") && m.get("rain_variance_absolute").getAsBoolean(),
            optionalInt(m, "min_forest", 0), optionalInt(m, "max_forest", 4),
            optionalInt(m, "min_elevation", -64), optionalInt(m, "max_elevation", 320),
            m.has("fuzzy") && m.get("fuzzy").getAsBoolean(), m.has("ignore_rivers") && m.get("ignore_rivers").getAsBoolean());
    }

    /** A block state ({@code {"Name": ..., "Properties": {...}}}) of a plant or wild crop. */
    static PlantState state(JsonObject s) {
        final PlantState base = blockState(string(s, "Name"));
        final JsonObject props = s.has("Properties") ? s.getAsJsonObject("Properties") : new JsonObject();
        PlantState out = base;
        if (props.has("part")) {
            out = out.withPart(switch (props.get("part").getAsString()) {
                case "lower", "bottom" -> PlantState.Part.LOWER;
                case "upper", "top" -> PlantState.Part.UPPER;
                default -> throw new IllegalArgumentException("unknown part in " + s);
            });
        }
        if (props.has("age") && PlantPlacer.hasAge(out)) {
            out = out.withAge(Integer.parseInt(props.get("age").getAsString()));
        }
        if (props.has("facing") && out.kind() == PlantState.Kind.PLANT) {
            out = out.withExtra(direction(props.get("facing").getAsString()));
        }
        if (props.has("fluid")) {
            out = out.withFluid(switch (props.get("fluid").getAsString()) {
                case "empty" -> PlantState.Fluid.EMPTY;
                case "water" -> PlantState.Fluid.WATER;
                case "salt_water", "tfp:salt_water" -> PlantState.Fluid.SALT_WATER;
                default -> throw new Unsupported("fluid " + props.get("fluid").getAsString());
            });
        }
        return out;
    }

    /** A {@code Direction}'s name to its {@code get3DDataValue} index. */
    static int direction(String name) {
        return switch (name) {
            case "down" -> 0;
            case "up" -> 1;
            case "north" -> 2;
            case "south" -> 3;
            case "west" -> 4;
            case "east" -> 5;
            default -> throw new IllegalArgumentException("unknown direction " + name);
        };
    }

    /** The default state of a block id: a plant whose survival is ported, or a wild crop. */
    static PlantState blockState(String id) {
        final PlantType type = PlantType.of(id);
        if (type != null) {
            if (!type.survivable()) {
                throw new Unsupported("plant type " + type + " (" + id + ")");
            }
            final PlantState plant = PlantState.of(id, PlantState.Kind.PLANT, type).withAge(type.hasAge() ? 0 : -1);
            // The default facing: EpiphytePlantBlock north, RotatableWaterPlantBlock up
            return switch (type) {
                case EPIPHYTE -> plant.withExtra(2);
                case OCEAN_ROTATABLE -> plant.withExtra(1);
                default -> plant;
            };
        }
        if (WildCrop.of(id) != null) {
            return PlantState.of(id, PlantState.Kind.WILD_CROP, null);
        }
        throw new Unsupported("block " + id);
    }

    private JsonObject json(String path, String id) {
        // datagen exports the plant and coral features only; a biome list's other features (lakes, boulders) are not there
        return parse(read.apply(path).orElseThrow(() -> new Unsupported("feature not exported: " + id)), id);
    }

    private static JsonObject parse(String text, String what) {
        try {
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed JSON of " + what, e);
        }
    }

    /** {@code tfp:plant/allium} to {@code plant/allium}. */
    private static String path(String id) {
        final int colon = id.indexOf(':');
        if (colon < 0 || !id.substring(0, colon + 1).equals(PlantState.NS)) {
            throw new Unsupported("feature outside our namespace: " + id);
        }
        return id.substring(colon + 1);
    }

    /** A {@code type} with the {@code minecraft:} namespace added when absent. */
    private static String type(JsonObject o) {
        final String type = string(o, "type");
        return type.indexOf(':') < 0 ? "minecraft:" + type : type.toLowerCase(Locale.ROOT);
    }

    private static String string(JsonObject o, String key) {
        final JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            throw new IllegalArgumentException("missing " + key + " in " + o);
        }
        return e.getAsString();
    }

    private static JsonObject object(JsonObject o, String key) {
        final JsonElement e = o.get(key);
        if (e == null || !e.isJsonObject()) {
            throw new IllegalArgumentException("missing object " + key + " in " + o);
        }
        return e.getAsJsonObject();
    }

    private static JsonArray array(JsonObject o, String key) {
        final JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) {
            throw new IllegalArgumentException("missing array " + key + " in " + o);
        }
        return e.getAsJsonArray();
    }

    private static int optionalInt(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    private static float optionalFloat(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }
}
