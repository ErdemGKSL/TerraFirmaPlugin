/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-DATA-11: the command's TFC ingredient table is the one datagen exported, and the datapack holds each material. */
class TrimTfcMaterialsTest {
    @Test
    void theIngredientTableMatchesTheGeneratedMaterials() throws Exception {
        Path data = Path.of(System.getProperty("tfp.contentData", "../content/generated/content/data"));
        Path file = data.resolve("tfp/data_maps/trim_materials.json");
        Map<String, String> generated = new HashMap<>();
        JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()
            .entrySet().forEach(e -> generated.put(e.getKey(), e.getValue().getAsString()));
        assertThat(TrimCommands.TFC_MATERIALS).hasSize(14).isEqualTo(generated);
        Path dir = data.resolveSibling("datapack").resolve("tfp_seasonal_biomes/data/tfp/trim_material");
        for (String name : generated.values()) {
            assertThat(Files.isRegularFile(dir.resolve(name + ".json"))).as(name).isTrue();
        }
    }
}
