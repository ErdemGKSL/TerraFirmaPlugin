/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.gui.GuiImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-PROC-09: the plugin's barrel screen layout must match the art datagen wrote ({@code BarrelScreenArt}): the images'
 * heights, ascents and grids, the tank item model definition with one model per fill level 1 to 50.
 */
class BarrelScreenLayoutContentTest {
    private static Path pack() {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null, "no generated content");
        Path root = Path.of(configured).getParent().resolve("craftengine/tfp");
        assumeTrue(Files.isRegularFile(root.resolve("configuration/barrel_gui.yml")), "no barrel screen art generated");
        return root;
    }

    @Test
    @SuppressWarnings("unchecked")
    void theImagesMatchTheLayout() throws IOException {
        Path pack = pack();
        Map<String, Object> root = new Yaml().load(Files.readString(pack.resolve("configuration/barrel_gui.yml")));
        Map<String, Map<String, Object>> images = (Map<String, Map<String, Object>>) root.get("images");
        for (GuiImage image : List.of(BarrelScreenLayout.BACKGROUND, BarrelScreenLayout.SEAL, BarrelScreenLayout.DISABLED)) {
            Map<String, Object> e = images.get(image.id());
            assertThat(e).as(image.id()).isNotNull();
            assertThat(e.get("height")).as(image.id() + " height").isEqualTo(image.height());
            assertThat(e.get("ascent")).as(image.id() + " ascent").isEqualTo(image.ascent());
            assertThat(e.get("file")).isEqualTo(image.file());
            if (image.frames() > 1) {
                assertThat(e.get("grid_size")).isEqualTo(image.rows() + "," + image.cols());
            }
            assertThat(pack.resolve("resourcepack/assets/tfp/" + image.packPath())).isRegularFile();
        }
        assertThat(images.keySet()).hasSize(3);
    }

    @Test
    void theTankModelDefinitionHasAModelPerLevel() throws IOException {
        Path rp = pack().resolve("resourcepack/assets/tfp/");
        String model = BarrelScreenLayout.TANK_ITEM_MODEL.substring("tfp:".length());
        JsonObject def = JsonParser.parseString(Files.readString(rp.resolve("items/" + model + ".json"))).getAsJsonObject();
        JsonObject dispatch = def.getAsJsonObject("model");
        assertThat(dispatch.get("type").getAsString()).isEqualTo("minecraft:range_dispatch");
        assertThat(dispatch.getAsJsonArray("entries")).hasSize(BarrelScreenLayout.TANK_PIXELS);
        for (int fill = 1; fill <= BarrelScreenLayout.TANK_PIXELS; fill++) {
            assertThat(rp.resolve("models/item/gui/barrel_tank/" + fill + ".json")).isRegularFile();
        }
        assertThat(rp.resolve("textures/item/gui/barrel_tank_gauge.png")).isRegularFile();
    }
}
