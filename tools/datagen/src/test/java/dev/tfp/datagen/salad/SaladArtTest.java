/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.salad;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.salad.SaladScreen;
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

/** PAR-PROC-36: the salad screen art on the real checkout. */
class SaladArtTest {
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
        assertThat(SaladArt.available(checkout)).isTrue();
        out = new OutputTree();
        images = SaladArt.run(checkout, out);
        tfc = SaladArt.read(checkout.read(SaladArt.TEXTURE), "salad.png");
    }

    private static BufferedImage background() {
        return SaladArt.read(out.get(SaladArt.RP + SaladScreen.BACKGROUND.packPath()), "salad");
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesOneImageInTheGuiFont() {
        assertThat(images).isEqualTo(1);
        Map<String, Object> root = new Yaml().load(new String(out.get(SaladArt.CONFIG), StandardCharsets.UTF_8));
        Map<String, Object> entries = (Map<String, Object>) root.get("images");
        assertThat(entries).containsOnlyKeys("tfp:gui/salad");
        Map<String, Object> e = (Map<String, Object>) entries.get("tfp:gui/salad");
        assertThat(e).containsEntry("font", "tfp:gui").containsEntry("file", SaladScreen.BACKGROUND.file())
            .containsEntry("height", SaladScreen.BACKGROUND.height())
            .containsEntry("ascent", SaladScreen.BACKGROUND.ascent());
    }

    @Test
    void pngIsTheChestWindow() {
        BufferedImage bg = background();
        assertThat(bg.getWidth()).isEqualTo(ChestGeometry.WIDTH).isEqualTo(176);
        assertThat(bg.getHeight()).isEqualTo(SaladScreen.CHEST.height()).isEqualTo(168);
    }

    @Test
    void bandMoves() {
        assertThat(SaladScreen.DY_INPUTS).isEqualTo(-6);
        assertThat(SaladScreen.DY_BOWLS).isEqualTo(-2);
        assertThat(SaladScreen.DY_PLAYER).isEqualTo(1);
    }

    @Test
    void everyTfcSlotFrameLandsUnderItsChestSlot() {
        BufferedImage bg = background();
        ChestGeometry chest = SaladScreen.CHEST;
        for (int t = SaladScreen.SLOT_INPUT_START; t <= SaladScreen.SLOT_OUTPUT; t++) {
            int tx;
            int ty;
            if (t <= SaladScreen.SLOT_INPUT_END) {
                tx = SaladScreen.TFC_INPUT_X + 18 * t;
                ty = SaladScreen.TFC_INPUT_Y;
            } else {
                tx = t == SaladScreen.SLOT_BOWLS ? SaladScreen.TFC_BOWL_X : SaladScreen.TFC_OUTPUT_X;
                ty = SaladScreen.TFC_BOWL_Y;
            }
            int slot = SaladScreen.chestSlot(t);
            assertThat(chest.slotX(slot)).isEqualTo(tx);
            for (int j = -1; j < 17; j++) {
                for (int i = -1; i < 17; i++) {
                    assertThat(bg.getRGB(chest.slotX(slot) + i, chest.slotY(slot) + j))
                        .as("slot %d pixel (%d, %d)", t, i, j).isEqualTo(tfc.getRGB(tx + i, ty + j));
                }
            }
        }
        // the player inventory and hotbar: TFC's (8, 84) and (8, 142) on the chest's rows
        for (int i = -1; i < 17; i++) {
            for (int j = -1; j < 17; j++) {
                assertThat(bg.getRGB(8 + i, chest.playerRowY(0) + j)).isEqualTo(tfc.getRGB(8 + i, 84 + j));
                assertThat(bg.getRGB(8 + i, chest.hotbarY() + j)).isEqualTo(tfc.getRGB(8 + i, 142 + j));
            }
        }
    }

    @Test
    void bowlPictureAndBracketAreKept() {
        BufferedImage bg = background();
        int bowlPicture = 0;
        int bracket = 0;
        for (int x = 0; x < 176; x++) {
            for (int y = 42; y < 52; y++) {
                if (tfc.getRGB(x, y) != tfc.getRGB(x, SaladScreen.PLAIN_ROW)) {
                    bracket++;
                    assertThat(bg.getRGB(x, y + SaladScreen.DY_INPUTS)).isEqualTo(tfc.getRGB(x, y));
                }
            }
            for (int y = 52; y < 78; y++) {
                if (tfc.getRGB(x, y) != tfc.getRGB(x, SaladScreen.PLAIN_ROW)) {
                    bowlPicture++;
                    assertThat(bg.getRGB(x, y + SaladScreen.DY_BOWLS)).isEqualTo(tfc.getRGB(x, y));
                }
            }
        }
        assertThat(bracket).isPositive();
        assertThat(bowlPicture).isPositive();
    }
}
