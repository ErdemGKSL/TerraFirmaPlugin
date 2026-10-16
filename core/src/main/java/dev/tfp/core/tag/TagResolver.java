/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects tag files (in data-pack order) and resolves them into a {@link TagRegistry}.
 *
 * <ul>
 *   <li>Several files for the same tag merge in the order added; {@code "replace": true} drops
 *       everything added before it (vanilla semantics).</li>
 *   <li>Nested {@code #tag} entries are flattened; a cycle is an error naming the whole cycle.</li>
 *   <li>Every plain id must exist in the {@link IdTable}; every {@code #tag} must be defined or
 *       declared external there. Unresolved required entries are errors; entries with
 *       {@code "required": false} are skipped.</li>
 *   <li>{@code remove} entries (NeoForge) are applied after resolution.</li>
 * </ul>
 *
 * Not thread-safe; used by one loader thread.
 */
public final class TagResolver {
    /** Which plain ids and undefined tags are acceptable (the datagen id table). */
    public interface IdTable {
        boolean hasId(TagType type, String id);

        /** A tag we don't define but that exists outside our data (a vanilla tag). */
        boolean hasExternalTag(TagType type, String tag);
    }

    /** Resolution output: the registry, plus errors (empty when valid). */
    public record Result(TagRegistry registry, List<String> errors) {
        public Result {
            errors = List.copyOf(errors);
        }
    }

    private static final class Pending {
        final List<TagFile.Entry> values = new ArrayList<>();
        final List<TagFile.Entry> remove = new ArrayList<>();
        final List<String> sources = new ArrayList<>();
    }

    private final Map<TagType, Map<String, Pending>> pending = new EnumMap<>(TagType.class);

    /** Adds one file for tag {@code tagId}; {@code source} names the file in errors. */
    public void add(TagType type, String tagId, TagFile file, String source) {
        Pending p = pending.computeIfAbsent(type, t -> new LinkedHashMap<>()).computeIfAbsent(tagId, t -> new Pending());
        if (file.replace()) {
            p.values.clear();
            p.remove.clear();
            p.sources.clear();
        }
        p.values.addAll(file.values());
        p.remove.addAll(file.remove());
        p.sources.add(source);
    }

    public Result resolve(IdTable ids) {
        List<String> errors = new ArrayList<>();
        Map<TagType, Map<String, Set<String>>> out = new EnumMap<>(TagType.class);
        Map<TagType, Set<String>> external = new EnumMap<>(TagType.class);
        for (TagType type : TagType.values()) {
            Map<String, Pending> files = pending.getOrDefault(type, Map.of());
            Walk walk = new Walk(type, files, ids, errors, external.computeIfAbsent(type, t -> new LinkedHashSet<>()));
            Map<String, Set<String>> resolved = new LinkedHashMap<>();
            for (String tag : files.keySet()) {
                resolved.put(tag, walk.resolve(tag));
            }
            out.put(type, resolved);
        }
        return new Result(new TagRegistry(out, external), errors);
    }

    private static final class Walk {
        final TagType type;
        final Map<String, Pending> files;
        final IdTable ids;
        final List<String> errors;
        final Set<String> external;
        final Map<String, Set<String>> done = new HashMap<>();
        final List<String> stack = new ArrayList<>();

        Walk(TagType type, Map<String, Pending> files, IdTable ids, List<String> errors, Set<String> external) {
            this.type = type;
            this.files = files;
            this.ids = ids;
            this.errors = errors;
            this.external = external;
        }

        Set<String> resolve(String tag) {
            Set<String> cached = done.get(tag);
            if (cached != null) {
                return cached;
            }
            int at = stack.indexOf(tag);
            if (at >= 0) {
                List<String> cycle = new ArrayList<>(stack.subList(at, stack.size()));
                cycle.add(tag);
                errors.add(type.directory() + " tag cycle: #" + String.join(" -> #", cycle));
                return Set.of();
            }
            stack.add(tag);
            Pending p = files.get(tag);
            Set<String> members = new LinkedHashSet<>();
            for (TagFile.Entry e : p.values) {
                if (!e.tag()) {
                    if (ids.hasId(type, e.id())) {
                        members.add(e.id());
                    } else if (e.required()) {
                        errors.add("unknown " + type.directory() + " id '" + e.id() + "' in tag #" + tag + " " + p.sources);
                    }
                } else {
                    members.addAll(child(tag, e, p));
                }
            }
            for (TagFile.Entry e : p.remove) {
                if (!e.tag()) {
                    members.remove(e.id());
                } else {
                    members.removeAll(child(tag, e, p));
                }
            }
            stack.removeLast();
            done.put(tag, members);
            return members;
        }

        private Set<String> child(String parent, TagFile.Entry e, Pending p) {
            if (files.containsKey(e.id())) {
                return resolve(e.id());
            }
            if (ids.hasExternalTag(type, e.id())) {
                external.add(e.id());
            } else if (e.required()) {
                errors.add("unknown " + type.directory() + " tag '#" + e.id() + "' in tag #" + parent + " " + p.sources);
            }
            return Set.of();
        }
    }
}
