/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Deterministic run report: counts, skipped ids with reasons, unresolved refs, divergences. */
public final class Report {
    private final Map<String, Integer> counts = new TreeMap<>();
    private final Map<String, Integer> data = new TreeMap<>();
    private final Map<String, Integer> lang = new TreeMap<>();
    private final Set<String> missing = new TreeSet<>();
    private final Set<String> emitted = new TreeSet<>();
    private final Map<String, String> skipped = new TreeMap<>();
    private final Map<String, Set<String>> divergences = new TreeMap<>();

    void count(String key) {
        counts.merge(key, 1, Integer::sum);
    }

    void data(Map<String, Integer> perKind) {
        data.putAll(perKind);
    }

    void missing(String what) {
        missing.add(what);
    }

    void emitted(String id) {
        emitted.add(id);
    }

    Set<String> emittedIds() {
        return Set.copyOf(emitted);
    }

    void skipped(String id, String reason) {
        skipped.put(id, reason);
    }

    void lang(String locale, int keys) {
        lang.put(locale, keys);
    }

    void divergence(String what, String id) {
        divergences.computeIfAbsent(what, k -> new TreeSet<>()).add(id);
    }

    public Set<String> missing() {
        return Set.copyOf(missing);
    }

    public int count(String key, int dflt) {
        return counts.getOrDefault(key, dflt);
    }

    public int emittedCount() {
        return emitted.size();
    }

    public int skippedCount() {
        return skipped.size();
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        JsonObject c = new JsonObject();
        counts.forEach(c::addProperty);
        c.addProperty("ce_blocks", emitted.size());
        c.addProperty("ce_blocks_skipped", skipped.size());
        o.add("counts", c);
        JsonObject d = new JsonObject();
        data.forEach(d::addProperty);
        o.add("data_files", d);
        JsonObject l = new JsonObject();
        lang.forEach(l::addProperty);
        o.add("lang_keys", l);
        JsonArray m = new JsonArray();
        missing.forEach(m::add);
        o.add("unresolved_refs", m);
        JsonObject dv = new JsonObject();
        divergences.forEach((k, ids) -> dv.addProperty(k, ids.size() + " blocks"));
        o.add("divergences", dv);
        JsonObject s = new JsonObject();
        skipped.forEach(s::addProperty);
        o.add("ce_blocks_skipped", s);
        return o;
    }
}
