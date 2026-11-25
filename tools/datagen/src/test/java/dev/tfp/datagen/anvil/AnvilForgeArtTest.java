/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.anvil;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.forge.AnvilScreenLayout;
import dev.tfp.core.forge.ForgeRule;
import dev.tfp.core.forge.ForgeStep;
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

/** PAR-METAL-05: the anvil forging screen art on the real checkout. */
class AnvilForgeArtTest {
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
        assertThat(AnvilForgeArt.available(checkout)).isTrue();
        out = new OutputTree();
        assertThat(AnvilForgeArt.run(checkout, out)).isEqualTo(6 + 6 + 2);
        tfc = ImageIO.read(new ByteArrayInputStream(checkout.read(AnvilForgeArt.TEXTURE)));
    }

    private static BufferedImage png(String name) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(out.get(AnvilForgeArt.RP + "textures/font/gui/" + name + ".png")));
    }

    @Test
    void backgroundIsTheFiveRowChestWithTheElementsOnItsGrid() throws Exception {
        BufferedImage bg = png("anvil");
        ChestGeometry chest = AnvilScreenLayout.CHEST;
        assertThat(bg.getWidth()).isEqualTo(176);
        assertThat(bg.getHeight()).isEqualTo(chest.height()).isEqualTo(204);
        // each TFC slot frame (item position - 1) was moved onto the chest slot that holds it: its corner pixels match
        for (int i = 0; i < AnvilScreenLayout.TFC_SLOTS.length; i++) {
            int[] from = AnvilScreenLayout.TFC_SLOTS[i];
            int slot = AnvilScreenLayout.CHEST_SLOTS[i];
            for (int[] p : new int[][] {{-1, -1}, {0, 0}, {16, 16}, {7, 8}}) {
                assertThat(bg.getRGB(chest.slotX(slot) + p[0], chest.slotY(slot) + p[1]))
                    .as("slot %d at %d,%d", i, p[0], p[1]).isEqualTo(tfc.getRGB(from[0] + p[0], from[1] + p[1]));
            }
        }
        // the player inventory frame moved onto the chest's rows
        assertThat(bg.getRGB(8, chest.playerRowY(0) - 1 + 3)).isEqualTo(tfc.getRGB(8, 83 + 41 + 1 + 3));
        assertThat(bg.getRGB(8, chest.hotbarY() - 1 + 3)).isEqualTo(tfc.getRGB(8, 141 + 41 + 1 + 3));
        // the bar did not move; the borders are TFC's
        assertThat(bg.getRGB(20, 103)).isEqualTo(tfc.getRGB(20, 103));
        assertThat(bg.getRGB(100, bg.getHeight() - 1)).isEqualTo(tfc.getRGB(100, 206));
    }

    @Test
    void theStepIconsAreOnTheButtons() throws Exception {
        BufferedImage bg = png("anvil");
        ChestGeometry chest = AnvilScreenLayout.CHEST;
        for (ForgeStep step : ForgeStep.VALUES) {
            int slot = AnvilScreenLayout.stepSlot(step);
            int x = chest.slotX(slot);
            int y = chest.slotY(slot);
            // the button's colour (red for a negative step, green for a positive one) is TFC's, moved by (9, -2)
            assertThat(bg.getRGB(x, y)).isEqualTo(tfc.getRGB(step.buttonX(), step.buttonY()));
            assertThat(step.buttonX() + AnvilScreenLayout.CENTRE_DX).isEqualTo(x);
            assertThat(step.buttonY() + AnvilScreenLayout.CENTRE_DY).isEqualTo(y);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void yamlDeclaresTheImagesAtTheirPositions() {
        Map<String, Object> root = new Yaml().load(new String(out.get(AnvilForgeArt.CONFIG), StandardCharsets.UTF_8));
        Map<String, Object> images = (Map<String, Object>) root.get("images");
        assertThat(images.keySet()).contains("tfp:gui/anvil", "tfp:gui/anvil_progress", "tfp:gui/anvil_target", "tfp:gui/anvil_rule_2",
            "tfp:gui/anvil_step_0", "tfp:gui/anvil_weld", "tfp:gui/anvil_plan_button");
        assertThat(((Map<String, Object>) images.get("tfp:gui/anvil_progress")).get("grid_size")).isEqualTo("13,12");
        assertThat(((Map<String, Object>) images.get("tfp:gui/anvil_progress")).get("ascent")).isEqualTo(13 - 104);
        assertThat(((Map<String, Object>) images.get("tfp:gui/anvil_rule_1")).get("ascent")).isEqualTo(13 - 11);
        assertThat(((Map<String, Object>) images.get("tfp:gui/anvil_rule_0")).get("grid_size")).isEqualTo("8,8");
    }

    @Test
    void theMarkerStripsPutTheSpriteAtItsOffset() throws Exception {
        BufferedImage s = png("anvil_progress");
        int cell = AnvilScreenLayout.BAR_WIDTH;
        // frame 0 at the left of cell (0, 0); frame 17 (row 1, column 5) with the sprite 17 pixels in
        assertThat(s.getRGB(0, 0)).isEqualTo(tfc.getRGB(176, 0));
        assertThat(s.getRGB(5 * cell + 17, 5)).isEqualTo(tfc.getRGB(176, 0));
        assertThat(s.getRGB(5 * cell + 16, 5)).isNotEqualTo(tfc.getRGB(176, 0));
    }

    @Test
    void ruleFramesAreTintedGreenWhenMatchedAndRedWhenNot() throws Exception {
        BufferedImage r = png("anvil_rule_0");
        var g = AnvilScreenLayout.ruleImage(0);
        ForgeRule rule = ForgeRule.HIT_LAST; // order LAST: overlay at (198, 0)
        int f = AnvilScreenLayout.ruleFrame(rule, true);
        int fx = g.frameCol(f) * g.width();
        int fy = g.frameRow(f) * g.height();
        // the overlay's top border row is black in TFC: it stays black under a tint; a light pixel turns green
        int lightX = -1;
        for (int x = 0; x < 20 && lightX < 0; x++) {
            int c = tfc.getRGB(198 + x, 2);
            if ((c >>> 24) == 255 && ((c >> 16) & 0xFF) > 150) {
                lightX = x;
            }
        }
        assertThat(lightX).isGreaterThanOrEqualTo(0);
        int green = r.getRGB(fx + lightX, fy + 2);
        assertThat((green >> 16) & 0xFF).isZero();
        assertThat((green >> 8) & 0xFF).isGreaterThan(80);
        int red = r.getRGB(g.frameCol(f - 1) * g.width() + lightX, g.frameRow(f - 1) * g.height() + 2);
        assertThat((red >> 16) & 0xFF).isGreaterThan(150);
        assertThat(red & 0xFF).isZero();
        assertThat(r.getRGB(0, 0) >>> 24).isZero(); // frame 0 is empty (the width marker is in its corner)
    }
}
