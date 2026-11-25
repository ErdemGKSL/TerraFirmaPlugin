/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.fog;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-CLIM-30: the foggy variants on the real TFC checkout and the registry room. */
class FogVariantsTest {
    @Test
    void foggyVariantsArePresentPerBiomeAndLevelWithinTheRegistryRoom() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc;
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        OutputTree out = new OutputTree();
        int biomes = FogVariants.run(tfc, out);
        assertThat(biomes).isEqualTo(125);
        long variants = out.files().keySet().stream().filter(k -> k.contains("/worldgen/biome/fog/")).count();
        assertThat(variants).isEqualTo((long) biomes * FogVariants.LEVELS).isLessThanOrEqualTo(FogBudget.room(biomes));
        String base = FogVariants.OUT + "data/tfp/worldgen/biome/fog/plains/";
        JsonObject l1 = Json.parse(new String(out.files().get(base + "l1.json")), "l1").getAsJsonObject();
        JsonObject l3 = Json.parse(new String(out.files().get(base + "l3.json")), "l3").getAsJsonObject();
        double far1 = l1.getAsJsonObject("attributes").get(FogVariants.ATTRIBUTES[1]).getAsDouble();
        double far3 = l3.getAsJsonObject("attributes").get(FogVariants.ATTRIBUTES[1]).getAsDouble();
        assertThat(far3).isLessThan(far1).isGreaterThan(0);
        assertThat(far1).isLessThan(FogVariants.REFERENCE_RENDER_DISTANCE);
        assertThat(l1.getAsJsonObject("effects").has("fog_color")).isTrue();
        assertThat(out.files()).containsKey(FogVariants.OUT + "pack.mcmeta");
    }

    @Test
    void levelFogRisesAndIsCappedAtTheClientMaximum() {
        assertThat(FogVariants.levelFog(1)).isLessThan(FogVariants.levelFog(2));
        assertThat(FogVariants.levelFog(FogVariants.LEVELS)).isEqualTo(0.86f);
    }

    @Test
    void theVariantPathFormatMatchesThePaperRemapIds() {
        assertThat(FogVariants.variantPath("plains", 2)).isEqualTo("fog/plains/l2");
    }
}
