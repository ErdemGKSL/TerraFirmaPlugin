/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tags;

import com.google.gson.JsonElement;
import dev.tfp.core.Tfp;
import dev.tfp.core.json.DecodeContext;
import dev.tfp.core.json.Ids;
import dev.tfp.core.json.StrictJson;
import dev.tfp.core.tag.TagFile;
import dev.tfp.core.tag.TagType;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The nesting structure of our tag files, kept for the vanilla side of a lookup (PAR-INFRA-07).
 *
 * <p>The core {@link dev.tfp.core.tag.TagRegistry} flattens every tag into its member ids but cannot list the members
 * of a vanilla tag ({@code #minecraft:logs}, TFC's additions to {@code #minecraft:logs_that_burn}): those live in the
 * server's own tags. To answer "is {@code minecraft:oak_log} in {@code #tfp:firepit_logs}" we keep, per tag, which
 * tags it includes and removes, and ask the server's tag for every non-{@code tfp} tag reached. A tag file in a
 * vanilla namespace adds to the server's tag of the same name (vanilla semantics), unless it says
 * {@code "replace": true}.
 *
 * <p><b>Common ({@code c:}) tags.</b> On NeoForge the vanilla members of the {@code c:} tags ({@code minecraft:stick}
 * in {@code #c:rods/wooden}, {@code minecraft:bow} in {@code #c:tools/bow}, ...) come from NeoForge's own data, and
 * TFC's {@code data/c/tags} files only add to them. Paper ships no {@code c:} tags, so a server tag lookup alone loses
 * them. When the data root holds {@value #COMMON_TAGS_FILE} (NeoForge's common tags, emitted by datagen), its entries
 * act as the base layer of those tags: a foreign tag holds an id when the server's tag holds it <em>or</em> the
 * common table does (plain ids, or a {@code #tag} entry that holds it in turn), before our own file's additions and
 * {@code remove} entries. Without that file {@code c:} tags hold only our own additions (ledger divergence).
 *
 * <p>Immutable and thread-safe. Built off-thread from the data root by {@link #load(Path)}.
 */
public final class TagRules {
    /**
     * The common-tag table next to {@code external_tags.json}: {@code {"<type dir>": {"<tag id>": [entries]}}} with
     * {@code <type dir>} one of {@code item}, {@code block}, {@code fluid}, {@code entity_type} and entries in tag-file
     * form ({@code "ns:id"}, {@code "#ns:tag"}, {@code {"id": ..., "required": false}}); other top-level keys (source,
     * licence notes) are ignored.
     */
    public static final String COMMON_TAGS_FILE = "neoforge_common_tags.json";

    public static final TagRules EMPTY = new TagRules(Map.of(), Map.of(), 0);

    /** One tag's own entries (after merging its files), as ids; plain ids are kept only for {@code remove}. */
    record Node(boolean replace, List<String> includeTags, Set<String> removeIds, List<String> removeTags) {
        Node {
            includeTags = List.copyOf(includeTags);
            removeIds = Set.copyOf(removeIds);
            removeTags = List.copyOf(removeTags);
        }
    }

    private final Map<TagType, Map<String, Node>> nodes;
    /** The common-tag base layer ({@link #COMMON_TAGS_FILE}), per type and tag. */
    private final Map<TagType, Map<String, List<TagFile.Entry>>> common;
    private final int conditionalFiles;

    private TagRules(Map<TagType, Map<String, Node>> nodes, Map<TagType, Map<String, List<TagFile.Entry>>> common,
            int conditionalFiles) {
        Map<TagType, Map<String, Node>> copy = new EnumMap<>(TagType.class);
        nodes.forEach((type, byTag) -> copy.put(type, Map.copyOf(byTag)));
        this.nodes = Collections.unmodifiableMap(copy);
        Map<TagType, Map<String, List<TagFile.Entry>>> commonCopy = new EnumMap<>(TagType.class);
        common.forEach((type, byTag) -> {
            Map<String, List<TagFile.Entry>> m = new LinkedHashMap<>();
            byTag.forEach((tag, entries) -> m.put(tag, List.copyOf(entries)));
            commonCopy.put(type, Collections.unmodifiableMap(m));
        });
        this.common = Collections.unmodifiableMap(commonCopy);
        this.conditionalFiles = conditionalFiles;
    }

    /** Rules from tag files given per type and tag id, each list in data-pack order. */
    public static TagRules of(Map<TagType, Map<String, List<TagFile>>> files) {
        return of(files, Map.of());
    }

    /**
     * Rules from tag files plus the common-tag base layer ({@code common}: per type, tag id → its entries as in
     * {@link #COMMON_TAGS_FILE}).
     */
    public static TagRules of(Map<TagType, Map<String, List<TagFile>>> files,
            Map<TagType, Map<String, List<TagFile.Entry>>> common) {
        return of(files, common, 0);
    }

    private static TagRules of(Map<TagType, Map<String, List<TagFile>>> files,
            Map<TagType, Map<String, List<TagFile.Entry>>> common, int conditionalFiles) {
        Map<TagType, Map<String, Node>> out = new EnumMap<>(TagType.class);
        files.forEach((type, byTag) -> {
            Map<String, Node> nodes = new LinkedHashMap<>();
            byTag.forEach((tag, list) -> nodes.put(tag, merge(list)));
            out.put(type, nodes);
        });
        return new TagRules(out, common, conditionalFiles);
    }

    private static Node merge(List<TagFile> files) {
        boolean replace = false;
        List<String> include = new ArrayList<>();
        Set<String> removeIds = new LinkedHashSet<>();
        List<String> removeTags = new ArrayList<>();
        for (TagFile f : files) {
            if (f.replace()) {
                replace = true;
                include.clear();
                removeIds.clear();
                removeTags.clear();
            }
            for (TagFile.Entry e : f.values()) {
                if (e.tag()) {
                    include.add(e.id());
                }
            }
            for (TagFile.Entry e : f.remove()) {
                if (e.tag()) {
                    removeTags.add(e.id());
                } else {
                    removeIds.add(e.id());
                }
            }
        }
        return new Node(replace, include, removeIds, removeTags);
    }

    /**
     * Reads {@code <root>/<namespace>/tags/<block|item|fluid|entity_type>/**.json} in the loader's order (sorted
     * relative paths), and the common-tag table {@code <root>/}{@value #COMMON_TAGS_FILE} when present. Load
     * conditions ({@code neoforge:conditions}) are not evaluated here; datagen's tag output has none (counted by
     * {@link #conditionalFiles()}, which the shipped-data test keeps at zero), and the member side (the registry)
     * still applies them.
     *
     * @throws UncheckedIOException when the tree cannot be read
     * @throws dev.tfp.core.json.JsonDataException when a tag file is malformed
     */
    public static TagRules load(Path root) {
        Map<TagType, Map<String, List<TagFile>>> files = new EnumMap<>(TagType.class);
        int conditional = 0;
        List<Path> list;
        try (Stream<Path> walk = Files.walk(root)) {
            list = walk.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> root.relativize(p).toString().replace('\\', '/')))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + root, e);
        }
        for (Path file : list) {
            List<String> parts = new ArrayList<>();
            root.relativize(file).forEach(p -> parts.add(p.toString()));
            if (parts.size() < 4 || !parts.get(1).equals("tags") || !parts.get(parts.size() - 1).endsWith(".json")) {
                continue;
            }
            var type = TagType.byDirectory(parts.get(2));
            if (type.isEmpty()) {
                continue;
            }
            String name = String.join("/", parts.subList(3, parts.size()));
            String id = Ids.parse(parts.get(0) + ":" + name.substring(0, name.length() - ".json".length()));
            String where = String.join("/", parts);
            JsonElement json;
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                json = StrictJson.parse(in, where);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + where, e);
            }
            if (json.isJsonObject() && json.getAsJsonObject().remove("neoforge:conditions") != null) {
                conditional++;
            }
            TagFile tag = TagFile.CODEC.decode(json, DecodeContext.root(where));
            files.computeIfAbsent(type.get(), t -> new LinkedHashMap<>()).computeIfAbsent(id, t -> new ArrayList<>()).add(tag);
        }
        return of(files, loadCommon(root.resolve(COMMON_TAGS_FILE)), conditional);
    }

    /** Reads {@value #COMMON_TAGS_FILE}; empty when the file is absent. */
    static Map<TagType, Map<String, List<TagFile.Entry>>> loadCommon(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        JsonElement json;
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            json = StrictJson.parse(in, COMMON_TAGS_FILE);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
        DecodeContext ctx = DecodeContext.root(COMMON_TAGS_FILE);
        if (!json.isJsonObject()) {
            throw ctx.error("expected an object");
        }
        Map<TagType, Map<String, List<TagFile.Entry>>> out = new EnumMap<>(TagType.class);
        for (Map.Entry<String, JsonElement> byType : json.getAsJsonObject().entrySet()) {
            var type = TagType.byDirectory(byType.getKey());
            if (type.isEmpty()) {
                continue; // source and licence notes
            }
            DecodeContext typeCtx = ctx.field(byType.getKey());
            if (!byType.getValue().isJsonObject()) {
                throw typeCtx.error("expected an object of tag id → entries");
            }
            Map<String, List<TagFile.Entry>> byTag = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> tag : byType.getValue().getAsJsonObject().entrySet()) {
                String id;
                try {
                    id = Ids.parse(tag.getKey());
                } catch (IllegalArgumentException e) {
                    throw typeCtx.error(e.getMessage());
                }
                byTag.put(id, TagFile.ENTRY.listOf().decode(tag.getValue(), typeCtx.field(tag.getKey())));
            }
            out.put(type.get(), byTag);
        }
        return out;
    }

    /** Membership answers for one lookup, given the registry side and the server side. */
    interface Members {
        /** Whether {@code id} is in our resolved (flattened) tag. */
        boolean ours(TagType type, String tag, String id);

        /** Whether the server's own tag {@code tag} contains the vanilla {@code id}. */
        boolean server(TagType type, String tag, String id);
    }

    private static final int MAX_DEPTH = 64;

    /**
     * Whether vanilla {@code id} is in {@code tag} through server or common tags reached from it (the registry side
     * excluded).
     */
    boolean viaServer(TagType type, String tag, String id, Members members) {
        return viaServer(type, tag, id, members, 0);
    }

    private boolean viaServer(TagType type, String tag, String id, Members members, int depth) {
        if (depth > MAX_DEPTH) {
            return false; // the loader refuses cycles (NeoForge's common tags have none); guards malformed data
        }
        Node node = nodes.getOrDefault(type, Map.of()).get(tag);
        boolean foreign = !Ids.namespace(tag).equals(Tfp.NAMESPACE);
        if (node == null) {
            return foreign && base(type, tag, id, members, depth);
        }
        boolean in = !node.replace() && foreign && base(type, tag, id, members, depth);
        for (int i = 0; !in && i < node.includeTags().size(); i++) {
            in = full(type, node.includeTags().get(i), id, members, depth + 1);
        }
        return in && !removedBy(node, type, id, members, depth);
    }

    /** The tag's members outside our data: the server's own tag, or the common-tag table's entries for it. */
    private boolean base(TagType type, String tag, String id, Members members, int depth) {
        if (members.server(type, tag, id)) {
            return true;
        }
        List<TagFile.Entry> entries = common.getOrDefault(type, Map.of()).get(tag);
        if (entries == null) {
            return false;
        }
        for (TagFile.Entry e : entries) {
            if (e.tag() ? full(type, e.id(), id, members, depth + 1) : e.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean removedBy(Node node, TagType type, String id, Members members, int depth) {
        if (node.removeIds().contains(id)) {
            return true;
        }
        for (String removed : node.removeTags()) {
            if (full(type, removed, id, members, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code tag}'s own {@code remove} entries exclude vanilla {@code id}, counting server and common-tag
     * members of the removed tags. A guard for ids the registry lists directly: the registry applies a
     * {@code remove: ["#c:sands"]} only to the members it knows, so a vanilla id named in our tag would survive the
     * removal of a server tag that holds it. Only the asked tag's own removes are checked; a remove inside a nested
     * {@code tfp} tag of such a case is not (TFC's data at the pinned commit has no remove of a foreign tag holding an
     * id that is also listed directly: its removes are {@code minecraft:water}, {@code #c:sands},
     * {@code minecraft:hay_block} and {@code tfc:food/lentil}).
     */
    boolean removedByForeign(TagType type, String tag, String id, Members members) {
        Node node = nodes.getOrDefault(type, Map.of()).get(tag);
        return node != null && !node.removeTags().isEmpty() && removedBy(node, type, id, members, 0);
    }

    private boolean full(TagType type, String tag, String id, Members members, int depth) {
        return members.ours(type, tag, id) || viaServer(type, tag, id, members, depth);
    }

    /** Number of tags of {@code type} in the common-tag table. */
    public int commonSize(TagType type) {
        return common.getOrDefault(type, Map.of()).size();
    }

    /** Tag files read by {@link #load} that carried {@code neoforge:conditions} (not evaluated by the rules). */
    public int conditionalFiles() {
        return conditionalFiles;
    }

    /** Number of tags with rules of {@code type} (for messages and tests). */
    public int size(TagType type) {
        return nodes.getOrDefault(type, Map.of()).size();
    }
}
