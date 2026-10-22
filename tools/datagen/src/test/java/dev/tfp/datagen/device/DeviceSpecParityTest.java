/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.TfcCheckout;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-INFRA-08: the shared spec against TFC and against the device entries generated today. Runs the whole datagen on the
 * real pinned checkout (skipped without it). Each device spec is built with TFC's settings
 * ({@link DeviceBlockSpec.Settings#fromTfc}: strength and sound from {@code TFCBlocks.java}, the tool from the mineable
 * tags), TFC's loot ({@link DeviceLoot#fromTfc}) and, for the firepit family, TFC's lit light. The test checks those
 * values against TFC's numbers, then that {@link DeviceYaml#literal} gives the same states, items and mappings as the
 * file the old generators wrote. The old generators' settings, loot and firepit light are placeholders recorded in the
 * ledger's Divergences (fixed when they switch to the spec, PAR-INFRA-19); the test pins them so that switch has to update
 * this test and the ledger together. Also checks that the device templates are written into
 * {@code configuration/templates/} and that every template the spec calls exists in the pack.
 */
class DeviceSpecParityTest {
    private static final String PACK = "craftengine/tfp/";

    private static TfcCheckout checkout() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            return TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            throw e;
        }
    }

    /** TFC's firepit state definition: LIT (false), SMOKE_LEVEL 0..4 (0), HORIZONTAL_AXIS (x), in builder.add order. */
    private static List<DeviceProperty> firepitProps() {
        return List.of(DeviceProperty.bool("lit", false), DeviceProperty.range("smoke_level", 0, 4, 0),
            DeviceProperty.names("axis", List.of("x", "z"), "x"));
    }

    /** TFCBlocks.java and the mineable tags / loot tables at the pinned commit. */
    @Test
    void tfcSettingsAndLootOfTheDevices() {
        TfcCheckout tfc = checkout();
        DeviceBlockSpec.Settings firepit = DeviceBlockSpec.Settings.of("0.4", "0.4", DeviceSound.NETHER_WART, "pickaxe");
        for (String device : List.of("firepit", "grill", "pot")) {
            assertThat(DeviceBlockSpec.Settings.fromTfc(tfc, device)).as(device).isEqualTo(firepit);
        }
        assertThat(DeviceBlockSpec.Settings.fromTfc(tfc, "bellows"))
            .isEqualTo(DeviceBlockSpec.Settings.of("3.0", "3.0", DeviceSound.WOOD, "axe"));
        assertThat(DeviceBlockSpec.Settings.fromTfc(tfc, "charcoal_forge"))
            .isEqualTo(DeviceBlockSpec.Settings.of("0.2", "0.2", DeviceSound.tfc("charcoal"), "shovel"));

        assertThat(DeviceLoot.fromTfc(tfc, "firepit")).isEqualTo(DeviceLoot.items("tfp:powder/wood_ash"));
        assertThat(DeviceLoot.fromTfc(tfc, "grill")).isEqualTo(DeviceLoot.items("tfp:powder/wood_ash", "tfp:wrought_iron_grill"));
        assertThat(DeviceLoot.fromTfc(tfc, "pot")).isEqualTo(DeviceLoot.items("tfp:powder/wood_ash", "tfp:ceramic/pot"));
        assertThat(DeviceLoot.fromTfc(tfc, "charcoal_forge")).isEqualTo(DeviceLoot.item("minecraft:charcoal", 7));
        assertThat(DeviceLoot.fromTfc(tfc, "bellows")).isEqualTo(DeviceLoot.self());
        // The crucible copies its contents into the drop: not a device loot shape, so it fails instead of losing them.
        assertThatThrownBy(() -> DeviceLoot.fromTfc(tfc, "crucible")).isInstanceOf(DatagenException.class);
    }

    @Test
    void existingDeviceEntriesMatchTheSpecApartFromTheRecordedPlaceholders() {
        TfcCheckout tfc = checkout();
        SortedMap<String, byte[]> files = Datagen.generate(tfc.root(), TfcCheckout.PINNED_COMMIT).tree().files();

        var lit = DeviceAppearance.Carrier.state("minecraft:campfire[lit=true]");
        var unlit = DeviceAppearance.Carrier.state("minecraft:campfire[lit=false]");
        for (String device : List.of("firepit", "grill", "pot")) {
            DeviceBlockSpec spec = DeviceSpecs.firepitFamily("tfp:" + device, firepitProps(), lit, unlit,
                DeviceBlockSpec.Settings.fromTfc(tfc, device), DeviceLoot.fromTfc(tfc, device), 15);
            assertThat(spec.stateCount()).isEqualTo(20);
            String text = text(files, PACK + "devices/" + device + ".yml");
            Map<String, Object> generated = load(text);
            // Ledger divergence (placeholder entries outside configuration/, not loaded): no settings, loot or light.
            assertThat(block(generated, spec.id())).doesNotContainKeys("settings", "loot");
            assertThat(text).doesNotContain("luminance");
            assertThat(withoutSettingsAndLoot(load(DeviceYaml.writeLiteral(spec)), spec.id(), true)).as(device)
                .isEqualTo(generated);
        }

        Map<Integer, String> models = new LinkedHashMap<>();
        for (int level = 0; level <= 7; level++) {
            models.put(level, "tfp:block/charcoal_forge/heat_" + level);
        }
        DeviceBlockSpec forge = DeviceSpecs.charcoalForge("tfp:charcoal_forge", DeviceProperty.range("heat_level", 0, 7, 0),
            models, DeviceAppearance.Carrier.state("minecraft:snow[layers=7]"), new BigDecimal("1.002"),
            "tfp:render/charcoal_forge/", heat -> heat * 2, DeviceBlockSpec.Settings.fromTfc(tfc, "charcoal_forge"),
            DeviceLoot.fromTfc(tfc, "charcoal_forge"), Map.of("minecraft:snow[layers=7]", "minecraft:snow[layers=8]"));
        assertThat(forge.strategies()).containsExactly(DeviceAppearance.Strategy.REPURPOSED_STATE);
        assertThat(forge.rendered()).isTrue();
        assertMatchesApartFromLegacySettings(files, PACK + "configuration/charcoal_forge.yml", forge);

        DeviceBlockSpec bellows = DeviceSpecs.facing("tfp:bellows",
            DeviceProperty.names("facing", List.of("north", "east", "south", "west"), "north"),
            f -> "minecraft:smoker[facing=" + f + ",lit=false]", "block.tfp.bellows",
            DeviceBlockSpec.Settings.fromTfc(tfc, "bellows"), DeviceLoot.fromTfc(tfc, "bellows"));
        assertMatchesApartFromLegacySettings(files, PACK + "configuration/bellows.yml", bellows);

        String templates = text(files, DeviceTemplates.OUTPUT);
        assertThat(templates).isEqualTo(dev.tfp.datagen.Yaml.write(DeviceTemplates.HEADER, DeviceTemplates.yaml()));
        assertCalledTemplatesExist(files, templates);
    }

    /**
     * Ledger divergence: ForgeDevices writes {@code tfp:settings/device} (hardness and resistance 1, stone sounds,
     * pickaxe, basedrum) and self loot for both the forge and the bellows. Everything else equals the spec.
     */
    @SuppressWarnings("unchecked")
    private static void assertMatchesApartFromLegacySettings(SortedMap<String, byte[]> files, String path,
            DeviceBlockSpec spec) {
        Map<String, Object> generated = load(text(files, path));
        Map<String, Object> block = block(generated, spec.id());
        Map<String, Object> legacySettings = (Map<String, Object>) block.get("settings");
        assertThat(legacySettings).containsEntry("template", "tfp:settings/device");
        assertThat((Map<String, Object>) legacySettings.get("arguments"))
            .containsEntry("device_hardness", 1.0).containsEntry("device_resistance", 1.0);
        assertThat(block.get("loot")).isEqualTo(Map.of("template", DeviceLoot.LOOT_SELF));
        assertThat(withoutSettingsAndLoot(load(DeviceYaml.writeLiteral(spec)), spec.id(), false)).as(path)
            .isEqualTo(withoutSettingsAndLoot(generated, spec.id(), false));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> withoutSettingsAndLoot(Map<String, Object> root, String id, boolean variantLight) {
        Map<String, Object> block = block(root, id);
        block.remove("settings");
        block.remove("loot");
        if (variantLight) {
            Map<String, Object> variants = (Map<String, Object>) ((Map<String, Object>) block.get("states")).get("variants");
            variants.values().forEach(v -> ((Map<String, Object>) v).remove("settings"));
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(Map<String, Object> root, String id) {
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(id);
    }

    private static Map<String, Object> load(String text) {
        return new Yaml().load(text);
    }

    /** Every {@code template:} id the spec's entries call is defined in the pack; the device templates call none. */
    @SuppressWarnings("unchecked")
    private static void assertCalledTemplatesExist(SortedMap<String, byte[]> files, String deviceTemplates) {
        Map<String, Object> defined = new LinkedHashMap<>();
        for (var e : files.entrySet()) {
            if (e.getKey().startsWith(PACK + "configuration/templates/")) {
                Map<String, Object> root = new Yaml().load(new String(e.getValue(), StandardCharsets.UTF_8));
                for (var section : root.entrySet()) {
                    assertThat(section.getKey()).startsWith("templates");
                    for (var t : ((Map<String, Object>) section.getValue()).entrySet()) {
                        assertThat(defined.put(t.getKey(), t.getValue())).as("duplicate template " + t.getKey()).isNull();
                    }
                }
            }
        }
        assertThat(defined).containsKeys(DeviceTemplates.DEVICE, DeviceTemplates.DEVICE_RENDERED, DeviceLoot.LOOT_SELF,
            DeviceLoot.LOOT_ITEM, DeviceLoot.LOOT_ITEM_COUNT, DeviceTemplates.BLOCK_ITEM, DeviceTemplates.RENDER_ITEM);
        assertThat(deviceTemplates).doesNotContain("template:");
        // The loot templates take exactly the arguments DeviceLoot passes.
        assertThat(new Yaml().dump(defined.get(DeviceLoot.LOOT_ITEM))).contains("${loot_item}");
        assertThat(new Yaml().dump(defined.get(DeviceLoot.LOOT_ITEM_COUNT)))
            .contains("${loot_item}", "${loot_count_min}", "${loot_count_max}");
    }

    private static String text(SortedMap<String, byte[]> files, String path) {
        assertThat(files).containsKey(path);
        return new String(files.get(path), StandardCharsets.UTF_8);
    }
}
