/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.aquatic;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
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

/** PAR-FAUNA-25: the mob bucket items on the real checkout. */
class MobBucketItemsTest {
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
        assertThat(MobBucketItems.available(tfc)).isTrue();
        out = new OutputTree();
        ids = MobBucketItems.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(MobBucketItems.CONFIG), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("items")).get(id);
    }

    @Test
    void theTwentyTfcMobBucketsExist() {
        assertThat(ids).hasSize(20).contains("bucket/cod", "bucket/pufferfish", "bucket/jellyfish", "bucket/tropical_fish",
            "bucket/bluegill", "bucket/tilapia", "bucket/rainbow_trout");
        for (String id : ids) {
            assertThat(item("tfp:" + id)).as(id).isNotNull();
            assertThat(out.contains(MobBucketItems.RP + "models/item/" + id + ".json")).as(id).isTrue();
            assertThat(out.contains(MobBucketItems.RP + "textures/item/" + id + ".png")).as(id).isTrue();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void entriesStackToOneWithTfcModelAndName() {
        Map<String, Object> cod = item("tfp:bucket/cod");
        Map<String, Object> data = (Map<String, Object>) cod.get("data");
        assertThat(data.get("max_stack_size")).isEqualTo(1);
        assertThat(data.get("item_name")).isEqualTo("<!i><lang:item.tfp.bucket.cod>");
        assertThat(((Map<String, Object>) cod.get("model")).get("path")).isEqualTo("tfp:item/bucket/cod");
        JsonObject model = Json.parse(new String(out.get(MobBucketItems.RP + "models/item/bucket/cod.json"),
            StandardCharsets.UTF_8), "model").getAsJsonObject();
        assertThat(model.getAsJsonObject("textures").get("layer0").getAsString()).isEqualTo("tfp:item/bucket/cod");
    }

    @Test
    void namesComeFromTfc() {
        JsonObject lang = Json.parse(new String(out.get(MobBucketItems.LANG_RP + "en_us.json"), StandardCharsets.UTF_8), "lang")
            .getAsJsonObject();
        assertThat(lang.get("item.tfp.bucket.cod").getAsString()).isEqualTo("Cod Bucket");
        assertThat(lang.get("item.tfp.bucket.bluegill").getAsString()).isEqualTo("Bluegill Bucket");
    }
}
