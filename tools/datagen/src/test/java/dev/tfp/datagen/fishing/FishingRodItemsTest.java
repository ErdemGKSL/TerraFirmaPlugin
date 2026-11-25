/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.fishing;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.material.ItemCoverage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-FAUNA-35: the nine metal fishing rods on the real checkout. */
class FishingRodItemsTest {
    private static OutputTree out;
    private static TfcCheckout tfc;
    private static List<String> metals;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(FishingRodItems.available(tfc)).isTrue();
        out = new OutputTree();
        metals = FishingRodItems.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(FishingRodItems.CONFIG), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("items")).get(id);
    }

    @Test
    void theNineTfcRodsExistWithTheirModelsAndTextures() {
        assertThat(metals).containsExactlyInAnyOrder("copper", "bronze", "black_bronze", "bismuth_bronze", "wrought_iron", "steel",
            "black_steel", "blue_steel", "red_steel");
        for (String m : metals) {
            assertThat(item("tfp:metal/fishing_rod/" + m)).as(m).isNotNull();
            assertThat(out.contains(FishingRodItems.RP + "models/item/metal/fishing_rod/" + m + ".json")).as(m).isTrue();
            assertThat(out.contains(FishingRodItems.RP + "models/item/metal/fishing_rod/" + m + "_cast.json")).as(m).isTrue();
            assertThat(out.contains(FishingRodItems.RP + "textures/item/metal/fishing_rod/" + m + ".png")).as(m).isTrue();
        }
        assertThat(out.contains(FishingRodItems.RP + "textures/item/metal/fishing_rod/alt_cast.png")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void durabilityIsTheMetalTiersUsesAndTheModelSwitchesOnCast() {
        Map<String, Object> copper = item("tfp:metal/fishing_rod/copper");
        assertThat(copper.get("material")).isEqualTo("fishing_rod");
        Map<String, Object> data = (Map<String, Object>) copper.get("data");
        assertThat(data.get("max_damage")).isEqualTo(600);
        assertThat(data.get("max_stack_size")).isEqualTo(1);
        assertThat(data.get("item_name")).isEqualTo("<!i><lang:item.tfp.metal.fishing_rod.copper>");
        assertThat(((Map<String, Object>) item("tfp:metal/fishing_rod/red_steel").get("data")).get("max_damage")).isEqualTo(6500);
        assertThat(((Map<String, Object>) item("tfp:metal/fishing_rod/steel").get("data")).get("max_damage")).isEqualTo(3300);
        Map<String, Object> model = (Map<String, Object>) copper.get("model");
        assertThat(model.get("type")).isEqualTo("minecraft:condition");
        assertThat(model.get("property")).isEqualTo("minecraft:fishing_rod/cast");
        assertThat(((Map<String, Object>) model.get("on_true")).get("path")).isEqualTo("tfp:item/metal/fishing_rod/copper_cast");
        assertThat(((Map<String, Object>) model.get("on_false")).get("path")).isEqualTo("tfp:item/metal/fishing_rod/copper");
    }

    @Test
    void modelsAreTfcsAndTheCastOneUsesTheVanillaOrAltSprite() {
        JsonObject base = model("metal/fishing_rod/copper");
        assertThat(base.get("parent").getAsString()).isEqualTo("item/handheld_rod");
        assertThat(base.has("overrides")).isFalse();
        assertThat(base.getAsJsonObject("textures").get("layer0").getAsString()).isEqualTo("tfp:item/metal/fishing_rod/copper");
        assertThat(model("metal/fishing_rod/copper_cast").getAsJsonObject("textures").get("layer0").getAsString())
            .isEqualTo("minecraft:item/fishing_rod_cast");
        assertThat(model("metal/fishing_rod/red_steel_cast").getAsJsonObject("textures").get("layer0").getAsString())
            .isEqualTo("tfp:item/metal/fishing_rod/alt_cast");
    }

    private static JsonObject model(String id) {
        return Json.parse(new String(out.get(FishingRodItems.RP + "models/item/" + id + ".json"), StandardCharsets.UTF_8), "model")
            .getAsJsonObject();
    }

    @Test
    void namesComeFromTfcAndCoverageNamesTheGenerator() {
        JsonObject lang = Json.parse(new String(out.get(FishingRodItems.LANG_RP + "en_us.json"), StandardCharsets.UTF_8), "lang")
            .getAsJsonObject();
        assertThat(lang.get("item.tfp.metal.fishing_rod.copper").getAsString()).isEqualTo("Copper Fishing Rod");
        assertThat(lang.get("item.tfp.metal.fishing_rod.black_bronze").getAsString()).isEqualTo("Black Bronze Fishing Rod");
        assertThat(ItemCoverage.OWNERS.stream().noneMatch(o -> o.pattern().pattern().contains("fishing_rod"))).isTrue();
    }
}
