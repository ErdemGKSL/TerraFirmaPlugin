/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.vesselgui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.VesselScreenLayout;
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

/** PAR-POT-10: the small vessel screen art on the real checkout. */
class VesselScreenArtTest {
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
        assertThat(VesselScreenArt.available(checkout)).isTrue();
        out = new OutputTree();
        images = VesselScreenArt.run(checkout, out);
        tfc = VesselScreenArt.read(checkout.read(VesselScreenArt.TEXTURE), "small_inventory.png");
    }

    private static BufferedImage image(String packPath) {
        return VesselScreenArt.read(out.get(VesselScreenArt.RP + packPath), packPath);
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesTheBackgroundInTheGuiFont() {
        assertThat(images).isEqualTo(1);
        Map<String, Object> root = new Yaml().load(new String(out.get(VesselScreenArt.CONFIG), StandardCharsets.UTF_8));
        Map<String, Object> entries = (Map<String, Object>) root.get("images");
        assertThat(entries).containsOnlyKeys("tfp:gui/small_vessel");
        assertThat((Map<String, Object>) entries.get("tfp:gui/small_vessel")).containsEntry("font", "tfp:gui")
            .containsEntry("ascent", VesselScreenLayout.BACKGROUND.ascent());
    }

    @Test
    void everyTfcSlotFrameLandsUnderItsChestSlot() {
        BufferedImage bg = image(VesselScreenLayout.BACKGROUND.packPath());
        assertThat(bg.getWidth()).isEqualTo(ChestGeometry.WIDTH);
        assertThat(bg.getHeight()).isEqualTo(VesselScreenLayout.CHEST.height());
        ChestGeometry chest = VesselScreenLayout.CHEST;
        for (int x = 0; x < VesselScreenLayout.COLUMNS; x++) {
            for (int y = 0; y < VesselScreenLayout.ROWS; y++) {
                int tfcSlot = y * VesselScreenLayout.COLUMNS + x;
                int slot = VesselScreenLayout.chestSlot(tfcSlot);
                int tx = VesselScreenLayout.TFC_SLOT_X + 18 * x;
                int ty = VesselScreenLayout.TFC_SLOT_Y + 18 * y;
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
        BufferedImage bg = image(VesselScreenLayout.BACKGROUND.packPath());
        // TFC's frame at (70..105, 22..57) is gone from the right edge of the moved block: (100, 50) is panel grey
        assertThat(bg.getRGB(100, 50)).isEqualTo(tfc.getRGB(0 + 20, 76));
        // the chest rows below the block are panel too
        assertThat(bg.getRGB(70, 60)).isEqualTo(tfc.getRGB(70, 76));
    }
}
