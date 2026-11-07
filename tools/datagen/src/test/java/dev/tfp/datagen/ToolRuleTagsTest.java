/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.toolblocktags.ToolBlockTags;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * PAR-PROC-27 build guard: every block tag a generated CraftEngine item names in a {@code minecraft:tool} rule (its own
 * {@code data.tool} / {@code components."minecraft:tool"}, or through a template, plus every {@code mineable_tag}
 * argument) must exist on the server. Vanilla's tool codec rejects a rule naming a missing block tag, and CE then
 * cannot build the item at all ({@code ce item give Bot tfp:gem_saw}: "Missing tag: tfp:mineable/glass_saw in
 * minecraft:block", found by the re-review of PAR-PROC-23). A tag passes when it is a vanilla block tag present on every
 * supported version ({@link #VANILLA}) or one datagen ships in the bootstrap datapack ({@link ToolBlockTags}); a
 * {@code c:} tag needs the datapack too, since Paper ships no convention tags.
 */
class ToolRuleTagsTest {
    /** Vanilla block tags a tool rule may name: present on 1.21.4 through 26.x. Extend only with real vanilla tags. */
    static final Set<String> VANILLA = Set.of(
        "minecraft:mineable/axe", "minecraft:mineable/hoe", "minecraft:mineable/pickaxe", "minecraft:mineable/shovel",
        "minecraft:sword_efficient", "minecraft:logs", "minecraft:leaves", "minecraft:wool", "minecraft:planks",
        "minecraft:incorrect_for_wooden_tool", "minecraft:incorrect_for_stone_tool", "minecraft:incorrect_for_iron_tool",
        "minecraft:incorrect_for_gold_tool", "minecraft:incorrect_for_diamond_tool",
        "minecraft:incorrect_for_netherite_tool", "minecraft:needs_stone_tool", "minecraft:needs_iron_tool",
        "minecraft:needs_diamond_tool");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-z_]+)(?::-([^}]*))?}");

    /** One block tag named by an item's tool rule (or mineable_tag argument). */
    record Named(String item, String tag, String where) {
    }

    /** Every block tag each CE item of {@code files} names in a tool rule, resolved through its templates. */
    @SuppressWarnings("unchecked")
    static List<Named> toolRuleTags(Map<String, byte[]> files) {
        Map<String, Object> templates = new HashMap<>();
        Map<String, Map.Entry<String, Object>> items = new TreeMap<>();
        for (var e : files.entrySet()) {
            if (!e.getKey().startsWith(AssetPipeline.CONFIG) || !e.getKey().endsWith(".yml")) {
                continue;
            }
            Object root = CeYamlCheck.parse(new String(e.getValue(), StandardCharsets.UTF_8), e.getKey());
            if (!(root instanceof Map<?, ?> sections)) {
                continue;
            }
            for (var s : ((Map<String, Object>) sections).entrySet()) {
                String section = s.getKey().contains("#") ? s.getKey().substring(0, s.getKey().indexOf('#')) : s.getKey();
                if (!(s.getValue() instanceof Map<?, ?> entries)) {
                    continue;
                }
                for (var en : ((Map<String, Object>) entries).entrySet()) {
                    if (section.equals("templates")) {
                        templates.put(en.getKey(), en.getValue());
                    } else if (section.equals("items")) {
                        items.put(en.getKey(), Map.entry(e.getKey(), en.getValue()));
                    }
                }
            }
        }
        List<Named> out = new ArrayList<>();
        items.forEach((id, src) -> collect(id, src.getValue(), Map.of(), templates, src.getKey(), out, 0));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collect(String item, Object node, Map<String, Object> args, Map<String, Object> templates,
            String where, List<Named> out, int depth) {
        if (depth > 16) {
            throw new AssertionError(item + ": template nesting deeper than 16 at " + where);
        }
        Object n = substitute(node, args, item, where);
        if (n instanceof List<?> list) {
            list.forEach(v -> collect(item, v, Map.of(), templates, where, out, depth + 1));
            return;
        }
        if (!(n instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) raw;
        Map<String, Object> callArgs = new LinkedHashMap<>(args);
        if (map.get("arguments") instanceof Map<?, ?> a) {
            callArgs.putAll((Map<String, Object>) a);
            if (a.get("mineable_tag") instanceof String t) {
                out.add(new Named(item, t, where + " mineable_tag"));
            }
        }
        Object template = map.get("template");
        List<Object> calls = template instanceof List<?> l ? new ArrayList<>(l) : template == null ? List.of() : List.of(template);
        for (Object t : calls) {
            Object body = templates.get(String.valueOf(t));
            if (body == null) {
                throw new AssertionError(item + ": unknown template " + t + " at " + where);
            }
            collect(item, body, callArgs, templates, where + " > " + t, out, depth + 1);
        }
        for (var e : map.entrySet()) {
            String key = e.getKey();
            if (key.equals("template") || key.equals("arguments")) {
                continue;
            }
            if ((key.equals("tool") || key.equals("minecraft:tool")) && e.getValue() instanceof Map<?, ?> tool) {
                if (tool.get("rules") instanceof List<?> rules) {
                    for (Object r : rules) {
                        if (r instanceof Map<?, ?> rule) {
                            Object blocks = rule.get("blocks");
                            List<?> entries = blocks instanceof List<?> bl ? bl : blocks == null ? List.of() : List.of(blocks);
                            for (Object b : entries) {
                                String s = String.valueOf(b);
                                if (s.startsWith("#")) {
                                    out.add(new Named(item, s.substring(1), where + " " + key + " rule"));
                                }
                            }
                        }
                    }
                }
                continue;
            }
            collect(item, e.getValue(), Map.of(), templates, where, out, depth + 1);
        }
    }

    /** Fills the {@code ${name}} / {@code ${name:-default}} placeholders of {@code node} from {@code args}. */
    @SuppressWarnings("unchecked")
    private static Object substitute(Object node, Map<String, Object> args, String item, String where) {
        if (args.isEmpty()) {
            return node;
        }
        if (node instanceof String s) {
            Matcher m = PLACEHOLDER.matcher(s);
            StringBuilder b = new StringBuilder();
            while (m.find()) {
                Object v = args.get(m.group(1));
                // an unfilled placeholder stays: a tool rule still holding one fails in violations()
                String r = v != null ? String.valueOf(v) : m.group(2) != null ? m.group(2) : m.group();
                m.appendReplacement(b, Matcher.quoteReplacement(r));
            }
            m.appendTail(b);
            return b.toString();
        }
        if (node instanceof List<?> list) {
            return list.stream().map(v -> substitute(v, args, item, where)).toList();
        }
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            ((Map<String, Object>) map).forEach((k, v) -> out.put(k, k.equals("arguments") ? v : substitute(v, args, item, where)));
            // a nested call's arguments are filled from ours too
            if (out.get("arguments") instanceof Map<?, ?> a) {
                out.put("arguments", substitute(a, args, item, where));
            }
            return out;
        }
        return node;
    }

    /** The tool-rule tags of {@code tree} that the server would not have: "item: #tag (where)". */
    static List<String> violations(OutputTree tree) {
        List<String> bad = new ArrayList<>();
        for (Named n : toolRuleTags(tree.files())) {
            boolean ok = !n.tag().contains("${") && n.tag().indexOf(':') > 0
                && (n.tag().startsWith("minecraft:") ? VANILLA.contains(n.tag())
                    : tree.contains(ToolBlockTags.serverTagPath(n.tag())));
            if (!ok) {
                bad.add(n.item() + ": #" + n.tag() + " (" + n.where() + ")");
            }
        }
        return bad;
    }

    private static final String TEMPLATES = """
        templates#items:
          tfp:item/tool:
            material: ${material}
            data:
              tool:
                rules:
                  - blocks: "#${mineable_tag}"
                    speed: 2.0
                    correct_for_drops: true
            model: ${__NAMESPACE__}:item/${__ID__}
          tfp:item/wrapper:
            template: tfp:item/tool
            arguments:
              material: ${material}
              mineable_tag: ${inner_tag:-tfp:mineable/knife}
        """;

    private static OutputTree tree(String items) {
        OutputTree out = new OutputTree();
        out.put(AssetPipeline.CONFIG + "templates/items.yml", TEMPLATES);
        out.put(AssetPipeline.CONFIG + "test.yml", "items:\n" + items);
        out.put(ToolBlockTags.serverTagPath("tfp:mineable/knife"), "{\"values\":[]}");
        return out;
    }

    @Test
    void shippedAndVanillaTagsPass() {
        OutputTree out = tree("""
              tfp:knife:
                template: tfp:item/tool
                arguments: {material: iron_sword, mineable_tag: tfp:mineable/knife}
              tfp:axe:
                template: tfp:item/tool
                arguments: {material: iron_axe, mineable_tag: minecraft:mineable/axe}
              tfp:wrapped:
                template: tfp:item/wrapper
                arguments: {material: iron_hoe}
              tfp:raw:
                material: stick
                data:
                  components:
                    minecraft:tool:
                      rules:
                        - blocks: ["#minecraft:logs", "minecraft:stone"]
                          speed: 3.0
            """);
        assertThat(toolRuleTags(out.files())).extracting(Named::item).contains("tfp:knife", "tfp:axe", "tfp:wrapped", "tfp:raw");
        assertThat(violations(out)).isEmpty();
    }

    /** The done criterion: the guard fails when a tool rule names an unshipped tfp: tag, in every form. */
    @Test
    void unshippedTagsFail() {
        OutputTree out = tree("""
              tfp:gem_saw:
                template: tfp:item/tool
                arguments: {material: iron_pickaxe, mineable_tag: tfp:mineable/glass_saw}
              tfp:wrapped:
                template: tfp:item/wrapper
                arguments: {material: iron_hoe, inner_tag: tfp:mineable/scythe}
              tfp:unfilled:
                template: tfp:item/tool
                arguments: {material: iron_hoe}
              tfp:raw:
                material: stick
                data:
                  components:
                    minecraft:tool:
                      rules:
                        - blocks: "#tfp:mineable/hammer"
                          speed: 3.0
              tfp:inline:
                material: stick
                data:
                  tool:
                    rules:
                      - blocks: ["#c:glass_blocks", "#minecraft:not_a_tag"]
                        speed: 3.0
            """);
        assertThat(String.join("\n", violations(out)))
            .contains("tfp:gem_saw: #tfp:mineable/glass_saw", "tfp:wrapped: #tfp:mineable/scythe",
                "tfp:raw: #tfp:mineable/hammer", "tfp:inline: #c:glass_blocks", "tfp:inline: #minecraft:not_a_tag",
                "tfp:unfilled: #${mineable_tag}");
    }

    /** The item's test on the real checkout: no generated tool rule names a tag the server lacks. */
    @Test
    void realCheckout() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(path)),
            "TFC checkout is not at the pinned commit");
        OutputTree tree = Datagen.generate(path, TfcCheckout.PINNED_COMMIT).tree();
        List<Named> named = toolRuleTags(tree.files());
        assertThat(named).extracting(Named::tag).contains("tfp:mineable/glass_saw", "tfp:mineable/knife",
            "tfp:mineable/hammer", "tfp:mineable/scythe", "minecraft:mineable/pickaxe");
        assertThat(named).extracting(Named::item).contains("tfp:gem_saw", "tfp:metal/knife/bronze",
            "tfp:metal/hammer/bronze", "tfp:metal/scythe/bronze", "tfp:metal/saw/bronze");
        assertThat(violations(tree)).isEmpty();
        // and the guard would catch a removed datapack tag on the real content
        OutputTree broken = new OutputTree();
        tree.files().forEach((p, b) -> {
            if (!p.equals(ToolBlockTags.serverTagPath("tfp:mineable/knife"))) {
                broken.put(p, b);
            }
        });
        assertThat(violations(broken)).isNotEmpty().allMatch(v -> v.contains("#tfp:mineable/knife"));
    }
}
