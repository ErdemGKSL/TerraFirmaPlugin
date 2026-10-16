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
 * <p>Every appearance takes {@code auto_state: solid}: this converter is only used for full opaque cubes. The
 * visual allocator (M1-DGN-04) later replaces the pool choice per profile. Known shapes become calls to our
 * {@code tfp:block_state/*} templates; anything else is emitted literally with fully specified, non-overlapping
 * variant keys.
 */
final class CeBlockStates {
    static final String POOL = "solid";

    private CeBlockStates() {
    }

    /** One weighted/rotated model reference of a blockstate variant. */
    record ModelRef(String path, int x, int y, boolean uvlock, int weight) {
        Map<String, Object> toYaml() {
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

        boolean plain() {
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

    /** Returns {@code ("state" | "states", section)}. */
    static Map.Entry<String, Object> toCe(Variants v) {
        Map<String, List<ModelRef>> by = v.byKey();
        if (by.size() == 1 && by.containsKey("")) {
            List<ModelRef> models = by.get("");
            if (models.size() == 1 && models.getFirst().plain()) {
                return Map.entry("state", call("tfp:block_state/cube", Map.of("cube_model", models.getFirst().path())));
            }
            return Map.entry("state", appearance(models));
        }
        String[] pillar = randomPillarModels(by);
        if (pillar != null) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("pillar_model", pillar[0]);
            args.put("pillar_model_mirrored", pillar[1]);
            return Map.entry("states", call("tfp:block_state/random_pillar", args));
        }
        return Map.entry("states", literal(by));
    }

    static Map<String, Object> call(String template, Map<String, ?> args) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("template", template);
        if (!args.isEmpty()) {
            m.put("arguments", new LinkedHashMap<>(args));
        }
        return m;
    }

    static Map<String, Object> appearance(List<ModelRef> models) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("auto_state", POOL);
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
     * (2 rotations for x/z, 4 for y). Must match {@code tfp:block_state/random_pillar} exactly.
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

    /** The literal blockstate shape that {@code tfp:block_state/random_pillar} stands for. */
    static Map<String, List<ModelRef>> expandRandomPillar(String a, String b) {
        Map<String, List<ModelRef>> m = new LinkedHashMap<>();
        m.put("axis=x", List.of(new ModelRef(a, 90, 90, false, 1), new ModelRef(b, 90, 90, false, 1)));
        m.put("axis=y", List.of(new ModelRef(a, 0, 0, false, 1), new ModelRef(b, 0, 0, false, 1),
            new ModelRef(a, 0, 180, false, 1), new ModelRef(b, 0, 180, false, 1)));
        m.put("axis=z", List.of(new ModelRef(a, 90, 0, false, 1), new ModelRef(b, 90, 0, false, 1),
            new ModelRef(a, 90, 180, false, 1), new ModelRef(b, 90, 180, false, 1)));
        return m;
    }

    /** Generic literal conversion: properties inferred from the variant keys, one appearance per key. */
    static Map<String, Object> literal(Map<String, List<ModelRef>> by) {
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
        Map<String, Object> appearances = new LinkedHashMap<>();
        Map<String, Object> variants = new LinkedHashMap<>();
        for (Map.Entry<String, List<ModelRef>> e : by.entrySet()) {
            String name = e.getKey().replace("=", "_").replace(",", "__");
            appearances.put(name, appearance(e.getValue()));
            variants.put(e.getKey(), Map.of("appearance", name));
        }
        Map<String, Object> states = new LinkedHashMap<>();
        states.put("properties", props);
        states.put("appearances", appearances);
        states.put("variants", variants);
        return states;
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
