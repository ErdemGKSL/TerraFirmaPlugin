/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.firebox;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.core.firebox.FireboxScreenLayout;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.ProgressFrames;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-HEAT-12: the tfp:firebox entry and the firebox screen art on the real checkout. */
class FireboxBlockTest {
    private static final String RP = "craftengine/tfp/resourcepack/assets/tfp/";
    private static OutputTree out;
    private static int states;
    private static int images;
    private static TfcCheckout tfc;

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
        assertThat(FireboxBlock.available(tfc)).isTrue();
        assertThat(FireboxScreenArt.available(tfc)).isTrue();
        out = new OutputTree();
        states = FireboxBlock.run(tfc, out);
        images = FireboxScreenArt.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String file) {
        return new Yaml().load(new String(out.get(file), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block() {
        return (Map<String, Object>) ((Map<String, Object>) yaml(FireboxBlock.OUTPUT).get("blocks")).get(FireboxBlock.ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void litPropertyTfcSettingsLootAndLight() {
        assertThat(states).isEqualTo(2);
        Map<String, Object> b = block();
        assertThat(b.get("template")).isEqualTo("tfp:block/device");
        Map<String, Object> args = (Map<String, Object>) b.get("arguments");
        // TFCBlocks: strength(6f); generated mineable/pickaxe.json; metal stands in for SoundType.COPPER (ledger)
        assertThat(args.get("device_hardness")).isEqualTo(6.0);
        assertThat(args.get("device_resistance")).isEqualTo(6.0);
        assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/pickaxe");
        assertThat(((Map<String, Object>) args.get("device_sound_break")).get("id")).isEqualTo("minecraft:block.metal.break");
        assertThat(((Map<String, Object>) b.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        Map<String, Object> props = (Map<String, Object>) st.get("properties");
        assertThat(props.keySet()).containsExactly(FireboxBlock.LIT);
        assertThat((Map<String, Object>) props.get(FireboxBlock.LIT)).containsEntry("type", "boolean").containsEntry("default", false);
        Map<String, Object> looks = (Map<String, Object>) st.get("appearances");
        assertThat(looks.keySet()).containsExactly(FireboxBlock.UNLIT_LOOK, FireboxBlock.LIT_LOOK);
        assertThat((Map<String, Object>) looks.get(FireboxBlock.UNLIT_LOOK)).containsEntry("auto_state", "solid")
            .containsEntry("model", "tfp:block/firebox_off");
        assertThat((Map<String, Object>) looks.get(FireboxBlock.LIT_LOOK)).containsEntry("auto_state", "solid")
            .containsEntry("model", "tfp:block/firebox_on");
        Map<String, Object> variants = (Map<String, Object>) st.get("variants");
        Map<String, Object> lit = (Map<String, Object>) variants.get("lit=true");
        assertThat(lit.get("appearance")).isEqualTo(FireboxBlock.LIT_LOOK);
        assertThat(((Map<String, Object>) lit.get("settings")).get("luminance")).isEqualTo(15);
        Map<String, Object> unlit = (Map<String, Object>) variants.get("lit=false");
        assertThat(((Map<String, Object>) unlit.get("settings")).get("luminance")).isEqualTo(0);
        Map<String, Object> items = (Map<String, Object>) yaml(FireboxBlock.OUTPUT).get("items");
        assertThat(items).containsKey(FireboxBlock.ID);
    }

    @Test
    void modelsTexturesAndNames() {
        JsonObject on = Json.parse(new String(out.get(RP + "models/block/firebox_on.json"), StandardCharsets.UTF_8), "on")
            .getAsJsonObject();
        assertThat(on.get("parent").getAsString()).isEqualTo("block/cube_column");
        assertThat(on.getAsJsonObject("textures").get("side").getAsString()).isEqualTo("tfp:block/devices/firebox/on");
        assertThat(out.get(RP + "models/block/firebox_off.json")).isNotNull();
        assertThat(out.get(RP + "models/item/firebox.json")).isNotNull();
        for (String t : new String[] {"on.png", "on.png.mcmeta", "off.png", "top.png"}) {
            assertThat(out.get(RP + "textures/block/devices/firebox/" + t)).as(t).isNotNull();
        }
        JsonObject en = Json.parse(new String(out.get("craftengine/tfp/resourcepack/assets/tfp_firebox/lang/en_us.json"),
            StandardCharsets.UTF_8), "en").getAsJsonObject();
        assertThat(en.get(FireboxBlock.LANG_KEY).getAsString()).isEqualTo("Firebox");
    }

    @Test
    @SuppressWarnings("unchecked")
    void screenArtOnTheFiveRowChest() throws IOException {
        assertThat(images).isEqualTo(2);
        Map<String, Object> imgs = (Map<String, Object>) yaml(FireboxScreenArt.CONFIG).get("images");
        assertThat(imgs.keySet()).containsExactly("tfp:gui/firebox", "tfp:gui/firebox_thermometer");
        BufferedImage bg = ImageIO.read(new ByteArrayInputStream(out.get(RP + FireboxScreenLayout.BACKGROUND.packPath())));
        assertThat(bg.getWidth()).isEqualTo(ChestGeometry.WIDTH);
        assertThat(bg.getHeight()).isEqualTo(FireboxScreenLayout.CHEST.height());
        BufferedImage src = FireboxScreenArt.read(tfc.read(FireboxScreenArt.TEXTURE), "src");
        // every TFC slot frame lands exactly one pixel above where TFC drew it, on its chest slot
        for (int i = 0; i < 16; i++) {
            int slot = FireboxScreenLayout.chestSlot(i);
            int x = FireboxScreenLayout.CHEST.slotX(slot) - 1;
            int y = FireboxScreenLayout.CHEST.slotY(slot) - 1;
            int tx = 62 + 18 * (i % 4) - 1;
            int ty = 19 + 18 * (i / 4) - 1;
            for (int d = 0; d < 18; d++) {
                assertThat(bg.getRGB(x + d, y)).isEqualTo(src.getRGB(tx + d, ty));
                assertThat(bg.getRGB(x, y + d)).isEqualTo(src.getRGB(tx, ty + d));
            }
        }
        // the player inventory and hotbar frames sit on the chest's rows
        int py = FireboxScreenLayout.CHEST.playerRowY(0) - 1;
        assertThat(bg.getRGB(8, py)).isEqualTo(src.getRGB(8, 119));
        int hy = FireboxScreenLayout.CHEST.hotbarY() - 1;
        assertThat(bg.getRGB(8, hy)).isEqualTo(src.getRGB(8, 177));
        BufferedImage strip = ImageIO.read(new ByteArrayInputStream(out.get(RP + FireboxScreenLayout.THERMOMETER.packPath())));
        assertThat(strip.getWidth()).isEqualTo(FireboxScreenLayout.THERMOMETER.pngWidth());
        assertThat(strip.getHeight()).isEqualTo(FireboxScreenLayout.THERMOMETER.pngHeight());
        assertThat(ProgressFrames.THERMOMETER_FRAMES).isEqualTo(FireboxScreenLayout.THERMOMETER.frames());
    }
}
