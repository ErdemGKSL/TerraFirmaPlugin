/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.nestbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.nestbox.NestBoxScreenLayout;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-FAUNA-07: the nest box screen art on the real checkout. */
class NestBoxScreenArtTest {
    private static OutputTree out;
    private static BufferedImage tfc;
    private static int images;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout checkout;
        try {
            checkout = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(NestBoxScreenArt.available(checkout)).isTrue();
        out = new OutputTree();
        images = NestBoxScreenArt.run(checkout, out);
        tfc = NestBoxScreenArt.read(checkout.read(NestBoxScreenArt.TEXTURE), "small_inventory.png");
    }

    private static BufferedImage image(String packPath) {
        return NestBoxScreenArt.read(out.get(NestBoxScreenArt.RP + packPath), packPath);
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesTheBackgroundInTheGuiFont() {
        assertThat(images).isEqualTo(1);
        Map<String, Object> root = new Yaml().load(new String(out.get(NestBoxScreenArt.CONFIG), StandardCharsets.UTF_8));
        Map<String, Object> entries = (Map<String, Object>) root.get("images");
        assertThat(entries).containsOnlyKeys("tfp:gui/nest_box");
        assertThat((Map<String, Object>) entries.get("tfp:gui/nest_box")).containsEntry("font", "tfp:gui")
            .containsEntry("ascent", NestBoxScreenLayout.BACKGROUND.ascent());
    }

    @Test
    void everyTfcSlotFrameLandsUnderItsChestSlot() {
        BufferedImage bg = image(NestBoxScreenLayout.BACKGROUND.packPath());
        assertThat(bg.getWidth()).isEqualTo(ChestGeometry.WIDTH);
        assertThat(bg.getHeight()).isEqualTo(NestBoxScreenLayout.CHEST.height());
        ChestGeometry chest = NestBoxScreenLayout.CHEST;
        for (int x = 0; x < NestBoxScreenLayout.COLUMNS; x++) {
            for (int y = 0; y < NestBoxScreenLayout.ROWS; y++) {
                int tfcSlot = y * NestBoxScreenLayout.COLUMNS + x;
                int slot = NestBoxScreenLayout.chestSlot(tfcSlot);
                int tx = NestBoxScreenLayout.TFC_SLOT_X + 18 * x;
                int ty = NestBoxScreenLayout.TFC_SLOT_Y + 18 * y;
                // the 18 x 18 frame, item position minus one, pixel for pixel
                for (int j = -1; j < 17; j++) {
                    for (int i = -1; i < 17; i++) {
                        assertThat(bg.getRGB(chest.slotX(slot) + i, chest.slotY(slot) + j))
                            .as("slot %d pixel (%d, %d)", tfcSlot, i, j).isEqualTo(tfc.getRGB(tx + i, ty + j));
                    }
                }
            }
        }
        // the player inventory: TFC's first slot (8, 84) on the chest's (8, playerRowY(0))
        for (int i = -1; i < 17; i++) {
            assertThat(bg.getRGB(8 + i, chest.playerRowY(0) + 3)).isEqualTo(tfc.getRGB(8 + i, 84 + 3));
            assertThat(bg.getRGB(8 + i, chest.hotbarY() + 3)).isEqualTo(tfc.getRGB(8 + i, 142 + 3));
        }
    }

    @Test
    void theOldSlotBlockPlaceIsPlainPanel() {
        BufferedImage bg = image(NestBoxScreenLayout.BACKGROUND.packPath());
        // TFC's frame at (70..105, 22..57) is gone from the right edge of the moved block: (100, 50) is panel grey
        assertThat(bg.getRGB(100, 50)).isEqualTo(tfc.getRGB(0 + 20, 76));
        // the chest rows below the block are panel too
        assertThat(bg.getRGB(70, 60)).isEqualTo(tfc.getRGB(70, 76));
    }
}
