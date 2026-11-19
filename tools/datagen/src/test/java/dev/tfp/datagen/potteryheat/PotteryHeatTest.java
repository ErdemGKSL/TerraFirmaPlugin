/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.potteryheat;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.pottery.PotteryItems;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** PAR-POT-11: fired vessels and molds get the pottery heat capacity, on the real pinned checkout. */
class PotteryHeatTest {
    @Test
    void everyFiredVesselAndMoldHasHeatCapacity1_2() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc;
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        Datagen.Result full = Datagen.generate(tfc.root(), TfcCheckout.PINNED_COMMIT);
        byte[] bytes = full.tree().files().get(PotteryHeat.PATH);
        assertThat(bytes).isNotNull();
        JsonObject o = Json.parse(new String(bytes, StandardCharsets.UTF_8), PotteryHeat.PATH).getAsJsonObject();
        assertThat(o.get("heat_capacity").getAsFloat()).isEqualTo(1.2f);
        List<String> ids = o.getAsJsonArray("ingredient").asList().stream()
            .map(e -> e.getAsJsonObject().get("item").getAsString()).collect(Collectors.toList());
        assertThat(ids).doesNotHaveDuplicates()
            .contains("tfp:ceramic/vessel", "tfp:ceramic/red_glazed_vessel", "tfp:ceramic/ingot_mold",
                "tfp:ceramic/bell_mold", "tfp:ceramic/fire_ingot_mold")
            .doesNotContain("tfp:ceramic/unfired_vessel", "tfp:ceramic/jug", "tfp:ceramic/unfired_ingot_mold");
        assertThat(ids.stream().filter(i -> i.endsWith("_glazed_vessel"))).hasSize(16);
        List<String> expected = PotteryItems.items(tfc).stream().map(PotteryItems.PotteryItem::id)
            .filter(i -> (i.endsWith("_mold") || i.endsWith("vessel")) && !i.contains("unfired") && !i.contains("large"))
            .map(i -> "tfp:" + i).toList();
        assertThat(ids).containsExactlyInAnyOrderElementsOf(expected);
    }
}
