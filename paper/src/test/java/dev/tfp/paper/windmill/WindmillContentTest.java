/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.windmill;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.power.Windmill;
import dev.tfp.paper.integration.BlockStateRef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-MECH-18: the CraftEngine windmill blocks and blade items the datagen writes agree with what the windmill module reads: a
 * blade used on any generated axle makes a generated windmill state (axis, count 1) and the last blade out gives that axle
 * back; every item of {@code #tfp:windmill_blades} exists as a CraftEngine item.
 */
class WindmillContentTest {
    private static final Path CE = repo().resolve("content/generated/content/craftengine/tfp/configuration");

    static Path repo() {
        Path here = Path.of("").toAbsolutePath();
        return Files.isDirectory(here.resolve("spikes")) ? here : here.getParent();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(String file, String key) throws Exception {
        Path p = CE.resolve(file);
        Assumptions.assumeTrue(Files.isRegularFile(p), "datagen output present: " + p);
        Map<String, Object> root = new Yaml().load(Files.readString(p));
        return (Map<String, Object>) root.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> block) {
        return (Map<String, Object>) ((Map<String, Object>) block.get("states")).get("properties");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aBladeOnEveryGeneratedAxleMakesAGeneratedWindmill() throws Exception {
        Map<String, Object> axles = section("rotation/axle.yml", "blocks");
        Map<String, Object> mills = section("power/windmill.yml", "blocks");
        assertThat(mills).hasSameSizeAs(axles);
        for (String axle : axles.keySet()) {
            assertThat(WindmillBlocks.isAxle(axle)).isTrue();
            for (String axis : List.of("x", "z")) {
                BlockStateRef mill = WindmillBlocks.windmillOf(BlockStateRef.of(axle).with(WindmillBlocks.AXIS, axis));
                assertThat(WindmillBlocks.isWindmill(mill.id())).isTrue();
                Map<String, Object> block = (Map<String, Object>) mills.get(mill.id());
                assertThat(block).as(mill.id()).isNotNull();
                assertThat(block.get("behavior")).isEqualTo(Map.of("type", "tfp:device"));
                Map<String, Object> props = properties(block);
                assertThat(((Map<String, Object>) props.get(WindmillBlocks.AXIS)).get("type")).isEqualTo("axis");
                assertThat(((Map<String, Object>) props.get(WindmillBlocks.COUNT)).get("range")).isEqualTo("1~" + Windmill.SLOTS);
                assertThat(WindmillBlocks.count(mill)).isEqualTo(1);
                assertThat(WindmillBlocks.axis(mill)).contains(axis.equals("x") ? ShaftAxis.X : ShaftAxis.Z);
                assertThat(WindmillBlocks.axleOf(mill)).isEqualTo(BlockStateRef.of(axle).with(WindmillBlocks.AXIS, axis));
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyBladeOfTheTagIsAGeneratedItem() throws Exception {
        Map<String, Object> items = section("power/windmill_blade.yml", "items");
        Path tags = repo().resolve("content/generated/content/data/tfp/tags/item");
        Set<String> blades = new HashSet<>();
        resolve(tags, "windmill_blades", blades);
        assertThat(blades).hasSize(18).contains("tfp:windmill_blade/white", "tfp:lattice_windmill_blade", "tfp:rustic_windmill_blade");
        assertThat(items.keySet()).containsExactlyInAnyOrderElementsOf(blades);
    }

    private static void resolve(Path tags, String name, Set<String> out) throws Exception {
        Path p = tags.resolve(name + ".json");
        Assumptions.assumeTrue(Files.isRegularFile(p), "tag present: " + p);
        com.google.gson.JsonObject tag = com.google.gson.JsonParser.parseString(Files.readString(p)).getAsJsonObject();
        for (com.google.gson.JsonElement v : tag.getAsJsonArray("values")) {
            String s = v.getAsString();
            if (s.startsWith("#tfp:")) {
                resolve(tags, s.substring("#tfp:".length()), out);
            } else {
                out.add(s);
            }
        }
    }
}
