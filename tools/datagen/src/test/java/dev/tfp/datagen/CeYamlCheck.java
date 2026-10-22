/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Static checks on generated CraftEngine YAML that catch the silent CE failure modes: tabs, duplicate keys or
 * ids, calls to undefined templates, template arguments the caller forgot, and invalid variant keys.
 */
final class CeYamlCheck {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-z_]+)(:-[^}]*)?}");
    private static final Set<String> BUILTINS = Set.of("__ID__", "__NAMESPACE__");

    private CeYamlCheck() {
    }

    static Object parse(String text, String what) {
        LoaderOptions opts = new LoaderOptions();
        opts.setAllowDuplicateKeys(false);
        assertThat(text).as(what + " has tabs").doesNotContain("\t");
        return new Yaml(opts).load(text);
    }

    /**
     * Validates the CE pack once per allocation: the pack's own templates plus that allocation's
     * {@code visual_templates.yml} must define every template the blocks call, and every variant must name an
     * appearance the visual template provides.
     */
    @SuppressWarnings("unchecked")
    static void assertValid(SortedMap<String, byte[]> files) {
        Map<String, Object> templates = new HashMap<>();
        Map<String, String> bodies = new HashMap<>();
        List<Map.Entry<String, Map<String, Object>>> configs = new ArrayList<>();
        List<String> allocations = new ArrayList<>();
        for (var e : files.entrySet()) {
            if (e.getKey().startsWith("visuals/") && e.getKey().endsWith("/visual_templates.yml")) {
                allocations.add(e.getKey());
            }
            if (!e.getKey().startsWith(AssetPipeline.CONFIG) || !e.getKey().endsWith(".yml")) {
                continue;
            }
            Map<String, Object> root = (Map<String, Object>) parse(new String(e.getValue(), StandardCharsets.UTF_8), e.getKey());
            if (!collectTemplates(root, templates, bodies)) {
                configs.add(Map.entry(e.getKey(), root));
            }
        }
        assertThat(templates).isNotEmpty();
        assertThat(allocations).as("visual allocations").isNotEmpty();
        for (String alloc : allocations) {
            Map<String, Object> t = new HashMap<>(templates);
            Map<String, String> b = new HashMap<>(bodies);
            Map<String, Object> root = (Map<String, Object>) parse(new String(files.get(alloc), StandardCharsets.UTF_8), alloc);
            assertThat(root.keySet()).as(alloc).containsExactly("templates#visuals");
            collectTemplates(root, t, b);
            String mappings = alloc.replace("visual_templates.yml", "visual_mappings.yml");
            assertThat(files).as(alloc).containsKey(mappings);
            Map<String, Object> m = (Map<String, Object>) parse(new String(files.get(mappings), StandardCharsets.UTF_8), mappings);
            assertThat(m.keySet()).as(mappings).containsExactly("block_state_mappings");
            for (var e : ((Map<String, Object>) m.get("block_state_mappings")).entrySet()) {
                assertThat(e.getKey()).as(mappings).matches(STATE);
                assertThat((String) e.getValue()).as(mappings).matches(STATE);
            }
            Set<String> ids = new HashSet<>();
            for (var c : configs) {
                for (var section : c.getValue().entrySet()) {
                    if (section.getKey().equals("block_state_mappings")) {
                        // A block's own fixed mapping (the charcoal forge hides real snow[layers=7]): full states, one
                        // hop, and never a state the allocation maps or claims.
                        var allocated = (Map<String, Object>) m.get("block_state_mappings");
                        for (var e : ((Map<String, Object>) section.getValue()).entrySet()) {
                            assertThat(e.getKey()).as(c.getKey()).matches(STATE);
                            assertThat((String) e.getValue()).as(c.getKey()).matches(STATE);
                            assertThat(allocated).as(c.getKey() + " vs " + mappings).doesNotContainKey(e.getKey())
                                .doesNotContainKey((String) e.getValue());
                        }
                        continue;
                    }
                    assertThat(section.getKey()).as(c.getKey()).isIn("blocks", "items", "equipments");
                    for (var entry : ((Map<String, Object>) section.getValue()).entrySet()) {
                        assertThat(ids.add(section.getKey() + "/" + entry.getKey())).as("duplicate id " + entry.getKey()).isTrue();
                        assertThat(entry.getKey()).startsWith("tfp:");
                        walk(entry.getValue(), t, b, alloc + ": " + c.getKey() + " " + entry.getKey());
                    }
                }
            }
        }
    }

    private static final String STATE = "minecraft:[a-z0-9_]+(\\[[a-z0-9_]+=[a-z0-9_]+(,[a-z0-9_]+=[a-z0-9_]+)*])?";

    @SuppressWarnings("unchecked")
    private static boolean collectTemplates(Map<String, Object> root, Map<String, Object> templates, Map<String, String> bodies) {
        boolean any = false;
        for (var section : root.entrySet()) {
            if (section.getKey().startsWith("templates")) {
                any = true;
                for (var t : ((Map<String, Object>) section.getValue()).entrySet()) {
                    assertThat(templates.put(t.getKey(), t.getValue())).as("duplicate template " + t.getKey()).isNull();
                    bodies.put(t.getKey(), new Yaml().dump(t.getValue()));
                }
            }
        }
        return any;
    }

    @SuppressWarnings("unchecked")
    private static void walk(Object node, Map<String, Object> templates, Map<String, String> bodies, String where) {
        if (node instanceof Map<?, ?> m) {
            if (m.containsKey("template")) {
                Object t = m.get("template");
                List<String> ids = t instanceof List<?> l ? (List<String>) l : List.of((String) t);
                Map<String, Object> args = m.containsKey("arguments") ? (Map<String, Object>) m.get("arguments") : Map.of();
                for (String id : ids) {
                    assertThat(templates).as(where + " calls undefined template").containsKey(id);
                    Set<String> required = required(id, templates, bodies, new HashSet<>());
                    assertThat(args.keySet()).as(where + " arguments for " + id).containsAll(required);
                }
            }
            if (m.containsKey("properties") && m.containsKey("variants")) {
                Map<String, Map<String, Object>> props = (Map<String, Map<String, Object>>) m.get("properties");
                Map<String, Object> apps = (Map<String, Object>) m.get("appearances");
                if (apps != null && apps.containsKey("template")) {
                    apps = (Map<String, Object>) templates.get((String) apps.get("template"));
                }
                for (var v : ((Map<?, ?>) m.get("variants")).entrySet()) {
                    for (String kv : v.getKey().toString().split(",")) {
                        String[] p = kv.split("=", 2);
                        assertThat(props).as(where + " variant " + v.getKey()).containsKey(p[0]);
                    }
                    Object app = ((Map<?, ?>) v.getValue()).get("appearance");
                    assertThat(apps).as(where + " appearances").isNotNull();
                    assertThat(apps).as(where + " variant " + v.getKey()).containsKey((String) app);
                }
            }
            m.values().forEach(v -> walk(v, templates, bodies, where));
        } else if (node instanceof List<?> l) {
            l.forEach(v -> walk(v, templates, bodies, where));
        }
    }

    /** Placeholders without a default used by a template (and the templates it calls), minus arguments it sets. */
    @SuppressWarnings("unchecked")
    private static Set<String> required(String id, Map<String, Object> templates, Map<String, String> bodies, Set<String> seen) {
        Set<String> req = new HashSet<>();
        if (!seen.add(id)) {
            return req;
        }
        Matcher m = PLACEHOLDER.matcher(bodies.get(id));
        while (m.find()) {
            if (m.group(2) == null && !BUILTINS.contains(m.group(1))) {
                req.add(m.group(1));
            }
        }
        Object body = templates.get(id);
        if (body instanceof Map<?, ?> map) {
            if (map.get("arguments") instanceof Map<?, ?> own) {
                req.removeAll(own.keySet());
            }
            Object t = map.get("template");
            List<String> nested = t == null ? List.of() : t instanceof List<?> l ? (List<String>) l : List.of((String) t);
            for (String n : nested) {
                assertThat(templates).as(id + " calls undefined template").containsKey(n);
                Set<String> inner = required(n, templates, bodies, seen);
                if (map.get("arguments") instanceof Map<?, ?> own) {
                    inner.removeAll(own.keySet());
                }
                req.addAll(inner);
            }
        }
        return req;
    }

    /**
     * Raw rock's visual template (every allocation) draws exactly TFC's random pillar blockstate: per axis the
     * models of {@link CeBlockStates#expandRandomPillar}.
     */
    @SuppressWarnings("unchecked")
    static void assertRandomPillarVisualsMatch(SortedMap<String, byte[]> files, String rockId) {
        String a = "tfp:block/" + rockId;
        Map<String, List<CeBlockStates.ModelRef>> expected = CeBlockStates.expandRandomPillar(a, a + "_mirrored");
        int checked = 0;
        for (var e : files.entrySet()) {
            if (!e.getKey().endsWith("/visual_templates.yml")) {
                continue;
            }
            Map<String, Object> root = (Map<String, Object>) parse(new String(e.getValue(), StandardCharsets.UTF_8), e.getKey());
            Map<String, Object> apps = (Map<String, Object>) ((Map<String, Object>) root.get("templates#visuals"))
                .get(CeBlockStates.visualTemplate(rockId));
            assertThat(apps.keySet()).containsExactly("axis_x", "axis_y", "axis_z");
            expected.forEach((key, models) -> {
                Map<String, Object> app = (Map<String, Object>) apps.get(key.replace("=", "_"));
                assertThat(app.get("models")).as(e.getKey() + " " + key)
                    .isEqualTo(models.stream().map(CeBlockStates.ModelRef::toYaml).toList());
                assertThat(app.containsKey("state") != app.containsKey("auto_state")).isTrue();
            });
            checked++;
        }
        assertThat(checked).isEqualTo(20);
    }
}
