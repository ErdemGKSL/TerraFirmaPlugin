/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TFC {@code variants} blockstate → CraftEngine {@code state}/{@code states} section.
 *
 * <p>This converter is only used for full opaque cubes. The visual of every appearance (which vanilla state it
 * is drawn with, and the model) comes from a per-block template {@code tfp:visual/<id>} that the visual allocator
 * ({@link dev.tfp.datagen.visuals.VisualAllocator}, M1-DGN-04) writes once per profile × MC version family. The
 * block entry itself keeps only the server-side shape: no appearance, or {@code properties} + {@code variants}
 * with the {@code appearances} map filled by that template. The appearances (name → models) are returned as the
 * allocator's demand. Variant keys are fully specified and non-overlapping.
 */
public final class CeBlockStates {
    private CeBlockStates() {
    }

    /** One weighted/rotated model reference of a blockstate variant. */
    public record ModelRef(String path, int x, int y, boolean uvlock, int weight) {
        public Map<String, Object> toYaml() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", path);
            if (x != 0) {
                m.put("x", x);
            }
            if (y != 0) {
                m.put("y", y);
            }
            if (uvlock) {
                m.put("uvlock", true);
            }
            if (weight != 1) {
                m.put("weight", weight);
            }
            return m;
        }

        public boolean plain() {
            return x == 0 && y == 0 && !uvlock && weight == 1;
        }
    }

    /** Parsed {@code variants} blockstate (already renamed to tfp:). Insertion order = TFC file order. */
    record Variants(Map<String, List<ModelRef>> byKey) {
    }

    static Variants parse(JsonObject blockstate, String source) {
        if (!blockstate.has("variants")) {
            throw new DatagenException(source + ": not a variants blockstate");
        }
        Map<String, List<ModelRef>> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : blockstate.getAsJsonObject("variants").entrySet()) {
            List<ModelRef> models = new ArrayList<>();
            JsonElement v = e.getValue();
            JsonArray arr = v.isJsonArray() ? v.getAsJsonArray() : wrap(v);
            for (JsonElement m : arr) {
                JsonObject o = m.getAsJsonObject();
                models.add(new ModelRef(
                    o.get("model").getAsString(),
                    o.has("x") ? o.get("x").getAsInt() : 0,
                    o.has("y") ? o.get("y").getAsInt() : 0,
                    o.has("uvlock") && o.get("uvlock").getAsBoolean(),
                    o.has("weight") ? o.get("weight").getAsInt() : 1));
            }
            out.put(e.getKey(), models);
        }
        return new Variants(out);
    }

    private static JsonArray wrap(JsonElement e) {
        JsonArray a = new JsonArray();
        a.add(e);
        return a;
    }

    /** Template id of the per-block visual written by the allocator. */
    public static String visualTemplate(String id) {
        return Json.TO_NS + ":visual/" + id;
    }

    /**
     * A converted block: {@code key} is {@code state} or {@code states}, {@code section} the profile-independent
     * YAML, {@code appearances} the appearance name → models that the visual template must provide (the single
     * appearance of a {@code state} block has the name {@code ""}).
     */
    public record CeState(String key, Map<String, Object> section, Map<String, List<ModelRef>> appearances) {
    }

    static CeState toCe(Variants v, String id) {
        Map<String, List<ModelRef>> by = v.byKey();
        String visual = visualTemplate(id);
        if (by.size() == 1 && by.containsKey("")) {
            return new CeState("state", call(visual, Map.of()), Map.of("", List.copyOf(by.get(""))));
        }
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, List<ModelRef>> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        if (randomPillarModels(by) != null) {
            Map<String, Object> axis = new LinkedHashMap<>();
            axis.put("type", "axis");
            axis.put("default", "y");
            props.put("axis", axis);
        } else {
            props.putAll(properties(by));
        }
        for (Map.Entry<String, List<ModelRef>> e : by.entrySet()) {
            String name = e.getKey().replace("=", "_").replace(",", "__");
            appearances.put(name, List.copyOf(e.getValue()));
            variants.put(e.getKey(), Map.of("appearance", name));
        }
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("properties", props);
        states.put("appearances", call(visual, Map.of()));
        states.put("variants", variants);
        return new CeState("states", states, appearances);
    }

    static Map<String, Object> call(String template, Map<String, ?> args) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("template", template);
        if (!args.isEmpty()) {
            // Map.of iterates in a per-JVM random order; sort unordered maps so the output is byte-identical per run.
            m.put("arguments", args instanceof LinkedHashMap<?, ?> || args instanceof java.util.SortedMap<?, ?>
                ? new LinkedHashMap<>(args) : new LinkedHashMap<>(new java.util.TreeMap<>(args)));
        }
        return m;
    }

    /** One CE appearance: the visual (an explicit state or an auto_state) plus the model(s). */
    public static Map<String, Object> appearance(Map<String, Object> visual, List<ModelRef> models) {
        Map<String, Object> a = new LinkedHashMap<>(visual);
        if (models.size() == 1) {
            ModelRef m = models.getFirst();
            a.put("model", m.plain() ? m.path() : m.toYaml());
        } else {
            a.put("models", models.stream().map(ModelRef::toYaml).toList());
        }
        return a;
    }

    /**
     * TFC raw/hardened rock: an {@code axis} property, each axis a random pick of the model and its mirrored twin
     * (2 rotations for x/z, 4 for y), exactly {@link #expandRandomPillar}.
     */
    static String[] randomPillarModels(Map<String, List<ModelRef>> by) {
        if (!by.keySet().equals(Set.of("axis=x", "axis=y", "axis=z"))) {
            return null;
        }
        List<ModelRef> y = by.get("axis=y");
        if (y.size() != 4) {
            return null;
        }
        String a = y.get(0).path();
        String b = y.get(1).path();
        return expandRandomPillar(a, b).equals(by) ? new String[] {a, b} : null;
    }

    /** The TFC blockstate shape of raw/hardened rock (each axis a random pick of a model and its mirrored twin). */
    public static Map<String, List<ModelRef>> expandRandomPillar(String a, String b) {
        Map<String, List<ModelRef>> m = new LinkedHashMap<>();
        m.put("axis=x", List.of(new ModelRef(a, 90, 90, false, 1), new ModelRef(b, 90, 90, false, 1)));
        m.put("axis=y", List.of(new ModelRef(a, 0, 0, false, 1), new ModelRef(b, 0, 0, false, 1),
            new ModelRef(a, 0, 180, false, 1), new ModelRef(b, 0, 180, false, 1)));
        m.put("axis=z", List.of(new ModelRef(a, 90, 0, false, 1), new ModelRef(b, 90, 0, false, 1),
            new ModelRef(a, 90, 180, false, 1), new ModelRef(b, 90, 180, false, 1)));
        return m;
    }

    /** CE properties inferred from the variant keys (generic literal conversion). */
    static Map<String, Object> properties(Map<String, List<ModelRef>> by) {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        for (String key : by.keySet()) {
            if (key.isEmpty()) {
                throw new DatagenException("mixed empty and keyed variants");
            }
            for (String kv : key.split(",")) {
                String[] p = kv.split("=", 2);
                values.computeIfAbsent(p[0], k -> new LinkedHashSet<>()).add(p[1]);
            }
        }
        long combinations = values.values().stream().mapToLong(Set::size).reduce(1, (x, y) -> x * y);
        if (combinations != by.size()) {
            throw new DatagenException("variant keys " + by.keySet() + " do not cover every property combination");
        }
        Map<String, Object> props = new LinkedHashMap<>();
        values.forEach((name, vals) -> props.put(name, property(name, vals)));
        return props;
    }

    private static Map<String, Object> property(String name, Set<String> vals) {
        Map<String, Object> p = new LinkedHashMap<>();
        if (name.equals("axis") && Set.of("x", "y", "z").containsAll(vals)) {
            p.put("type", "axis");
            p.put("default", vals.contains("y") ? "y" : vals.iterator().next());
        } else if (Set.of("true", "false").containsAll(vals)) {
            p.put("type", "boolean");
            p.put("default", false);
        } else {
            p.put("type", "string");
            p.put("values", List.copyOf(vals));
            p.put("default", vals.iterator().next());
        }
        return p;
    }
}
