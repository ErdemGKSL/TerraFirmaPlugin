/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-14: the common-tag table from the real NeoForge jar (Gradle passes -Dtfp.neoforgeJar). */
class CommonTagsTest {
    /** The {@code c:} tags of {@code data/external_tags.json} (referenced by TFC's data, defined by NeoForge). */
    private static final Map<String, List<String>> EXTERNAL = Map.of(
        "block", List.of("c:chests", "c:cobblestones", "c:glass_blocks", "c:glass_panes"),
        "item", List.of("c:bricks", "c:chests", "c:dusts", "c:dusts/redstone", "c:dyes", "c:dyes/black", "c:dyes/red",
            "c:feathers", "c:leathers", "c:storage_blocks/slime", "c:tools/bow", "c:tools/crossbow"));

    private static Path jar() {
        String p = System.getProperty(CommonTags.JAR_PROPERTY);
        assumeTrue(p != null && Files.isRegularFile(Path.of(p)), "no NeoForge jar (-D" + CommonTags.JAR_PROPERTY + ")");
        return Path.of(p);
    }

    private static JsonObject table() {
        return JsonParser.parseString(CommonTags.generate(jar())).getAsJsonObject();
    }

    /** Whether {@code id} is in {@code tag}, following {@code #c:} references inside the table. */
    private static boolean reaches(JsonObject table, String type, String tag, String id) {
        return reaches(table.getAsJsonObject(type), tag, id, new HashSet<>());
    }

    private static boolean reaches(JsonObject tags, String tag, String id, Set<String> seen) {
        if (!seen.add(tag) || !tags.has(tag)) {
            return false;
        }
        for (JsonElement e : tags.getAsJsonArray(tag)) {
            String ref = e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString();
            if (ref.equals(id) || ref.startsWith("#") && reaches(tags, ref.substring(1), id, seen)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void vanillaMembersOfTheCommonTags() {
        JsonObject t = table();
        assertThat(t.getAsJsonObject("item").getAsJsonArray("c:rods/wooden").toString()).contains("\"minecraft:stick\"");
        assertThat(t.getAsJsonObject("item").getAsJsonArray("c:tools/bow").toString()).contains("\"minecraft:bow\"");
        assertThat(reaches(t, "block", "c:glass_blocks", "minecraft:glass")).isTrue();
        assertThat(reaches(t, "item", "c:glass_blocks", "minecraft:glass")).isTrue();
        assertThat(reaches(t, "block", "c:chests", "minecraft:chest")).isTrue();
        assertThat(reaches(t, "item", "c:chests", "minecraft:chest")).isTrue();
        assertThat(reaches(t, "block", "c:cobblestones", "minecraft:cobblestone")).isTrue();
        assertThat(reaches(t, "block", "c:sands", "minecraft:sand")).isTrue();
        assertThat(reaches(t, "item", "c:leathers", "minecraft:leather")).isTrue();
        assertThat(reaches(t, "item", "c:feathers", "minecraft:feather")).isTrue();
        assertThat(reaches(t, "item", "c:dusts/redstone", "minecraft:redstone")).isTrue();
        assertThat(reaches(t, "item", "c:dyes/black", "minecraft:black_dye")).isTrue();
        assertThat(reaches(t, "item", "c:tools/shear", "minecraft:shears")).isTrue();
        assertThat(reaches(t, "fluid", "c:water", "minecraft:water")).isTrue();
        assertThat(reaches(t, "entity_type", "c:boats", "minecraft:boat")).isTrue(); // 1.21.1 ids, as NeoForge wrote them
        assertThat(reaches(t, "block", "c:sands", "minecraft:dirt")).isFalse();
    }

    @Test
    void onlyVanillaAndCommonIdsSortedAndHeaded() {
        JsonObject t = table();
        assertThat(t.keySet()).containsExactly("block", "entity_type", "fluid", "item", "license", "sha256", "source");
        assertThat(t.get("source").getAsString()).isEqualTo("NeoForge 21.1.234 universal jar, data/c/tags");
        assertThat(t.get("license").getAsString()).startsWith("LGPL-2.1-only");
        assertThat(t.get("sha256").getAsString()).matches("[0-9a-f]{64}");
        for (String type : CommonTags.TYPES) {
            List<String> keys = new ArrayList<>(t.getAsJsonObject(type).keySet());
            assertThat(keys).isSorted().allMatch(k -> k.startsWith("c:"));
            for (String k : keys) {
                for (JsonElement e : t.getAsJsonObject(type).getAsJsonArray(k)) {
                    String ref = e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString();
                    assertThat(ref.replace("#", "")).as("%s %s", type, k).matches("(minecraft|c):[a-z0-9_/.\\-]+");
                }
            }
        }
        assertThat(t.getAsJsonObject("item").size()).isGreaterThan(200);
        assertThat(t.getAsJsonObject("block").size()).isGreaterThan(100);
        assertThat(t.toString()).doesNotContain("forge:").doesNotContain("worldgen");
    }

    @Test
    void everyCommonTagTfcUsesHasAnEntry() throws IOException {
        JsonObject t = table();
        EXTERNAL.forEach((type, tags) -> assertThat(t.getAsJsonObject(type).keySet()).as(type).containsAll(tags));
        // Every c: tag TFC adds to that NeoForge defines (TFC-only c: tags, e.g. ores and molten fluids, have no base).
        Path tfc = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"), TfcCheckout.GENERATED, "data/c/tags");
        assumeTrue(Files.isDirectory(tfc), "no TFC checkout");
        Set<String> neoforge = new HashSet<>();
        try (var zip = new java.util.zip.ZipFile(jar().toFile())) {
            zip.stream().map(java.util.zip.ZipEntry::getName).filter(n -> n.startsWith("data/c/tags/")).forEach(neoforge::add);
        }
        int checked = 0;
        for (String type : CommonTags.TYPES) {
            Path dir = tfc.resolve(type);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> s = Files.walk(dir)) {
                files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
            }
            for (Path f : files) {
                String rel = dir.relativize(f).toString().replace('\\', '/');
                if (neoforge.contains("data/c/tags/" + type + "/" + rel)) {
                    assertThat(t.getAsJsonObject(type).has("c:" + rel.substring(0, rel.length() - 5))).as(type + " " + rel)
                        .isTrue();
                    checked++;
                }
            }
        }
        assertThat(checked).isGreaterThan(40);
    }

    @Test
    void deterministic() {
        assertThat(CommonTags.generate(jar())).isEqualTo(CommonTags.generate(jar()));
    }

    @Test
    void pinnedToTfcsNeoForgeVersion() throws IOException {
        Path build = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"), "build.gradle.kts");
        assumeTrue(Files.isRegularFile(build), "no TFC checkout");
        Matcher m = Pattern.compile("val neoForgeVersion: String = \"([^\"]+)\"").matcher(Files.readString(build));
        assertThat(m.find()).isTrue();
        assertThat(CommonTags.NEOFORGE_VERSION).isEqualTo(m.group(1));
    }

    @Test
    void entriesKeepVanillaAndCommonOnly() {
        JsonArray kept = CommonTags.entries(JsonParser.parseString("""
            {"values": ["minecraft:stick", "#c:rods", {"id": "#forge:rods/wooden", "required": false},
              {"id": "minecraft:bamboo", "required": false}, "othermod:rod"]}"""), "t.json");
        assertThat(kept.toString())
            .isEqualTo("[\"minecraft:stick\",\"#c:rods\",{\"id\":\"minecraft:bamboo\",\"required\":false}]");
        assertThatThrownBy(() -> CommonTags.entries(JsonParser.parseString("{\"replace\": true, \"values\": []}"), "r"))
            .isInstanceOf(DatagenException.class);
        assertThatThrownBy(() -> CommonTags.entries(JsonParser.parseString("{\"values\": [\"stick\"]}"), "n"))
            .isInstanceOf(DatagenException.class);
    }
}
