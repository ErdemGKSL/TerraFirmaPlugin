/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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

    @SuppressWarnings("unchecked")
    static void assertValid(SortedMap<String, byte[]> files) {
        Map<String, Object> templates = new HashMap<>();
        Map<String, String> bodies = new HashMap<>();
        List<Map.Entry<String, Map<String, Object>>> configs = new ArrayList<>();
        for (var e : files.entrySet()) {
            if (!e.getKey().startsWith(AssetPipeline.CONFIG) || !e.getKey().endsWith(".yml")) {
                continue;
            }
            Map<String, Object> root = (Map<String, Object>) parse(new String(e.getValue(), StandardCharsets.UTF_8), e.getKey());
            for (var section : root.entrySet()) {
                if (section.getKey().startsWith("templates")) {
                    for (var t : ((Map<String, Object>) section.getValue()).entrySet()) {
                        assertThat(templates.put(t.getKey(), t.getValue())).as("duplicate template " + t.getKey()).isNull();
                        bodies.put(t.getKey(), new Yaml().dump(t.getValue()));
                    }
                } else {
                    configs.add(Map.entry(e.getKey(), root));
                }
            }
        }
        assertThat(templates).isNotEmpty();
        Set<String> ids = new HashSet<>();
        for (var c : new HashSet<>(configs)) {
            for (var section : c.getValue().entrySet()) {
                assertThat(section.getKey()).as(c.getKey()).isIn("blocks", "items");
                for (var entry : ((Map<String, Object>) section.getValue()).entrySet()) {
                    assertThat(ids.add(section.getKey() + "/" + entry.getKey())).as("duplicate id " + entry.getKey()).isTrue();
                    assertThat(entry.getKey()).startsWith("tfp:");
                    walk(entry.getValue(), templates, bodies, c.getKey() + " " + entry.getKey());
                }
            }
        }
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
                for (Object key : ((Map<?, ?>) m.get("variants")).keySet()) {
                    for (String kv : key.toString().split(",")) {
                        String[] p = kv.split("=", 2);
                        assertThat(props).as(where + " variant " + key).containsKey(p[0]);
                    }
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

    @SuppressWarnings("unchecked")
    static void assertRandomPillarTemplateMatches() {
        String text;
        try (InputStream in = AssetPipeline.class.getResourceAsStream("ce/templates/block_states.yml")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        text = text.replace("${pillar_model_mirrored}", "B").replace("${pillar_model}", "A");
        Map<String, Object> root = (Map<String, Object>) parse(text, "block_states.yml");
        Object template = ((Map<String, Object>) root.get("templates#block_states")).get("tfp:block_state/random_pillar");
        assertThat(template).isEqualTo(CeBlockStates.literal(CeBlockStates.expandRandomPillar("A", "B")));
    }
}
