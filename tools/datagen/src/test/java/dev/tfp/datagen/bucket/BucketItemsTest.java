/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.bucket;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-FLUID-02: the bucket items on the real checkout. */
class BucketItemsTest {
    private static OutputTree out;
    private static List<String> ids;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc;
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(BucketItems.available(tfc)).isTrue();
        out = new OutputTree();
        ids = BucketItems.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(BucketItems.CONFIG), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("items")).get(id);
    }

    @Test
    void everyTfcBucketIsGenerated() {
        assertThat(ids).startsWith("wooden_bucket", "metal/bucket/red_steel", "metal/bucket/blue_steel");
        // TFCItems.FLUID_BUCKETS: one vanilla-style bucket per TFC fluid source (FluidId), molten metals included
        assertThat(ids.stream().filter(i -> i.startsWith("bucket/"))).hasSize(TfcFluids.TFC_SOURCE_COUNT - 1)
            .contains("bucket/salt_water", "bucket/brine", "bucket/metal/silver");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tankBucketsShowTheFluidThroughCustomModelData() {
        Map<String, Object> wooden = item("tfp:wooden_bucket");
        Map<String, Object> data = (Map<String, Object>) wooden.get("data");
        assertThat(data.get("max_stack_size")).isEqualTo(16);
        assertThat(data.get("item_name")).isEqualTo("<!i><lang:item.tfp.wooden_bucket>");
        Map<String, Object> model = (Map<String, Object>) wooden.get("model");
        assertThat(model.get("type")).isEqualTo("minecraft:condition");
        assertThat(model.get("property")).isEqualTo("minecraft:custom_model_data");
        Map<String, Object> filled = (Map<String, Object>) model.get("on_true");
        assertThat(filled.get("path")).isEqualTo("tfp:item/wooden_bucket_filled");
        List<Map<String, Object>> tints = (List<Map<String, Object>>) filled.get("tints");
        assertThat(tints.get(1).get("type")).isEqualTo("minecraft:custom_model_data");
        assertThat(((Map<String, Object>) model.get("on_false")).get("path")).isEqualTo("tfp:item/wooden_bucket");

        JsonObject filledModel = Json.parse(new String(out.get(BucketItems.RP + "models/item/wooden_bucket_filled.json"),
            StandardCharsets.UTF_8), "model").getAsJsonObject();
        assertThat(filledModel.getAsJsonObject("textures").get("layer1").getAsString())
            .isEqualTo("tfp:item/bucket/wooden_bucket_overlay");
        assertThat(out.contains(BucketItems.RP + "textures/item/metal/bucket/red_steel.png")).isTrue();
        assertThat(out.contains(BucketItems.RP + "textures/item/metal/bucket/overlay.png")).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fluidBucketsAreTintedWithTheFluid() {
        Map<String, Object> brine = item("tfp:bucket/brine");
        assertThat(((Map<String, Object>) brine.get("data")).get("max_stack_size")).isEqualTo(1);
        List<Map<String, Object>> tints = (List<Map<String, Object>>) ((Map<String, Object>) brine.get("model")).get("tints");
        assertThat(tints.get(1).get("value")).isEqualTo(TfcFluids.get("tfp:brine").orElseThrow().rgb());
    }

    @Test
    void namesComeFromTfc() {
        byte[] en = out.get(BucketItems.LANG_RP + "en_us.json");
        assertThat(en).isNotNull();
        JsonObject lang = Json.parse(new String(en, StandardCharsets.UTF_8), "lang").getAsJsonObject();
        assertThat(lang.get("item.tfp.wooden_bucket").getAsString()).isEqualTo("Wooden Bucket");
        assertThat(lang.get("item.tfp.bucket.salt_water").getAsString()).isEqualTo("Salt Water Bucket");
    }
}
