/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.light.candle;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * PAR-HEAT-11: the candle entries on the real checkout, with stand-in carriers (the selector's choice comes with
 * PAR-HEAT-26): candles on their vanilla look, candle cakes and the jack-o'-lantern with TFC's (vanilla) model.
 */
class CandleBlocksTest {
    private static OutputTree out;
    private static int states;

    static CandleBlocks.Carrier carrier(String block, String appearance) {
        CandleBlocks.Look look = block.startsWith(CandleBlocks.CANDLE + "/") || block.equals(CandleBlocks.CANDLE)
            ? CandleBlocks.Look.VANILLA : CandleBlocks.Look.MODEL;
        return new CandleBlocks.Carrier("carrier:" + block + "[" + appearance + "]", look);
    }

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
        assertThat(CandleBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = CandleBlocks.run(tfc, out, CandleBlocksTest::carrier);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(CandleBlocks.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o, String key) {
        return (Map<String, Object>) ((Map<String, Object>) o).get(key);
    }

    private static Map<String, Object> block(String id) {
        return map(map(root(), "blocks"), id);
    }

    @Test
    void thirtyFiveBlocksAndEighteenItems() {
        assertThat(states).isEqualTo(17 * 16 + 17 * 2 + 4);
        assertThat(map(root(), "blocks").keySet()).containsExactlyElementsOf(CandleBlocks.blocks());
        assertThat(map(root(), "items").keySet()).hasSize(18).contains("tfp:candle/red", "tfp:jack_o_lantern")
            .doesNotContain("tfp:candle_cake");
        for (String id : CandleBlocks.blocks()) {
            assertThat(block(id)).as(id).doesNotContainKey("loot");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void candlesStackGlowAndWaterlog() {
        Map<String, Object> red = block("tfp:candle/red");
        assertThat(String.valueOf(map(red, "settings").get("hardness"))).isEqualTo("0.1");
        assertThat(map(map(red, "settings"), "sounds").get("break")).isEqualTo("minecraft:block.candle.break");
        List<Map<String, Object>> b = (List<Map<String, Object>>) red.get("behavior");
        assertThat(b).contains(Map.of("type", "tfp:device"),
            Map.of("type", "stackable_block", "property", "candles", "items", List.of("tfp:candle/red")));
        Map<String, Object> variants = map(map(red, "states"), "variants");
        assertThat(variants).hasSize(16);
        assertThat(map(map(variants, "candles=4,lit=true,waterlogged=false"), "settings")).containsEntry("luminance", 14);
        assertThat(map(map(variants, "candles=1,lit=true,waterlogged=true"), "settings"))
            .containsEntry("luminance", 5).containsEntry("fluid_state", "water");
        assertThat(map(map(variants, "candles=2,lit=false,waterlogged=false"), "settings")).containsEntry("luminance", 0);
        assertThat(map(map(map(red, "states"), "appearances"), "candles=3,lit=true,waterlogged=false"))
            .containsExactly(Map.entry("state", "carrier:tfp:candle/red[candles=3,lit=true,waterlogged=false]"));
    }

    @Test
    void candleCakesAndTheJackOLantern() {
        Map<String, Object> cake = block("tfp:candle_cake/black");
        assertThat(map(map(map(cake, "states"), "variants"), "lit=true")).containsEntry("settings", Map.of("luminance", 3));
        assertThat(map(map(map(cake, "states"), "appearances"), "lit=true"))
            .containsEntry("model", "minecraft:block/black_candle_cake_lit");
        assertThat(map(cake, "settings")).containsEntry("push_reaction", "normal");
        assertThat(map(block("tfp:candle_cake"), "settings")).containsEntry("push_reaction", "destroy");
        Map<String, Object> jack = block("tfp:jack_o_lantern");
        assertThat(map(jack, "settings")).containsEntry("luminance", 15);
        Map<String, Object> apps = map(map(jack, "states"), "appearances");
        assertThat(map(apps, "east")).containsEntry("model", Map.of("path", "minecraft:block/jack_o_lantern", "y", 90));
        assertThat(map(apps, "north")).containsEntry("model", "minecraft:block/jack_o_lantern");
    }

    @Test
    void modelsLootAndNamesAreTfcs() {
        String rp = "craftengine/tfp/resourcepack/assets/tfp/";
        assertThat(text(rp + "models/item/candle/black.json")).contains("item/black_candle").doesNotContain("tfc:");
        assertThat(text(rp + "models/item/jack_o_lantern.json")).contains("block/jack_o_lantern");
        assertThat(text("data/tfp/loot_table/blocks/candle_cake/red.json")).contains("\"tfp:candle/red\"");
        assertThat(text("data/tfp/loot_table/blocks/jack_o_lantern.json")).contains("minecraft:carved_pumpkin");
        assertThat(text("data/tfp/loot_table/blocks/candle.json")).contains("\"candles\": \"4\"");
        String en = text("craftengine/tfp/resourcepack/assets/tfp_candle/lang/en_us.json");
        assertThat(en).contains("\"block.tfp.candle.red\": \"Red Candle\"").contains("\"block.tfp.jack_o_lantern\"")
            .contains("\"block.tfp.candle_cake\": \"Candle Cake\"");
    }

    private static String text(String path) {
        byte[] b = out.get(path);
        assertThat(b).as(path).isNotNull();
        return new String(b, StandardCharsets.UTF_8);
    }
}
