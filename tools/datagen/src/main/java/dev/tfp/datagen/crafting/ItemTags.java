/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Item tag resolution for the crafting generator: what a recipe's {@code {"tag": ...}} ingredient accepts.
 *
 * <p>Layers, the way the tag is built on TFC's NeoForge server:
 * <ul>
 *   <li>{@code minecraft:} tags: the server's own vanilla tag (kept as the opaque element {@code #minecraft:<tag>}, because
 *       the vanilla members are not in the TFC checkout) plus TFC's additions under {@code data/minecraft/tags/item};</li>
 *   <li>{@code c:} tags: NeoForge's members ({@code data/neoforge_common_tags.json}, PAR-INFRA-14, when the generation has it)
 *       plus TFC's additions under {@code data/c/tags/item};</li>
 *   <li>every other namespace: TFC's files only.</li>
 * </ul>
 * TFC's generated and main resources are merged, nested tags are expanded, {@code remove} lists are applied last and
 * {@code replace: true} drops the lower layer. A cycle reads as empty. An optional vanilla tag reference keeps only TFC's
 * members ({@link #add}).
 */
final class ItemTags {
    /** One resolved tag: concrete item ids and the vanilla tags the server resolves itself. */
    record Members(Set<String> items, Set<String> vanillaTags) {
        boolean isEmpty() {
            return items.isEmpty() && vanillaTags.isEmpty();
        }
    }

    private final TfcCheckout tfc;
    private final Map<String, JsonArray> neoforge;
    private final Map<String, Members> cache = new HashMap<>();

    /** {@code neoforge}: NeoForge's {@code c:} item tags (tag id → tag-file entries); empty when not generated. */
    ItemTags(TfcCheckout tfc, Map<String, JsonArray> neoforge) {
        this.tfc = tfc;
        this.neoforge = neoforge;
    }

    Members resolve(String tag) {
        Members cached = cache.get(tag);
        if (cached != null) {
            return cached;
        }
        cache.put(tag, new Members(Set.of(), Set.of()));
        int colon = tag.indexOf(':');
        String ns = tag.substring(0, colon);
        String rel = "data/" + ns + "/tags/item/" + tag.substring(colon + 1) + ".json";
        Set<String> items = new TreeSet<>();
        Set<String> vanilla = new TreeSet<>();
        List<JsonElement> removed = new ArrayList<>();
        boolean replace = false;
        List<JsonElement> values = new ArrayList<>();
        for (String root : List.of(TfcCheckout.GENERATED, TfcCheckout.MAIN)) {
            if (!tfc.exists(root + rel)) {
                continue;
            }
            JsonObject json = Json.parse(tfc.readString(root + rel), root + rel).getAsJsonObject();
            replace |= json.has("replace") && json.get("replace").getAsBoolean();
            if (json.has("values")) {
                values.addAll(json.getAsJsonArray("values").asList());
            }
            if (json.has("remove")) {
                removed.addAll(json.getAsJsonArray("remove").asList());
            }
        }
        if (!replace) {
            if (ns.equals("minecraft")) {
                vanilla.add("#" + tag);
            } else if (ns.equals("c") && neoforge.containsKey(tag)) {
                values.addAll(0, neoforge.get(tag).asList());
            }
        }
        for (JsonElement v : values) {
            add(entry(v), optional(v), items, vanilla);
        }
        for (JsonElement v : removed) {
            String r = entry(v);
            if (r.startsWith("#")) {
                Members m = resolve(r.substring(1));
                items.removeAll(m.items());
                vanilla.removeAll(m.vanillaTags());
            } else {
                items.remove(r);
            }
        }
        Members out = new Members(Set.copyOf(items), Set.copyOf(vanilla));
        cache.put(tag, out);
        return out;
    }

    /**
     * An optional ({@code "required": false}) reference to a vanilla tag keeps only TFC's members: whether vanilla has the
     * tag cannot be read from the checkout, and the one TFC uses this way ({@code #minecraft:stone_pressure_plates} in
     * {@code c:stones/pressure_plate}) is a vanilla block tag only, which CE rejects as an empty item tag.
     */
    private void add(String value, boolean optional, Set<String> items, Set<String> vanilla) {
        if (value.startsWith("#")) {
            Members m = resolve(value.substring(1));
            items.addAll(m.items());
            m.vanillaTags().stream().filter(t -> !(optional && t.equals(value))).forEach(vanilla::add);
        } else {
            items.add(value);
        }
    }

    private static boolean optional(JsonElement v) {
        return v.isJsonObject() && v.getAsJsonObject().has("required") && !v.getAsJsonObject().get("required").getAsBoolean();
    }

    private static String entry(JsonElement v) {
        return v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
    }
}
