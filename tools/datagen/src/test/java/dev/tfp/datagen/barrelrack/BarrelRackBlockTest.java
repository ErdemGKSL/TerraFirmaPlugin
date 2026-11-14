/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.barrelrack;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/** PAR-PROC-38: the tfp:barrel_rack entry on the real checkout. */
class BarrelRackBlockTest {
    private static OutputTree out;
    private static int states;

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
        assertThat(BarrelRackBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = BarrelRackBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        LoaderOptions opts = new LoaderOptions();
        opts.setCodePointLimit(Integer.MAX_VALUE);
        return new Yaml(opts).load(new String(out.get(BarrelRackBlock.OUTPUT), StandardCharsets.UTF_8));
    }

    @Test
    @SuppressWarnings("unchecked")
    void oneStateWithTfcsSettingsSupportAndTheSelectorsCarrier() {
        assertThat(states).isEqualTo(1);
        Map<String, Object> blocks = (Map<String, Object>) root().get("blocks");
        assertThat(blocks.keySet()).containsExactly("tfp:barrel_rack");
        Map<String, Object> b = (Map<String, Object>) blocks.get("tfp:barrel_rack");
        Map<String, Object> settings = (Map<String, Object>) b.get("settings");
        // TFCBlocks.BARREL_RACK: strength(4f), SoundType.WOOD, flammableLikePlanks = flammable(20, 5); the axe tag
        assertThat(settings.get("hardness")).isEqualTo(4.0);
        assertThat(settings.get("resistance")).isEqualTo(4.0);
        assertThat(settings.get("tags")).isEqualTo(List.of("minecraft:mineable/axe"));
        assertThat((Map<String, Object>) settings.get("sounds")).containsEntry("break", "minecraft:block.wood.break");
        assertThat(settings).containsEntry("burnable", true).containsEntry("burn_chance", 5)
            .containsEntry("fire_spread_chance", 20).containsEntry("can_occlude", false);
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        // BottomSupportedDeviceBlock.canSurvive: a sturdy top face below
        assertThat((Map<String, Object>) b.get("behavior")).containsEntry("type", "sturdy_base_block")
            .containsEntry("direction", "down").containsEntry("support_types", List.of("full"));
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        assertThat(st).doesNotContainKeys("properties", "variants");
        Map<String, Object> app = (Map<String, Object>) ((Map<String, Object>) st.get("appearances")).get("base");
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(BarrelRackBlock.ID, BarrelRackBlock.APPEARANCE);
        assertThat(a.demand().tfcKey()).isEqualTo("tfc:barrel_rack");
        assertThat(app.get("state")).isEqualTo(a.state());
        assertThat(app.get("transparent")).isEqualTo(true);
        List<Map<String, Object>> r = (List<Map<String, Object>>) app.get("entity_renderer");
        assertThat(r).hasSize(1);
        assertThat(r.getFirst()).containsEntry("item", BarrelRackBlock.RENDER_ITEM).containsEntry("rotation", "0,180,0");
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        assertThat(items.keySet()).containsExactlyInAnyOrder("tfp:barrel_rack", BarrelRackBlock.RENDER_ITEM);
        assertThat((Map<String, Object>) ((Map<String, Object>) items.get("tfp:barrel_rack")).get("arguments"))
            .containsEntry("item_lang_key", "block.tfp.barrel_rack");
        assertThat((Map<String, Object>) ((Map<String, Object>) items.get(BarrelRackBlock.RENDER_ITEM)).get("arguments"))
            .containsEntry("render_model", "tfp:block/barrel_rack");
    }

    @Test
    void modelTextureAndNamesAreCopied() {
        String rp = "craftengine/tfp/resourcepack/assets/tfp/";
        JsonObject block = json(rp + "models/block/barrel_rack.json");
        assertThat(block.getAsJsonObject("textures").get("rack").getAsString()).isEqualTo("tfp:block/barrel_rack");
        assertThat(block.getAsJsonArray("elements")).hasSize(5);
        assertThat(json(rp + "models/item/barrel_rack.json").get("parent").getAsString()).isEqualTo("tfp:block/barrel_rack");
        assertThat(out.contains(rp + "textures/block/barrel_rack.png")).isTrue();
        JsonObject en = json("craftengine/tfp/resourcepack/assets/tfp_barrel_rack/lang/en_us.json");
        assertThat(en.get("block.tfp.barrel_rack").getAsString()).isEqualTo("Barrel Rack");
    }

    private static JsonObject json(String path) {
        byte[] bytes = out.get(path);
        assertThat(bytes).as(path).isNotNull();
        return Json.parse(new String(bytes, StandardCharsets.UTF_8), path).getAsJsonObject();
    }
}
