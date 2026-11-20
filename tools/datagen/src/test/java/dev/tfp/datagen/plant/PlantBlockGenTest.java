/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.plant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-12 against the real TFC checkout: every land and floating plant of Plant.java, its states, the plant pools. */
class PlantBlockGenTest {
    private static TfcCheckout tfc;
    private static OutputTree out;
    private static PlantBlockGen.Result result;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        out = new OutputTree();
        result = PlantBlockGen.run(tfc, out);
    }

    private static String yml(String name) {
        return new String(out.get(PlantBlockGen.OUTPUT_DIR + name + ".yml"), StandardCharsets.UTF_8);
    }

    @Test
    void everyPlantOfTheGeneratedTypesCountedFromPlantJava() {
        // independent count: the enum lines of Plant.java whose block type is generated here
        Matcher m = Pattern.compile("^ {4}([A-Z_]+)\\(BlockType\\.([A-Z_]+)", Pattern.MULTILINE)
            .matcher(tfc.readString(PlantBlockGen.PLANT_JAVA));
        int expected = 0;
        int all = 0;
        while (m.find()) {
            all++;
            if (PlantBlockGen.TYPES.contains(m.group(2))) {
                expected++;
                assertThat(out.contains(PlantBlockGen.OUTPUT_DIR + m.group(1).toLowerCase() + ".yml")).as(m.group(1)).isTrue();
            }
        }
        assertThat(all).isEqualTo(170);
        assertThat(result.plants()).hasSize(expected);
        assertThat(expected).isEqualTo(100);
        // states: age 0..3 x halves as TFC (Plant.getMaxAgeForType, part)
        int states = 0;
        for (PlantBlockGen.Plant p : result.plants()) {
            states += (p.hasAge() ? 4 : 1) * (p.tall() ? 2 : 1);
        }
        assertThat(result.states()).isEqualTo(states);
    }

    @Test
    void poolsHoldEveryLook() {
        int used = result.poolUse().values().stream().mapToInt(Integer::intValue).sum();
        assertThat(used).isEqualTo(result.looks());
        result.poolUse().forEach((pool, n) -> assertThat(n).as(pool).isLessThanOrEqualTo(PlantBlockGen.POOLS.get(pool)));
        assertThat(result.poolUse()).containsEntry("sugar_cane", 15).containsEntry("sapling", 8);
    }

    @Test
    void shapesAndStatesAsTfc() {
        String rose = yml("rose"); // TALL_GRASS: age and two halves, double high
        assertThat(rose).contains("tfp:plant/rose:", "type: double_high_block", "type: double_block_half", "range: '0~3'",
            "'age=3,half=upper':", "stackable: true", "type: double_high_block_item");
        String allium = yml("allium"); // STANDARD: one state
        assertThat(allium).contains("state:", "auto_state:", "model: tfp:block/plant/allium_0", "hardness: 0.0",
            "minecraft:block.grass.break", "replaceable: true", "type: bush_block", "<lang:block.tfp.plant.allium>");
        assertThat(allium).doesNotContain("states:");
        String duckweed = yml("duckweed"); // FLOATING_FRESH: on water
        assertThat(duckweed).contains("type: on_liquid_block", "type: liquid_collision_block_item");
        String pincushion = yml("silken_pincushion_cactus"); // CACTUSBED: solid(), wool, 0.25
        assertThat(pincushion).contains("hardness: 0.25", "minecraft:block.wool.step", "replaceable: false");
        // ages 0 and 1 share one look
        String blue = yml("bluegrass");
        assertThat(appearanceOf(blue, "age=0")).isEqualTo(appearanceOf(blue, "age=1"));
        assertThat(appearanceOf(blue, "age=1")).isNotEqualTo(appearanceOf(blue, "age=3"));
    }

    private static String appearanceOf(String yml, String key) {
        Matcher m = Pattern.compile("'?" + Pattern.quote(key) + "'?:\\s*\\n\\s*appearance: (\\S+)").matcher(yml);
        assertThat(m.find()).as(key).isTrue();
        return m.group(1);
    }

    @Test
    void tintedLooksAreBakedWithTfcsDefaultClimateColour() throws Exception {
        String blue = yml("bluegrass"); // tall-grass colour map
        assertThat(blue).contains("tfp:block/plant_tinted/tall_grass/");
        String baked = PlantBlockGen.RP + "textures/block/plant_tinted/tall_grass/plant/bluegrass/bluegrass.png";
        assertThat(out.contains(baked)).isTrue();
        var src = ImageIO.read(new ByteArrayInputStream(tfc.read(
            "src/main/resources/assets/tfc/textures/block/plant/bluegrass/bluegrass.png")));
        var img = ImageIO.read(new ByteArrayInputStream(out.get(baked)));
        var map = ImageIO.read(new ByteArrayInputStream(tfc.read("src/main/resources/assets/tfc/textures/colormap/tall_grass.png")));
        int colour = map.getRGB(102, 128);
        assertThat(PlantBlockGen.TINT_X).isEqualTo(102);
        assertThat(PlantBlockGen.TINT_Y).isEqualTo(128);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int s = src.getRGB(x, y);
                if ((s >>> 24) == 0) {
                    continue;
                }
                int expectedG = (s >> 8 & 0xFF) * (colour >> 8 & 0xFF) / 255;
                assertThat(img.getRGB(x, y) >> 8 & 0xFF).isEqualTo(expectedG);
                return;
            }
        }
    }

    @Test
    void tintedLooksTakeOnlyTheTripwirePool() {
        for (PlantBlockGen.Plant p : result.plants()) {
            if (p.tint() == null) {
                continue;
            }
            String y = yml(p.name());
            Matcher m = Pattern.compile("type: (sugar_cane|sapling|tripwire)\\n\\s*id: \\S+\\n\\s*model: (\\S+)").matcher(y);
            while (m.find()) {
                if (m.group(2).contains("plant_tinted")) {
                    assertThat(m.group(1)).as(p.name()).isEqualTo("tripwire");
                }
            }
        }
    }

    @Test
    void namesAndLootFromTfc() {
        String en = new String(out.get(PlantBlockGen.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(en).contains("\"block.tfp.plant.allium\"");
        assertThat(out.contains("data/tfp/loot_table/blocks/plant/allium.json")
            || out.files().keySet().stream().anyMatch(k -> k.endsWith("blocks/plant/allium.json"))).isTrue();
        assertThat(List.copyOf(Map.copyOf(result.poolUse()).keySet())).containsExactlyInAnyOrder("sugar_cane", "sapling", "tripwire");
    }
}
