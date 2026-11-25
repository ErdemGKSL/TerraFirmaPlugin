/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.bellows;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.core.bellows.BellowsMotion;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-METAL-26: the level models and TFC's blow sound are generated from the pinned checkout. */
class BellowsAppearanceTest {
    private static TfcCheckout checkout() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        return TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
    }

    @Test
    void levelsSpanTfcsExtensionRange() {
        assertThat(BellowsAppearance.LEVELS).isEqualTo(5);
        assertThat(BellowsAppearance.extension(0)).isEqualTo(BellowsMotion.MIN_EXTENSION);
        assertThat(BellowsAppearance.extension(BellowsAppearance.LEVELS_MAX)).isEqualTo(BellowsMotion.MAX_EXTENSION);
        assertThat(BellowsAppearance.PROPERTY).isEqualTo("pressed");
    }

    @Test
    void theBackPlateMovesTowardTheFrontPlateByTheLevel() {
        double[] plateEnd = new double[BellowsAppearance.LEVELS];
        for (int level = 0; level < BellowsAppearance.LEVELS; level++) {
            JsonObject model = BellowsAppearance.levelModel(level);
            JsonArray elements = model.getAsJsonArray("elements");
            assertThat(elements).hasSize(2 + BellowsAppearance.PLANES);
            JsonObject back = elements.get(1).getAsJsonObject();
            assertThat(back.get("name").getAsString()).isEqualTo("back");
            plateEnd[level] = back.getAsJsonArray("to").get(2).getAsDouble();
            assertThat(plateEnd[level]).isEqualTo(BellowsAppearance.extension(level) * 16);
            assertThat(back.getAsJsonArray("from").get(2).getAsDouble()).isEqualTo(plateEnd[level] - 2);
            // the middle fills the gap up to the front plate (z 14)
            JsonObject last = elements.get(elements.size() - 1).getAsJsonObject();
            assertThat(last.getAsJsonArray("to").get(2).getAsDouble()).isEqualTo(14.0);
            assertThat(elements.get(2).getAsJsonObject().getAsJsonArray("from").get(2).getAsDouble()).isEqualTo(plateEnd[level]);
            assertThat(elements.get(0).getAsJsonObject().get("name").getAsString()).isEqualTo("front");
        }
        assertThat(plateEnd).containsExactly(2, 4, 6, 8, 10);
    }

    @Test
    void theSoundIsCopiedWithItsOggAndSubtitle() {
        OutputTree out = new OutputTree();
        BellowsAppearance.writeSound(checkout(), out);
        BellowsAppearance.writeLang(checkout(), out);
        String json = new String(out.files().get("craftengine/tfp/resourcepack/assets/tfp_bellows/sounds.json"), StandardCharsets.UTF_8);
        JsonObject event = Json.parse(json, "sounds.json").getAsJsonObject().getAsJsonObject("block.bellows.blow");
        assertThat(event.get("subtitle").getAsString()).isEqualTo("subtitles.block.tfp.bellows.blow");
        assertThat(event.getAsJsonArray("sounds").get(0).getAsString()).isEqualTo("tfp_bellows:block/bellows/blow1");
        assertThat(out.files()).containsKey("craftengine/tfp/resourcepack/assets/tfp_bellows/sounds/block/bellows/blow1.ogg");
        assertThat(out.files()).containsKey("craftengine/tfp/resourcepack/assets/tfp_bellows/lang/en_us.json");
        assertThat(BellowsAppearance.soundId()).isEqualTo("tfp_bellows:block.bellows.blow");
    }
}
