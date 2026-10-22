/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.core.registry.DataLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * {@code data/neoforge_common_tags.json}: the vanilla members of NeoForge's common ({@code c:}) tags (PAR-INFRA-14).
 *
 * <p>On NeoForge, {@code minecraft:stick} is in {@code #c:rods/wooden}, {@code minecraft:bow} in {@code #c:tools/bow}
 * and so on because NeoForge's own jar ships {@code data/c/tags}; TFC's {@code data/c/tags} files only add to them.
 * Paper ships no {@code c:} tags, so the plugin's tag runtime ({@code paper/tags/TagRules}) reads this table as the base
 * layer of every foreign tag. It is read from the NeoForge universal jar of the version TFC pins
 * ({@link #NEOFORGE_VERSION}), resolved by Gradle and passed as the system property {@value #JAR_PROPERTY}; without it
 * (the small fixture checkouts of the tests) the step is skipped.
 *
 * <p>Only {@code data/c/tags/{item,block,fluid,entity_type}/**.json} are read (not {@code worldgen/} or
 * {@code enchantment/}). Entries stay in tag-file form ({@code "ns:id"}, {@code "#ns:tag"},
 * {@code {"id": ..., "required": ...}}) in file order; entries whose namespace is neither {@code minecraft} nor
 * {@code c} (NeoForge's optional {@code #forge:} legacy tags and other mods' ids) are dropped. A tag whose entries
 * all drop stays, with an empty list. Tag ids and top-level keys are sorted, so the output is deterministic.
 */
final class CommonTags {
    /** TFC's NeoForge version ({@code build.gradle.kts} {@code neoForgeVersion} at the pinned commit). */
    static final String NEOFORGE_VERSION = "21.1.234";
    /** System property holding the path of {@code neoforge-<version>-universal.jar}. */
    static final String JAR_PROPERTY = "tfp.neoforgeJar";
    /** At the top of the data root; core's loader allows exactly this name there ({@code DataLoader.ROOT_FILES}). */
    static final String FILE = "data/" + DataLoader.COMMON_TAGS_FILE;
    /** Tag directories the runtime reads (TagType directories). */
    static final List<String> TYPES = List.of("block", "entity_type", "fluid", "item");
    private static final String PREFIX = "data/c/tags/";

    private CommonTags() {
    }

    /** Emits {@link #FILE} when {@value #JAR_PROPERTY} is set; otherwise does nothing. */
    static void run(OutputTree out) {
        String jar = System.getProperty(JAR_PROPERTY);
        if (jar == null || jar.isBlank()) {
            return;
        }
        out.put(FILE, generate(Path.of(jar)));
    }

    /** The table as JSON text from a NeoForge universal jar. */
    static String generate(Path jar) {
        if (!Files.isRegularFile(jar)) {
            throw new DatagenException("NeoForge jar not found: " + jar + " (" + JAR_PROPERTY + ")");
        }
        Map<String, SortedMap<String, JsonArray>> byType = new TreeMap<>();
        TYPES.forEach(t -> byType.put(t, new TreeMap<>()));
        String sha;
        try {
            sha = TfcCheckout.sha256(Files.readAllBytes(jar));
            try (JarFile file = new JarFile(jar.toFile())) {
                String version = version(file.getManifest());
                if (!NEOFORGE_VERSION.equals(version)) {
                    throw new DatagenException(jar + " is NeoForge " + version + ", but datagen is pinned to "
                        + NEOFORGE_VERSION + " (TFC's neoForgeVersion)");
                }
                for (JarEntry e : file.stream().toList()) {
                    String name = e.getName();
                    if (e.isDirectory() || !name.startsWith(PREFIX) || !name.endsWith(".json")) {
                        continue;
                    }
                    String rest = name.substring(PREFIX.length());
                    int slash = rest.indexOf('/');
                    if (slash < 0 || !TYPES.contains(rest.substring(0, slash))) {
                        continue;
                    }
                    String tag = "c:" + rest.substring(slash + 1, rest.length() - ".json".length());
                    String text;
                    try (var in = file.getInputStream(e)) {
                        text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    byType.get(rest.substring(0, slash)).put(tag, entries(Json.parse(text, name), name));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + jar, e);
        }
        JsonObject root = new JsonObject();
        for (String type : TYPES) { // sorted: block, entity_type, fluid, item
            JsonObject tags = new JsonObject();
            byType.get(type).forEach(tags::add);
            root.add(type, tags);
        }
        root.addProperty("license", "LGPL-2.1-only (NeoForge); vanilla ids only");
        root.addProperty("sha256", sha);
        root.addProperty("source", "NeoForge " + NEOFORGE_VERSION + " universal jar, data/c/tags");
        return Json.write(root);
    }

    private static String version(Manifest manifest) {
        if (manifest == null) {
            return "unknown";
        }
        var section = manifest.getAttributes("net/neoforged/neoforge/internal/versions/neoforge/");
        String v = section == null ? null : section.getValue("Implementation-Version");
        return v == null ? "unknown" : v;
    }

    /** The kept entries of one tag file. */
    static JsonArray entries(JsonElement json, String where) {
        if (!json.isJsonObject() || !json.getAsJsonObject().has("values")
                || !json.getAsJsonObject().get("values").isJsonArray()) {
            throw new DatagenException(where + ": expected a tag file with a values array");
        }
        JsonObject o = json.getAsJsonObject();
        if (o.has("replace") && o.get("replace").getAsBoolean() || o.has("remove")) {
            throw new DatagenException(where + ": replace/remove are not supported in the common-tag table");
        }
        JsonArray out = new JsonArray();
        for (JsonElement v : o.getAsJsonArray("values")) {
            String id;
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                id = v.getAsString();
            } else if (v.isJsonObject() && v.getAsJsonObject().has("id")) {
                id = v.getAsJsonObject().get("id").getAsString();
            } else {
                throw new DatagenException(where + ": unrecognised tag entry " + v);
            }
            String ref = id.startsWith("#") ? id.substring(1) : id;
            int colon = ref.indexOf(':');
            if (colon <= 0) {
                throw new DatagenException(where + ": entry without a namespace: " + id);
            }
            String ns = ref.substring(0, colon);
            if (ns.equals("minecraft") || ns.equals("c")) {
                out.add(v.deepCopy());
            }
        }
        return out;
    }
}
