/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.powderkeg;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.powderkeg.PowderkegScreenLayout;
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

/** PAR-PROC-18: the powderkeg screen art on the real checkout. */
class PowderkegScreenArtTest {
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
        assertThat(PowderkegScreenArt.available(checkout)).isTrue();
        out = new OutputTree();
        images = PowderkegScreenArt.run(checkout, out);
        tfc = PowderkegScreenArt.read(checkout.read(PowderkegScreenArt.TEXTURE), "powderkeg.png");
    }

    private static BufferedImage image(String packPath) {
        return PowderkegScreenArt.read(out.get(PowderkegScreenArt.RP + packPath), packPath);
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesThreeImagesInTheGuiFont() {
        assertThat(images).isEqualTo(3);
        Map<String, Object> root = new Yaml().load(new String(out.get(PowderkegScreenArt.CONFIG), StandardCharsets.UTF_8));
        Map<String, Object> entries = (Map<String, Object>) root.get("images");
        assertThat(entries).containsOnlyKeys("tfp:gui/powderkeg", "tfp:gui/powderkeg_seal", "tfp:gui/powderkeg_disabled");
        Map<String, Object> seal = (Map<String, Object>) entries.get("tfp:gui/powderkeg_seal");
        assertThat(seal).containsEntry("grid_size", "2,1").containsEntry("font", "tfp:gui")
            .containsEntry("ascent", PowderkegScreenLayout.SEAL.ascent());
    }

    @Test
    void everyTfcSlotFrameLandsUnderItsChestSlot() {
        BufferedImage bg = image(PowderkegScreenLayout.BACKGROUND.packPath());
        assertThat(bg.getWidth()).isEqualTo(ChestGeometry.WIDTH);
        assertThat(bg.getHeight()).isEqualTo(PowderkegScreenLayout.CHEST.height());
        ChestGeometry chest = PowderkegScreenLayout.CHEST;
        for (int x = 0; x < PowderkegScreenLayout.COLUMNS; x++) {
            for (int y = 0; y < PowderkegScreenLayout.ROWS; y++) {
                int tfcSlot = x * PowderkegScreenLayout.ROWS + y;
                int slot = PowderkegScreenLayout.chestSlot(tfcSlot);
                int tx = PowderkegScreenLayout.TFC_SLOT_X + 18 * x;
                int ty = PowderkegScreenLayout.TFC_SLOT_Y + 18 * y;
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
    void sealSpritesAndDisabledHighlight() {
        BufferedImage seal = image(PowderkegScreenLayout.SEAL.packPath());
        for (int j = 0; j < 20; j++) {
            for (int i = 0; i < 20; i++) {
                assertThat(seal.getRGB(i, j)).isEqualTo(tfc.getRGB(236 + i, j));
                assertThat(seal.getRGB(i, 20 + j)).isEqualTo(tfc.getRGB(236 + i, 20 + j));
            }
        }
        BufferedImage dis = image(PowderkegScreenLayout.DISABLED.packPath());
        int covered = 0;
        for (int y = 0; y < dis.getHeight(); y++) {
            for (int x = 0; x < dis.getWidth(); x++) {
                if (dis.getRGB(x, y) == 0x80FFFFFF) {
                    covered++;
                }
            }
        }
        assertThat(covered).isEqualTo(12 * 16 * 16);
    }
}
