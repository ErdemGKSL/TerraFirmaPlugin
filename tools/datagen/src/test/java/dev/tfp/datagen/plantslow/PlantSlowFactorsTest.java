/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.plantslow;

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

/** PAR-GATHER-10 against the real TFC checkout: every one of the 170 plants has its speed factor. */
class PlantSlowFactorsTest {
    private static TfcCheckout tfc;
    private static OutputTree out;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        out = new OutputTree();
        PlantSlowFactors.run(tfc, out);
    }

    private static JsonObject plants() {
        return Json.parse(new String(out.get(PlantSlowFactors.FILE), StandardCharsets.UTF_8), "plants").getAsJsonObject()
            .getAsJsonObject("plants");
    }

    @Test
    void everyPlantEntryOfPlantJavaHasAFactor() {
        Matcher m = Pattern.compile("^ {4}([A-Z_]+)\\(BlockType\\.", Pattern.MULTILINE).matcher(tfc.readString(PlantSlowFactors.PLANT_JAVA));
        int all = 0;
        while (m.find()) {
            all++;
            assertThat(plants().has(m.group(1).toLowerCase())).as(m.group(1)).isTrue();
        }
        assertThat(all).isEqualTo(170);
        assertThat(plants().size()).isEqualTo(170);
    }

    @Test
    void valuesMatchTfc() {
        JsonObject p = plants();
        assertThat(p.get("athyrium_fern").getAsFloat()).isEqualTo(0.7f);
        assertThat(p.get("goldenrod").getAsFloat()).isEqualTo(1.0f);
        assertThat(p.get("pampas_grass").getAsFloat()).isEqualTo(0.8f);
        assertThat(p.get("beachgrass").getAsFloat()).isEqualTo(0.9f);
        assertThat(p.get("manatee_grass").getAsFloat()).isEqualTo(0.8f);
        assertThat(p.get("rose").getAsFloat()).isEqualTo(0.6f);
    }
}
