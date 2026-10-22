/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.device;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.TfcCheckout;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-INFRA-08: the shared spec writes the existing device entries byte for byte. Runs the whole datagen on the real
 * pinned checkout (skipped without it) and compares each device file the old generators wrote with
 * {@link DeviceYaml#literal} of the {@link DeviceSpecs} factory fed with TFC's values. Also checks that the device
 * templates are written into {@code configuration/templates/} and that every template they call exists in the pack.
 */
class DeviceSpecParityTest {
    private static final BigDecimal ONE = new BigDecimal("1");
    private static final String PACK = "craftengine/tfp/";

    private static Path checkout() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        return path;
    }

    /** TFC's firepit state definition: LIT (false), SMOKE_LEVEL 0..4 (0), HORIZONTAL_AXIS (x), in builder.add order. */
    private static List<DeviceProperty> firepitProps() {
        return List.of(DeviceProperty.bool("lit", false), DeviceProperty.range("smoke_level", 0, 4, 0),
            DeviceProperty.names("axis", List.of("x", "z"), "x"));
    }

    @Test
    void existingDeviceEntriesAreByteIdenticalThroughTheSpec() {
        SortedMap<String, byte[]> files = Datagen.generate(checkout(), TfcCheckout.PINNED_COMMIT).tree().files();

        var lit = DeviceAppearance.Carrier.state("minecraft:campfire[lit=true]");
        var unlit = DeviceAppearance.Carrier.state("minecraft:campfire[lit=false]");
        for (String device : List.of("firepit", "grill", "pot")) {
            DeviceBlockSpec spec = DeviceSpecs.firepitFamily("tfp:" + device, firepitProps(), lit, unlit);
            assertThat(spec.stateCount()).isEqualTo(20);
            assertThat(DeviceYaml.writeLiteral(spec)).as(device)
                .isEqualTo(text(files, PACK + "devices/" + device + ".yml"));
        }

        Map<Integer, String> models = new LinkedHashMap<>();
        for (int level = 0; level <= 7; level++) {
            models.put(level, "tfp:block/charcoal_forge/heat_" + level);
        }
        DeviceBlockSpec forge = DeviceSpecs.charcoalForge("tfp:charcoal_forge", DeviceProperty.range("heat_level", 0, 7, 0),
            models, DeviceAppearance.Carrier.state("minecraft:snow[layers=7]"), new BigDecimal("1.002"),
            "tfp:render/charcoal_forge/", heat -> heat * 2, ONE, ONE,
            Map.of("minecraft:snow[layers=7]", "minecraft:snow[layers=8]"));
        assertThat(forge.strategies()).containsExactly(DeviceAppearance.Strategy.REPURPOSED_STATE);
        assertThat(forge.rendered()).isTrue();
        assertThat(DeviceYaml.writeLiteral(forge)).isEqualTo(text(files, PACK + "configuration/charcoal_forge.yml"));

        DeviceBlockSpec bellows = DeviceSpecs.facing("tfp:bellows",
            DeviceProperty.names("facing", List.of("north", "east", "south", "west"), "north"),
            f -> "minecraft:smoker[facing=" + f + ",lit=false]", "block.tfp.bellows", ONE, ONE);
        assertThat(DeviceYaml.writeLiteral(bellows)).isEqualTo(text(files, PACK + "configuration/bellows.yml"));

        String templates = text(files, DeviceTemplates.OUTPUT);
        assertThat(templates).isEqualTo(dev.tfp.datagen.Yaml.write(DeviceTemplates.HEADER, DeviceTemplates.yaml()));
        assertCalledTemplatesExist(files, templates);
    }

    /** Every {@code template:} id the device templates (and the spec's item calls) use is defined in the pack. */
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
        assertThat(defined).containsKeys(DeviceTemplates.DEVICE, DeviceTemplates.DEVICE_RENDERED,
            DeviceTemplates.SETTINGS, DeviceTemplates.LOOT_SELF, DeviceTemplates.BLOCK_ITEM, DeviceTemplates.RENDER_ITEM);
        Matcher m = Pattern.compile("template: (\\S+)").matcher(deviceTemplates);
        int calls = 0;
        while (m.find()) {
            assertThat(defined).containsKey(m.group(1));
            calls++;
        }
        assertThat(calls).isEqualTo(4);
        // tfp:settings/device needs exactly the two arguments the device templates pass on.
        String settings = new Yaml().dump(defined.get(DeviceTemplates.SETTINGS));
        assertThat(settings).contains("${device_hardness}", "${device_resistance}");
    }

    private static String text(SortedMap<String, byte[]> files, String path) {
        assertThat(files).containsKey(path);
        return new String(files.get(path), StandardCharsets.UTF_8);
    }
}
