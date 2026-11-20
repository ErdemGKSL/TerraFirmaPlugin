/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.crucible;

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

/** PAR-METAL-12: the crucible screen art on the real checkout. */
class CrucibleScreenArtTest {
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
        assertThat(CrucibleScreenArt.available(tfc)).isTrue();
        out = new OutputTree();
        assertThat(CrucibleScreenArt.run(tfc, out)).isEqualTo(3);
        tfcScreen = ImageIO.read(new ByteArrayInputStream(tfc.read(CrucibleScreenArt.TEXTURE)));
    }

    private static BufferedImage png(String name) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(out.get(CrucibleScreenArt.RP + "textures/font/gui/" + name + ".png")));
    }

    @Test
    void backgroundIsTheSixRowChestWithTfcsSlotsMovedOntoItsGrid() throws Exception {
        BufferedImage bg = png("crucible");
        assertThat(bg.getWidth()).isEqualTo(176);
        assertThat(bg.getHeight()).isEqualTo(ChestGeometry.WIDTH == 176 ? new ChestGeometry(6).height() : 0);
        // the input frames of TFC (x 25 + 18c, y 81 + 18l) land 10 higher, on chest rows 3 to 5
        for (int line = 0; line < 3; line++) {
            for (int col = 0; col < 3; col++) {
                int srcX = 25 + 18 * col;
                int srcY = 81 + 18 * line;
                assertThat(bg.getRGB(srcX + 3, srcY + DY() + 3)).as("input %d,%d", col, line)
                    .isEqualTo(tfcScreen.getRGB(srcX + 3, srcY + 3));
                ChestGeometry chest = new ChestGeometry(6);
                assertThat(chest.slotX(28 + col + 9 * line) - 1).isEqualTo(srcX);
                assertThat(chest.slotY(28 + col + 9 * line) - 1).isEqualTo(srcY + DY());
            }
        }
        // the output frame (151, 99) is chest slot 44's frame
        assertThat(bg.getRGB(151 + 3, 99 + DY() + 3)).isEqualTo(tfcScreen.getRGB(151 + 3, 99 + 3));
        // the player inventory is on the chest's rows: TFC's first inventory slot frame (7, 138)
        assertThat(bg.getRGB(8, 140)).isEqualTo(tfcScreen.getRGB(8, 140));
    }

    private static int DY() {
        return CrucibleScreenArt.DY;
    }

    @Test
    @SuppressWarnings("unchecked")
    void yamlDeclaresTheThreeImagesAtTfcsPositions() {
        Map<String, Object> root = new Yaml().load(new String(out.get(CrucibleScreenArt.CONFIG), StandardCharsets.UTF_8));
        Map<String, Object> images = (Map<String, Object>) root.get("images");
        assertThat(images.keySet()).containsExactly("tfp:gui/crucible", "tfp:gui/crucible_thermometer",
            "tfp:gui/crucible_fluid");
        assertThat(((Map<String, Object>) images.get("tfp:gui/crucible_thermometer")).get("grid_size")).isEqualTo("4,13");
        assertThat(((Map<String, Object>) images.get("tfp:gui/crucible_fluid")).get("grid_size")).isEqualTo("4,8");
    }

    @Test
    void fluidFrameFillsFromTheBottom() throws Exception {
        BufferedImage fluid = png("crucible_fluid");
        int w = CrucibleScreenArt.FLUID_WIDTH;
        int h = CrucibleScreenArt.FLUID_HEIGHT;
        // frame 5: column 5 of row 0; five rows filled
        int fx = 5 * w;
        assertThat(fluid.getRGB(fx + 3, h - 1) >>> 24).isEqualTo(255);
        assertThat(fluid.getRGB(fx + 3, h - 5) >>> 24).isEqualTo(255);
        assertThat(fluid.getRGB(fx + 3, h - 6) >>> 24).isEqualTo(0);
        // the full frame (31) is row 3, column 7
        assertThat(fluid.getRGB(7 * w + 3, 3 * h) >>> 24).isEqualTo(255);
    }
}
