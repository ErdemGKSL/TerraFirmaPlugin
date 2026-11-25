/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.anvil;

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

/** PAR-METAL-04: the anvil plan screen art on the real checkout. */
class AnvilPlanArtTest {
    private static OutputTree out;
    private static BufferedImage tfc;

    @BeforeAll
    static void generate() throws Exception {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout checkout;
        try {
            checkout = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(AnvilPlanArt.available(checkout)).isTrue();
        out = new OutputTree();
        assertThat(AnvilPlanArt.run(checkout, out)).isEqualTo(3);
        tfc = ImageIO.read(new ByteArrayInputStream(checkout.read(AnvilPlanArt.TEXTURE)));
    }

    private static BufferedImage png(String name) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(out.get(AnvilPlanArt.RP + "textures/font/gui/" + name + ".png")));
    }

    @Test
    void backgroundIsTheThreeRowChestWithTfcsInventoryOnItsRows() throws Exception {
        BufferedImage bg = png("anvil_plan");
        assertThat(bg.getWidth()).isEqualTo(176);
        assertThat(bg.getHeight()).isEqualTo(new ChestGeometry(3).height()).isEqualTo(168);
        // TFC's first player inventory slot frame (7, 83) is one pixel lower: the chest's frame of slot (0, 0) of the inventory
        ChestGeometry chest = new ChestGeometry(3);
        int frameY = chest.playerRowY(0) - 1;
        assertThat(frameY - 83).isEqualTo(1);
        assertThat(bg.getRGB(8, frameY + 3)).isEqualTo(tfc.getRGB(8, 83 + 3));
        // the hotbar frame too
        assertThat(bg.getRGB(8, chest.hotbarY() - 1 + 3)).isEqualTo(tfc.getRGB(8, 141 + 3));
        // the top panel is plain where the buttons go: no slot frame is baked in
        assertThat(bg.getRGB(9, 19)).isEqualTo(bg.getRGB(100, 40));
    }

    @Test
    @SuppressWarnings("unchecked")
    void yamlDeclaresTheThreeImagesAtTfcsPositions() {
        Map<String, Object> root = new Yaml().load(new String(out.get(AnvilPlanArt.CONFIG), StandardCharsets.UTF_8));
        Map<String, Object> images = (Map<String, Object>) root.get("images");
        assertThat(images.keySet()).containsExactly("tfp:gui/anvil_plan", "tfp:gui/anvil_plan_buttons", "tfp:gui/anvil_plan_arrows");
        assertThat(((Map<String, Object>) images.get("tfp:gui/anvil_plan_buttons")).get("grid_size")).isEqualTo("4,5");
        assertThat(((Map<String, Object>) images.get("tfp:gui/anvil_plan_buttons")).get("height")).isEqualTo(36);
        assertThat(((Map<String, Object>) images.get("tfp:gui/anvil_plan_buttons")).get("ascent")).isEqualTo(13 - 17);
        assertThat(((Map<String, Object>) images.get("tfp:gui/anvil_plan_arrows")).get("grid_size")).isEqualTo("2,2");
    }

    @Test
    void buttonFramesFillRowByRowFromTfcsSprite() throws Exception {
        BufferedImage b = png("anvil_plan_buttons");
        int w = 162;
        int h = 36;
        // frame 0 is empty; frame 3 (col 3, row 0) has three cells; frame 11 (col 1, row 2) has eleven: nine and two
        assertThat(b.getRGB(10, 10) >>> 24).isZero();
        int f3x = 3 * w;
        assertThat(b.getRGB(f3x + 5, 5)).isEqualTo(tfc.getRGB(176 + 5, 5));
        assertThat(b.getRGB(f3x + 2 * 18 + 5, 5)).isEqualTo(tfc.getRGB(176 + 5, 5));
        assertThat(b.getRGB(f3x + 3 * 18 + 5, 5) >>> 24).isZero();
        assertThat(b.getRGB(f3x + 5, 18 + 5) >>> 24).isZero();
        int f11x = 1 * w;
        int f11y = 2 * h;
        assertThat(b.getRGB(f11x + 8 * 18 + 5, f11y + 5)).isEqualTo(tfc.getRGB(176 + 5, 5));
        assertThat(b.getRGB(f11x + 1 * 18 + 5, f11y + 18 + 5)).isEqualTo(tfc.getRGB(176 + 5, 5));
        assertThat(b.getRGB(f11x + 2 * 18 + 5, f11y + 18 + 5) >>> 24).isZero();
        // frame 18 (col 3, row 3): all eighteen
        assertThat(b.getRGB(3 * w + 8 * 18 + 5, 3 * h + 18 + 5)).isEqualTo(tfc.getRGB(176 + 5, 5));
    }

    @Test
    void arrowFramesAreTfcsSprites() throws Exception {
        BufferedImage a = png("anvil_plan_arrows");
        int w = 162;
        int h = 13;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < 9; x++) {
                // frame 1 (col 1, row 0): the left arrow at x 0; the right side empty
                assertThat(a.getRGB(w + x, y)).as("left %d,%d", x, y).isEqualTo(tfc.getRGB(201 + x, 3 + y));
                // frame 2 (col 0, row 1): the right arrow at x 153
                assertThat(a.getRGB(153 + x, h + y)).as("right %d,%d", x, y).isEqualTo(tfc.getRGB(212 + x, 3 + y));
                // frame 3: both
                assertThat(a.getRGB(w + 153 + x, h + y)).isEqualTo(tfc.getRGB(212 + x, 3 + y));
                assertThat(a.getRGB(w + x, h + y)).isEqualTo(tfc.getRGB(201 + x, 3 + y));
            }
        }
        assertThat(a.getRGB(20, 5) >>> 24).isZero();
    }
}
