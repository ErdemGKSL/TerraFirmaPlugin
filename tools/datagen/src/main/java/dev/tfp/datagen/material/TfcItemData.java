/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/DataManager.java @ c3b4d9536 (apply: load order),
 *       src/main/java/net/dries007/tfc/util/collections/IndirectHashCollection.java @ c3b4d9536 (first match wins),
 *       src/main/java/net/dries007/tfc/common/component/size/ItemSizeManager.java @ c3b4d9536 (getDefinition)
 */
package dev.tfp.datagen.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.TfcCheckout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * A copy of {@code dev.tfp.datagen.food.TfcItemData} (package-private there, owned by PAR-SURV-01) for the material items'
 * {@code item_size}; the two must stay identical in behaviour (MaterialItemsTest checks the soup/salad load-order case).
 *
 * <p>Reads TFC data definitions keyed by an item ingredient ({@code tfc/food}, {@code tfc/item_size}) and finds the one an item
 * gets, the way TFC does: the definitions in TFC's load order, the first whose ingredient matches the item.
 *
 * <p>TFC's {@code DataManager} keeps the definitions in the iteration order of the {@code HashMap} that vanilla's
 * {@code SimpleJsonResourceReloadListener} fills from the sorted resource listing, and {@code IndirectHashCollection} keeps
 * that order per item. {@link #loadOrder} reproduces it: the same {@code java.util.HashMap}, keys hashed like
 * {@code ResourceLocation} ({@code 31 * namespace.hashCode() + path.hashCode()}), filled in sorted order. This matters where
 * two definitions match one item: TFC's soups and salads match both {@code foods} (light) and {@code soups}/{@code salads}
 * (medium), and {@code foods} comes first.
 *
 * <p>Ingredients: {@code item}, {@code tag} (item tags from the checkout, nested tags and {@code remove} lists), a list of
 * ingredients and {@code neoforge:difference}. Any other ingredient type is an error, so a TFC change shows up here.
 */
final class TfcItemData {
    private final TfcCheckout tfc;
    private final Map<String, Set<String>> tags = new HashMap<>();

    TfcItemData(TfcCheckout tfc) {
        this.tfc = tfc;
    }

    /** One definition: its id ({@code tfc:<path>}, path below the data folder) and its JSON. */
    record Definition(String id, JsonObject json) {
    }

    /**
     * The definitions of the data folder {@code data/tfc/tfc/<folder>/} of the generated resources, in TFC's load order.
     * Fails when the folder is empty.
     */
    List<Definition> definitions(String folder) {
        String dir = TfcCheckout.GENERATED + "data/" + Json.FROM_NS + "/" + Json.FROM_NS + "/" + folder + "/";
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String f : tfc.list(dir)) {
            if (f.endsWith(".json")) {
                sorted.put(f.substring(dir.length(), f.length() - ".json".length()), f);
            }
        }
        if (sorted.isEmpty()) {
            throw new DatagenException("TFC has no " + folder + " definitions at " + dir);
        }
        List<Definition> out = new ArrayList<>();
        for (String path : loadOrder(Json.FROM_NS, sorted.keySet())) {
            String file = sorted.get(path);
            out.add(new Definition(Json.FROM_NS + ":" + path, Json.parse(tfc.readString(file), file).getAsJsonObject()));
        }
        return out;
    }

    /** The first definition (in the given order) whose {@code ingredient} matches {@code item} (a {@code tfc:} or vanilla id). */
    Optional<Definition> first(List<Definition> definitions, String item) {
        for (Definition d : definitions) {
            if (!d.json().has("ingredient")) {
                throw new DatagenException("TFC definition " + d.id() + " has no ingredient");
            }
            if (matches(d.json().get("ingredient"), item, d.id())) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    /** Every definition (in the given order) whose ingredient matches {@code item}. */
    List<Definition> all(List<Definition> definitions, String item) {
        List<Definition> out = new ArrayList<>();
        for (Definition d : definitions) {
            if (d.json().has("ingredient") && matches(d.json().get("ingredient"), item, d.id())) {
                out.add(d);
            }
        }
        return out;
    }

    /**
     * The paths of {@code namespace} in the iteration order of a {@code java.util.HashMap} keyed like
     * {@code ResourceLocation} and filled in sorted order (TFC's {@code DataManager} order).
     */
    static List<String> loadOrder(String namespace, Iterable<String> sortedPaths) {
        Map<Location, Boolean> map = new HashMap<>();
        for (String p : sortedPaths) {
            map.put(new Location(namespace, p), Boolean.TRUE);
        }
        List<String> out = new ArrayList<>(map.size());
        for (Location l : map.keySet()) {
            out.add(l.path());
        }
        return out;
    }

    /** A key hashed and compared like vanilla's {@code ResourceLocation}. */
    private record Location(String namespace, String path) {
        @Override
        public int hashCode() {
            return 31 * namespace.hashCode() + path.hashCode();
        }
    }

    boolean matches(JsonElement ingredient, String item, String where) {
        if (ingredient.isJsonArray()) {
            for (JsonElement e : ingredient.getAsJsonArray()) {
                if (matches(e, item, where)) {
                    return true;
                }
            }
            return false;
        }
        JsonObject o = ingredient.getAsJsonObject();
        if (o.has("type")) {
            String type = o.get("type").getAsString();
            if (type.equals("neoforge:difference")) {
                return matches(o.get("base"), item, where) && !matches(o.get("subtracted"), item, where);
            }
            throw new DatagenException(where + ": ingredient type " + type + " is not read by the material generator");
        }
        if (o.has("item")) {
            return o.get("item").getAsString().equals(item);
        }
        if (o.has("tag")) {
            return tag(o.get("tag").getAsString()).contains(item);
        }
        throw new DatagenException(where + ": unknown ingredient " + o);
    }

    /** The items of an item tag of the checkout (generated and main resources merged, nested tags, then the removals). */
    Set<String> tag(String id) {
        Set<String> cached = tags.get(id);
        if (cached != null) {
            return cached;
        }
        tags.put(id, Set.of()); // a cycle reads as empty instead of recursing forever
        int colon = id.indexOf(':');
        String rel = "data/" + id.substring(0, colon) + "/tags/item/" + id.substring(colon + 1) + ".json";
        Set<String> members = new LinkedHashSet<>();
        List<String> removed = new ArrayList<>();
        boolean found = false;
        for (String root : List.of(TfcCheckout.GENERATED, TfcCheckout.MAIN)) {
            if (!tfc.exists(root + rel)) {
                continue;
            }
            found = true;
            JsonObject json = Json.parse(tfc.readString(root + rel), root + rel).getAsJsonObject();
            for (JsonElement v : json.has("values") ? json.getAsJsonArray("values") : new JsonArray()) {
                String value = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                if (value.startsWith("#")) {
                    members.addAll(tag(value.substring(1)));
                } else {
                    members.add(value);
                }
            }
            for (JsonElement v : json.has("remove") ? json.getAsJsonArray("remove") : new JsonArray()) {
                String value = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                removed.add(value);
            }
        }
        for (String r : removed) {
            if (r.startsWith("#")) {
                members.removeAll(tag(r.substring(1)));
            } else {
                members.remove(r);
            }
        }
        Set<String> out = found ? Set.copyOf(members) : Set.of();
        tags.put(id, out);
        return out;
    }
}
