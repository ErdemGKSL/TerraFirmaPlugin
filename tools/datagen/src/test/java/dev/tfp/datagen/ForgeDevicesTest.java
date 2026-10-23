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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** M4-FORGE-01: the charcoal forge and bellows entries, read from the real pinned TFC checkout when it is available. */
class ForgeDevicesTest {
    private static Path path() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(TfcCheckout.resolveHead(path)),
            "TFC checkout is not at the pinned commit");
        return path;
    }

    private static TfcCheckout checkout() {
        return TfcCheckout.open(path(), TfcCheckout.PINNED_COMMIT);
    }

    @Test
    void theForgeHasEightHeatLevelsAndTheBellowsFourFacings() {
        // The whole pipeline: the bellows item model is copied by AssetPipeline before ForgeDevices checks it.
        var files = Datagen.generate(path(), TfcCheckout.PINNED_COMMIT).tree().files();

        String forge = new String(files.get(ForgeDevices.FORGE_OUTPUT), StandardCharsets.UTF_8);
        assertThat(forge).contains("tfp:charcoal_forge:", "heat_level:", "range: '0~7'", "default: 0");
        // PAR-INFRA-19: TFC's strength(0.2F), TFCSounds.CHARCOAL, mineable/shovel and 7 charcoal (the forge has no item).
        assertThat(forge).doesNotContain("tfp:settings/device", "tfp:loot_table/self");
        Map<?, ?> forgeBlock = block(forge, ForgeDevices.FORGE_ID);
        Map<?, ?> forgeSettings = (Map<?, ?>) forgeBlock.get("settings");
        assertThat(forgeSettings.get("hardness")).isEqualTo(0.2);
        assertThat(forgeSettings.get("resistance")).isEqualTo(0.2);
        assertThat(forgeSettings.get("tags")).isEqualTo(List.of("minecraft:mineable/shovel"));
        assertThat(forgeSettings.get("sounds")).isEqualTo(Map.of("break", "tfp:block.charcoal.break",
            "step", "tfp:block.charcoal.step", "place", "tfp:block.charcoal.place", "hit", "tfp:block.charcoal.hit",
            "fall", "tfp:block.charcoal.fall"));
        assertThat(forgeBlock.get("loot")).isEqualTo(Map.of("template", "tfp:loot_table/item_count",
            "arguments", Map.of("loot_item", "minecraft:charcoal", "loot_count_min", 7, "loot_count_max", 7)));
        assertThat(forge).as("no placeholder campfire any more").doesNotContain("campfire");
        assertThat(forge).as("the forge has no placing item: TFC makes it by lighting a charcoal pile")
            .doesNotContain("tfp:item/block_item");
        for (int level = 0; level <= 7; level++) {
            assertThat(forge).contains("heat_level=" + level + ":", "appearance: heat_" + level, "luminance: " + (2 * level));
            assertThat(files).containsKey(AssetPipeline.RP + "models/block/charcoal_forge/heat_" + level + ".json");
            assertThat(files).containsKey(AssetPipeline.RP + "textures/block/devices/charcoal_forge/" + level + ".png");
        }
        assertThat(files).containsKey(AssetPipeline.RP + "models/block/charcoal_forge/template_forge.json");
        assertThat(files).containsKey(AssetPipeline.RP + "textures/block/charcoal_pile.png");
        assertThat(files).as("the lit tops keep their animation")
            .containsKey(AssetPipeline.RP + "textures/block/devices/charcoal_forge/7.png.mcmeta");
        String heat7 = new String(files.get(AssetPipeline.RP + "models/block/charcoal_forge/heat_7.json"), StandardCharsets.UTF_8);
        assertThat(heat7).contains("tfp:block/charcoal_forge/template_forge", "tfp:block/devices/charcoal_forge/7")
            .doesNotContain("tfc:");
        assertThat(forge).as("every level on the 14 px snow state, all with the one cold model")
            .contains("state: 'minecraft:snow[layers=7]'", "model: tfp:block/charcoal_forge/heat_0")
            .doesNotContain("\n          model: tfp:block/charcoal_forge/heat_1");
        assertThat(forge.split("state: 'minecraft:snow\\[layers=7\\]'", -1)).hasSize(9);
        assertThat(forge).as("lit levels draw TFC's model of their level with an item display")
            .contains("entity_renderer:", "item: tfp:render/charcoal_forge/heat_1", "item: tfp:render/charcoal_forge/heat_7",
                "template: tfp:item/render", "render_model: tfp:block/charcoal_forge/heat_7", "block_light: 14")
            .doesNotContain("tfp:render/charcoal_forge/heat_0");
        assertThat(forge).as("real 7-layer snow is hidden as 8 layers")
            .contains("block_state_mappings:", "'minecraft:snow[layers=7]': 'minecraft:snow[layers=8]'");
        String bellows = new String(files.get(ForgeDevices.BELLOWS_OUTPUT), StandardCharsets.UTF_8);
        assertThat(bellows).contains("items:", "template: tfp:item/block_item", "block.tfp.bellows");
        assertThat(files).containsKey(AssetPipeline.RP + "models/item/bellows.json");
        assertThat(bellows).contains("tfp:bellows:", "facing:", "default: north");
        // PAR-INFRA-19: TFC's strength(3.0f), SoundType.WOOD, mineable/axe; it drops itself.
        Map<?, ?> bellowsBlock = block(bellows, ForgeDevices.BELLOWS_ID);
        Map<?, ?> bellowsSettings = (Map<?, ?>) bellowsBlock.get("settings");
        assertThat(bellowsSettings.get("hardness")).isEqualTo(3.0);
        assertThat(bellowsSettings.get("resistance")).isEqualTo(3.0);
        assertThat(bellowsSettings.get("tags")).isEqualTo(List.of("minecraft:mineable/axe"));
        assertThat(((Map<?, ?>) bellowsSettings.get("sounds")).get("break")).isEqualTo("minecraft:block.wood.break");
        assertThat(bellowsBlock.get("loot")).isEqualTo(Map.of("template", "tfp:loot_table/self"));
        assertThat(bellows).doesNotContain("tfp:settings/device", "luminance");
        for (String f : ForgeDevices.FACINGS) {
            assertThat(bellows).contains("facing=" + f + ":");
        }
    }

    private static Map<?, ?> block(String yaml, String id) {
        Map<?, ?> root = (Map<?, ?>) CeYamlCheck.parse(yaml, id);
        return (Map<?, ?>) ((Map<?, ?>) root.get("blocks")).get(id);
    }

    @Test
    void theForgeLevelsAreReadFromTfcAndNotHardCoded() {
        ForgeDevices.HeatRange range = ForgeDevices.heatRange(checkout());
        assertThat(range.name()).isEqualTo("heat_level");
        assertThat(range.min()).isZero();
        assertThat(range.max()).isEqualTo(7);
    }

    @Test
    void theForgeModelsAndShapeAreReadFromTfc() {
        var models = ForgeDevices.forgeModels(checkout());
        assertThat(models).hasSize(8).containsEntry(0, "block/charcoal_forge/heat_0").containsEntry(7, "block/charcoal_forge/heat_7");
        ForgeDevices.checkShape(checkout()); // throws when TFC's forge is no longer the 14 px pile shape
        assertThat(ForgeDevices.lightLevel(7)).isEqualTo(14);
    }
}
