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
 * <p>Immutable and thread-safe. Built off-thread from the data root by {@link #load(Path)}.
 */
public final class TagRules {
    public static final TagRules EMPTY = new TagRules(Map.of());

    /** One tag's own entries (after merging its files), as ids; plain ids are kept only for {@code remove}. */
    record Node(boolean replace, List<String> includeTags, Set<String> removeIds, List<String> removeTags) {
        Node {
            includeTags = List.copyOf(includeTags);
            removeIds = Set.copyOf(removeIds);
            removeTags = List.copyOf(removeTags);
        }
    }

    private final Map<TagType, Map<String, Node>> nodes;

    private TagRules(Map<TagType, Map<String, Node>> nodes) {
        Map<TagType, Map<String, Node>> copy = new EnumMap<>(TagType.class);
        nodes.forEach((type, byTag) -> copy.put(type, Map.copyOf(byTag)));
        this.nodes = Collections.unmodifiableMap(copy);
    }

    /** Rules from tag files given per type and tag id, each list in data-pack order. */
    public static TagRules of(Map<TagType, Map<String, List<TagFile>>> files) {
        Map<TagType, Map<String, Node>> out = new EnumMap<>(TagType.class);
        files.forEach((type, byTag) -> {
            Map<String, Node> nodes = new LinkedHashMap<>();
            byTag.forEach((tag, list) -> nodes.put(tag, merge(list)));
            out.put(type, nodes);
        });
        return new TagRules(out);
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
     * relative paths). Load conditions ({@code neoforge:conditions}) are not evaluated here; datagen's tag output
     * has none, and the member side (the registry) still applies them.
     *
     * @throws UncheckedIOException when the tree cannot be read
     * @throws dev.tfp.core.json.JsonDataException when a tag file is malformed
     */
    public static TagRules load(Path root) {
        Map<TagType, Map<String, List<TagFile>>> files = new EnumMap<>(TagType.class);
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
            if (json.isJsonObject()) {
                json.getAsJsonObject().remove("neoforge:conditions");
            }
            TagFile tag = TagFile.CODEC.decode(json, DecodeContext.root(where));
            files.computeIfAbsent(type.get(), t -> new LinkedHashMap<>()).computeIfAbsent(id, t -> new ArrayList<>()).add(tag);
        }
        return of(files);
    }

    /** Membership answers for one lookup, given the registry side and the server side. */
    interface Members {
        /** Whether {@code id} is in our resolved (flattened) tag. */
        boolean ours(TagType type, String tag, String id);

        /** Whether the server's own tag {@code tag} contains the vanilla {@code id}. */
        boolean server(TagType type, String tag, String id);
    }

    private static final int MAX_DEPTH = 64;

    /** Whether vanilla {@code id} is in {@code tag} through server tags reached from it (the registry side excluded). */
    boolean viaServer(TagType type, String tag, String id, Members members) {
        return viaServer(type, tag, id, members, 0);
    }

    private boolean viaServer(TagType type, String tag, String id, Members members, int depth) {
        if (depth > MAX_DEPTH) {
            return false; // the loader refuses cycles; this only guards a malformed root
        }
        Node node = nodes.getOrDefault(type, Map.of()).get(tag);
        boolean foreign = !Ids.namespace(tag).equals(Tfp.NAMESPACE);
        if (node == null) {
            return foreign && members.server(type, tag, id);
        }
        boolean in = !node.replace() && foreign && members.server(type, tag, id);
        for (int i = 0; !in && i < node.includeTags().size(); i++) {
            in = full(type, node.includeTags().get(i), id, members, depth + 1);
        }
        if (!in || node.removeIds().contains(id)) {
            return false;
        }
        for (String removed : node.removeTags()) {
            if (full(type, removed, id, members, depth + 1)) {
                return false;
            }
        }
        return true;
    }

    private boolean full(TagType type, String tag, String id, Members members, int depth) {
        return members.ours(type, tag, id) || viaServer(type, tag, id, members, depth);
    }

    /** Number of tags with rules of {@code type} (for messages and tests). */
    public int size(TagType type) {
        return nodes.getOrDefault(type, Map.of()).size();
    }
}
