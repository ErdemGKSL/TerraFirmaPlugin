/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.charcoalforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-METAL-09: the charcoal forge screen art on the real checkout. */
class CharcoalForgeScreenArtTest {
    private static OutputTree out;
    private static BufferedImage tfcScreen;

    @BeforeAll
    static void generate() throws Exception {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc;
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(CharcoalForgeScreenArt.available(tfc)).isTrue();
        out = new OutputTree();
        assertThat(CharcoalForgeScreenArt.run(tfc, out)).isEqualTo(2);
        tfcScreen = ImageIO.read(new ByteArrayInputStream(tfc.read(CharcoalForgeScreenArt.TEXTURE)));
    }

    private static BufferedImage png(String name) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(out.get(CharcoalForgeScreenArt.RP + "textures/font/gui/" + name + ".png")));
    }

    /** TFC's slot frames (x - 1, y - 1, 18 x 18) at the slots of its container. */
    private static final int[][] TFC_SLOTS = {
        {80, 70}, {98, 52}, {62, 52}, {116, 34}, {44, 34}, {80, 52}, {98, 34}, {62, 34}, {116, 16}, {44, 16},
        {152, 16}, {152, 34}, {152, 52}, {152, 70}};

    @Test
    void backgroundIsTheFourRowChestWithTfcsSlotsMovedOntoItsGrid() throws Exception {
        BufferedImage bg = png("charcoal_forge");
        assertThat(bg.getWidth()).isEqualTo(176);
        assertThat(bg.getHeight()).isEqualTo(new ChestGeometry(4).height()).isEqualTo(186);
        for (int[] s : TFC_SLOTS) {
            for (int j = 0; j < 18; j++) {
                for (int i = 0; i < 18; i++) {
                    assertThat(bg.getRGB(s[0] - 1 + i, s[1] - 1 + CharcoalForgeScreenArt.DY + j))
                        .as("slot frame pixel %d,%d of the slot at %d,%d", i, j, s[0], s[1])
                        .isEqualTo(tfcScreen.getRGB(s[0] - 1 + i, s[1] - 1 + j));
                }
            }
        }
        // the player inventory frames of the chest (x 7, y 102 + 18 row) are TFC's (y 103 + 18 row)
        ChestGeometry chest = new ChestGeometry(4);
        for (int row = 0; row < 3; row++) {
            for (int j = 0; j < 18; j++) {
                for (int i = 0; i < 162; i++) {
                    assertThat(bg.getRGB(7 + i, chest.playerRowY(row) - 1 + j))
                        .isEqualTo(tfcScreen.getRGB(7 + i, 84 + 20 + 18 * row - 1 + j));
                }
            }
        }
        // the top and the thermometer box are TFC's, moved down
        for (int x = 0; x < 176; x++) {
            assertThat(bg.getRGB(x, 0)).isEqualTo(tfcScreen.getRGB(x, 0));
            assertThat(bg.getRGB(x, 30)).isEqualTo(tfcScreen.getRGB(x, 28));
        }
    }

    @Test
    void thermometerFramesAreTfcsIndicatorOnItsBar() throws Exception {
        BufferedImage t = png("charcoal_forge_thermometer");
        assertThat(t.getWidth()).isEqualTo(CharcoalForgeScreenArt.THERMOMETER.pngWidth());
        // frame 51 is the full bar: the indicator at the frame top
        for (int j = 0; j < 5; j++) {
            for (int i = 0; i < 15; i++) {
                int f = 51;
                int fx = CharcoalForgeScreenArt.THERMOMETER.frameCol(f) * 15;
                int fy = CharcoalForgeScreenArt.THERMOMETER.frameRow(f) * CharcoalForgeScreenArt.THERMOMETER.height();
                assertThat(t.getRGB(fx + i, fy + j)).isEqualTo(tfcScreen.getRGB(176 + i, j));
            }
        }
        assertThat(CharcoalForgeScreenArt.THERMOMETER.x()).isEqualTo(8);
        assertThat(CharcoalForgeScreenArt.THERMOMETER.y()).isEqualTo(76 - 51 + 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void yamlDeclaresBothImagesOnTheGuiFont() {
        String yaml = new String(out.get(CharcoalForgeScreenArt.CONFIG), StandardCharsets.UTF_8);
        Map<String, Object> images = (Map<String, Object>) ((Map<String, Object>) new Yaml().load(yaml)).get("images");
        assertThat(images).containsKeys("tfp:gui/charcoal_forge", "tfp:gui/charcoal_forge_thermometer");
        Map<String, Object> bg = (Map<String, Object>) images.get("tfp:gui/charcoal_forge");
        assertThat(bg.get("font")).isEqualTo("tfp:gui");
        assertThat(bg.get("height")).isEqualTo(186);
    }
}
