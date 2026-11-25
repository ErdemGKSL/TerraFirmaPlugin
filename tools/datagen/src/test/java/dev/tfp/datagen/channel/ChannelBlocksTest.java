/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.channel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.Shape;
import dev.tfp.datagen.hitbox.TfcShapes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-METAL-13: the tfp:channel and tfp:mold_table entries on the real checkout. */
class ChannelBlocksTest {
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
        assertThat(ChannelBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = ChannelBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String file, String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(file), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void channelHasTfcsSevenStatesSettingsAndTheMetalLight() {
        assertThat(states).isEqualTo(128 + 16);
        Map<String, Object> b = block(ChannelBlocks.CHANNEL_OUTPUT, ChannelBlocks.CHANNEL_ID);
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        assertThat(args.get("device_hardness")).isEqualTo(3.0);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/pickaxe");
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        Map<String, Object> props = (Map<String, Object>) st.get("properties");
        assertThat(props.keySet()).containsExactlyElementsOf(ChannelBlocks.CHANNEL_PROPERTIES);
        Map<String, Object> variants = (Map<String, Object>) st.get("variants");
        assertThat(variants).hasSize(128);
        Map<String, Object> lit = (Map<String, Object>) variants.get(
            "east=false,south=false,west=false,north=false,down=false,triggered=false,with_metal=true");
        assertThat(((Map<String, Object>) lit.get("settings")).get("luminance")).isEqualTo(ChannelBlocks.METAL_LIGHT);
        Map<String, Object> dark = (Map<String, Object>) variants.get(
            "east=true,south=false,west=false,north=false,down=true,triggered=true,with_metal=false");
        assertThat(((Map<String, Object>) dark.get("settings")).get("luminance")).isEqualTo(0);
        assertThat(dark.get("appearance")).isEqualTo("m8_down");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyConnectionMaskSitsOnItsOwnTfcOutline() {
        Map<String, Object> b = block(ChannelBlocks.CHANNEL_OUTPUT, ChannelBlocks.CHANNEL_ID);
        Map<String, Object> appearances = (Map<String, Object>) ((Map<String, Object>) b.get("states")).get("appearances");
        assertThat(appearances).hasSize(64);
        for (int mask = 0; mask < 16; mask++) {
            String carrier = HitboxAllocation.agreed(ChannelBlocks.CHANNEL_ID, HitboxDemands.channelPattern(mask)).state();
            for (String name : List.of(ChannelBlocks.appearanceName(mask, false, false), ChannelBlocks.appearanceName(mask, true, true))) {
                assertThat(((Map<String, Object>) appearances.get(name)).get("state")).as(name).isEqualTo(carrier);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theLookFollowsTfcsMultipart() {
        Map<String, Object> b = block(ChannelBlocks.CHANNEL_OUTPUT, ChannelBlocks.CHANNEL_ID);
        Map<String, Object> appearances = (Map<String, Object>) ((Map<String, Object>) b.get("states")).get("appearances");
        // no connection, floor: base + bottom + four stops
        assertThat(renderers(appearances, "m0_floor")).hasSize(6);
        // all four connections over a drop, with metal: base + 4 connections + centre + 4 arms + the stream
        assertThat(renderers(appearances, "m15_down_metal")).hasSize(1 + 4 + 1 + 4 + 1);
        assertThat(renderers(appearances, "m15_down")).hasSize(5);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> renderers(Map<String, Object> appearances, String name) {
        Map<String, Object> a = (Map<String, Object>) appearances.get(name);
        return (List<Object>) a.get("entity_renderer");
    }

    @Test
    void moldTableHasFourConnectionsAndTfcsSlab() {
        Map<String, Object> b = block(ChannelBlocks.TABLE_OUTPUT, ChannelBlocks.TABLE_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) st.get("properties");
        assertThat(props.keySet()).containsExactly("east", "south", "west", "north");
        @SuppressWarnings("unchecked")
        Map<String, Object> variants = (Map<String, Object>) st.get("variants");
        assertThat(variants).hasSize(16);
        assertThat(TfcShapes.load().get(TfcShapes.MOLD_TABLE_KEY).selection()).isEqualTo(Shape.box(0, 0, 0, 16, 5, 16));
    }

    @Test
    void channelShapesAreTfcsPostWithAnArmPerConnection() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        Shape post = Shape.box(5, 0, 5, 11, 5, 11);
        assertThat(rows.get(TfcShapes.channelKey(0)).selection()).isEqualTo(post);
        // east is bit 8: the arm 11..16
        assertThat(rows.get(TfcShapes.channelKey(8)).selection()).isEqualTo(post.union(Shape.box(11, 0, 5, 16, 5, 11)));
        // TFC's north arm runs to z 10 (a quirk kept as is)
        assertThat(rows.get(TfcShapes.channelKey(4)).selection()).isEqualTo(post.union(Shape.box(5, 0, 0, 11, 5, 10)));
        assertThat(rows.get(TfcShapes.channelKey(15)).selection().height()).isEqualTo(5);
    }

    @Test
    void modelsTexturesItemsAndNamesAreWritten() {
        for (String f : new String[] {"models/block/channel_base.json", "models/block/channel_bottom.json",
            "models/block/channel_connection.json", "models/block/channel_stop.json", "models/block/mold_table_base.json",
            "models/block/mold_table_stop.json", "models/block/channel_flow_center.json", "models/block/channel_flow_arm.json",
            "models/block/channel_flow_fall.json", "models/item/channel.json", "models/item/mold_table.json",
            "textures/block/crucible_like.png", "textures/block/crucible_top_like.png", "textures/block/molten_still.png"}) {
            assertThat(out.get(ChannelBlocks.RP + f)).as(f).isNotNull();
        }
        assertThat(new String(out.get(ChannelBlocks.RP + "models/block/mold_table_base.json"), StandardCharsets.UTF_8))
            .doesNotContain("loader");
        String lang = new String(out.get(ChannelBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(lang).contains("\"block.tfp.channel\"").contains("Casting Channel").contains("\"block.tfp.mold_table\"");
    }
}
