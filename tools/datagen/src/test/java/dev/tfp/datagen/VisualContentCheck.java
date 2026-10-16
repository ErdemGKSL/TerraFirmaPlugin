/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VersionFamily;
import dev.tfp.datagen.visuals.VisualAllocator;
import dev.tfp.datagen.visuals.VisualOutputs;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/** Checks the allocator's outputs (M1-DGN-04) and the suppression data and datapack (M1-DGN-05). */
final class VisualContentCheck {
    private static final String ID = "#?[a-z0-9_.\\-]+:[a-z0-9_./\\-]+";

    private VisualContentCheck() {
    }

    private static JsonObject json(SortedMap<String, byte[]> files, String path) {
        assertThat(files).containsKey(path);
        return JsonParser.parseString(new String(files.get(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @SuppressWarnings("unchecked")
    static void assertComplete(SortedMap<String, byte[]> files) {
        JsonObject index = json(files, VisualOutputs.INDEX);
        assertThat(index.getAsJsonArray("allocations")).hasSize(VersionFamily.values().length * Profile.values().length * 2);
        for (VersionFamily f : VersionFamily.values()) {
            for (Profile p : Profile.values()) {
                assertThat(files).containsKey("reports/visual-allocation-" + p.id() + "-" + f.id() + ".md");
                for (boolean ne : new boolean[] {false, true}) {
                    String dir = VisualAllocator.Allocation.dir(f, p, ne) + "/";
                    Map<String, Object> mappings = (Map<String, Object>) ((Map<String, Object>) CeYamlCheck.parse(
                        new String(files.get(dir + "visual_mappings.yml"), StandardCharsets.UTF_8), dir))
                        .get("block_state_mappings");
                    // one hop: no target is itself a repurposed state
                    assertThat(mappings.values()).noneMatch(mappings::containsKey);
                    JsonObject sup = json(files, dir + "suppressed_blocks.json");
                    assertThat(sup.get("profile").getAsString()).isEqualTo(p.id());
                    assertThat(sup.get("family").getAsString()).isEqualTo(f.id());
                    assertThat(sup.get("nether_end").getAsBoolean()).isEqualTo(ne);
                    Set<String> listed = new HashSet<>();
                    for (String section : new String[] {"suppress", "identity_duplicates"}) {
                        for (JsonElement e : sup.getAsJsonArray(section)) {
                            JsonObject b = e.getAsJsonObject();
                            assertThat(b.get("block").getAsString()).matches(ID);
                            if (section.equals("suppress")) {
                                assertThat(b.getAsJsonArray("creation_paths").asList()).isNotEmpty();
                                assertThat(b.get("source").getAsString()).isIn("absent", "nether_end");
                                if (!ne) {
                                    assertThat(b.get("source").getAsString()).isEqualTo("absent");
                                }
                            }
                            b.getAsJsonArray("states").forEach(s -> listed.add(s.getAsString()));
                        }
                    }
                    // every claimed state is listed for suppression or as an identity duplicate, and vice versa
                    assertThat(listed).isEqualTo(mappings.keySet());
                }
            }
        }
        assertDatapackParses(files);
    }

    static void assertDatapackParses(SortedMap<String, byte[]> files) {
        JsonObject pack = json(files, VisualOutputs.DATAPACK + "pack.mcmeta").getAsJsonObject("pack");
        assertThat(pack.get("pack_format").getAsInt()).isPositive();
        assertThat(pack.get("description").getAsString()).isNotBlank();
        for (var e : files.entrySet()) {
            if (!e.getKey().startsWith(VisualOutputs.DATAPACK) || e.getKey().endsWith("pack.mcmeta")) {
                continue;
            }
            assertThat(e.getKey()).startsWith(VisualOutputs.TAGS).endsWith(".json");
            JsonObject tag = json(files, e.getKey());
            assertThat(tag.keySet()).containsExactlyInAnyOrder("replace", "values");
            assertThat(tag.get("replace").getAsJsonPrimitive().isBoolean()).isTrue();
            tag.getAsJsonArray("values").forEach(v -> assertThat(v.getAsString()).matches(ID));
        }
    }
}
