/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.plant.stage;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-48 against the real TFC checkout: the bloom fields of all 170 plants and the 154 tfc:plant loader models. */
class PlantStageGenTest {
    private static TfcCheckout tfc;
    private static JsonObject root;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        OutputTree out = new OutputTree();
        PlantStageGen.run(tfc, out);
        root = Json.parse(new String(out.get(PlantStageGen.FILE), StandardCharsets.UTF_8), "lifecycle").getAsJsonObject();
    }

    @Test
    void everyPlantHasItsBloomFields() {
        Matcher m = Pattern.compile("^ {4}([A-Z_]+)\\(BlockType\\.", Pattern.MULTILINE).matcher(tfc.readString(PlantStageGen.PLANT_JAVA));
        int all = 0;
        while (m.find()) {
            all++;
            assertThat(root.getAsJsonObject("plants").has(m.group(1).toLowerCase())).as(m.group(1)).isTrue();
        }
        assertThat(all).isEqualTo(170);
        assertThat(root.getAsJsonObject("plants").size()).isEqualTo(170);
    }

    @Test
    void valuesMatchTfc() {
        JsonObject p = root.getAsJsonObject("plants");
        // CANNA(STANDARD, 0.7F, false, -0.1F, 0.43F, 0.51F, 0.6F, 0.74F, 0.93F)
        JsonObject canna = p.getAsJsonObject("canna");
        assertThat(canna.get("wet_season_blooming").getAsBoolean()).isFalse();
        assertThat(canna.get("bloom_offset").getAsFloat()).isEqualTo(-0.1f);
        assertThat(canna.get("sprouting_end").getAsFloat()).isEqualTo(0.93f);
        // ATHYRIUM_FERN(STANDARD, 0.7F): the defaults
        JsonObject fern = p.getAsJsonObject("athyrium_fern");
        assertThat(fern.get("bloom_offset").getAsFloat()).isEqualTo(-0.124f);
        assertThat(fern.get("blooming_end").getAsFloat()).isEqualTo(0.25f);
        assertThat(fern.get("dormant_end").getAsFloat()).isEqualTo(0.75f);
        // PAMPAS_GRASS(TALL_GRASS, 0.8F, true, 0F, ...)
        assertThat(p.getAsJsonObject("pampas_grass").get("wet_season_blooming").getAsBoolean()).isTrue();
        // LOTUS(..., 1000, 13000), SACRED_DATURA(..., 12000, 0)
        assertThat(p.getAsJsonObject("lotus").get("start_hour").getAsInt()).isEqualTo(1000);
        assertThat(p.getAsJsonObject("lotus").get("end_hour").getAsInt()).isEqualTo(13000);
        assertThat(p.getAsJsonObject("sacred_datura").get("start_hour").getAsInt()).isEqualTo(12000);
        assertThat(p.getAsJsonObject("sacred_datura").get("end_hour").getAsInt()).isEqualTo(0);
    }

    @Test
    void stageModelsAndBudget() {
        JsonObject models = root.getAsJsonObject("models");
        assertThat(models.size()).isEqualTo(154);
        JsonObject glory = models.getAsJsonObject("tfc:block/plant/morning_glory_dynamic_down_a");
        assertThat(glory.get("blooming").getAsString()).isEqualTo("tfc:block/plant/morning_glory_0a");
        assertThat(glory.get("dying").getAsString()).isEqualTo("tfc:block/plant/morning_glory_1");
        assertThat(glory.keySet()).containsExactlyElementsOf(PlantStageGen.STAGES);
        assertThat(root.getAsJsonObject("budget").get("distinct_looks").getAsInt()).isEqualTo(430);
    }
}
