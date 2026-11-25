/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.pan;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PanItemsTest {
    private static TfcCheckout pinned() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        try {
            return TfcCheckout.open(checkout, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return null;
        }
    }

    private static String text(OutputTree out, String path) {
        return new String(out.get(path), StandardCharsets.UTF_8);
    }

    @Test
    void emitsItemsModelsAndEveryDepositTable() {
        TfcCheckout tfc = pinned();
        OutputTree out = new OutputTree();
        PanItems.run(tfc, out);
        assertThat(PanItems.modelKeys(tfc)).hasSize(84).contains("native_copper/granite", "cassiterite/slate");
        assertThat(text(out, PanItems.CONFIG)).contains("tfp:pan/empty:", "tfp:pan/filled:", "consume_seconds: 6",
            "animation: brush", "when: native_copper/granite");
        assertThat(out.contains(PanItems.RP + "models/item/pan/native_gold/basalt_full.json")).isTrue();
        assertThat(out.contains(PanItems.RP + "textures/item/pan/grid.png")).isTrue();
        assertThat(out.files().keySet().stream().filter(k -> k.startsWith(PanItems.LOOT_TARGET)).count()).isEqualTo(84);
    }

    @Test
    void depositTableIsTfcsRenamedWithSluiceCondition() {
        TfcCheckout tfc = pinned();
        OutputTree out = new OutputTree();
        PanItems.run(tfc, out);
        String text = text(out, PanItems.LOOT_TARGET + "native_copper_granite.json");
        JsonObject table = Json.parse(text, "t").getAsJsonObject();
        assertThat(table.get("type").getAsString()).isEqualTo("minecraft:entity");
        assertThat(text).contains("tfp:ore/small_native_copper", "tfp:rock/loose/granite", "tfp:ore/topaz",
            "tfp:is_sluice", "0.0533").doesNotContain("tfc:");
    }
}
