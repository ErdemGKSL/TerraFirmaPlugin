/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.waterwheel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.power.ShaftAxis;
import dev.tfp.paper.integration.BlockStateRef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-MECH-18: every CraftEngine water wheel the datagen writes is a block of the water wheel module's device kind, placed by
 * its own item, with the axis property the module reads and writes ({@link WaterWheelBlocks#axis}).
 */
class WaterWheelContentTest {
    static Path repo() {
        Path here = Path.of("").toAbsolutePath();
        return Files.isDirectory(here.resolve("spikes")) ? here : here.getParent();
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyGeneratedWheelIsTheModulesDevice() throws Exception {
        Path p = repo().resolve("content/generated/content/craftengine/tfp/configuration/power/water_wheel.yml");
        Assumptions.assumeTrue(Files.isRegularFile(p), "datagen output present: " + p);
        Map<String, Object> root = new Yaml().load(Files.readString(p));
        Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
        Map<String, Object> items = (Map<String, Object>) root.get("items");
        assertThat(blocks).hasSize(20).containsKey("tfp:wood/water_wheel/oak");
        for (Map.Entry<String, Object> e : blocks.entrySet()) {
            String id = e.getKey();
            assertThat(WaterWheelBlocks.isWheel(id)).as(id).isTrue();
            Map<String, Object> block = (Map<String, Object>) e.getValue();
            assertThat(block.get("behavior")).isEqualTo(Map.of("type", "tfp:device"));
            Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) block.get("states")).get("properties");
            assertThat(((Map<String, Object>) props.get(WaterWheelBlocks.AXIS)).get("default")).isEqualTo("x");
            for (ShaftAxis axis : new ShaftAxis[] {ShaftAxis.X, ShaftAxis.Z}) {
                BlockStateRef s = BlockStateRef.of(id).with(WaterWheelBlocks.AXIS, WaterWheelBlocks.name(axis));
                assertThat(WaterWheelBlocks.axis(s)).contains(axis);
            }
            // CraftEngine's axis type also has y: the module turns it to the player's horizontal axis on placement
            assertThat(WaterWheelBlocks.axis(BlockStateRef.of(id).with(WaterWheelBlocks.AXIS, "y"))).isEmpty();
            Map<String, Object> item = (Map<String, Object>) items.get(id);
            assertThat(item).as("block item of " + id).isNotNull();
            assertThat(item.get("template")).isEqualTo("tfp:item/block_item");
        }
    }
}
