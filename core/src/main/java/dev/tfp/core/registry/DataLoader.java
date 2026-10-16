/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.DecodeContext;
import dev.tfp.core.json.Ids;
import dev.tfp.core.json.JsonDataException;
import dev.tfp.core.json.ObjectReader;
import dev.tfp.core.json.Reference;
import dev.tfp.core.json.StrictJson;
import dev.tfp.core.tag.TagFile;
import dev.tfp.core.tag.TagRegistry;
import dev.tfp.core.tag.TagResolver;
import dev.tfp.core.tag.TagType;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads data-pack style roots ({@code <root>/<namespace>/...}, i.e. the {@code data/} folder) into
 * an immutable {@link RegistrySnapshot}. Pure and blocking: call it off the server threads (see
 * {@link Registries#reload}).
 *
 * <p>Layout per namespace:
 * <ul>
 *   <li>{@code tags/<block|item|fluid|entity_type>/**} → {@link TagRegistry}</li>
 *   <li>{@code recipe/**} and {@code <tfc|tfp>/<kind>/**} → the registered {@link DataKind}s</li>
 *   <li>vanilla data-pack folders ({@code loot_table}, {@code worldgen}, other tag types, ...) are left
 *       to the shipped datapack and skipped</li>
 *   <li>anything else is an error</li>
 * </ul>
 *
 * <p>It fails ({@link DataLoadException}, all problems listed) on malformed JSON, unknown types or
 * keys, duplicate ids, tag cycles, and references to ids/tags that exist neither in the loaded data
 * nor in the {@link KnownIds} table. {@code neoforge:conditions} are honoured: a file whose
 * conditions are false is skipped (recipes are recorded as disabled).
 */
public final class DataLoader {
    /** Folders that belong to the vanilla datapack we ship as-is. */
    public static final Set<String> DATAPACK_DIRECTORIES = Set.of(
            "advancement", "banner_pattern", "chat_type", "damage_type", "data_maps", "dimension", "dimension_type",
            "enchantment", "enchantment_provider", "function", "instrument", "item_modifier", "jukebox_song",
            "loot_table", "painting_variant", "predicate", "structure", "trim_material", "trim_pattern",
            "wolf_variant", "worldgen");

    /** Second-level folders holding TFC definition managers ({@code data/tfc/tfc/...}; {@code tfp} after renaming). */
    public static final Set<String> DEFINITION_ROOTS = Set.of("tfc", "tfp");

    /** Files allowed directly in a data root: datagen's id map (read separately, see {@link KnownIds#fromIdMap}). */
    public static final Set<String> ROOT_FILES = Set.of(KnownIds.ID_MAP_FILE);

    private static final String CONDITIONS = "neoforge:conditions";

    private final List<DataKind<?>> kinds;
    private final KnownIds ids;

    public DataLoader(List<DataKind<?>> kinds, KnownIds ids) {
        this.kinds = List.copyOf(kinds);
        this.ids = ids;
    }

    /** Recipes + all TFC definition kinds. */
    public static DataLoader standard(KnownIds ids) {
        return new DataLoader(DefinitionKinds.standard(), ids);
    }

    /** Loads the given roots in order (later roots add to tags; duplicate entries fail). */
    public RegistrySnapshot load(List<Path> roots) {
        Run run = new Run();
        for (Path root : roots) {
            run.root(root);
        }
        return run.finish();
    }

    private final class Run {
        final List<String> errors = new ArrayList<>();
        final List<Reference> references = new ArrayList<>();
        final Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        final Map<String, Map<String, String>> sources = new HashMap<>();
        final Set<String> disabledRecipes = new LinkedHashSet<>();
        final TagResolver tags = new TagResolver();

        Run() {
            for (DataKind<?> kind : kinds) {
                values.put(kind.key().name(), new LinkedHashMap<>());
                sources.put(kind.key().name(), new HashMap<>());
            }
        }

        void root(Path root) {
            if (!Files.isDirectory(root)) {
                errors.add("data root is not a directory: " + root);
                return;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(Files::isRegularFile).sorted(Comparator.comparing(p -> root.relativize(p).toString())).toList();
            } catch (IOException e) {
                errors.add("cannot list " + root + ": " + e);
                return;
            }
            for (Path file : files) {
                try {
                    file(root, file);
                } catch (JsonDataException e) {
                    errors.add(e.getMessage());
                }
            }
        }

        /** {@code <root folder name>/<path below root>}, used in every message about the file. */
        String where(Path root, Path file) {
            Path name = root.getFileName();
            return (name == null ? "" : name + "/") + root.relativize(file).toString().replace('\\', '/');
        }

        void file(Path root, Path file) {
            List<String> parts = new ArrayList<>();
            root.relativize(file).forEach(p -> parts.add(p.toString()));
            if (parts.size() == 1 && ROOT_FILES.contains(parts.get(0))) {
                return;
            }
            if (parts.size() < 3) {
                errors.add(where(root, file) + ": not inside <namespace>/<folder>/");
                return;
            }
            String namespace = parts.get(0);
            String top = parts.get(1);
            if (DATAPACK_DIRECTORIES.contains(top)) {
                return;
            }
            if (top.equals("tags")) {
                String dir = parts.get(2);
                if (TagType.byDirectory(dir).isEmpty()) {
                    return; // damage_type, worldgen/biome, ... tags stay in the datapack
                }
                parse(root, file, namespace, parts.subList(3, parts.size()), null, TagType.byDirectory(dir).get());
                return;
            }
            if (top.equals("recipe")) {
                parse(root, file, namespace, parts.subList(2, parts.size()), kind(false, "recipe"), null);
                return;
            }
            if (DEFINITION_ROOTS.contains(top)) {
                List<String> rest = parts.subList(2, parts.size());
                for (int n = rest.size() - 1; n >= 1; n--) {
                    DataKind<?> kind = kind(true, String.join("/", rest.subList(0, n)));
                    if (kind != null) {
                        parse(root, file, namespace, rest.subList(n, rest.size()), kind, null);
                        return;
                    }
                }
                errors.add(where(root, file) + ": unknown data kind '" + String.join("/", rest.subList(0, Math.max(1, rest.size() - 1))) + "'");
                return;
            }
            errors.add(where(root, file) + ": unknown data folder '" + top + "'");
        }

        DataKind<?> kind(boolean definition, String directory) {
            for (DataKind<?> k : kinds) {
                if (k.definition() == definition && k.directory().equals(directory)) {
                    return k;
                }
            }
            return null;
        }

        /** Exactly one of {@code kind} / {@code tagType} is set. */
        void parse(Path root, Path file, String namespace, List<String> relative, DataKind<?> kind, TagType tagType) {
            String name = String.join("/", relative);
            String where = where(root, file);
            if (!name.endsWith(".json")) {
                errors.add(where + ": not a .json file");
                return;
            }
            String id;
            try {
                id = Ids.parse(namespace + ":" + name.substring(0, name.length() - ".json".length()));
            } catch (IllegalArgumentException e) {
                errors.add(where + ": " + e.getMessage());
                return;
            }
            JsonElement json;
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                json = StrictJson.parse(in, where);
            } catch (IOException e) {
                errors.add(where + ": cannot read: " + e);
                return;
            }
            DecodeContext ctx = DecodeContext.root(where, references::add);
            if (!conditionsHold(json, ctx)) {
                if (kind != null && kind.key().name().equals(DefinitionKinds.RECIPE.name())) {
                    disabledRecipes.add(id);
                }
                return;
            }
            if (tagType != null) {
                tags.add(tagType, id, TagFile.CODEC.decode(json, ctx), where);
                return;
            }
            Object value = kind.codec().decode(json, ctx);
            String previous = sources.get(kind.key().name()).putIfAbsent(id, where);
            if (previous != null) {
                errors.add("duplicate " + kind.key().name() + " '" + id + "' in " + previous + " and " + where);
                return;
            }
            values.get(kind.key().name()).put(id, value);
        }

        /** Evaluates and strips {@code neoforge:conditions}; false → skip the file. */
        boolean conditionsHold(JsonElement json, DecodeContext ctx) {
            if (!json.isJsonObject() || !json.getAsJsonObject().has(CONDITIONS)) {
                return true;
            }
            JsonObject object = json.getAsJsonObject();
            List<Boolean> results = CONDITION.listOf().decode(object.get(CONDITIONS), ctx.field(CONDITIONS));
            object.remove(CONDITIONS);
            return results.stream().allMatch(b -> b);
        }

        RegistrySnapshot finish() {
            TagResolver.Result tagResult = tags.resolve(ids);
            errors.addAll(tagResult.errors());
            TagRegistry tagRegistry = tagResult.registry();
            for (Reference ref : references) {
                String problem = check(ref, tagRegistry);
                if (problem != null) {
                    errors.add(ref.location() + ": " + problem);
                }
            }
            if (!errors.isEmpty()) {
                throw new DataLoadException(errors);
            }
            Map<String, Registry<?>> registries = new LinkedHashMap<>();
            for (DataKind<?> kind : kinds) {
                registries.put(kind.key().name(), build(kind, values.get(kind.key().name())));
            }
            return new RegistrySnapshot(registries, tagRegistry, disabledRecipes);
        }

        String check(Reference ref, TagRegistry tagRegistry) {
            var tagType = TagType.byDirectory(ref.registry());
            if (tagType.isPresent()) {
                if (ref.tag()) {
                    return tagRegistry.exists(tagType.get(), ref.id()) || ids.hasExternalTag(ref.registry(), ref.id())
                            ? null : "unknown " + ref.registry() + " tag '#" + ref.id() + "'";
                }
                return ids.hasId(ref.registry(), ref.id()) ? null : "unknown " + ref.registry() + " '" + ref.id() + "'";
            }
            Map<String, Object> loaded = values.get(ref.registry());
            if (loaded != null) {
                if (ref.tag()) {
                    return ref.registry() + " has no tags";
                }
                return loaded.containsKey(ref.id()) ? null : "unknown " + ref.registry() + " '" + ref.id() + "'";
            }
            return ids.hasId(ref.registry(), ref.id()) ? null : "unknown " + ref.registry() + " '" + ref.id() + "'";
        }
    }

    private static <T> Registry<T> build(DataKind<T> kind, Map<String, Object> entries) {
        Registry.Builder<T> builder = Registry.builder(kind.key());
        entries.forEach((id, value) -> builder.register(id, kind.key().type().cast(value)));
        return builder.build();
    }

    /** NeoForge load conditions as TFC data uses them: true/false/not/and/or. */
    static final Codec<Boolean> CONDITION = new Codec<>() {
        @Override
        public Boolean decode(JsonElement json, DecodeContext ctx) {
            ObjectReader r = ObjectReader.of(json, ctx);
            String type = r.required("type", Codecs.ID);
            boolean result = switch (type) {
                case "neoforge:true" -> true;
                case "neoforge:false" -> false;
                case "neoforge:not" -> !r.required("value", this);
                case "neoforge:and" -> r.required("values", this.listOf()).stream().allMatch(b -> b);
                case "neoforge:or" -> r.required("values", this.listOf()).stream().anyMatch(b -> b);
                default -> throw ctx.field("type").error("unsupported load condition type '" + type + "'");
            };
            r.finish();
            return result;
        }
    };
}
