/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-INFRA-27: CraftEngine's {@code BlockStateUtils.isCorrectTool} looks the CE item id up in the block's expanded
 * {@code correct_tools}. A {@code #tag} there expands (CE {@code AbstractItemManager.itemIdsByTag}) to the vanilla members
 * of the tag plus the CE items whose {@code settings.tags} list it; a CE item's vanilla material does not count. TFC
 * (Rock.java: {@code requiresCorrectToolForDrops}, no {@code needs_*}/{@code incorrect_for_*} block tags) lets any pickaxe
 * harvest rock, so every TFC metal pickaxe must be in the expansion of a raw rock's {@code correct_tools}, and no other
 * TFC tool kind may be.
 *
 * <p>The test expands the generated YAML the same way, after resolving the {@code tfp:} templates (template list,
 * arguments with the caller winning, {@code ${arg}} / {@code ${arg:-default}} placeholders, overrides, merges).
 */
class CorrectToolTagsTest {
    /** Vanilla 1.21.x item tag members CE adds for a {@code #minecraft:} tag (only the tags our blocks name). */
    private static final Map<String, List<String>> VANILLA_ITEM_TAGS = Map.of(
        "minecraft:pickaxes", List.of("minecraft:wooden_pickaxe", "minecraft:stone_pickaxe", "minecraft:iron_pickaxe",
            "minecraft:golden_pickaxe", "minecraft:diamond_pickaxe", "minecraft:netherite_pickaxe"));
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-z_]+)(?::-([^}]*))?}");

    private static Map<String, Object> templates;
    private static Map<String, Map<String, Object>> items;
    private static Map<String, Map<String, Object>> blocks;
    /** Each CE item's resolved {@code settings.tags}. */
    private static Map<String, List<?>> itemTags;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(path)), "not at the pinned commit");
        var files = Datagen.generate(path, TfcCheckout.PINNED_COMMIT).tree().files();
        templates = new LinkedHashMap<>();
        items = new LinkedHashMap<>();
        blocks = new LinkedHashMap<>();
        files.forEach((name, bytes) -> {
            if (!name.contains("craftengine/") || !name.contains("/configuration/") || !name.endsWith(".yml")) {
                return;
            }
            Object doc = new Yaml().load(new String(bytes, StandardCharsets.UTF_8));
            if (!(doc instanceof Map<?, ?> root)) {
                return;
            }
            root.forEach((section, body) -> {
                String s = String.valueOf(section);
                if (!(body instanceof Map<?, ?> entries)) {
                    return;
                }
                if (s.startsWith("templates")) {
                    ((Map<String, Object>) entries).forEach(templates::put);
                } else if (s.equals("items")) {
                    ((Map<String, Object>) entries).forEach((id, e) -> items.put(id, (Map<String, Object>) e));
                } else if (s.equals("blocks")) {
                    ((Map<String, Object>) entries).forEach((id, e) -> blocks.put(id, (Map<String, Object>) e));
                }
            });
        });
        assertThat(templates).containsKeys("tfp:settings/rock", "tfp:item/tool");
        itemTags = new LinkedHashMap<>();
        items.forEach((id, item) -> {
            if (resolve(item, Map.of()) instanceof Map<?, ?> r && r.get("settings") instanceof Map<?, ?> is
                    && is.get("tags") instanceof List<?> tags) {
                itemTags.put(id, tags);
            }
        });
    }

    @Test
    void everyMetalPickaxeIsACorrectToolForRawRockAndNoOtherToolKindIs() {
        Set<String> pickaxes = idsWithPrefix("tfp:metal/pickaxe/");
        assertThat(pickaxes).contains("tfp:metal/pickaxe/bronze", "tfp:metal/pickaxe/copper", "tfp:metal/pickaxe/red_steel");
        for (String rock : List.of("tfp:rock/raw/granite", "tfp:rock/raw/chalk", "tfp:rock/cobble/basalt",
                "tfp:rock/hardened/gneiss")) {
            Set<String> correct = correctTools(rock);
            assertThat(correct).as(rock).containsAll(pickaxes).contains("minecraft:iron_pickaxe");
            for (String kind : List.of("axe", "shovel", "hoe", "knife", "hammer", "scythe", "saw", "propick", "chisel")) {
                assertThat(correct).as(rock + " vs " + kind).noneMatch(id -> id.startsWith("tfp:metal/" + kind + "/"));
            }
        }
    }

    @Test
    void everyBlockThatRequiresAPickaxeAcceptsTheBronzePickaxeButNotTheBronzeAxe() {
        int checked = 0;
        for (String id : blocks.keySet()) {
            Set<String> correct = correctTools(id);
            if (!correct.contains("minecraft:iron_pickaxe")) {
                continue;
            }
            checked++;
            assertThat(correct).as(id).contains("tfp:metal/pickaxe/bronze").doesNotContain("tfp:metal/axe/bronze");
        }
        assertThat(checked).as("rock and ore blocks with correct_tools").isGreaterThan(500);
    }

    private static Set<String> idsWithPrefix(String prefix) {
        Set<String> out = new HashSet<>();
        items.keySet().stream().filter(id -> id.startsWith(prefix)).forEach(out::add);
        return out;
    }

    /** The block's {@code correct_tools}, expanded as CraftEngine does at load. */
    @SuppressWarnings("unchecked")
    private static Set<String> correctTools(String blockId) {
        Map<String, Object> block = blocks.get(blockId);
        assertThat(block).as(blockId).isNotNull();
        Object settings = resolve(block.get("settings"), Map.of());
        Set<String> out = new HashSet<>();
        if (!(settings instanceof Map<?, ?> m) || !(m.get("correct_tools") instanceof List<?> tools)) {
            return out;
        }
        for (Object t : tools) {
            String s = String.valueOf(t);
            if (s.startsWith("#")) {
                String tag = s.substring(1);
                out.addAll(VANILLA_ITEM_TAGS.getOrDefault(tag, List.of()));
                itemTags.forEach((id, tags) -> {
                    if (tags.contains(tag)) {
                        out.add(id);
                    }
                });
            } else {
                out.add(s);
            }
        }
        return out;
    }

    /** Resolves CE template calls in {@code node} with the incoming {@code args}. */
    @SuppressWarnings("unchecked")
    private static Object resolve(Object node, Map<String, Object> args) {
        if (node instanceof Map<?, ?> map) {
            if (map.containsKey("template")) {
                Map<String, Object> eff = new LinkedHashMap<>();
                if (map.get("arguments") instanceof Map<?, ?> own) {
                    own.forEach((k, v) -> eff.put(String.valueOf(k), resolve(v, args)));
                }
                eff.putAll(args);
                Object names = map.get("template");
                List<Object> list = names instanceof List<?> l ? (List<Object>) l : List.of(names);
                Map<String, Object> out = new LinkedHashMap<>();
                for (Object name : list) {
                    Object body = templates.get(String.valueOf(name));
                    assertThat(body).as("template " + name).isNotNull();
                    merge(out, (Map<String, Object>) resolve(body, eff));
                }
                if (map.get("merges") instanceof Map<?, ?> merges) {
                    merge(out, (Map<String, Object>) resolve(merges, eff));
                }
                if (map.get("overrides") instanceof Map<?, ?> overrides) {
                    ((Map<String, Object>) resolve(overrides, eff)).forEach(out::put);
                }
                return out;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), resolve(v, args)));
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            list.forEach(v -> out.add(resolve(v, args)));
            return out;
        }
        if (node instanceof String s) {
            Matcher m = PLACEHOLDER.matcher(s);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                Object v = args.containsKey(m.group(1)) ? args.get(m.group(1)) : m.group(2);
                m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group() : String.valueOf(v)));
            }
            m.appendTail(sb);
            return sb.toString();
        }
        return node;
    }

    /** CE template merge: maps merge deeply, lists concatenate, other values are replaced. */
    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> into, Map<String, Object> from) {
        from.forEach((k, v) -> {
            Object old = into.get(k);
            if (old instanceof Map<?, ?> om && v instanceof Map<?, ?> vm) {
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) om);
                merge(copy, (Map<String, Object>) vm);
                into.put(k, copy);
            } else if (old instanceof List<?> ol && v instanceof List<?> vl) {
                List<Object> copy = new ArrayList<>(ol);
                copy.addAll(vl);
                into.put(k, copy);
            } else {
                into.put(k, v);
            }
        });
    }
}
