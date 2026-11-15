/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.firepitgui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.ProgressFrames;
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

/** PAR-HEAT-20: the firepit screen art on the real checkout. */
class FirepitScreenArtTest {
    private static final int PANEL = 0xFFC6C6C6;
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
        assertThat(FirepitScreenArt.available(checkout)).isTrue();
        out = new OutputTree();
        images = FirepitScreenArt.run(checkout, out);
        tfc = FirepitScreenArt.read(checkout.read(FirepitScreenArt.TEXTURE), "fire_pit.png");
    }

    private static BufferedImage image(String packPath) {
        return FirepitScreenArt.read(out.get(FirepitScreenArt.RP + packPath), packPath);
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesTheBackgroundAndThermometerInTheGuiFont() {
        assertThat(images).isEqualTo(2);
        Map<String, Object> root = new Yaml().load(new String(out.get(FirepitScreenArt.CONFIG), StandardCharsets.UTF_8));
        Map<String, Object> entries = (Map<String, Object>) root.get("images");
        assertThat(entries).containsOnlyKeys("tfp:gui/firepit", "tfp:gui/firepit_thermometer");
        assertThat((Map<String, Object>) entries.get("tfp:gui/firepit")).containsEntry("height", 186)
            .containsEntry("font", "tfp:gui").containsEntry("file", "tfp:font/gui/firepit.png")
            .doesNotContainKey("grid_size");
        assertThat((Map<String, Object>) entries.get("tfp:gui/firepit_thermometer")).containsEntry("grid_size", "4,13")
            .containsEntry("height", ProgressFrames.THERMOMETER_FRAME_HEIGHT)
            .containsEntry("ascent", FirepitScreenArt.THERMOMETER.ascent());
        assertThat(FirepitScreenArt.THERMOMETER.y()).as("TFC 76 - 51, moved down 2").isEqualTo(27);
    }

    @Test
    void everyTfcSlotFrameLandsUnderItsChestSlot() {
        BufferedImage bg = image(FirepitScreenArt.BACKGROUND.packPath());
        ChestGeometry chest = FirepitScreenArt.CHEST;
        assertThat(bg.getWidth()).isEqualTo(ChestGeometry.WIDTH);
        assertThat(bg.getHeight()).isEqualTo(chest.height()).isEqualTo(FirepitScreenArt.TFC_HEIGHT);
        for (int s = 0; s < 7; s++) {
            int[] p = FirepitScreenArt.TFC_SLOTS.get(s);
            int slot = FirepitScreenArt.CHEST_SLOTS.get(s);
            for (int j = -1; j < 17; j++) {
                for (int i = -1; i < 17; i++) {
                    assertThat(bg.getRGB(chest.slotX(slot) + i, chest.slotY(slot) + j))
                        .as("slot %d pixel (%d, %d)", s, i, j).isEqualTo(tfc.getRGB(p[0] + i, p[1] + j));
                }
            }
        }
        // TFC's player inventory and hotbar (y 104 and 162) on the chest's (103 and 161)
        assertThat(FirepitScreenArt.DY_PLAYER).isEqualTo(-1);
        assertThat(chest.hotbarY()).isEqualTo(162 + FirepitScreenArt.DY_PLAYER);
        for (int y = chest.playerRowY(0) - 1; y < chest.height() - 5; y++) {
            for (int x = 0; x < ChestGeometry.WIDTH; x++) {
                assertThat(bg.getRGB(x, y)).as("(%d, %d)", x, y).isEqualTo(tfc.getRGB(x, y - FirepitScreenArt.DY_PLAYER));
            }
        }
        // the thermometer tube moved down with the fuel column
        for (int y = 14; y < 88; y++) {
            for (int x = 29; x < 46; x++) {
                assertThat(bg.getRGB(x, y + FirepitScreenArt.DY)).as("thermometer (%d, %d)", x, y).isEqualTo(tfc.getRGB(x, y));
            }
        }
    }

    @Test
    void theChestSlotsTfcDoesNotHaveShowPlainPanelOrTheThermometer() {
        BufferedImage bg = image(FirepitScreenArt.BACKGROUND.packPath());
        ChestGeometry chest = FirepitScreenArt.CHEST;
        int covered = 0;
        for (int slot = 0; slot < chest.size(); slot++) {
            if (FirepitScreenArt.CHEST_SLOTS.contains(slot) || slot % 9 == 1) {
                continue; // TFC's slots, and the thermometer column
            }
            // a slot frame has its dark (55, 55, 55) top-left corner; an uncovered slot has no art at all
            assertThat(bg.getRGB(chest.slotX(slot) - 1, chest.slotY(slot) - 1)).as("slot %d corner", slot).isNotEqualTo(0xFF373737);
            assertThat(bg.getRGB(chest.slotX(slot) + 8, chest.slotY(slot) + 8) >>> 24).as("slot %d opaque", slot).isEqualTo(0xFF);
            covered++;
        }
        assertThat(covered).isEqualTo(36 - 7 - 4);
        assertThat(bg.getRGB(150, 60)).isEqualTo(PANEL);
    }

    @Test
    void theArrowsPointFromTheInputToEachOutput() {
        BufferedImage bg = image(FirepitScreenArt.BACKGROUND.packPath());
        int ink = tfc.getRGB(82, 47);
        // TFC's arrow head (rows 52 to 54 of the source) right above each output's centre, one plain row above the frame
        for (int x : new int[] {69, 106}) {
            assertThat(bg.getRGB(x, 51)).as("tip at %d", x).isEqualTo(ink);
            assertThat(bg.getRGB(x - 1, 50)).isEqualTo(ink);
            assertThat(bg.getRGB(x + 1, 50)).isEqualTo(ink);
            assertThat(bg.getRGB(x, 52)).as("gap above the frame").isEqualTo(PANEL);
            assertThat(bg.getRGB(x, 43)).as("bar").isEqualTo(ink);
        }
        for (int x : new int[] {82, 93}) {
            assertThat(bg.getRGB(x, 35)).as("gap under the input").isEqualTo(PANEL);
            assertThat(bg.getRGB(x, 36)).as("stem top").isEqualTo(ink);
        }
    }

    @Test
    void everyThermometerFrameKeepsItsFullAdvance() {
        BufferedImage strip = image(FirepitScreenArt.THERMOMETER.packPath());
        var t = FirepitScreenArt.THERMOMETER;
        for (int f = 0; f < t.frames(); f++) {
            int x = t.frameCol(f) * t.width() + t.width() - 1;
            boolean any = false;
            for (int y = t.frameRow(f) * t.height(); y < (t.frameRow(f) + 1) * t.height(); y++) {
                any |= (strip.getRGB(x, y) >>> 24) != 0;
            }
            assertThat(any).as("frame %d has a pixel in its right column", f).isTrue();
        }
    }

    @Test
    void thermometerFramesMoveTheIndicatorUpOnePixelPerFrame() {
        BufferedImage strip = image(FirepitScreenArt.THERMOMETER.packPath());
        var t = FirepitScreenArt.THERMOMETER;
        assertThat(strip.getWidth()).isEqualTo(13 * 15);
        assertThat(strip.getHeight()).isEqualTo(4 * 56);
        for (int y = 0; y < t.height(); y++) {
            for (int x = 0; x < t.width(); x++) {
                boolean marker = x == t.width() - 1 && y == t.height() - 1;
                assertThat(strip.getRGB(x, y)).as("frame 0 is empty but for the width marker")
                    .isEqualTo(marker ? FirepitScreenArt.MARKER : 0);
            }
        }
        for (int k = 1; k < ProgressFrames.THERMOMETER_FRAMES; k++) {
            int fx = t.frameCol(k) * t.width();
            int fy = t.frameRow(k) * t.height();
            for (int j = 0; j < t.height(); j++) {
                for (int i = 0; i < t.width(); i++) {
                    int dy = j - (ProgressFrames.THERMOMETER_TRAVEL - k);
                    int expected = dy >= 0 && dy < 5 ? tfc.getRGB(176 + i, dy) : 0;
                    if ((expected >>> 24) == 0) {
                        assertThat(strip.getRGB(fx + i, fy + j) >>> 24).as("frame %d (%d, %d)", k, i, j).isLessThanOrEqualTo(1);
                    } else {
                        assertThat(strip.getRGB(fx + i, fy + j)).as("frame %d (%d, %d)", k, i, j).isEqualTo(expected);
                    }
                }
            }
        }
    }
}
