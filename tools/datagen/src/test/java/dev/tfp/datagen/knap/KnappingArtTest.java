/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.knap;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.knap.KnappingScreen;
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
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/** PAR-PROC-01: the knapping screen art from the real TFC checkout. */
class KnappingArtTest {
    private static TfcCheckout tfc;
    private static OutputTree out;
    private static int count;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        out = new OutputTree();
        assertThat(KnappingArt.available(tfc)).isTrue();
        count = KnappingArt.run(tfc, out);
    }

    private static BufferedImage png(GuiImage image) {
        byte[] b = out.get(KnappingArt.RP + image.packPath());
        assertThat(b).as(image.id()).isNotNull();
        try {
            return ImageIO.read(new ByteArrayInputStream(b));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void oneBackgroundAndOneImagePerTextureAndRow() {
        assertThat(KnappingScreen.TEXTURES).hasSize(47);
        assertThat(count).isEqualTo(1 + 47 * 5);
    }

    @Test
    void everyImageHasItsDeclaredSize() {
        BufferedImage bg = png(KnappingScreen.BACKGROUND);
        assertThat(bg.getWidth()).isEqualTo(ChestGeometry.WIDTH);
        assertThat(bg.getHeight()).isEqualTo(222);
        for (GuiImage image : KnappingScreen.rowImages()) {
            BufferedImage p = png(image);
            assertThat(p.getWidth()).as(image.id()).isEqualTo(image.pngWidth());
            assertThat(p.getHeight()).as(image.id()).isEqualTo(16);
        }
        assertThat(KnappingScreen.cellImage("clay_ball", 0, 0).frames()).isEqualTo(2);
        assertThat(KnappingScreen.cellImage("leather", 0, 0).frames()).isEqualTo(1);
    }

    @Test
    void theOutputFrameAndPlayerSlotsSitUnderTheChestSlots() {
        BufferedImage bg = png(KnappingScreen.BACKGROUND);
        BufferedImage src = KnappingArt.read(tfc, KnappingArt.BACKGROUND);
        ChestGeometry chest = KnappingScreen.CHEST;
        // the output slot's frame: TFC's (127, 45) pixels at the chest output slot's frame corner
        int ox = chest.slotX(KnappingScreen.OUTPUT_SLOT) - 1;
        int oy = chest.slotY(KnappingScreen.OUTPUT_SLOT) - 1;
        for (int i = 0; i < 18; i++) {
            assertThat(bg.getRGB(ox + i, oy)).isEqualTo(src.getRGB(127 + i, 45));
            assertThat(bg.getRGB(ox + i, oy + 17)).isEqualTo(src.getRGB(127 + i, 62));
        }
        // the first player slot frame and the first hotbar frame
        assertThat(bg.getRGB(7, chest.playerRowY(0) - 1)).isEqualTo(src.getRGB(7, 103));
        assertThat(bg.getRGB(8, chest.playerRowY(0))).isEqualTo(src.getRGB(8, 104));
        assertThat(bg.getRGB(8, chest.hotbarY())).isEqualTo(src.getRGB(8, 162));
        // the grid area is plain panel (TFC draws only the buttons there)
        int panel = src.getRGB(50, 30);
        for (int i = 0; i < 25; i++) {
            int slot = KnappingScreen.cellSlot(i);
            assertThat(bg.getRGB(chest.slotX(slot) + 8, chest.slotY(slot) + 8)).isEqualTo(panel);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theYamlListsEveryImageWithItsAscent() {
        Map<String, Object> root = new Yaml(new LoaderOptions()).load(
            new String(out.get(KnappingArt.CONFIG), StandardCharsets.UTF_8));
        Map<String, Map<String, Object>> images = (Map<String, Map<String, Object>>) root.get("images");
        assertThat(images).hasSize(count);
        assertThat(images.get("tfp:gui/knapping")).containsEntry("ascent", 13).containsEntry("height", 222);
        Map<String, Object> clay = images.get("tfp:gui/knapping/clay_ball_r4");
        assertThat(clay).containsEntry("ascent", 13 - (18 + 18 * 4)).containsEntry("grid_size", "1,2")
            .containsEntry("file", "tfp:font/gui/knapping/clay_ball_r4.png");
        assertThat(images.get("tfp:gui/knapping/rock/loose/granite_r0")).doesNotContainKey("grid_size");
    }
}
